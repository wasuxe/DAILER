"""Enrollment blueprint: enroll, list, and delete relative voiceprints."""

import logging
import uuid
from typing import Any

from flask import Blueprint, current_app, jsonify, request
from pydantic import ValidationError

from models.enrollment import EnrollmentRequest, EnrollmentResponse
from utils.audio import decode_base64_audio, validate_audio

logger = logging.getLogger(__name__)

enroll_bp = Blueprint("enroll", __name__)


def _services() -> dict[str, Any]:
    return current_app.extensions["services"]


@enroll_bp.route("/enroll", methods=["POST"])
def enroll_speaker():
    """Validate enrollment request, extract speaker embedding, encrypt and store it."""
    payload = request.get_json(silent=True)
    if payload is None or not isinstance(payload, dict):
        return jsonify({"success": False, "error": "Invalid or missing JSON body."}), 400

    try:
        req = EnrollmentRequest(**payload)
    except ValidationError as exc:
        return jsonify({"success": False, "error": "Validation failed.", "details": exc.errors()}), 422

    try:
        audio_bytes = decode_base64_audio(req.audio_base64)
        max_duration = float(current_app.config.get("ENROLLMENT_DURATION_S", 10.0))
        # Allow up to MAX_AUDIO_DURATION_S if enrollment clip slightly exceeds target duration
        hard_limit = max(max_duration, float(current_app.config.get("MAX_AUDIO_DURATION_S", 30.0)))
        validate_audio(audio_bytes, max_duration_s=hard_limit)
    except ValueError as exc:
        return jsonify({"success": False, "error": str(exc)}), 400

    services = _services()
    eidos = services["eidos"]
    storage = services["storage"]

    try:
        enrollment_id = str(uuid.uuid4())
        eidos.enroll_speaker(
            audio_bytes=audio_bytes,
            speaker_id=enrollment_id,
            name=req.name,
        )
        embedding = eidos.extract_embedding(audio_bytes) if eidos.is_placeholder else []
        storage.save_embedding(
            enrollment_id=enrollment_id,
            embedding=embedding,
            metadata={
                "name": req.name,
                "phone_number": req.phone_number,
                "relationship": req.relationship,
            },
        )
    except Exception as exc:
        logger.exception("Failed to enroll speaker: %s", exc)
        return jsonify({"success": False, "error": "Failed to process voice enrollment."}), 500

    resp = EnrollmentResponse(
        success=True,
        enrollment_id=enrollment_id,
        message=f"Voiceprint enrolled for {req.name} ({req.relationship}).",
    )
    return jsonify(resp.model_dump(mode="json")), 201


@enroll_bp.route("/enrollment/<enrollment_id>", methods=["DELETE"])
def delete_enrollment(enrollment_id: str):
    """Delete an enrolled speaker's voiceprint by enrollment ID."""
    if not enrollment_id or not enrollment_id.strip():
        return jsonify({"success": False, "error": "Invalid enrollment ID."}), 400

    storage = _services()["storage"]
    deleted = storage.delete_embedding(enrollment_id.strip())
    if not deleted:
        return jsonify({"success": False, "error": "Enrollment not found."}), 404

    return (
        jsonify(
            {
                "success": True,
                "enrollment_id": enrollment_id,
                "message": "Enrollment deleted successfully.",
            }
        ),
        200,
    )


@enroll_bp.route("/enrollments", methods=["GET"])
def list_enrollments():
    """List all active enrolled voiceprints."""
    storage = _services()["storage"]
    records = storage.list_enrollments(active_only=True)
    return (
        jsonify(
            {
                "enrollments": [record.model_dump(mode="json") for record in records],
                "count": len(records),
            }
        ),
        200,
    )
