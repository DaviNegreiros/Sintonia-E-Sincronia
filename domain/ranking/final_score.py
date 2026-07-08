from __future__ import annotations

import numpy as np

from contracts.result import GameResult


class FinalScoreCalculator:
    """Calculate final score from continuous similarity and emitted feedbacks."""

    FEEDBACK_POINTS = {
        "Perfeito!": 4,
        "Ótimo!": 3,
        "Ok": 2,
        "Eita!": 1,
    }
    MAX_FEEDBACK_POINTS = 4

    def calculate(
        self,
        feedbacks: list[str],
        similarity_average: float,
        duration_seconds: float,
    ) -> GameResult:
        """Return the final score and rank for a completed song."""
        if duration_seconds < 0:
            raise ValueError("Song duration cannot be negative.")

        bounded_similarity = float(np.clip(similarity_average, 0.0, 100.0))
        feedback_score = self._feedback_score(feedbacks)
        final_score = (bounded_similarity * 0.70) + (feedback_score * 0.30)
        final_score = float(np.clip(final_score, 0.0, 100.0))

        return GameResult(
            final_score=final_score,
            ranque=self._rank(final_score),
            similarity_average=bounded_similarity,
            feedback_score=feedback_score,
        )

    def _feedback_score(self, feedbacks: list[str]) -> float:
        if not feedbacks:
            return 0.0

        total_points = sum(self.FEEDBACK_POINTS.get(feedback, 0) for feedback in feedbacks)
        max_points = len(feedbacks) * self.MAX_FEEDBACK_POINTS
        return float(np.clip((total_points / max_points) * 100.0, 0.0, 100.0))

    @staticmethod
    def _rank(score: float) -> str:
        if score >= 95.0:
            return "S"
        if score >= 90.0:
            return "A+"
        if score >= 80.0:
            return "A"
        if score >= 70.0:
            return "B"
        if score >= 60.0:
            return "C"
        if score >= 40.0:
            return "D"
        return "E"
