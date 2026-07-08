from __future__ import annotations

from dataclasses import dataclass, field

from contracts.evaluation import FrameEvaluation
from contracts.events import GameEvent
from contracts.pose import PoseFrame


@dataclass(frozen=True, slots=True)
class FrameLogEntry:
    """Structured diagnostic entry for one processed frame."""

    frame: int
    timestamp: float
    pose_detected: bool
    confidence: float
    score: float
    feedback: str
    overall_similarity: float
    landmark_similarity: float
    angle_similarity: float


@dataclass(slots=True)
class SessionLog:
    """Collect structured frame entries and emitted events."""

    frames: list[FrameLogEntry] = field(default_factory=list)
    events: list[GameEvent] = field(default_factory=list)

    def record_frame(self, pose: PoseFrame, evaluation: FrameEvaluation, score: float) -> None:
        """Record diagnostic information for a processed frame."""
        self.frames.append(
            FrameLogEntry(
                frame=pose.frame,
                timestamp=pose.timestamp,
                pose_detected=pose.pose_detected,
                confidence=evaluation.confidence,
                score=score,
                feedback=evaluation.feedback,
                overall_similarity=evaluation.overall_similarity,
                landmark_similarity=evaluation.landmark_similarity,
                angle_similarity=evaluation.angle_similarity,
            )
        )

    def record_event(self, event: GameEvent) -> None:
        """Record an emitted event."""
        self.events.append(event)
