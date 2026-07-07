from __future__ import annotations

import cv2
import numpy as np

from core.pose_comparator import SimilarityResult
from core.pose_frame import PoseFrame
from core.visualizers.realtime_view import RealtimeView


class ComparisonHUD:
    """Render reference video and webcam side by side with gameplay HUD."""

    window_name = "Dance Comparison"

    def __init__(self, panel_width: int = 640, panel_height: int = 480) -> None:
        self.panel_width = panel_width
        self.panel_height = panel_height
        self.realtime_view = RealtimeView()

    def draw(
        self,
        reference_frame: np.ndarray,
        webcam_frame: np.ndarray,
        realtime_pose: PoseFrame,
        result: SimilarityResult,
        fps: float,
        score: float,
        feedback: str,
        countdown_text: str | None = None,
    ) -> np.ndarray:
        """Return a side-by-side gameplay frame."""
        reference = self._resize(reference_frame)
        webcam = self._resize(self.realtime_view.draw(webcam_frame, realtime_pose, fps))
        canvas = np.hstack((reference, webcam))
        self._draw_hud(canvas, result, fps, score, feedback, countdown_text)
        return canvas

    def show(self, image: np.ndarray) -> None:
        """Display the comparison HUD."""
        cv2.imshow(self.window_name, image)

    def draw_countdown(
        self,
        reference_frame: np.ndarray,
        webcam_frame: np.ndarray,
        text: str,
        fps: float = 0.0,
    ) -> np.ndarray:
        """Render the pre-game countdown without comparison data."""
        empty_pose = PoseFrame(
            frame=0,
            timestamp=0.0,
            pose_detected=False,
            landmarks=np.empty((0, 4), dtype=np.float32),
            normalized_landmarks=np.empty((0, 4), dtype=np.float32),
            joint_angles={},
        )
        empty_result = SimilarityResult(0.0, 0.0, 0.0, 0.0, 0.0, "")
        return self.draw(reference_frame, webcam_frame, empty_pose, empty_result, fps, 0.0, "", text)

    def _draw_hud(
        self,
        canvas: np.ndarray,
        result: SimilarityResult,
        fps: float,
        score: float,
        feedback: str,
        countdown_text: str | None,
    ) -> None:
        if countdown_text:
            self._draw_center_text(canvas, countdown_text)

        lines = (
            f"Tempo: {result.timestamp / 1000.0:05.1f}s",
            f"FPS: {fps:05.1f}",
            f"Score Atual: {score:05.1f}",
            f"Feedback: {feedback}",
        )
        x, y = 18, 34
        for index, line in enumerate(lines):
            y_position = y + index * 28
            cv2.putText(canvas, line, (x, y_position), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 4, cv2.LINE_AA)
            cv2.putText(canvas, line, (x, y_position), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 1, cv2.LINE_AA)

        cv2.putText(canvas, "Referencia", (18, canvas.shape[0] - 18), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
        cv2.putText(canvas, "Webcam", (self.panel_width + 18, canvas.shape[0] - 18), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)

    def _draw_center_text(self, canvas: np.ndarray, text: str) -> None:
        font = cv2.FONT_HERSHEY_SIMPLEX
        scale = 3.0 if len(text) <= 3 else 2.0
        thickness = 7
        size, _ = cv2.getTextSize(text, font, scale, thickness)
        x = (canvas.shape[1] - size[0]) // 2
        y = (canvas.shape[0] + size[1]) // 2
        cv2.putText(canvas, text, (x, y), font, scale, (0, 0, 0), thickness + 4, cv2.LINE_AA)
        cv2.putText(canvas, text, (x, y), font, scale, (0, 255, 255), thickness, cv2.LINE_AA)

    def _resize(self, frame: np.ndarray) -> np.ndarray:
        return cv2.resize(frame, (self.panel_width, self.panel_height), interpolation=cv2.INTER_AREA)
