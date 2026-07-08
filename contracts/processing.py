from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class ProcessingStatus(StrEnum):
    IDLE = "idle"
    VIDEO_SELECTED = "video_selected"
    PROCESSING = "processing"
    SUCCESS = "success"
    FAILED = "failed"


@dataclass(frozen=True, slots=True)
class ProcessingState:
    """State for local video-to-moveset processing."""

    status: ProcessingStatus
    progress: float = 0.0
    message: str = ""
