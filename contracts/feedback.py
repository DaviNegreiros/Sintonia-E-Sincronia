from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class FeedbackState:
    """Current visible feedback information."""

    label: str
    score: float
    timestamp: float
