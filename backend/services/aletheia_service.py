"""Aletheia synthetic/AI voice detection service."""

import json
import logging
import random
import uuid
from typing import Any, Optional
from urllib import error as urllib_error
from urllib import request as urllib_request

from config import Config
from models.verification import AletheiaResult
from utils.multipart import encode_multipart_formdata

logger = logging.getLogger(__name__)


class AletheiaService:
    """
    Detects whether an audio sample is natural human speech or AI-generated.
    Runs in PLACEHOLDER mode when ALETHEIA_API_URL is unconfigured.
    """

    def __init__(
        self,
        api_url: Optional[str] = None,
        api_key: Optional[str] = None,
        req_key: Optional[str] = None,
        human_threshold: Optional[float] = None,
        timeout_s: float = 15.0,
    ) -> None:
        self.api_url = (Config.ALETHEIA_API_URL if api_url is None else api_url).strip()
        self.api_key = (Config.ALETHEIA_API_KEY if api_key is None else api_key).strip()
        self.req_key = (Config.REQ_KEY if req_key is None else req_key).strip()
        self.human_threshold = (
            Config.ALETHEIA_HUMAN_THRESHOLD if human_threshold is None else human_threshold
        )
        self.timeout_s = timeout_s
        self.is_placeholder = not (bool(self.api_url) and bool(self.api_key))

    def analyze(self, audio_bytes: bytes) -> AletheiaResult:
        """Analyze raw audio bytes and return an AletheiaResult."""
        if not audio_bytes:
            raise ValueError("Cannot analyze empty audio bytes.")

        if self.is_placeholder:
            return self._analyze_placeholder()

        return self._analyze_remote(audio_bytes)

    def _analyze_placeholder(self) -> AletheiaResult:
        """Placeholder inference returning is_human=True ~85% of the time with confidence 0.6-0.99."""
        is_human = random.random() < 0.85
        confidence = round(random.uniform(0.60, 0.99), 4)
        complement = round(1.0 - confidence, 4)

        raw_scores = {
            "human": confidence if is_human else complement,
            "synthetic": complement if is_human else confidence,
            "placeholder": 1.0,
        }
        logger.debug(
            "Aletheia placeholder result: is_human=%s confidence=%.4f",
            is_human,
            confidence,
        )
        return AletheiaResult(
            is_human=is_human,
            confidence=confidence,
            raw_scores=raw_scores,
        )

    def _analyze_remote(self, audio_bytes: bytes) -> AletheiaResult:
        """Send audio to the Detectif.ai Aletheia endpoint (/v1/deepfake/detect)."""
        payload, content_type = encode_multipart_formdata(
            fields={},
            files={"file": ("call.wav", audio_bytes, "audio/wav")},
        )

        headers = {
            "Content-Type": content_type,
            "Idempotency-Key": str(uuid.uuid4()),
        }
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
                body = resp.read().decode("utf-8")
                data: dict[str, Any] = json.loads(body)
        except urllib_error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            logger.error("Aletheia API HTTP %s: %s", exc.code, err_body)
            raise RuntimeError(f"Aletheia API returned HTTP {exc.code}") from exc
        except (urllib_error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            logger.error("Aletheia API request failed: %s", exc)
            raise RuntimeError(f"Aletheia API communication error: {exc}") from exc

        # Detectif.ai returns {"result": {"verdict": "real"|"fake", "score": <synthetic_risk>}}
        result_obj = data.get("result") if isinstance(data.get("result"), dict) else data
        if "verdict" in result_obj:
            verdict_str = str(result_obj.get("verdict", "")).strip().lower()
            synthetic_score = float(result_obj.get("score", 0.0))
            synthetic_score = max(0.0, min(1.0, synthetic_score))
            is_human = verdict_str == "real"
            human_confidence = round(1.0 - synthetic_score, 4)
            return AletheiaResult(
                is_human=is_human,
                confidence=human_confidence,
                raw_scores={
                    "human": human_confidence,
                    "synthetic": round(synthetic_score, 4),
                },
            )

        raw_scores_raw = data.get("raw_scores", {})
        raw_scores = {
            str(k): float(v)
            for k, v in raw_scores_raw.items()
            if isinstance(v, (int, float))
        }
        confidence = float(data.get("confidence", raw_scores.get("human", 0.0)))
        confidence = max(0.0, min(1.0, round(confidence, 4)))

        if "is_human" in data:
            is_human = bool(data["is_human"])
        else:
            human_score = float(raw_scores.get("human", confidence))
            is_human = human_score >= self.human_threshold

        return AletheiaResult(
            is_human=is_human,
            confidence=confidence,
            raw_scores=raw_scores,
        )
