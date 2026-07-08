from __future__ import annotations

from dataclasses import dataclass

import numpy as np


LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_HIP = 23
RIGHT_HIP = 24


@dataclass(frozen=True, slots=True)
class NormalizationResult:
    """Normalized landmarks plus values useful for diagnostics."""

    normalized_landmarks: np.ndarray
    hip_center: np.ndarray
    scale_factor: float


class PoseNormalizer:
    """Center pose on hips and scale by shoulder-to-hip torso distance."""

    def normalize(self, landmarks: np.ndarray) -> NormalizationResult:
        """Normalize landmarks while preserving the x, y, z, visibility layout."""
        if landmarks.size == 0:
            return NormalizationResult(
                normalized_landmarks=np.empty((0, 4), dtype=np.float32),
                hip_center=np.full(3, np.nan, dtype=np.float32),
                scale_factor=0.0,
            )

        normalized = landmarks.astype(np.float32, copy=True)
        hip_center = self._center_between(normalized, LEFT_HIP, RIGHT_HIP)
        shoulder_center = self._center_between(normalized, LEFT_SHOULDER, RIGHT_SHOULDER)

        translated_xyz = normalized[:, :3] - hip_center
        scale_factor = float(np.linalg.norm(shoulder_center - hip_center))
        if scale_factor <= 1e-6:
            scale_factor = 1.0

        normalized[:, :3] = translated_xyz / scale_factor
        return NormalizationResult(normalized, hip_center.astype(np.float32), scale_factor)

    @staticmethod
    def _center_between(landmarks: np.ndarray, first_idx: int, second_idx: int) -> np.ndarray:
        return (landmarks[first_idx, :3] + landmarks[second_idx, :3]) / 2.0
