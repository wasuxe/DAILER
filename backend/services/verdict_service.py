"""Verdict combination logic for Aletheia and Eidos verification results."""

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

        if aletheia.is_human and eidos.is_match:
            speaker_label = f" as {eidos.matched_name}" if eidos.matched_name else ""
            return (
                "verified",
                (
                    f"Call verified{speaker_label}: natural human voice ({human_pct}% human) "
                    f"and matched enrolled voiceprint ({match_pct}% >= {thresh_pct}%).{details}"
                ),
            )

        if not aletheia.is_human and not eidos.is_match:
            return (
                "warning",
                (
                    f"Warning: AI-cloned speech detected ({100 - human_pct}% synthetic risk) and "
                    f"voice did not match any enrolled relative ({match_pct}% < {thresh_pct}%).{details}"
                ),
            )

        if not aletheia.is_human:
            return (
                "warning",
                (
                    f"Warning: Synthetic/AI-cloned speech detected ({100 - human_pct}% synthetic risk). "
                    f"Do not trust this call without out-of-band verification.{details}"
                ),
            )

        return (
            "warning",
            (
                f"Warning: Voice appears human ({human_pct}%), but speaker match was too low "
                f"({match_pct}% < {thresh_pct}% required).{details}"
            ),
        )
