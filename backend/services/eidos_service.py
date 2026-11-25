"""Eidos speaker embedding extraction and verification service."""

import json
import logging
import uuid
from typing import Any, Optional
from urllib import error as urllib_error
from urllib import request as urllib_request

import numpy as np

from config import Config
from models.verification import EidosResult
from services.storage_service import StorageService
from utils.multipart import encode_multipart_formdata

logger = logging.getLogger(__name__)


class EidosService:
    """
    Extracts 256-dim speaker embeddings and performs cosine-similarity speaker verification.
    Runs in PLACEHOLDER mode when EIDOS_API_URL is unconfigured.
    """

    EMBEDDING_DIM = 256

    def __init__(
        self,
        storage_service: Optional[StorageService] = None,
        api_url: Optional[str] = None,
        api_key: Optional[str] = None,
        req_key: Optional[str] = None,
        match_threshold: Optional[float] = None,
        timeout_s: float = 15.0,
    ) -> None:
        self.storage = storage_service if storage_service is not None else StorageService()
        self.api_url = (Config.EIDOS_API_URL if api_url is None else api_url).strip()
        self.api_key = (Config.EIDOS_API_KEY if api_key is None else api_key).strip()
        self.req_key = (Config.REQ_KEY if req_key is None else req_key).strip()
        self.match_threshold = (
            Config.EIDOS_MATCH_THRESHOLD if match_threshold is None else match_threshold
        )
        self.timeout_s = timeout_s
        self.is_placeholder = not (bool(self.api_url) and bool(self.api_key))

    def _remote_endpoint(self, action: str) -> str:
        """Resolve /v1/speakers/<action> from EIDOS_API_URL."""
        base = self.api_url.rstrip("/")
        if base.endswith(f"/{action}"):
            return base
        if base.endswith("/speakers"):
            return f"{base}/{action}"
        return f"{base}/v1/speakers/{action}"

    def _post_multipart(
        self,
        url: str,
        fields: dict[str, str],
        audio_bytes: bytes,
        filename: str = "audio.wav",
    ) -> dict[str, Any]:
        """POST multipart/form-data with Bearer auth and Idempotency-Key."""
        payload, content_type = encode_multipart_formdata(
            fields=fields,
            files={"file": (filename, audio_bytes, "audio/wav")},
        )
        headers = {
            "Content-Type": content_type,
            "Idempotency-Key": str(uuid.uuid4()),
        }
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        if self.req_key:
            headers["X-Request-Key"] = self.req_key

        req = urllib_request.Request(url, data=payload, headers=headers, method="POST")
        try:
            with urllib_request.urlopen(req, timeout=self.timeout_s) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib_error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            logger.error("Eidos API HTTP %s (%s): %s", exc.code, url, err_body)
            raise RuntimeError(f"Eidos API returned HTTP {exc.code}") from exc
        except (urllib_error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            logger.error("Eidos API request failed (%s): %s", url, exc)
            raise RuntimeError(f"Eidos API communication error: {exc}") from exc

    def enroll_speaker(self, audio_bytes: bytes, speaker_id: str, name: str) -> None:
        """Enroll a speaker remotely on Detectif.ai (/v1/speakers/enroll) when configured."""
        if self.is_placeholder:
            return
        url = self._remote_endpoint("enroll")
        self._post_multipart(
            url=url,
            fields={"speaker_id": speaker_id, "name": name},
            audio_bytes=audio_bytes,
            filename="enroll.wav",
        )

    def extract_embedding(self, audio_bytes: bytes) -> np.ndarray:
        """Extract a normalized 256-dimensional float32 speaker embedding."""
        if not audio_bytes:
            raise ValueError("Cannot extract embedding from empty audio bytes.")

        if self.is_placeholder:
            rng = np.random.default_rng()
            vec = rng.standard_normal(self.EMBEDDING_DIM).astype(np.float32)
            return self._l2_normalize(vec)

        return self._extract_remote_embedding(audio_bytes)

    def _extract_remote_embedding(self, audio_bytes: bytes) -> np.ndarray:
        """Call remote Eidos API to obtain a 256-dim speaker embedding."""
        payload = json.dumps(
            {
                "audio_base64": base64.b64encode(audio_bytes).decode("ascii"),
            }
        ).encode("utf-8")

        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        if self.req_key:
            headers["X-Request-Key"] = self.req_key

        req = urllib_request.Request(
            self.api_url,
            data=payload,
            headers=headers,
            method="POST",
        )

        try:
            with urllib_request.urlopen(req, timeout=self.timeout_s) as resp:
                data: dict[str, Any] = json.loads(resp.read().decode("utf-8"))
        except urllib_error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            logger.error("Eidos API HTTP %s: %s", exc.code, err_body)
            raise RuntimeError(f"Eidos API returned HTTP {exc.code}") from exc
        except (urllib_error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            logger.error("Eidos API request failed: %s", exc)
            raise RuntimeError(f"Eidos API communication error: {exc}") from exc

        embedding_raw = data.get("embedding")
        if not isinstance(embedding_raw, list) or len(embedding_raw) != self.EMBEDDING_DIM:
            raise RuntimeError(
                f"Eidos API returned invalid embedding (expected {self.EMBEDDING_DIM} dimensions)."
            )

        vec = np.asarray(embedding_raw, dtype=np.float32)
        return self._l2_normalize(vec)

    @staticmethod
    def _l2_normalize(vec: np.ndarray) -> np.ndarray:
        """Normalize a vector to unit L2 norm."""
        norm = float(np.linalg.norm(vec))
        if norm == 0.0:
            fallback = np.ones(vec.shape, dtype=np.float32)
            return fallback / float(np.linalg.norm(fallback))
        return (vec / norm).astype(np.float32)

    @staticmethod
    def compare_embeddings(e1: np.ndarray, e2: np.ndarray) -> float:
        """Compute cosine similarity between two embedding vectors in [-1.0, 1.0]."""
        v1 = np.asarray(e1, dtype=np.float32).flatten()
        v2 = np.asarray(e2, dtype=np.float32).flatten()
        if v1.size == 0 or v2.size == 0:
            raise ValueError("Embeddings must not be empty.")
        if v1.shape != v2.shape:
            raise ValueError(f"Embedding dimension mismatch: {v1.shape} vs {v2.shape}")

        denom = float(np.linalg.norm(v1) * np.linalg.norm(v2))
        if denom == 0.0:
            return 0.0
        sim = float(np.dot(v1, v2) / denom)
        return float(np.clip(sim, -1.0, 1.0))

    def verify_speaker(
        self,
        audio_bytes: bytes,
        enrollment_id: Optional[str] = None,
        phone_number: Optional[str] = None,
    ) -> EidosResult:
        """
        Verify call audio against a specific enrolled voiceprint (by enrollment_id)
        or search active enrollments (filtered by phone_number if available).
        """
        if not self.is_placeholder:
            return self._verify_speaker_remote(
                audio_bytes=audio_bytes,
                enrollment_id=enrollment_id,
                phone_number=phone_number,
            )

        probe_embedding = self.extract_embedding(audio_bytes)

        if enrollment_id:
            stored = self.storage.load_embedding(enrollment_id)
            record = self.storage.get_enrollment(enrollment_id)
            if stored is None:
                logger.warning("Enrollment ID %s not found during verification.", enrollment_id)
                return EidosResult(
                    match_score=0.0,
                    matched_name=None,
                    threshold=self.match_threshold,
                    is_match=False,
                )

            similarity = self.compare_embeddings(probe_embedding, stored)
            match_score = round(max(0.0, min(1.0, similarity)), 4)
            is_match = match_score >= self.match_threshold
            return EidosResult(
                match_score=match_score,
                matched_name=record.name if (is_match and record is not None) else None,
                threshold=self.match_threshold,
                is_match=is_match,
            )

        # No explicit enrollment_id: match against active enrollments (prefer matching phone_number)
        enrollments = self.storage.list_enrollments(active_only=True)
        if phone_number:
            phone_matches = [r for r in enrollments if r.phone_number == phone_number]
            if phone_matches:
                enrollments = phone_matches

        if not enrollments:
            return EidosResult(
                match_score=0.0,
                matched_name=None,
                threshold=self.match_threshold,
                is_match=False,
            )

        best_score = 0.0
        best_name: Optional[str] = None
        for record in enrollments:
            stored = self.storage.load_embedding(record.enrollment_id)
            if stored is None:
                continue
            similarity = self.compare_embeddings(probe_embedding, stored)
            score = round(max(0.0, min(1.0, similarity)), 4)
            if score >= best_score:
                best_score = score
                best_name = record.name

        is_match = best_score >= self.match_threshold
        return EidosResult(
            match_score=best_score,
            matched_name=best_name if is_match else None,
            threshold=self.match_threshold,
            is_match=is_match,
        )

    def _verify_speaker_remote(
        self,
        audio_bytes: bytes,
        enrollment_id: Optional[str] = None,
        phone_number: Optional[str] = None,
    ) -> EidosResult:
        """Verify audio against Detectif.ai /v1/speakers/verify."""
        candidates = []
        if enrollment_id:
            rec = self.storage.get_enrollment(enrollment_id)
            if rec is not None:
                candidates = [rec]
        else:
            enrollments = self.storage.list_enrollments(active_only=True)
            if phone_number:
                phone_matches = [r for r in enrollments if r.phone_number == phone_number]
                candidates = phone_matches or enrollments
            else:
                candidates = enrollments

        if not candidates:
            return EidosResult(
                match_score=0.0,
                matched_name=None,
                threshold=self.match_threshold,
                is_match=False,
            )

        url = self._remote_endpoint("verify")
        best_score = 0.0
        best_name: Optional[str] = None
        best_match = False

        for record in candidates:
            data = self._post_multipart(
                url=url,
                fields={"speaker_id": record.enrollment_id},
                audio_bytes=audio_bytes,
                filename="verify.wav",
            )
            result_obj = data.get("result") if isinstance(data.get("result"), dict) else data
            similarity = float(result_obj.get("similarity", 0.0))
            score = round(max(0.0, min(1.0, similarity)), 4)
            decision = str(result_obj.get("decision", "")).strip().lower()
            matched = decision == "match" if decision else score >= self.match_threshold

            if score >= best_score:
                best_score = score
                best_name = record.name
                best_match = matched

        return EidosResult(
            match_score=best_score,
            matched_name=best_name if best_match else None,
            threshold=self.match_threshold,
            is_match=best_match,
        )
