from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class FrameEvaluation:
    """Similarity details for one comparison instant."""

    timestamp: float
    overall_similarity: float
    landmark_similarity: float
    angle_similarity: float
    confidence: float
    feedback: str = ""
