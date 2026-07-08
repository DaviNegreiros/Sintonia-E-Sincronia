from __future__ import annotations

from pathlib import Path
from typing import Protocol

from contracts.moveset import Moveset


class DanceRepository(Protocol):
    """Persistence boundary for reference dances and movesets."""

    def load_moveset(self, path: str | Path) -> Moveset:
        """Load a local moveset."""
        ...
