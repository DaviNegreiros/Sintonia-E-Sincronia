from __future__ import annotations

import json

import cv2
import numpy as np

from core.pose_frame import PoseFrame


class DebugPanel:
    """Render technical pose information in a separate OpenCV window."""

    window_name = "Pose Debug"

    def __init__(self, width: int = 760, height: int = 920) -> None:
        self.width = width
        self.height = height

    def draw(self, pose_frame: PoseFrame, fps: float, hip_center: np.ndarray, scale_factor: float) -> np.ndarray:
        """Return a debug panel image for the current pose frame."""
        panel = np.full((self.height, self.width, 3), 24, dtype=np.uint8)
        lines = self._build_lines(pose_frame, fps, hip_center, scale_factor)

        y = 28
        for line in lines:
            cv2.putText(panel, line, (18, y), cv2.FONT_HERSHEY_SIMPLEX, 0.48, (230, 230, 230), 1, cv2.LINE_AA)
            y += 20
            if y > self.height - 20:
                break
        return panel

    def show(self, image: np.ndarray) -> None:
        """Display the debug panel."""
        cv2.imshow(self.window_name, image)

    def _build_lines(
        self,
        pose_frame: PoseFrame,
        fps: float,
        hip_center: np.ndarray,
        scale_factor: float,
    ) -> list[str]:
        hip_center_text = np.array2string(hip_center, precision=4, suppress_small=True)
        normalized_preview = pose_frame.normalized_landmarks[:5].round(4).tolist()
        json_preview = json.dumps(pose_frame.to_debug_dict(), indent=2)

        lines = [
            "Pose Debug",
            f"Frame: {pose_frame.frame}",
            f"Timestamp: {pose_frame.timestamp:.1f} ms",
            f"FPS: {fps:.1f}",
            f"Hip Center: {hip_center_text}",
            f"Scale Factor: {scale_factor:.6f}",
            f"Pose Detected: {pose_frame.pose_detected}",
            f"Landmark Count: {len(pose_frame.landmarks)}",
            "First Normalized Landmarks:",
            *self._wrap_text(str(normalized_preview), 100),
            "Joint Angles:",
            *[f"  {key}: {value:.2f}" for key, value in pose_frame.joint_angles.items()],
            "PoseFrame JSON Preview:",
            *json_preview.splitlines(),
        ]
        return lines

    @staticmethod
    def _wrap_text(text: str, width: int) -> list[str]:
        return [text[index : index + width] for index in range(0, len(text), width)] or [""]

