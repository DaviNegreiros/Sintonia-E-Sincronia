from __future__ import annotations

import numpy as np

from contracts.pose import PoseFrame
from domain.evaluation import PoseComparator
from domain.feedback import FeedbackMapper
from domain.pose import JointAngleCalculator, PoseNormalizer
from domain.ranking import FinalScoreCalculator


def _landmarks() -> np.ndarray:
    points = np.zeros((33, 4), dtype=np.float32)
    points[:, 3] = 1.0
    points[11, :3] = [-1.0, -1.0, 0.0]
    points[12, :3] = [1.0, -1.0, 0.0]
    points[23, :3] = [-1.0, 1.0, 0.0]
    points[24, :3] = [1.0, 1.0, 0.0]
    points[13, :3] = [-2.0, 0.0, 0.0]
    points[14, :3] = [2.0, 0.0, 0.0]
    points[15, :3] = [-3.0, 0.0, 0.0]
    points[16, :3] = [3.0, 0.0, 0.0]
    points[25, :3] = [-1.0, 3.0, 0.0]
    points[26, :3] = [1.0, 3.0, 0.0]
    points[27, :3] = [-1.0, 5.0, 0.0]
    points[28, :3] = [1.0, 5.0, 0.0]
    return points


def test_feedback_thresholds_are_preserved() -> None:
    mapper = FeedbackMapper()

    assert mapper.map_score(0.0) == "Eita!"
    assert mapper.map_score(25.0) == "Ok"
    assert mapper.map_score(50.0) == "Ótimo!"
    assert mapper.map_score(75.0) == "Perfeito!"


def test_final_score_and_rank_are_preserved() -> None:
    result = FinalScoreCalculator().calculate(["Perfeito!", "Ok"], 80.0, 10.0)

    assert result.similarity_average == 80.0
    assert result.feedback_score == 75.0
    assert result.final_score == 78.5
    assert result.ranque == "B"


def test_pose_normalization_preserves_shape_and_centers_hips() -> None:
    result = PoseNormalizer().normalize(_landmarks())

    assert result.normalized_landmarks.shape == (33, 4)
    np.testing.assert_allclose(result.hip_center, np.array([0.0, 1.0, 0.0], dtype=np.float32))
    np.testing.assert_allclose(result.normalized_landmarks[23, :3], [-0.5, 0.0, 0.0])
    np.testing.assert_allclose(result.normalized_landmarks[24, :3], [0.5, 0.0, 0.0])


def test_comparator_scores_identical_pose_as_perfect() -> None:
    landmarks = _landmarks()
    normalized = PoseNormalizer().normalize(landmarks).normalized_landmarks
    angles = JointAngleCalculator().calculate(normalized)
    reference = PoseFrame(0, 0.0, True, landmarks, normalized, angles)
    realtime = PoseFrame(0, 0.0, True, landmarks.copy(), normalized.copy(), dict(angles))

    result = PoseComparator().compare(reference, realtime)

    assert result.overall_similarity == 1.0
    assert result.landmark_similarity == 1.0
    assert result.angle_similarity == 1.0
    assert result.confidence == 1.0


def test_comparator_scores_missing_pose_as_zero() -> None:
    empty = PoseFrame(
        frame=0,
        timestamp=0.0,
        pose_detected=False,
        landmarks=np.empty((0, 4), dtype=np.float32),
        normalized_landmarks=np.empty((0, 4), dtype=np.float32),
        joint_angles={},
    )

    result = PoseComparator().compare(empty, empty)

    assert result.overall_similarity == 0.0
    assert result.confidence == 0.0
