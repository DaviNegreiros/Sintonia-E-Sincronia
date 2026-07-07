from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass(slots=True)
class PoseFrame:
    """Represents pose data for exactly one frame."""

    frame: int
    timestamp: float
    pose_detected: bool
    landmarks: np.ndarray
    normalized_landmarks: np.ndarray
    joint_angles: dict[str, float]

    def to_debug_dict(self, max_landmarks: int = 5) -> dict[str, object]:
        """Return a compact JSON-like representation for visual debugging."""
        landmarks_preview = self.landmarks[:max_landmarks].round(4).tolist()
        normalized_preview = self.normalized_landmarks[:max_landmarks].round(4).tolist()
        return {
            "frame": self.frame,
            "timestamp": round(self.timestamp, 1),
            "pose_detected": self.pose_detected,
            "landmarks": landmarks_preview,
            "normalized_landmarks": normalized_preview,
            "joint_angles": {key: round(value, 2) for key, value in self.joint_angles.items()},
        }

