from __future__ import annotations

import time


class SessionClock:
    """Monotonic clock for a game session."""

    def __init__(self) -> None:
        self._started_at: float | None = None
        self._paused_at: float | None = None
        self._paused_total = 0.0

    def start(self) -> None:
        """Start or restart the clock."""
        self._started_at = time.perf_counter()
        self._paused_at = None
        self._paused_total = 0.0

    def pause(self) -> None:
        """Pause elapsed time accounting."""
        if self._started_at is not None and self._paused_at is None:
            self._paused_at = time.perf_counter()

    def resume(self) -> None:
        """Resume elapsed time accounting."""
        if self._paused_at is not None:
            self._paused_total += time.perf_counter() - self._paused_at
            self._paused_at = None

    @property
    def elapsed_ms(self) -> float:
        """Return elapsed time in milliseconds."""
        if self._started_at is None:
            return 0.0
        current = self._paused_at if self._paused_at is not None else time.perf_counter()
        return max(0.0, (current - self._started_at - self._paused_total) * 1000.0)
