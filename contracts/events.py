from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from types import MappingProxyType
from typing import Mapping


class GameEventType(StrEnum):
    DANCE_LOADED = "dance_loaded"
    COUNTDOWN_STARTED = "countdown_started"
    COUNTDOWN_TICK = "countdown_tick"
    COUNTDOWN_FINISHED = "countdown_finished"
    SESSION_STARTED = "session_started"
    FEEDBACK_CHANGED = "feedback_changed"
    POSE_LOST = "pose_lost"
    POSE_RECOVERED = "pose_recovered"
    SCORE_UPDATED = "score_updated"
    SESSION_PAUSED = "session_paused"
    SESSION_RESUMED = "session_resumed"
    DANCE_FINISHED = "dance_finished"
    ERROR_OCCURRED = "error_occurred"


@dataclass(frozen=True, slots=True)
class GameEvent:
    """Discrete event emitted by the engine."""

    type: GameEventType
    timestamp: float
    payload: Mapping[str, object] = field(default_factory=dict)

    def __post_init__(self) -> None:
        object.__setattr__(self, "payload", MappingProxyType(dict(self.payload)))
