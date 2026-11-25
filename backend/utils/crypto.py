"""Cryptographic utilities for encrypting voice embeddings and temporary audio."""

import base64
import hashlib
import logging
from typing import Optional, Union

from cryptography.fernet import Fernet, InvalidToken

from config import Config

logger = logging.getLogger(__name__)


def generate_key() -> bytes:
    """Generate a new URL-safe base64-encoded 32-byte Fernet key."""
    return Fernet.generate_key()


def _normalize_key(raw_key: Union[str, bytes]) -> bytes:
    """Validate a Fernet key or deterministically derive one from an arbitrary secret."""
    key_bytes = raw_key.encode("utf-8") if isinstance(raw_key, str) else raw_key
    try:
        Fernet(key_bytes)
        return key_bytes
    except (ValueError, TypeError):
        digest = hashlib.sha256(key_bytes).digest()
        return base64.urlsafe_b64encode(digest)


def get_fernet(key: Optional[Union[str, bytes]] = None) -> Fernet:
    """
    Create a Fernet instance from an explicit key, Config.ENCRYPTION_KEY,
    or a fallback derived from Config.SECRET_KEY.
    """
    effective_key: Union[str, bytes] = key if key is not None else Config.ENCRYPTION_KEY
    if not effective_key:
        logger.warning(
            "ENCRYPTION_KEY is not configured; deriving fallback key from SECRET_KEY."
        )
        effective_key = Config.SECRET_KEY or "dailer-default-fallback-secret"

    return Fernet(_normalize_key(effective_key))


def encrypt_data(data: bytes, key: Optional[Union[str, bytes]] = None) -> bytes:
    """Encrypt raw bytes using Fernet symmetric encryption."""
    if not isinstance(data, (bytes, bytearray)):
        raise TypeError("data must be bytes")
    fernet = get_fernet(key)
    return fernet.encrypt(bytes(data))


def decrypt_data(token: bytes, key: Optional[Union[str, bytes]] = None) -> bytes:
    """Decrypt Fernet-encrypted bytes, raising ValueError on invalid token."""
    if not isinstance(token, (bytes, bytearray)):
        raise TypeError("token must be bytes")
    fernet = get_fernet(key)
    try:
        return fernet.decrypt(bytes(token))
    except InvalidToken as exc:
        logger.error("Failed to decrypt payload: invalid Fernet token or key mismatch.")
        raise ValueError("Invalid encryption token or key.") from exc
