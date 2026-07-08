from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from contracts.moveset import Moveset


@dataclass(frozen=True, slots=True)
class DancePackage:
    """Files that compose one locally stored dance."""

    id: str
    root: Path
    video_path: Path
    preview_path: Path
    moveset_path: Path
    moveset: Moveset
