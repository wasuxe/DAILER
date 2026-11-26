"""Speaker isolation service: removes the parent's own voice from mixed call audio."""

import io
import logging
import os
import shutil
import subprocess
import tempfile
import wave
from typing import Any, Optional

import numpy as np

from services.eidos_service import EidosService
from services.storage_service import StorageService

logger = logging.getLogger(__name__)

PARENT_RELATIONSHIPS = frozenset({"self", "self (parent)", "parent", "owner", "me"})


def is_parent_relationship(relationship: Optional[str]) -> bool:
    """Return True if an enrollment relationship represents the phone owner (parent)."""
    if not relationship:
        return False
    return relationship.strip().lower() in PARENT_RELATIONSHIPS


class SpeakerIsolationService:
    """
    Separates speech segments in a mixed call recording and filters out segments
    matching an enrolled parent voiceprint so Aletheia and Eidos analyze only
    the remote caller's voice.
    """

    TARGET_SAMPLE_RATE = 16000
    FRAME_MS = 30
    MIN_SEGMENT_S = 1.5
    MAX_SEGMENT_S = 6.0
    MAX_SEGMENTS_TO_CHECK = 6
    PARENT_MATCH_THRESHOLD = 0.65

    def __init__(self, storage_service: StorageService) -> None:
        self.storage = storage_service
        self._ffmpeg_path = shutil.which("ffmpeg")

    def find_parent_enrollment(self):
        """Return the active enrollment marked as the parent/owner, if any."""
        for rec in self.storage.list_enrollments(active_only=True):
            if is_parent_relationship(rec.relationship):
                return rec
        return None

    def isolate_caller_audio(
        self,
        audio_bytes: bytes,
        eidos: Optional[EidosService] = None,
    ) -> tuple[bytes, dict[str, Any]]:
        """
        Attempt to remove the parent's speech segments from `audio_bytes`.

        Returns:
            (isolated_audio_bytes, metadata_dict)
            Falls back to original `audio_bytes` if no parent voiceprint is enrolled,
            ffmpeg is unavailable for non-WAV input, or no non-parent speech remains.
        """
        parent = self.find_parent_enrollment()
        if parent is None:
            return audio_bytes, {"isolated": False, "reason": "no_parent_enrolled"}

        if eidos is None or eidos.is_placeholder:
            return audio_bytes, {"isolated": False, "reason": "eidos_placeholder_mode"}

        pcm_samples = self._decode_to_16k_mono(audio_bytes)
        if pcm_samples is None or pcm_samples.size < self.TARGET_SAMPLE_RATE:
            return audio_bytes, {"isolated": False, "reason": "decode_unavailable_or_too_short"}

        segments = self._segment_speech(pcm_samples)
        if len(segments) <= 1:
            return audio_bytes, {"isolated": False, "reason": "single_or_no_speech_segment"}

        kept_segments: list[np.ndarray] = []
        excluded_count = 0

        for idx, seg in enumerate(segments[: self.MAX_SEGMENTS_TO_CHECK]):
            seg_wav = self._samples_to_wav_bytes(seg)
            try:
                res = eidos.verify_speaker(
                    audio_bytes=seg_wav,
                    enrollment_id=parent.enrollment_id,
                )
                is_parent = res.is_match or res.match_score >= self.PARENT_MATCH_THRESHOLD
                if is_parent:
                    excluded_count += 1
                    logger.info(
                        "Excluded parent speech segment %d (score=%.3f)",
                        idx,
                        res.match_score,
                    )
                else:
                    kept_segments.append(seg)
            except Exception as exc:
                logger.warning("Segment %d parent check failed (%s); keeping segment", idx, exc)
                kept_segments.append(seg)

        # Keep any remaining unchecked segments if the call was longer than MAX_SEGMENTS_TO_CHECK
        if len(segments) > self.MAX_SEGMENTS_TO_CHECK:
            kept_segments.extend(segments[self.MAX_SEGMENTS_TO_CHECK :])

        if not kept_segments or excluded_count == 0:
            reason = "no_caller_segments_left" if not kept_segments else "no_parent_segments_detected"
            return audio_bytes, {"isolated": False, "reason": reason}

        combined = np.concatenate(kept_segments)
        if combined.size < self.TARGET_SAMPLE_RATE:
            return audio_bytes, {"isolated": False, "reason": "isolated_audio_too_short"}

        isolated_wav = self._samples_to_wav_bytes(combined)
        logger.info(
            "Caller audio isolated: kept %d/%d segments (excluded %d parent segments)",
            len(kept_segments),
            len(segments),
            excluded_count,
        )
        return isolated_wav, {
            "isolated": True,
            "total_segments": len(segments),
            "excluded_parent_segments": excluded_count,
            "kept_segments": len(kept_segments),
        }

    def _decode_to_16k_mono(self, audio_bytes: bytes) -> Optional[np.ndarray]:
        """Decode WAV directly or transcode M4A/MP4/AAC via ffmpeg to 16kHz mono int16."""
        if audio_bytes[:4] == b"RIFF":
            return self._read_wav_int16(audio_bytes)

        if not self._ffmpeg_path:
            logger.debug("ffmpeg not found on PATH; skipping speaker isolation for non-WAV audio")
            return None

        in_path: Optional[str] = None
        out_path: Optional[str] = None
        try:
            with tempfile.NamedTemporaryFile(suffix=".m4a", delete=False) as tmp_in:
                tmp_in.write(audio_bytes)
                in_path = tmp_in.name
            out_path = in_path + ".wav"

            proc = subprocess.run(
                [
                    self._ffmpeg_path,
                    "-y",
                    "-i",
                    in_path,
                    "-ac",
                    "1",
                    "-ar",
                    str(self.TARGET_SAMPLE_RATE),
                    "-f",
                    "wav",
                    out_path,
                ],
                capture_output=True,
                timeout=15,
                check=False,
            )
            if proc.returncode != 0 or not os.path.exists(out_path):
                logger.warning("ffmpeg transcode failed (rc=%s)", proc.returncode)
                return None

            with open(out_path, "rb") as f:
                return self._read_wav_int16(f.read())
        except Exception as exc:
            logger.warning("Audio decode failed: %s", exc)
            return None
        finally:
            for p in (in_path, out_path):
                if p and os.path.exists(p):
                    try:
                        os.remove(p)
                    except OSError:
                        pass

    def _read_wav_int16(self, wav_bytes: bytes) -> Optional[np.ndarray]:
        """Read a 16-bit PCM WAV byte buffer into a mono float/int16 numpy array."""
        try:
            with wave.open(io.BytesIO(wav_bytes), "rb") as wf:
                channels = wf.getnchannels()
                rate = wf.getframerate()
                sampwidth = wf.getsampwidth()
                raw = wf.readframes(wf.getnframes())
            if sampwidth != 2 or not raw:
                return None
            samples = np.frombuffer(raw, dtype=np.int16)
            if channels > 1:
                samples = samples.reshape(-1, channels).mean(axis=1).astype(np.int16)
            if rate != self.TARGET_SAMPLE_RATE and rate > 0:
                # Simple linear resample to 16kHz
                target_len = int(len(samples) * self.TARGET_SAMPLE_RATE / rate)
                if target_len <= 0:
                    return None
                samples = np.interp(
                    np.linspace(0, len(samples) - 1, target_len),
                    np.arange(len(samples)),
                    samples,
                ).astype(np.int16)
            return samples
        except Exception as exc:
            logger.debug("WAV read failed: %s", exc)
            return None

    def _segment_speech(self, samples: np.ndarray) -> list[np.ndarray]:
        """Split 16kHz mono int16 audio into speech segments using adaptive RMS energy."""
        frame_len = int(self.TARGET_SAMPLE_RATE * self.FRAME_MS / 1000)
        num_frames = len(samples) // frame_len
        if num_frames == 0:
            return []

        frames = samples[: num_frames * frame_len].reshape(num_frames, frame_len).astype(np.float32)
        rms = np.sqrt(np.mean(frames ** 2, axis=1))
        noise_floor = float(np.percentile(rms, 20))
        threshold = max(200.0, noise_floor * 2.5)

        active = rms > threshold
        segments: list[np.ndarray] = []
        min_frames = int(self.MIN_SEGMENT_S * 1000 / self.FRAME_MS)
        max_frames = int(self.MAX_SEGMENT_S * 1000 / self.FRAME_MS)
        hangover_frames = int(0.45 * 1000 / self.FRAME_MS)

        start: Optional[int] = None
        silence_run = 0
        for i, is_speech in enumerate(active):
            if is_speech:
                if start is None:
                    start = i
                silence_run = 0
            elif start is not None:
                silence_run += 1
                if silence_run >= hangover_frames or (i - start) >= max_frames:
                    end = i - silence_run + 1
                    if end - start >= min_frames:
                        segments.append(samples[start * frame_len : end * frame_len])
                    start = None
                    silence_run = 0

        if start is not None:
            end = num_frames
            if end - start >= min_frames:
                segments.append(samples[start * frame_len : end * frame_len])

        return segments

    def _samples_to_wav_bytes(self, samples: np.ndarray) -> bytes:
        """Encode 16kHz mono int16 numpy array into in-memory WAV bytes."""
        buf = io.BytesIO()
        with wave.open(buf, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(self.TARGET_SAMPLE_RATE)
            wf.writeframes(samples.astype(np.int16).tobytes())
        return buf.getvalue()
