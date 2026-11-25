"""Encrypted storage service for speaker embeddings, enrollment index, and temp call audio."""

import json
import logging
import threading
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Optional

import boto3
from botocore.exceptions import ClientError
import numpy as np

from config import Config
from models.enrollment import EnrollmentRecord
from utils.crypto import decrypt_data, encrypt_data

logger = logging.getLogger(__name__)


class StorageService:
    """
    Manages encrypted speaker embeddings, the enrollments.json index,
    and temporary call audio files on local disk or S3-compatible storage.
    """

    INDEX_FILENAME = "enrollments.json"

    def __init__(self, config: type[Config] = Config) -> None:
        self.storage_type = config.STORAGE_TYPE.lower()
        self.storage_path = Path(config.STORAGE_PATH)
        self.embeddings_dir = self.storage_path / "embeddings"
        self.temp_audio_dir = self.storage_path / "temp_audio"
        self.index_path = self.storage_path / self.INDEX_FILENAME
        self.encryption_key = config.ENCRYPTION_KEY
        self.s3_bucket = config.S3_BUCKET_NAME
        self._lock = threading.RLock()

        if self.storage_type == "s3":
            self._s3 = boto3.client(
                "s3",
                endpoint_url=config.S3_ENDPOINT_URL or None,
                aws_access_key_id=config.S3_ACCESS_KEY or None,
                aws_secret_access_key=config.S3_SECRET_KEY or None,
                region_name=config.S3_REGION,
            )
            logger.info("StorageService initialized with S3 backend (bucket=%s).", self.s3_bucket)
        else:
            self._s3 = None
            self.ensure_directories()
            logger.info("StorageService initialized with local backend (path=%s).", self.storage_path)

    def ensure_directories(self) -> None:
        """Create local storage directories and index file if missing."""
        if self.storage_type == "s3":
            return
        self.storage_path.mkdir(parents=True, exist_ok=True)
        self.embeddings_dir.mkdir(parents=True, exist_ok=True)
        self.temp_audio_dir.mkdir(parents=True, exist_ok=True)
        if not self.index_path.exists():
            self.index_path.write_text("{}", encoding="utf-8")

    def _read_index(self) -> dict[str, dict[str, Any]]:
        """Load the enrollments.json index dictionary."""
        if self.storage_type == "s3" and self._s3 is not None:
            try:
                obj = self._s3.get_object(Bucket=self.s3_bucket, Key=self.INDEX_FILENAME)
                raw = obj["Body"].read().decode("utf-8")
                data = json.loads(raw)
                return data if isinstance(data, dict) else {}
            except ClientError as exc:
                code = exc.response.get("Error", {}).get("Code", "")
                if code in ("NoSuchKey", "404"):
                    return {}
                raise
            except Exception as exc:
                logger.warning("Failed to read S3 enrollment index: %s", exc)
                return {}

        self.ensure_directories()
        if not self.index_path.exists():
            return {}
        raw_text = self.index_path.read_text(encoding="utf-8").strip()
        if not raw_text:
            return {}
        data = json.loads(raw_text)
        return data if isinstance(data, dict) else {}

    def _write_index(self, index: dict[str, dict[str, Any]]) -> None:
        """Persist the enrollments.json index dictionary atomically."""
        serialized = json.dumps(index, indent=2, default=str)
        if self.storage_type == "s3" and self._s3 is not None:
            self._s3.put_object(
                Bucket=self.s3_bucket,
                Key=self.INDEX_FILENAME,
                Body=serialized.encode("utf-8"),
                ContentType="application/json",
            )
            return

        self.ensure_directories()
        tmp_path = self.index_path.with_suffix(".json.tmp")
        tmp_path.write_text(serialized, encoding="utf-8")
        tmp_path.replace(self.index_path)

    def save_embedding(
        self,
        enrollment_id: str,
        embedding: np.ndarray,
        metadata: dict[str, Any],
    ) -> EnrollmentRecord:
        """Encrypt and persist a speaker embedding and update the enrollment index."""
        embedding_list = np.asarray(embedding, dtype=np.float32).flatten().tolist()
        payload = json.dumps(
            {
                "enrollment_id": enrollment_id,
                "embedding": embedding_list,
                "metadata": metadata,
            }
        ).encode("utf-8")
        encrypted_payload = encrypt_data(payload, key=self.encryption_key or None)

        if self.storage_type == "s3" and self._s3 is not None:
            key = f"embeddings/{enrollment_id}.enc"
            self._s3.put_object(Bucket=self.s3_bucket, Key=key, Body=encrypted_payload)
            embedding_path = f"s3://{self.s3_bucket}/{key}"
        else:
            self.ensure_directories()
            target = self.embeddings_dir / f"{enrollment_id}.enc"
            target.write_bytes(encrypted_payload)
            embedding_path = str(target)

        record = EnrollmentRecord(
            enrollment_id=enrollment_id,
            name=str(metadata.get("name", "Unknown")),
            phone_number=str(metadata.get("phone_number", "")),
            relationship=str(metadata.get("relationship", "Family")),
            embedding_path=embedding_path,
            created_at=datetime.now(timezone.utc),
            is_active=bool(metadata.get("is_active", True)),
        )

        with self._lock:
            index = self._read_index()
            index[enrollment_id] = record.model_dump(mode="json")
            self._write_index(index)

        logger.info("Saved encrypted embedding for enrollment_id=%s", enrollment_id)
        return record

    def load_embedding(self, enrollment_id: str) -> Optional[np.ndarray]:
        """Load and decrypt a stored speaker embedding as a float32 numpy array."""
        encrypted: Optional[bytes] = None

        if self.storage_type == "s3" and self._s3 is not None:
            key = f"embeddings/{enrollment_id}.enc"
            try:
                obj = self._s3.get_object(Bucket=self.s3_bucket, Key=key)
                encrypted = obj["Body"].read()
            except ClientError as exc:
                code = exc.response.get("Error", {}).get("Code", "")
                if code in ("NoSuchKey", "404"):
                    return None
                raise
        else:
            target = self.embeddings_dir / f"{enrollment_id}.enc"
            if not target.exists():
                return None
            encrypted = target.read_bytes()

        decrypted = decrypt_data(encrypted, key=self.encryption_key or None)
        data = json.loads(decrypted.decode("utf-8"))
        return np.asarray(data["embedding"], dtype=np.float32)

    def get_enrollment(self, enrollment_id: str) -> Optional[EnrollmentRecord]:
        """Fetch an EnrollmentRecord from the index by ID."""
        with self._lock:
            index = self._read_index()
            entry = index.get(enrollment_id)
        if not entry:
            return None
        return EnrollmentRecord.model_validate(entry)

    def delete_embedding(self, enrollment_id: str) -> bool:
        """Delete an encrypted embedding and remove its entry from enrollments.json."""
        existed = False

        with self._lock:
            index = self._read_index()
            if enrollment_id in index:
                index.pop(enrollment_id)
                self._write_index(index)
                existed = True

        if self.storage_type == "s3" and self._s3 is not None:
            key = f"embeddings/{enrollment_id}.enc"
            if not existed:
                try:
                    self._s3.head_object(Bucket=self.s3_bucket, Key=key)
                    existed = True
                except ClientError:
                    return False
            self._s3.delete_object(Bucket=self.s3_bucket, Key=key)
            logger.info("Deleted S3 embedding for enrollment_id=%s", enrollment_id)
            return existed

        target = self.embeddings_dir / f"{enrollment_id}.enc"
        if target.exists():
            target.unlink()
            existed = True

        if existed:
            logger.info("Deleted local embedding for enrollment_id=%s", enrollment_id)
        return existed

    def list_enrollments(self, active_only: bool = True) -> list[EnrollmentRecord]:
        """List all enrolled speaker records from enrollments.json."""
        with self._lock:
            index = self._read_index()

        records: list[EnrollmentRecord] = []
        for entry in index.values():
            try:
                record = EnrollmentRecord.model_validate(entry)
                if not active_only or record.is_active:
                    records.append(record)
            except Exception as exc:
                logger.warning("Skipping malformed enrollment record: %s", exc)

        records.sort(key=lambda r: r.created_at, reverse=True)
        return records

    def save_temp_audio(self, audio_bytes: bytes, call_id: str) -> str:
        """Encrypt and temporarily store call audio bytes for auditing/retention."""
        encrypted = encrypt_data(audio_bytes, key=self.encryption_key or None)
        filename = f"{call_id}_{int(time.time())}.enc"

        if self.storage_type == "s3" and self._s3 is not None:
            key = f"temp_audio/{filename}"
            self._s3.put_object(Bucket=self.s3_bucket, Key=key, Body=encrypted)
            return f"s3://{self.s3_bucket}/{key}"

        self.ensure_directories()
        target = self.temp_audio_dir / filename
        target.write_bytes(encrypted)
        return str(target)

    def cleanup_expired_audio(self, retention_hours: Optional[float] = None) -> int:
        """Delete temporary call audio files older than retention_hours."""
        hours = Config.CALL_AUDIO_RETENTION_HOURS if retention_hours is None else retention_hours
        cutoff_dt = datetime.now(timezone.utc) - timedelta(hours=hours)
        cutoff_ts = cutoff_dt.timestamp()
        deleted_count = 0

        if self.storage_type == "s3" and self._s3 is not None:
            paginator = self._s3.get_paginator("list_objects_v2")
            for page in paginator.paginate(Bucket=self.s3_bucket, Prefix="temp_audio/"):
                for obj in page.get("Contents", []):
                    last_modified = obj.get("LastModified")
                    if last_modified is not None and last_modified < cutoff_dt:
                        self._s3.delete_object(Bucket=self.s3_bucket, Key=obj["Key"])
                        deleted_count += 1
            if deleted_count:
                logger.info("Cleaned up %d expired S3 temp audio objects.", deleted_count)
            return deleted_count

        if not self.temp_audio_dir.exists():
            return 0

        for file_path in self.temp_audio_dir.iterdir():
            if not file_path.is_file():
                continue
            try:
                if file_path.stat().st_mtime < cutoff_ts:
                    file_path.unlink()
                    deleted_count += 1
            except OSError as exc:
                logger.warning("Failed to delete expired temp audio %s: %s", file_path, exc)

        if deleted_count:
            logger.info("Cleaned up %d expired local temp audio files.", deleted_count)
        return deleted_count
