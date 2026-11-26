"""Verdict combination logic for Aletheia and Eidos verification results."""

from config import Config
from models.verification import AletheiaResult, EidosResult


class VerdictService:
    """Combines Aletheia deepfake detection and Eidos speaker verification into a final verdict."""

    @staticmethod
    def _detail_suffix(aletheia: AletheiaResult, eidos: EidosResult) -> str:
        parts: list[str] = []
        if aletheia.reason:
            parts.append(aletheia.reason)
        if eidos.reason:
            parts.append(eidos.reason)
        req_id = (
            aletheia.api_metadata.get("X-Request-Id")
            or aletheia.api_metadata.get("x-request-id")
            or eidos.api_metadata.get("X-Request-Id")
            or eidos.api_metadata.get("x-request-id")
        )
        if req_id:
            parts.append(f"Ref: {req_id}")
        return (" [" + " | ".join(parts) + "]") if parts else ""

    @staticmethod
    def compute_verdict(aletheia: AletheiaResult, eidos: EidosResult) -> tuple[str, str]:
        """
        Compute the final call verdict and human-readable guidance message.

        Returns:
            ("verified" | "warning", message)
        """
        details = VerdictService._detail_suffix(aletheia, eidos)
        human_pct = int(round(aletheia.confidence * 100))
        match_pct = int(round(eidos.match_score * 100))
        thresh_pct = int(round(eidos.threshold * 100))
        min_human_pct = int(round(Config.ALETHEIA_HUMAN_THRESHOLD * 100))

        if not aletheia.is_human or aletheia.confidence < Config.ALETHEIA_HUMAN_THRESHOLD:
            return (
                "warning",
                (
                    f"Warning: Low human confidence ({human_pct}% human < {min_human_pct}% required threshold). "
                    f"Synthetic speech risk.{details}"
                ),
            )

        if eidos.is_match:
            speaker_label = f" as {eidos.matched_name}" if eidos.matched_name else ""
            return (
                "verified",
                (
                    f"Call verified{speaker_label}: natural human voice ({human_pct}% >= {min_human_pct}%) "
                    f"and matched enrolled voiceprint ({match_pct}% >= {thresh_pct}%).{details}"
                ),
            )

        return (
            "verified",
            (
                f"Human voice verified ({human_pct}% >= {min_human_pct}% required). "
                f"Natural human speech.{details}"
            ),
        )
