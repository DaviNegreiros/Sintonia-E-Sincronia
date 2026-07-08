"""Compatibility imports for continuous scoring services."""

from config.defaults import FEEDBACK_INTERVAL_MS, SLIDING_WINDOW_MS, TIMESTAMP_TOLERANCE_MS
from domain.scoring import ComparisonEngine

__all__ = [
    "ComparisonEngine",
    "FEEDBACK_INTERVAL_MS",
    "SLIDING_WINDOW_MS",
    "TIMESTAMP_TOLERANCE_MS",
]
