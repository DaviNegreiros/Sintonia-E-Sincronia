from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class GameResult:
    """Final consolidated result for one completed dance."""

    final_score: float
    ranque: str
    similarity_average: float
    feedback_score: float
