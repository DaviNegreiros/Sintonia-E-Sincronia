from __future__ import annotations

from dataclasses import dataclass, field
from types import MappingProxyType
from typing import Mapping

import numpy as np


@dataclass(frozen=True, slots=True)
class PoseFrame:
    """Pose data for exactly one frame.

    Timestamps are always expressed in milliseconds.
    """

    frame: int
    timestamp: float
    pose_detected: bool
    landmarks: np.ndarray
    normalized_landmarks: np.ndarray
    joint_angles: Mapping[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        object.__setattr__(self, "joint_angles", MappingProxyType(dict(self.joint_angles)))

    def to_debug_dict(self, max_landmarks: int = 5) -> dict[str, object]:
        """Return a compact JSON-like representation for diagnostics."""
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
