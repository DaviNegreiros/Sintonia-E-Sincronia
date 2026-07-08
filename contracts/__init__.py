"""Public contracts shared by the game engine and future clients."""

from .evaluation import FrameEvaluation
from .events import GameEvent, GameEventType
from .feedback import FeedbackState
from .moveset import DanceMetadata, Moveset
from .pose import PoseFrame
from .processing import ProcessingState, ProcessingStatus
from .result import GameResult
from .session import GameSessionState, GameSessionStatus, SessionConfig
from .versions import API_VERSION, MOVESET_SCHEMA_VERSION

__all__ = [
    "API_VERSION",
    "MOVESET_SCHEMA_VERSION",
    "DanceMetadata",
    "FeedbackState",
    "FrameEvaluation",
    "GameEvent",
    "GameEventType",
    "GameResult",
    "GameSessionState",
    "GameSessionStatus",
    "Moveset",
    "PoseFrame",
    "ProcessingState",
    "ProcessingStatus",
    "SessionConfig",
]
