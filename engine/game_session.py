from __future__ import annotations

from contracts.events import GameEvent, GameEventType
from contracts.moveset import Moveset
from contracts.pose import PoseFrame
from contracts.result import GameResult
from contracts.session import GameSessionState, GameSessionStatus, SessionConfig
from domain.evaluation import PoseComparator
from domain.feedback import FeedbackMapper
from domain.ranking import FinalScoreCalculator
from domain.scoring import ComparisonEngine
from domain.timing import ReferenceTimeline
from observability import SessionLog

from .session_clock import SessionClock


class GameSession:
    """Coordinate one dance session without depending on UI or Android APIs."""

    def __init__(
        self,
        moveset: Moveset,
        config: SessionConfig,
        clock: SessionClock | None = None,
    ) -> None:
        self.moveset = moveset
        self.config = config
        self.clock = clock or SessionClock()
        self.timeline = ReferenceTimeline(moveset.frames)
        self.comparison = ComparisonEngine(
            moveset_source=self.timeline,
            comparator=PoseComparator(
                landmark_distance_threshold=config.landmark_distance_threshold,
                landmark_similarity_weight=config.landmark_similarity_weight,
                angle_similarity_weight=config.angle_similarity_weight,
            ),
            feedback_mapper=FeedbackMapper(),
            timestamp_tolerance_ms=config.timestamp_tolerance_ms,
            feedback_interval_ms=config.feedback_interval_ms,
            sliding_window_ms=config.sliding_window_ms,
        )
        self.final_score_calculator = FinalScoreCalculator()
        self.log = SessionLog()
        self._events: list[GameEvent] = []
        self._status = GameSessionStatus.READY
        self._last_pose_detected = False
        self._last_confidence = 0.0
        self._last_feedback = self.comparison.current_feedback
        self._last_frame = 0
        self._result: GameResult | None = None
        self._emit(GameEventType.DANCE_LOADED, 0.0, {"video": moveset.metadata.video})

    def start(self) -> GameSessionState:
        """Start the session."""
        self.clock.start()
        self._status = GameSessionStatus.PLAYING
        self._emit(GameEventType.SESSION_STARTED, self.clock.elapsed_ms)
        return self.get_state()

    def pause(self) -> GameSessionState:
        """Pause the session."""
        self.clock.pause()
        self._status = GameSessionStatus.PAUSED
        self._emit(GameEventType.SESSION_PAUSED, self.clock.elapsed_ms)
        return self.get_state()

    def resume(self) -> GameSessionState:
        """Resume the session."""
        self.clock.resume()
        self._status = GameSessionStatus.PLAYING
        self._emit(GameEventType.SESSION_RESUMED, self.clock.elapsed_ms)
        return self.get_state()

    def process_pose(self, pose: PoseFrame) -> GameSessionState:
        """Process one already-detected pose frame."""
        if self._status not in {GameSessionStatus.PLAYING, GameSessionStatus.READY}:
            return self.get_state()

        evaluation = self.comparison.compare(pose)
        score = self.comparison.current_score()
        self._last_confidence = evaluation.confidence
        self._last_frame = pose.frame

        if self._last_pose_detected and not pose.pose_detected:
            self._emit(GameEventType.POSE_LOST, pose.timestamp)
        elif not self._last_pose_detected and pose.pose_detected:
            self._emit(GameEventType.POSE_RECOVERED, pose.timestamp)
        self._last_pose_detected = pose.pose_detected

        self._emit(GameEventType.SCORE_UPDATED, pose.timestamp, {"score": score})
        if evaluation.feedback and evaluation.feedback != self._last_feedback:
            self._last_feedback = evaluation.feedback
            self._emit(GameEventType.FEEDBACK_CHANGED, pose.timestamp, {"feedback": evaluation.feedback})

        self.log.record_frame(pose, evaluation, score)
        return self.get_state(elapsed_ms=pose.timestamp)

    def finish(self) -> GameResult:
        """Finish the session and calculate the final result."""
        self._status = GameSessionStatus.FINISHED
        self._result = self.final_score_calculator.calculate(
            feedbacks=self.comparison.feedback_history,
            similarity_average=self.comparison.similarity_average(),
            duration_seconds=self.moveset.metadata.duration,
        )
        self._emit(GameEventType.DANCE_FINISHED, self.clock.elapsed_ms, {"final_score": self._result.final_score})
        return self._result

    def get_state(self, elapsed_ms: float | None = None) -> GameSessionState:
        """Return a stable state snapshot for consumers."""
        return GameSessionState(
            status=self._status,
            elapsed_ms=self.clock.elapsed_ms if elapsed_ms is None else elapsed_ms,
            duration_ms=self.moveset.metadata.duration_ms,
            current_score=self.comparison.current_score(),
            similarity_average=self.comparison.similarity_average(),
            feedback=self.comparison.current_feedback,
            pose_detected=self._last_pose_detected,
            confidence=self._last_confidence,
            frame=self._last_frame,
            api_version=self.config.api_version,
        )

    def consume_events(self) -> tuple[GameEvent, ...]:
        """Return pending events and clear the event queue."""
        events = tuple(self._events)
        self._events.clear()
        return events

    @property
    def result(self) -> GameResult | None:
        return self._result

    def _emit(
        self,
        event_type: GameEventType,
        timestamp: float,
        payload: dict[str, object] | None = None,
    ) -> None:
        event = GameEvent(event_type, timestamp, payload or {})
        self._events.append(event)
        self.log.record_event(event)
