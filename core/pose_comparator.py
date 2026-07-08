"""Compatibility imports for frame evaluation domain services."""

from contracts.evaluation import FrameEvaluation as SimilarityResult
from domain.evaluation import PoseComparator

__all__ = ["PoseComparator", "SimilarityResult"]
