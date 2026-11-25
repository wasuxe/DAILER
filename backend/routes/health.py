"""Health check blueprint reporting service status, version, uptime, and enrollment count."""

from datetime import datetime, timezone
import time
from typing import Any

from flask import Blueprint, current_app, jsonify

health_bp = Blueprint("health", __name__)


def _services() -> dict[str, Any]:
    return current_app.extensions["services"]


@health_bp.route("/health", methods=["GET"])
def health_check():
    """Return service health, version, uptime in seconds, and active enrollment count."""
    start_time = float(current_app.config.get("START_TIME", time.time()))
    uptime_seconds = round(max(0.0, time.time() - start_time), 2)
    version = str(current_app.config.get("APP_VERSION", "1.0.0"))

    storage = _services()["storage"]
    enrollment_count = len(storage.list_enrollments(active_only=True))

    return (
        jsonify(
            {
                "status": "healthy",
                "service": "dailer-backend",
                "version": version,
                "uptime": uptime_seconds,
                "enrollment_count": enrollment_count,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            }
        ),
        200,
    )
