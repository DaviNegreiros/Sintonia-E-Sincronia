from __future__ import annotations

from dataclasses import dataclass, field
from typing import Mapping

from .pose import PoseFrame
from .versions import MOVESET_SCHEMA_VERSION


@dataclass(frozen=True, slots=True)
class DanceMetadata:
    """Metadata for a reference dance/video."""

    video: str
    fps: float
    frame_count: int
    width: int
    height: int
    duration: float
    landmark_count: int
    title: str | None = None

    @property
    def duration_ms(self) -> float:
        return self.duration * 1000.0


@dataclass(frozen=True, slots=True)
class Moveset:
    """Versioned reference pose timeline."""

    metadata: DanceMetadata
    frames: tuple[PoseFrame, ...]
    schema_version: int = MOVESET_SCHEMA_VERSION
    raw_metadata: Mapping[str, object] = field(default_factory=dict)
