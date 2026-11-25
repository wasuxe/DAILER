"""Integration tests for the Dailer Flask backend API."""

import base64
import io
import os
import sys
import wave

import pytest

# Ensure backend root is importable
BACKEND_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if BACKEND_DIR not in sys.path:
    sys.path.insert(0, BACKEND_DIR)

from app import create_app  # noqa: E402
from config import Config  # noqa: E402


def _make_wav_base64(duration_s: float = 1.0, sample_rate: int = 16000) -> str:
    """Generate a silent valid WAV clip encoded as base64."""
    buf = io.BytesIO()
    n_frames = int(duration_s * sample_rate)
    with wave.open(buf, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(b"\x00\x00" * n_frames)
    return base64.b64encode(buf.getvalue()).decode("ascii")


@pytest.fixture()
def client(tmp_path):
    """Create a Flask test client backed by an isolated temporary storage directory."""
    class TestConfig(Config):
        TESTING = True
        STORAGE_TYPE = "local"
        STORAGE_PATH = str(tmp_path / "storage")
        ENCRYPTION_KEY = ""
        ALETHEIA_API_URL = ""
        EIDOS_API_URL = ""

    app = create_app(TestConfig)
    with app.test_client() as test_client:
        yield test_client


def test_index_and_favicon_endpoints(client):
    index_resp = client.get("/")
    assert index_resp.status_code == 200
    data = index_resp.get_json()
    assert data["service"] == "dailer-backend"
    assert data["status"] == "running"

    fav_resp = client.get("/favicon.ico")
    assert fav_resp.status_code == 204


def test_health_endpoint(client):
    resp = client.get("/api/health")
    assert resp.status_code == 200
    data = resp.get_json()
    assert data["status"] == "healthy"
    assert data["enrollment_count"] == 0


def test_enroll_list_verify_and_delete_flow(client):
    audio_b64 = _make_wav_base64(duration_s=1.0)

    # 1. Enroll a relative
    enroll_resp = client.post(
        "/api/enroll",
        json={
            "name": "Aarav",
            "phone_number": "+919876543210",
            "relationship": "Son",
            "audio_base64": audio_b64,
        },
    )
    assert enroll_resp.status_code == 201
    enroll_data = enroll_resp.get_json()
    assert enroll_data["success"] is True
    enrollment_id = enroll_data["enrollment_id"]

    # 2. List enrollments
    list_resp = client.get("/api/enrollments")
    assert list_resp.status_code == 200
    list_data = list_resp.get_json()
    assert list_data["count"] == 1
    assert list_data["enrollments"][0]["enrollment_id"] == enrollment_id

    # 3. Verify a call against enrolled relative
    verify_resp = client.post(
        "/api/verify",
        json={
            "phone_number": "+919876543210",
            "claimed_relative_id": enrollment_id,
            "audio_base64": audio_b64,
        },
    )
    assert verify_resp.status_code == 200
    verify_data = verify_resp.get_json()
    assert verify_data["verdict"] in ("verified", "warning", "unknown")
    assert "aletheia_result" in verify_data
    assert "eidos_result" in verify_data

    # 4. Revoke/delete enrollment
    del_resp = client.delete(f"/api/enrollment/{enrollment_id}")
    assert del_resp.status_code == 200
    assert del_resp.get_json()["success"] is True

    # 5. Confirm deleted
    list_after = client.get("/api/enrollments").get_json()
    assert list_after["count"] == 0

