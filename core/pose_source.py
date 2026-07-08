from __future__ import annotations

import time
from pathlib import Path
from typing import Protocol

import numpy as np

from contracts.pose import PoseFrame
from domain.pose import JointAngleCalculator, NormalizationResult, PoseNormalizer
from domain.timing import ReferenceTimeline
from storage import MovesetRepository
from vision.camera_capture import CameraCapture
from vision.pose_detector import PoseDetector


class PoseSource(Protocol):
    """Common source interface for real-time and moveset poses."""

    def get_next_pose(self) -> PoseFrame:
        """Return the next available pose frame."""
        ...


class RealtimePoseSource:
    """Create PoseFrame objects from webcam frames."""

    def __init__(
        self,
        camera: CameraCapture,
        detector: PoseDetector,
        normalizer: PoseNormalizer,
        angle_calculator: JointAngleCalculator,
    ) -> None:
        self.camera = camera
        self.detector = detector
        self.normalizer = normalizer
        self.angle_calculator = angle_calculator
        self.frame_index = 0
        self.latest_frame: np.ndarray | None = None
        self.latest_normalization = NormalizationResult(
            normalized_landmarks=np.empty((0, 4), dtype=np.float32),
            hip_center=np.full(3, np.nan, dtype=np.float32),
            scale_factor=0.0,
        )
        self.start_time = time.perf_counter()
        self._last_timestamp_ms = -1

    def get_next_pose(self) -> PoseFrame:
        """Capture, detect, normalize and convert the next webcam frame."""
        success, frame = self.camera.read()
        if not success or frame is None:
            raise RuntimeError("Could not read frame from webcam.")

        self.latest_frame = frame
        timestamp_ms = int((time.perf_counter() - self.start_time) * 1000.0)
        timestamp_ms = max(timestamp_ms, self._last_timestamp_ms + 1)
        self._last_timestamp_ms = timestamp_ms
        landmarks = self.detector.detect(frame, timestamp_ms)
        self.latest_normalization = self.normalizer.normalize(landmarks)
        joint_angles = self.angle_calculator.calculate(self.latest_normalization.normalized_landmarks)

        pose_frame = PoseFrame(
            frame=self.frame_index,
            timestamp=float(timestamp_ms),
            pose_detected=landmarks.size > 0,
            landmarks=landmarks,
            normalized_landmarks=self.latest_normalization.normalized_landmarks,
            joint_angles=joint_angles,
        )
        self.frame_index += 1
        return pose_frame


class MovesetPoseSource:
    """Create PoseFrame objects from a moveset JSON using timestamp lookup."""

    def __init__(
        self,
        moveset_path: str | Path,
        normalizer: PoseNormalizer,
        angle_calculator: JointAngleCalculator,
    ) -> None:
        self.moveset_path = Path(moveset_path).expanduser().resolve()
        self.normalizer = normalizer
        self.angle_calculator = angle_calculator
        repository = MovesetRepository(normalizer=normalizer, angle_calculator=angle_calculator)
        self.moveset = repository.load_moveset(self.moveset_path)
        self.timeline = ReferenceTimeline(self.moveset.frames)
        self.frames = list(self.timeline.frames)
        self.timestamps = list(self.timeline.timestamps)

    def get_next_pose(self) -> PoseFrame:
        """Return poses sequentially for Protocol compatibility."""
        return self.timeline.get_next_pose()

    def get_poses_near(self, timestamp_ms: float, tolerance_ms: float) -> list[PoseFrame]:
        """Return all poses inside a timestamp tolerance window."""
        return self.timeline.get_poses_near(timestamp_ms, tolerance_ms)
