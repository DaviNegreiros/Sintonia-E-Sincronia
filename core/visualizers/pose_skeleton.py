from __future__ import annotations

from typing import Any

import cv2
import numpy as np

from core.pose_connections import POSE_CONNECTIONS


class PoseSkeletonDrawer:
    """Draw MediaPipe pose landmarks and official connections."""

    def __init__(
        self,
        visibility_threshold: float = 0.35,
        connection_color: tuple[int, int, int] = (0, 255, 0),
        landmark_color: tuple[int, int, int] = (0, 128, 255),
        connection_thickness: int = 2,
        landmark_radius: int = 4,
    ) -> None:
        self.visibility_threshold = visibility_threshold
        self.connection_color = connection_color
        self.landmark_color = landmark_color
        self.connection_thickness = connection_thickness
        self.landmark_radius = landmark_radius

    def draw(self, frame: np.ndarray, landmarks: np.ndarray | list[Any]) -> np.ndarray:
        """Draw the pose skeleton on the provided frame."""
        points = self._to_points(frame, landmarks)
        if not points:
            return frame

        for start_idx, end_idx in POSE_CONNECTIONS:
            start = points[start_idx]
            end = points[end_idx]
            if start[2] >= self.visibility_threshold and end[2] >= self.visibility_threshold:
                cv2.line(
                    frame,
                    start[:2],
                    end[:2],
                    self.connection_color,
                    self.connection_thickness,
                    cv2.LINE_AA,
                )

        for x, y, visibility in points:
            if visibility >= self.visibility_threshold:
                cv2.circle(frame, (x, y), self.landmark_radius, self.landmark_color, -1, cv2.LINE_AA)

        return frame

    @staticmethod
    def _to_points(frame: np.ndarray, landmarks: np.ndarray | list[Any]) -> list[tuple[int, int, float]]:
        height, width = frame.shape[:2]
        points = []
        for landmark in landmarks:
            if hasattr(landmark, "x"):
                x = int(round(float(landmark.x) * width))
                y = int(round(float(landmark.y) * height))
                visibility = float(landmark.visibility)
            else:
                x = int(round(float(landmark[0]) * width))
                y = int(round(float(landmark[1]) * height))
                visibility = float(landmark[3])
            points.append((x, y, visibility))
        return points

