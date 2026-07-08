from __future__ import annotations

import math

import numpy as np


LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_ELBOW = 13
RIGHT_ELBOW = 14
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_KNEE = 25
RIGHT_KNEE = 26
LEFT_ANKLE = 27
RIGHT_ANKLE = 28


class JointAngleCalculator:
    """Calculate selected joint angles in degrees."""

    def calculate(self, normalized_landmarks: np.ndarray) -> dict[str, float]:
        """Return all configured joint angles for one normalized pose."""
        if normalized_landmarks.size == 0:
            return {}

        points = normalized_landmarks[:, :3]
        shoulder_center = (points[LEFT_SHOULDER] + points[RIGHT_SHOULDER]) / 2.0
        hip_center = (points[LEFT_HIP] + points[RIGHT_HIP]) / 2.0

        return {
            "left_shoulder": self._angle(points[LEFT_ELBOW], points[LEFT_SHOULDER], points[LEFT_HIP]),
            "right_shoulder": self._angle(points[RIGHT_ELBOW], points[RIGHT_SHOULDER], points[RIGHT_HIP]),
            "left_elbow": self._angle(points[LEFT_SHOULDER], points[LEFT_ELBOW], points[LEFT_WRIST]),
            "right_elbow": self._angle(points[RIGHT_SHOULDER], points[RIGHT_ELBOW], points[RIGHT_WRIST]),
            "left_hip": self._angle(points[LEFT_SHOULDER], points[LEFT_HIP], points[LEFT_KNEE]),
            "right_hip": self._angle(points[RIGHT_SHOULDER], points[RIGHT_HIP], points[RIGHT_KNEE]),
            "left_knee": self._angle(points[LEFT_HIP], points[LEFT_KNEE], points[LEFT_ANKLE]),
            "right_knee": self._angle(points[RIGHT_HIP], points[RIGHT_KNEE], points[RIGHT_ANKLE]),
            "torso": self._torso_angle(shoulder_center, hip_center),
        }

    @staticmethod
    def _angle(first: np.ndarray, middle: np.ndarray, last: np.ndarray) -> float:
        first_vector = first - middle
        last_vector = last - middle
        denominator = np.linalg.norm(first_vector) * np.linalg.norm(last_vector)
        if denominator <= 1e-8:
            return 0.0

        cosine = float(np.dot(first_vector, last_vector) / denominator)
        return math.degrees(math.acos(np.clip(cosine, -1.0, 1.0)))

    @staticmethod
    def _torso_angle(shoulder_center: np.ndarray, hip_center: np.ndarray) -> float:
        torso_vector = shoulder_center - hip_center
        vertical_vector = np.array([0.0, -1.0, 0.0], dtype=np.float32)
        denominator = np.linalg.norm(torso_vector) * np.linalg.norm(vertical_vector)
        if denominator <= 1e-8:
            return 0.0

        cosine = float(np.dot(torso_vector, vertical_vector) / denominator)
        return math.degrees(math.acos(np.clip(cosine, -1.0, 1.0)))
