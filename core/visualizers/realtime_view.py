from __future__ import annotations

import cv2
import numpy as np

from core.pose_frame import PoseFrame
from core.visualizers.pose_skeleton import PoseSkeletonDrawer


class RealtimeView:
    """Draw webcam frame, pose skeleton and camera HUD."""

    window_name = "Realtime Tracking"

    def __init__(self) -> None:
        self.skeleton_drawer = PoseSkeletonDrawer()

    def draw(self, frame: np.ndarray, pose_frame: PoseFrame, fps: float) -> np.ndarray:
        """Return a rendered real-time tracking frame."""
        canvas = frame.copy()
        if pose_frame.pose_detected:
            self.skeleton_drawer.draw(canvas, pose_frame.landmarks)
        self._draw_hud(canvas, pose_frame, fps)
        return canvas

    def show(self, image: np.ndarray) -> None:
        """Display the rendered frame."""
        cv2.imshow(self.window_name, image)

    @staticmethod
    def _draw_hud(canvas: np.ndarray, pose_frame: PoseFrame, fps: float) -> None:
        lines = (
            f"FPS: {fps:05.1f}",
            f"Frame: {pose_frame.frame}",
            f"Timestamp: {pose_frame.timestamp:.1f} ms",
            f"Pose Detected: {pose_frame.pose_detected}",
        )
        x, y = 16, 28
        for index, line in enumerate(lines):
            y_position = y + index * 26
            cv2.putText(canvas, line, (x, y_position), cv2.FONT_HERSHEY_SIMPLEX, 0.65, (0, 0, 0), 3, cv2.LINE_AA)
            cv2.putText(canvas, line, (x, y_position), cv2.FONT_HERSHEY_SIMPLEX, 0.65, (255, 255, 255), 1, cv2.LINE_AA)
