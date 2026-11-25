"""Audio decoding, duration inspection, and validation helpers."""

import base64
import binascii
import io
import logging
import wave

logger = logging.getLogger(__name__)

# Maximum raw decoded payload size (50 MB safety limit)
MAX_AUDIO_BYTES = 50 * 1024 * 1024
MIN_AUDIO_BYTES = 16
# Fallback assumption for placeholder duration estimation: 16 kHz, 16-bit mono (32 KB/s)
_PLACEHOLDER_BYTES_PER_SECOND = 32000.0


def decode_base64_audio(audio_base64: str) -> bytes:
    """
    Decode a base64-encoded audio string (with optional data URI header) to raw bytes.
    Raises ValueError if the input is empty or malformed.
    """
    if not audio_base64 or not isinstance(audio_base64, str):
        raise ValueError("Audio payload must be a non-empty base64 string.")

    cleaned = audio_base64.strip()
    if cleaned.startswith("data:") and "," in cleaned:
        cleaned = cleaned.split(",", 1)[1].strip()

    if not cleaned:
        raise ValueError("Base64 audio payload is empty.")

    try:
        decoded = base64.b64decode(cleaned, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError(f"Invalid base64 audio encoding: {exc}") from exc

    if not decoded:
        raise ValueError("Decoded audio payload is empty.")

    return decoded


def get_audio_duration(audio_bytes: bytes) -> float:
    """
    Estimate audio duration in seconds.
    Parses WAV headers directly when available; otherwise uses a byte-rate
    placeholder estimate (16 kHz / 16-bit mono).
    """
    if not audio_bytes:
        return 0.0

    try:
        with wave.open(io.BytesIO(audio_bytes), "rb") as wf:
            framerate = wf.getframerate()
            nframes = wf.getnframes()
            if framerate > 0:
                return round(nframes / float(framerate), 4)
    except (wave.Error, EOFError):
        pass
    except Exception as exc:
        logger.debug("WAV header inspection failed, using placeholder duration: %s", exc)

    # Placeholder fallback for non-WAV formats (MP3/AAC/OPUS/raw PCM)
    estimated = len(audio_bytes) / _PLACEHOLDER_BYTES_PER_SECOND
    return round(max(0.1, estimated), 4)


def validate_audio(
    audio_bytes: bytes,
    max_duration_s: float = 30.0,
    min_bytes: int = MIN_AUDIO_BYTES,
) -> float:
    """
    Validate decoded audio bytes for minimum size, maximum payload limit,
    and maximum duration. Returns the estimated duration in seconds.
    Raises ValueError if validation fails.
    """
    if not isinstance(audio_bytes, (bytes, bytearray)):
        raise ValueError("Audio data must be bytes.")

    size = len(audio_bytes)
    if size < min_bytes:
        raise ValueError(f"Audio payload too small ({size} bytes; minimum {min_bytes} bytes).")

    if size > MAX_AUDIO_BYTES:
        raise ValueError(f"Audio payload exceeds maximum allowed size ({MAX_AUDIO_BYTES} bytes).")

    duration = get_audio_duration(bytes(audio_bytes))
    if duration <= 0.0:
        raise ValueError("Audio duration must be greater than zero.")

    if duration > max_duration_s:
        raise ValueError(
            f"Audio duration ({duration:.2f}s) exceeds maximum allowed ({max_duration_s:.2f}s)."
        )

    return duration
