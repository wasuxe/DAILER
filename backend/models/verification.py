"""Pydantic schemas for real-time call verification requests and responses."""

from datetime import datetime, timezone
from typing import Optional
from pydantic import BaseModel, Field, field_validator


class VerifyRequest(BaseModel):
    """Incoming payload to verify a call audio snippet."""

    audio_base64: str = Field(..., min_length=1, description="Base64-encoded call audio snippet")
    phone_number: str = Field(..., min_length=3, max_length=32, description="Caller phone number")
    claimed_relative_id: Optional[str] = Field(
        default=None,
        description="Optional enrollment ID of the relative the caller claims to be",
    )

    @field_validator("phone_number", "claimed_relative_id", mode="before")
    @classmethod
    def _strip_optional_strings(cls, value: object) -> object:
        if isinstance(value, str):
            stripped = value.strip()
            return stripped or None
        return value


class AletheiaResult(BaseModel):
    """Result from Aletheia synthetic/deepfake voice detection."""

    is_human: bool
    confidence: float = Field(..., ge=0.0, le=1.0)
    raw_scores: dict[str, float] = Field(default_factory=dict)


class EidosResult(BaseModel):
    """Result from Eidos speaker verification against enrolled voiceprints."""

    match_score: float = Field(..., ge=0.0, le=1.0)
    matched_name: Optional[str] = Field(default=None)
    threshold: float = Field(..., ge=0.0, le=1.0)
    is_match: bool


class VerifyResponse(BaseModel):
    """Combined verification response returned by /api/verify."""

    verified: bool
    aletheia_result: AletheiaResult
    eidos_result: EidosResult
    verdict: str
    message: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
