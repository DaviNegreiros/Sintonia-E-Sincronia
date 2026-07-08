from __future__ import annotations

from typing import Protocol

from contracts.pose import PoseFrame


class PoseProvider(Protocol):
    """Boundary for any source that can provide pose frames."""

    def get_next_pose(self) -> PoseFrame:
        """Return the next available pose frame."""
        ...
