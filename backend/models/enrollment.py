"""Pydantic schemas for voice enrollment requests, responses, and stored records."""

from datetime import datetime, timezone
from pydantic import BaseModel, Field, field_validator


class EnrollmentRequest(BaseModel):
    """Incoming payload to enroll a relative's voiceprint."""

    name: str = Field(..., min_length=1, max_length=120, description="Relative's display name")
    phone_number: str = Field(..., min_length=3, max_length=32, description="Relative's phone number")
    audio_base64: str = Field(..., min_length=1, description="Base64-encoded enrollment audio")
    relationship: str = Field(
        default="Family",
        min_length=1,
        max_length=64,
        description="Relationship to the protected user (e.g., Mother, Brother)",
    )

    @field_validator("name", "phone_number", "relationship", mode="before")
    @classmethod
    def _strip_strings(cls, value: object) -> object:
        if isinstance(value, str):
            return value.strip()
        return value


class EnrollmentResponse(BaseModel):
    """Response returned after enrolling a voiceprint."""

    success: bool
    enrollment_id: str
    message: str


class EnrollmentRecord(BaseModel):
    """Persistent index entry for an enrolled speaker voiceprint."""

    enrollment_id: str
    name: str
    phone_number: str
    relationship: str
    embedding_path: str
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    is_active: bool = True
