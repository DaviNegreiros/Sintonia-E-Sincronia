from __future__ import annotations

import cv2
import numpy as np


class CameraCapture:
    """Small wrapper around OpenCV webcam capture."""

    def __init__(self, camera_index: int = 0, width: int | None = None, height: int | None = None) -> None:
        self.camera_index = camera_index
        self.width = width
        self.height = height
        self.capture: cv2.VideoCapture | None = None

    def open(self) -> None:
        """Open the configured webcam."""
        self.capture = cv2.VideoCapture(self.camera_index)
        if not self.capture.isOpened():
            raise RuntimeError(f"Could not open webcam at index {self.camera_index}.")

        if self.width is not None:
            self.capture.set(cv2.CAP_PROP_FRAME_WIDTH, self.width)
        if self.height is not None:
            self.capture.set(cv2.CAP_PROP_FRAME_HEIGHT, self.height)

    def read(self) -> tuple[bool, np.ndarray | None]:
        """Read one frame from the webcam."""
        if self.capture is None:
            raise RuntimeError("Camera is not initialized.")
        return self.capture.read()

    def release(self) -> None:
        """Release the webcam resource."""
        if self.capture is not None:
            self.capture.release()
            self.capture = None

