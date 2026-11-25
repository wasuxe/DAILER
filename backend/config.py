"""Application configuration for the Dailer backend."""

import os
from dotenv import load_dotenv

load_dotenv()


def _parse_bool(value: str, default: bool = False) -> bool:
    """Parse boolean environment variable values."""
    if not value:
        return default
    return value.strip().lower() in ("true", "1", "yes", "on")


class Config:
    """Central configuration loaded from environment variables."""

    APP_VERSION: str = os.getenv("APP_VERSION", "1.0.0")
    SECRET_KEY: str = os.getenv("SECRET_KEY", "dailer-dev-secret-change-in-production")
    HOST: str = os.getenv("HOST", "0.0.0.0")
    PORT: int = int(os.getenv("PORT", "5000"))
    DEBUG: bool = _parse_bool(os.getenv("DEBUG", os.getenv("FLASK_DEBUG", "false")), default=False)
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO").upper()

    # Shared API credentials (used by both Aletheia and Eidos when separate keys are not set)
    API_KEY: str = os.getenv("API_KEY", os.getenv("VOICE_API_KEY", "")).strip()
    REQ_KEY: str = os.getenv("REQ_KEY", os.getenv("VOICE_REQ_KEY", "")).strip()

    # Aletheia (AI synthetic speech detection) API
    ALETHEIA_API_URL: str = os.getenv("ALETHEIA_API_URL", "").strip()
    ALETHEIA_API_KEY: str = os.getenv("ALETHEIA_API_KEY", "").strip() or API_KEY

    # Eidos (speaker verification / voiceprint) API
    EIDOS_API_URL: str = os.getenv("EIDOS_API_URL", "").strip()
    EIDOS_API_KEY: str = os.getenv("EIDOS_API_KEY", "").strip() or API_KEY

    # Storage configuration ("local" or "s3")
    STORAGE_TYPE: str = os.getenv("STORAGE_TYPE", "local").strip().lower()
    STORAGE_PATH: str = os.getenv("STORAGE_PATH", "./data/storage")

    # Fernet encryption key (32-byte URL-safe base64 string)
    ENCRYPTION_KEY: str = os.getenv("ENCRYPTION_KEY", "").strip()

    # S3-compatible object storage settings (used when STORAGE_TYPE=s3)
    S3_ENDPOINT_URL: str = os.getenv("S3_ENDPOINT_URL", "").strip()
    S3_BUCKET_NAME: str = os.getenv("S3_BUCKET_NAME", "dailer-embeddings").strip()
    S3_ACCESS_KEY: str = os.getenv("S3_ACCESS_KEY", "").strip()
    S3_SECRET_KEY: str = os.getenv("S3_SECRET_KEY", "").strip()
    S3_REGION: str = os.getenv("S3_REGION", "us-east-1").strip()

    # Audio & retention limits
    MAX_AUDIO_DURATION_S: float = float(os.getenv("MAX_AUDIO_DURATION_S", "140.0"))
    ENROLLMENT_DURATION_S: float = float(os.getenv("ENROLLMENT_DURATION_S", "30.0"))
    CALL_AUDIO_RETENTION_HOURS: float = float(os.getenv("CALL_AUDIO_RETENTION_HOURS", "24.0"))

    # Detection & verification thresholds
    EIDOS_MATCH_THRESHOLD: float = float(os.getenv("EIDOS_MATCH_THRESHOLD", "0.75"))
    ALETHEIA_HUMAN_THRESHOLD: float = float(os.getenv("ALETHEIA_HUMAN_THRESHOLD", "0.80"))
