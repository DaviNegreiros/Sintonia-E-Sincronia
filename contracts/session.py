from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

from .versions import API_VERSION


class GameSessionStatus(StrEnum):
    IDLE = "idle"
    READY = "ready"
    COUNTDOWN = "countdown"
    PLAYING = "playing"
    PAUSED = "paused"
    FINISHED = "finished"
    ERROR = "error"


@dataclass(frozen=True, slots=True)
class SessionConfig:
    """Configuration values for one game session."""

    timestamp_tolerance_ms: int = 200
    feedback_interval_ms: int = 5000
    sliding_window_ms: int = 5000
    countdown_labels: tuple[str, ...] = ("3", "2", "1", "JÁ!")
    landmark_distance_threshold: float = 1.0
    landmark_similarity_weight: float = 0.7
    angle_similarity_weight: float = 0.3
    api_version: str = API_VERSION


@dataclass(frozen=True, slots=True)
class GameSessionState:
    """State snapshot intended for UI/application consumers."""

    status: GameSessionStatus
    elapsed_ms: float
    duration_ms: float
    current_score: float
    similarity_average: float
    feedback: str
    pose_detected: bool
    confidence: float
    frame: int
    api_version: str = API_VERSION
