from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from .pose_frame import PoseFrame


@dataclass(slots=True)
class SimilarityResult:
    """Similarity details for one comparison instant."""

    timestamp: float
    overall_similarity: float
    landmark_similarity: float
    angle_similarity: float
    confidence: float
    feedback: str


class PoseComparator:
    """Compare two PoseFrame objects using normalized landmarks and joint angles."""

    def __init__(self, landmark_distance_threshold: float = 1.0) -> None:
        self.landmark_distance_threshold = landmark_distance_threshold

    def compare(self, reference_pose: PoseFrame, realtime_pose: PoseFrame) -> SimilarityResult:
        """Return hybrid similarity without UI, webcam or video concerns."""
        if not reference_pose.pose_detected or not realtime_pose.pose_detected:
            return SimilarityResult(
                timestamp=realtime_pose.timestamp,
                overall_similarity=0.0,
                landmark_similarity=0.0,
                angle_similarity=0.0,
                confidence=0.0,
                feedback="",
            )

        landmark_similarity = self._landmark_similarity(reference_pose, realtime_pose)
        angle_similarity = self._angle_similarity(reference_pose, realtime_pose)
        confidence = self._confidence(reference_pose, realtime_pose)
        overall_similarity = (0.7 * landmark_similarity) + (0.3 * angle_similarity)

        return SimilarityResult(
            timestamp=realtime_pose.timestamp,
            overall_similarity=float(np.clip(overall_similarity, 0.0, 1.0)),
            landmark_similarity=landmark_similarity,
            angle_similarity=angle_similarity,
            confidence=confidence,
            feedback="",
        )

    def _landmark_similarity(self, reference_pose: PoseFrame, realtime_pose: PoseFrame) -> float:
        reference = reference_pose.normalized_landmarks[:, :3]
        realtime = realtime_pose.normalized_landmarks[:, :3]
        if reference.size == 0 or realtime.size == 0 or len(reference) != len(realtime):
            return 0.0

        distances = np.linalg.norm(reference - realtime, axis=1)
        mean_distance = float(np.mean(distances))
        similarity = 1.0 - (mean_distance / self.landmark_distance_threshold)
        return float(np.clip(similarity, 0.0, 1.0))

    @staticmethod
    def _angle_similarity(reference_pose: PoseFrame, realtime_pose: PoseFrame) -> float:
        common_keys = set(reference_pose.joint_angles).intersection(realtime_pose.joint_angles)
        if not common_keys:
            return 0.0

        differences = [
            abs(reference_pose.joint_angles[key] - realtime_pose.joint_angles[key])
            for key in common_keys
        ]
        mean_difference = float(np.mean(differences))
        return float(np.clip(1.0 - (mean_difference / 180.0), 0.0, 1.0))

    @staticmethod
    def _confidence(reference_pose: PoseFrame, realtime_pose: PoseFrame) -> float:
        if reference_pose.landmarks.size == 0 or realtime_pose.landmarks.size == 0:
            return 0.0

        reference_confidence = float(np.mean(reference_pose.landmarks[:, 3]))
        realtime_confidence = float(np.mean(realtime_pose.landmarks[:, 3]))
        return float(np.clip(min(reference_confidence, realtime_confidence), 0.0, 1.0))

