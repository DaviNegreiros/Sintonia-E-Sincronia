from __future__ import annotations

import time
from typing import Protocol

import numpy as np

from .camera_capture import CameraCapture
from .joint_angle_calculator import JointAngleCalculator
from .pose_detector import PoseDetector
from .pose_frame import PoseFrame
from .pose_normalizer import NormalizationResult, PoseNormalizer


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
