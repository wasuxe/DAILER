"""Call verification blueprint: parallel Aletheia + Eidos analysis and verdict computation."""

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import logging
from typing import Any
import uuid

from flask import Blueprint, current_app, jsonify, request
from pydantic import ValidationError

from models.verification import VerifyRequest, VerifyResponse
from utils.audio import decode_base64_audio, validate_audio

logger = logging.getLogger(__name__)

verify_bp = Blueprint("verify", __name__)


def _services() -> dict[str, Any]:
    return current_app.extensions["services"]


@verify_bp.route("/verify", methods=["POST"])
def verify_call():
    """
    Verify an incoming call audio sample by running Aletheia synthetic detection
    and Eidos speaker verification in parallel, computing the final verdict,
    and cleaning up expired temporary audio.
    """
    payload = request.get_json(silent=True)
    if payload is None or not isinstance(payload, dict):
        return jsonify({"error": "Invalid or missing JSON body."}), 400

    try:
        req = VerifyRequest(**payload)
    except ValidationError as exc:
        return jsonify({"error": "Validation failed.", "details": exc.errors()}), 422

    try:
        audio_bytes = decode_base64_audio(req.audio_base64)
        max_duration = float(current_app.config.get("MAX_AUDIO_DURATION_S", 30.0))
        validate_audio(audio_bytes, max_duration_s=max_duration)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 400

    services = _services()
    aletheia = services["aletheia"]
    eidos = services["eidos"]
    storage = services["storage"]
    verdict_service = services["verdict"]

    call_id = str(uuid.uuid4())
    try:
        storage.save_temp_audio(audio_bytes, call_id)
    except Exception as exc:
        logger.warning("Failed to persist temp audio for call_id=%s: %s", call_id, exc)

    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            aletheia_future = executor.submit(aletheia.analyze, audio_bytes)
            eidos_future = executor.submit(
                eidos.verify_speaker,
                audio_bytes,
                req.claimed_relative_id,
                req.phone_number,
            )
            aletheia_result = aletheia_future.result()
            eidos_result = eidos_future.result()
    except Exception as exc:
        logger.exception("Verification pipeline failed for call_id=%s: %s", call_id, exc)
        return jsonify({"error": "Voice verification failed.", "details": str(exc)}), 502

    verdict, message = verdict_service.compute_verdict(aletheia_result, eidos_result)

    try:
        retention_hours = float(current_app.config.get("CALL_AUDIO_RETENTION_HOURS", 24.0))
        storage.cleanup_expired_audio(retention_hours)
    except Exception as exc:
        logger.warning("Expired audio cleanup failed: %s", exc)

    response = VerifyResponse(
        verified=(verdict == "verified"),
        aletheia_result=aletheia_result,
        eidos_result=eidos_result,
        verdict=verdict,
        message=message,
        timestamp=datetime.now(timezone.utc),
    )
    return jsonify(response.model_dump(mode="json")), 200
