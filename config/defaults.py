from __future__ import annotations

from contracts.session import SessionConfig

TIMESTAMP_TOLERANCE_MS = 200
FEEDBACK_INTERVAL_MS = 5000
SLIDING_WINDOW_MS = 5000
LANDMARK_DISTANCE_THRESHOLD = 1.0
LANDMARK_SIMILARITY_WEIGHT = 0.7
ANGLE_SIMILARITY_WEIGHT = 0.3
DEFAULT_COUNTDOWN_LABELS = ("3", "2", "1", "JÁ!")

DEFAULT_SESSION_CONFIG = SessionConfig(
    timestamp_tolerance_ms=TIMESTAMP_TOLERANCE_MS,
    feedback_interval_ms=FEEDBACK_INTERVAL_MS,
    sliding_window_ms=SLIDING_WINDOW_MS,
    countdown_labels=DEFAULT_COUNTDOWN_LABELS,
    landmark_distance_threshold=LANDMARK_DISTANCE_THRESHOLD,
    landmark_similarity_weight=LANDMARK_SIMILARITY_WEIGHT,
    angle_similarity_weight=ANGLE_SIMILARITY_WEIGHT,
)
