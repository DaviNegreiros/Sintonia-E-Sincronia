from __future__ import annotations

from collections import deque
from dataclasses import replace

from config.defaults import FEEDBACK_INTERVAL_MS, SLIDING_WINDOW_MS, TIMESTAMP_TOLERANCE_MS
from contracts.evaluation import FrameEvaluation
from contracts.pose import PoseFrame
from domain.evaluation import PoseComparator
from domain.feedback import FeedbackMapper
from domain.timing import ReferenceTimeline


class ComparisonEngine:
    """Coordinate timestamp-window comparison, scoring and periodic feedback."""

    def __init__(
        self,
        moveset_source: ReferenceTimeline,
        comparator: PoseComparator,
        feedback_mapper: FeedbackMapper,
        timestamp_tolerance_ms: int = TIMESTAMP_TOLERANCE_MS,
        feedback_interval_ms: int = FEEDBACK_INTERVAL_MS,
        sliding_window_ms: int = SLIDING_WINDOW_MS,
    ) -> None:
        self.moveset_source = moveset_source
        self.comparator = comparator
        self.feedback_mapper = feedback_mapper
        self.timestamp_tolerance_ms = timestamp_tolerance_ms
        self.feedback_interval_ms = feedback_interval_ms
        self.sliding_window_ms = sliding_window_ms
        self.score_window: deque[tuple[float, float]] = deque()
        self.all_scores: list[float] = []
        self.feedback_history: list[str] = []
        self.current_feedback = "Eita!"
        self._last_feedback_timestamp = 0.0

    def compare(self, realtime_pose: PoseFrame) -> FrameEvaluation:
        """Compare realtime pose against the best reference pose in the tolerance window."""
        candidates = self.moveset_source.get_poses_near(realtime_pose.timestamp, self.timestamp_tolerance_ms)
        if not candidates:
            result = FrameEvaluation(realtime_pose.timestamp, 0.0, 0.0, 0.0, 0.0, self.current_feedback)
            self._update_score_window(result)
            return replace(result, feedback=self.current_feedback)

        result = max(
            (self.comparator.compare(reference_pose, realtime_pose) for reference_pose in candidates),
            key=lambda item: item.overall_similarity,
        )
        self._update_score_window(result)
        return replace(result, feedback=self.current_feedback)

    def current_score(self) -> float:
        """Return the moving average score on a 0-100 scale."""
        if not self.score_window:
            return 0.0
        return sum(score for _, score in self.score_window) / len(self.score_window)

    def similarity_average(self) -> float:
        """Return the full-song continuous similarity average on a 0-100 scale."""
        if not self.all_scores:
            return 0.0
        return sum(self.all_scores) / len(self.all_scores)

    def _update_score_window(self, result: FrameEvaluation) -> None:
        score = result.overall_similarity * 100.0
        self.all_scores.append(score)
        self.score_window.append((result.timestamp, score))

        min_timestamp = result.timestamp - self.sliding_window_ms
        while self.score_window and self.score_window[0][0] < min_timestamp:
            self.score_window.popleft()

        if result.timestamp - self._last_feedback_timestamp >= self.feedback_interval_ms:
            self.current_feedback = self.feedback_mapper.map_score(self.current_score())
            self.feedback_history.append(self.current_feedback)
            self._last_feedback_timestamp = result.timestamp
