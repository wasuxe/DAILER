"""Verdict combination logic for Aletheia and Eidos verification results."""

from models.verification import AletheiaResult, EidosResult


class VerdictService:
    """Combines Aletheia deepfake detection and Eidos speaker verification into a final verdict."""

    @staticmethod
    def compute_verdict(aletheia: AletheiaResult, eidos: EidosResult) -> tuple[str, str]:
        """
        Compute the final call verdict and human-readable guidance message.

        Returns:
            ("verified" | "warning", message)
        """
        if aletheia.is_human and eidos.is_match:
            speaker_label = f" as {eidos.matched_name}" if eidos.matched_name else ""
            return (
                "verified",
                (
                    f"Call verified: natural human speech matched enrolled voiceprint"
                    f"{speaker_label} (human confidence {aletheia.confidence:.2f}, "
                    f"speaker score {eidos.match_score:.2f})."
                ),
            )

        if not aletheia.is_human and not eidos.is_match:
            return (
                "warning",
                (
                    "Warning: Potential AI-generated or cloned voice detected and "
                    "the caller does not match any enrolled relative."
                ),
            )

        if not aletheia.is_human:
            return (
                "warning",
                (
                    "Warning: Synthetic or AI-cloned voice characteristics detected. "
                    "Do not trust this call without out-of-band verification."
                ),
            )

        return (
            "warning",
            (
                "Warning: Voice appears human, but it does not match the enrolled "
                "speaker's voiceprint."
            ),
        )
