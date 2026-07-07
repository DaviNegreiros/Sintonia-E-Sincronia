from __future__ import annotations

import cv2
import numpy as np

from core.pose_frame import PoseFrame


POSE_CONNECTIONS: tuple[tuple[int, int], ...] = (
    (0, 1), (1, 2), (2, 3), (3, 7),
    (0, 4), (4, 5), (5, 6), (6, 8),
    (9, 10),
    (11, 12), (11, 13), (13, 15), (15, 17), (15, 19), (15, 21), (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22), (18, 20),
    (11, 23), (12, 24), (23, 24),
    (23, 25), (24, 26), (25, 27), (26, 28),
    (27, 29), (28, 30), (29, 31), (30, 32), (27, 31), (28, 32),
)


class RealtimeView:
    """Draw webcam frame, pose skeleton and camera HUD."""

    window_name = "Realtime Tracking"

    def draw(self, frame: np.ndarray, pose_frame: PoseFrame, fps: float) -> np.ndarray:
        """Return a rendered real-time tracking frame."""
        canvas = frame.copy()
        if pose_frame.pose_detected:
            self._draw_skeleton(canvas, pose_frame.landmarks)
        self._draw_hud(canvas, pose_frame, fps)
        return canvas

    def show(self, image: np.ndarray) -> None:
        """Display the rendered frame."""
        cv2.imshow(self.window_name, image)

    @staticmethod
    def _draw_skeleton(canvas: np.ndarray, landmarks: np.ndarray) -> None:
        height, width = canvas.shape[:2]
        points = []
        for landmark in landmarks:
            x = int(round(float(landmark[0]) * width))
            y = int(round(float(landmark[1]) * height))
            visibility = float(landmark[3])
            points.append((x, y, visibility))

        for start_idx, end_idx in POSE_CONNECTIONS:
            start = points[start_idx]
            end = points[end_idx]
            if start[2] >= 0.35 and end[2] >= 0.35:
                cv2.line(canvas, start[:2], end[:2], (0, 255, 0), 2, cv2.LINE_AA)

        for x, y, visibility in points:
            if visibility >= 0.35:
                cv2.circle(canvas, (x, y), 4, (0, 128, 255), -1, cv2.LINE_AA)

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

