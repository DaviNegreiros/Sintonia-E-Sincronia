from __future__ import annotations

import time
from pathlib import Path

import cv2
import numpy as np


class VideoPlayer:
    """OpenCV-backed reference video player with timestamp synchronization."""

    def __init__(self, video_path: str | Path) -> None:
        self.video_path = Path(video_path).expanduser().resolve()
        self.capture: cv2.VideoCapture | None = None
        self.fps = 0.0
        self.frame_count = 0
        self.current_frame_index = 0
        self.current_frame: np.ndarray | None = None
        self._started_at: float | None = None
        self._paused = True

    def open(self) -> None:
        """Open the reference video."""
        if not self.video_path.exists():
            raise FileNotFoundError(f"Video not found: {self.video_path}")

        self.capture = cv2.VideoCapture(str(self.video_path))
        if not self.capture.isOpened():
            raise RuntimeError(f"Could not open video: {self.video_path}")

        self.fps = float(self.capture.get(cv2.CAP_PROP_FPS))
        self.frame_count = int(self.capture.get(cv2.CAP_PROP_FRAME_COUNT))
        if self.fps <= 0 or self.frame_count <= 0:
            raise ValueError("Invalid video metadata.")

        success, frame = self.capture.read()
        if not success or frame is None:
            raise RuntimeError("Could not read first video frame.")
        self.current_frame = frame
        self.current_frame_index = 0
        self.capture.set(cv2.CAP_PROP_POS_FRAMES, 0)

    def play(self) -> None:
        """Start or resume playback."""
        self._started_at = time.perf_counter() - (self.timestamp_ms / 1000.0)
        self._paused = False

    def pause(self) -> None:
        """Pause playback."""
        self._paused = True

    def countdown_sequence(self) -> tuple[str, ...]:
        """Return the fixed pre-game countdown labels."""
        return ("3", "2", "1", "JÁ!")

    def read(self) -> tuple[bool, np.ndarray | None]:
        """Return the frame synchronized to elapsed playback time."""
        if self.capture is None:
            raise RuntimeError("Video player is not initialized.")
        if self._paused:
            return True, self.current_frame

        target_frame = min(int((self.timestamp_ms / 1000.0) * self.fps), self.frame_count - 1)
        if target_frame != self.current_frame_index:
            self.capture.set(cv2.CAP_PROP_POS_FRAMES, target_frame)
            success, frame = self.capture.read()
            if not success or frame is None:
                self.current_frame_index = self.frame_count - 1
                return self.current_frame is not None, self.current_frame
            self.current_frame = frame
            self.current_frame_index = target_frame

        return True, self.current_frame

    def reset(self) -> None:
        """Seek playback to the beginning and pause."""
        if self.capture is None:
            raise RuntimeError("Video player is not initialized.")
        self.capture.set(cv2.CAP_PROP_POS_FRAMES, 0)
        success, frame = self.capture.read()
        if success and frame is not None:
            self.current_frame = frame
        self.capture.set(cv2.CAP_PROP_POS_FRAMES, 0)
        self.current_frame_index = 0
        self._started_at = None
        self._paused = True

    @property
    def timestamp_ms(self) -> float:
        """Return the current playback timestamp in milliseconds."""
        if self._started_at is None:
            return (self.current_frame_index / self.fps) * 1000.0 if self.fps > 0 else 0.0
        return max(0.0, (time.perf_counter() - self._started_at) * 1000.0)

    @property
    def duration_ms(self) -> float:
        """Return the full video duration in milliseconds."""
        return (self.frame_count / self.fps) * 1000.0 if self.fps > 0 else 0.0

    @property
    def is_finished(self) -> bool:
        """Return whether playback reached the last frame."""
        if self._started_at is not None and self.timestamp_ms >= self.duration_ms:
            return True
        return self.current_frame_index >= self.frame_count - 1

    def release(self) -> None:
        """Release native video resources."""
        if self.capture is not None:
            self.capture.release()
            self.capture = None
