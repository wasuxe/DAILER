"""Flask application factory for the Dailer AI voice clone detection backend."""

import logging
import time
from typing import Type

from flask import Flask, jsonify, redirect, request
from flask_cors import CORS
from werkzeug.exceptions import HTTPException

from config import Config
from routes.enroll import enroll_bp
from routes.health import health_bp
from routes.verify import verify_bp
from services.aletheia_service import AletheiaService
from services.eidos_service import EidosService
from services.speaker_isolation_service import SpeakerIsolationService
from services.storage_service import StorageService
from services.verdict_service import VerdictService


def _configure_logging(level_name: str) -> None:
    """Configure application-wide structured logging."""
    level = getattr(logging, level_name.upper(), logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


def _register_error_handlers(app: Flask) -> None:
    """Register JSON error handlers for HTTP and unexpected exceptions."""

    @app.errorhandler(HTTPException)
    def handle_http_exception(exc: HTTPException):
        return (
            jsonify(
                {
                    "error": exc.name,
                    "message": exc.description,
                    "status_code": exc.code,
                }
            ),
            exc.code or 500,
        )

    @app.errorhandler(400)
    def handle_bad_request(exc):
        return jsonify({"error": "Bad Request", "message": str(exc), "status_code": 400}), 400

    @app.errorhandler(404)
    def handle_not_found(exc):
        app.logger.warning("404 Not Found requested for path: %s", request.path)
        return (
            jsonify(
                {
                    "error": "Not Found",
                    "message": f"Resource not found: {request.path}",
                    "requested_path": request.path,
                    "status_code": 404,
                    "available_endpoints": [
                        "/",
                        "/api/health",
                        "/api/enrollments",
                        "/api/enroll",
                        "/api/verify",
                    ],
                }
            ),
            404,
        )

    @app.errorhandler(500)
    def handle_internal_error(exc):
        app.logger.exception("Unhandled internal server error: %s", exc)
        return (
            jsonify(
                {
                    "error": "Internal Server Error",
                    "message": "An unexpected internal error occurred.",
                    "status_code": 500,
                }
            ),
            500,
        )


def create_app(config_class: Type[Config] = Config) -> Flask:
    """Create and configure the Dailer Flask application."""
    _configure_logging(getattr(config_class, "LOG_LEVEL", "INFO"))

    app = Flask(__name__)
    app.config.from_object(config_class)
    app.config["START_TIME"] = time.time()
    # Allow up to 64 MB payloads for base64-encoded audio
    app.config.setdefault("MAX_CONTENT_LENGTH", 64 * 1024 * 1024)

    CORS(app, resources={r"/api/*": {"origins": "*"}})

    storage_service = StorageService(config_class)
    storage_service.ensure_directories()

    aletheia_service = AletheiaService(
        api_url=config_class.ALETHEIA_API_URL,
        api_key=config_class.ALETHEIA_API_KEY,
        req_key=config_class.REQ_KEY,
        human_threshold=config_class.ALETHEIA_HUMAN_THRESHOLD,
    )
    eidos_service = EidosService(
        storage_service=storage_service,
        api_url=config_class.EIDOS_API_URL,
        api_key=config_class.EIDOS_API_KEY,
        req_key=config_class.REQ_KEY,
        match_threshold=config_class.EIDOS_MATCH_THRESHOLD,
    )
    verdict_service = VerdictService()
    isolation_service = SpeakerIsolationService(storage_service=storage_service)

    app.extensions["services"] = {
        "storage": storage_service,
        "aletheia": aletheia_service,
        "eidos": eidos_service,
        "verdict": verdict_service,
        "isolation": isolation_service,
    }

    app.register_blueprint(health_bp, url_prefix="/api")
    app.register_blueprint(enroll_bp, url_prefix="/api")
    app.register_blueprint(verify_bp, url_prefix="/api")

    @app.route("/")
    @app.route("/api")
    @app.route("/api/")
    def index():
        return jsonify(
            {
                "service": "dailer-backend",
                "version": config_class.APP_VERSION,
                "status": "running",
                "endpoints": {
                    "health": "/api/health",
                    "enroll": "/api/enroll",
                    "enrollments": "/api/enrollments",
                    "verify": "/api/verify",
                },
            }
        )

    @app.route("/health")
    @app.route("/health/")
    def health_alias():
        return redirect("/api/health", code=307)

    @app.route("/enrollments")
    @app.route("/enrollments/")
    def enrollments_alias():
        return redirect("/api/enrollments", code=307)

    @app.route("/favicon.ico")
    def favicon():
        return "", 204

    _register_error_handlers(app)

    app.logger.info(
        "Dailer backend initialized (version=%s, storage=%s, aletheia=%s, eidos=%s)",
        config_class.APP_VERSION,
        config_class.STORAGE_TYPE,
        "placeholder" if aletheia_service.is_placeholder else "remote",
        "placeholder" if eidos_service.is_placeholder else "remote",
    )
    return app


app = create_app()
