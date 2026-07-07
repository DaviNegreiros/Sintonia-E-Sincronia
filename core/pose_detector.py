from __future__ import annotations

from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision


class PoseDetector:
    """MediaPipe Tasks Vision pose detector."""

    def __init__(
        self,
        model_path: str | Path | None = None,
        min_pose_detection_confidence: float = 0.5,
        min_pose_presence_confidence: float = 0.5,
        min_tracking_confidence: float = 0.5,
    ) -> None:
        self.model_path = Path(model_path).expanduser().resolve() if model_path else self._find_task_model()
        self.min_pose_detection_confidence = min_pose_detection_confidence
        self.min_pose_presence_confidence = min_pose_presence_confidence
        self.min_tracking_confidence = min_tracking_confidence
        self.detector: vision.PoseLandmarker | None = None

    def initialize(self) -> None:
        """Initialize PoseLandmarker in VIDEO mode for notebook-friendly real-time use."""
        if not self.model_path.exists():
            raise FileNotFoundError(f"Pose Landmarker model not found: {self.model_path}")

        base_options = python.BaseOptions(model_asset_path=str(self.model_path))
        options = vision.PoseLandmarkerOptions(
            base_options=base_options,
            running_mode=vision.RunningMode.VIDEO,
            num_poses=1,
            min_pose_detection_confidence=self.min_pose_detection_confidence,
            min_pose_presence_confidence=self.min_pose_presence_confidence,
            min_tracking_confidence=self.min_tracking_confidence,
            output_segmentation_masks=False,
        )
        self.detector = vision.PoseLandmarker.create_from_options(options)

    def detect(self, frame_bgr: np.ndarray, timestamp_ms: int) -> np.ndarray:
        """Return the first detected pose as an Nx4 array: x, y, z, visibility."""
        if self.detector is None:
            raise RuntimeError("PoseDetector is not initialized.")

        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = self.detector.detect_for_video(mp_image, timestamp_ms)

        if not result.pose_landmarks:
            return np.empty((0, 4), dtype=np.float32)

        landmarks = result.pose_landmarks[0]
        return np.array(
            [[landmark.x, landmark.y, landmark.z, landmark.visibility] for landmark in landmarks],
            dtype=np.float32,
        )

    def close(self) -> None:
        """Close MediaPipe native resources."""
        if self.detector is not None:
            self.detector.close()
            self.detector = None

    @staticmethod
    def _find_task_model() -> Path:
        """Find a local Pose Landmarker .task model."""
        search_roots = [Path.cwd(), Path.cwd() / "models"]
        preferred_names = (
            "pose_landmarker_full.task",
            "pose_landmarker_lite.task",
            "pose_landmarker_heavy.task",
        )

        for root in search_roots:
            for name in preferred_names:
                candidate = root / name
                if candidate.exists():
                    return candidate.resolve()

        for root in search_roots:
            if root.exists():
                task_files = sorted(root.glob("*.task"))
                if task_files:
                    return task_files[0].resolve()

        raise FileNotFoundError(
            "No .task model found. Place a Pose Landmarker model in the project root or models/."
        )

