from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np

from contracts.moveset import DanceMetadata, Moveset
from contracts.pose import PoseFrame
from contracts.versions import MOVESET_SCHEMA_VERSION
from domain.pose import JointAngleCalculator, PoseNormalizer


class MovesetRepository:
    """Load versioned local moveset JSON files."""

    def __init__(
        self,
        normalizer: PoseNormalizer | None = None,
        angle_calculator: JointAngleCalculator | None = None,
    ) -> None:
        self.normalizer = normalizer or PoseNormalizer()
        self.angle_calculator = angle_calculator or JointAngleCalculator()

    def load_moveset(self, path: str | Path) -> Moveset:
        """Load a moveset JSON while preserving compatibility with current files."""
        moveset_path = Path(path).expanduser().resolve()
        if not moveset_path.exists():
            raise FileNotFoundError(f"Moveset not found: {moveset_path}")

        with moveset_path.open("r", encoding="utf-8") as file:
            raw_moveset = json.load(file)

        raw_metadata = raw_moveset.get("metadata", {})
        if not isinstance(raw_metadata, dict):
            raw_metadata = {}

        metadata = DanceMetadata(
            video=str(raw_metadata.get("video", "")),
            fps=float(raw_metadata.get("fps", 0.0)),
            frame_count=int(raw_metadata.get("frame_count", 0)),
            width=int(raw_metadata.get("width", 0)),
            height=int(raw_metadata.get("height", 0)),
            duration=float(raw_metadata.get("duration", 0.0)),
            landmark_count=int(raw_metadata.get("landmark_count", 33)),
            title=str(raw_metadata["title"]) if "title" in raw_metadata else None,
        )
        schema_version = int(raw_moveset.get("schema_version", MOVESET_SCHEMA_VERSION))
        frames = tuple(self._create_pose_frame(item) for item in raw_moveset.get("frames", []))

        return Moveset(
            metadata=metadata,
            frames=tuple(sorted(frames, key=lambda pose_frame: pose_frame.timestamp)),
            schema_version=schema_version,
            raw_metadata=raw_metadata,
        )

    def _create_pose_frame(self, item: dict[str, Any]) -> PoseFrame:
        landmarks = np.array(item.get("landmarks", []), dtype=np.float32)
        if landmarks.size == 0:
            landmarks = np.empty((0, 4), dtype=np.float32)

        normalized_landmarks = np.array(item.get("normalized_landmarks", []), dtype=np.float32)
        if normalized_landmarks.size == 0:
            normalization = self.normalizer.normalize(landmarks)
            normalized_landmarks = normalization.normalized_landmarks
        elif normalized_landmarks.ndim == 1:
            normalized_landmarks = normalized_landmarks.reshape((-1, 4))

        raw_joint_angles = item.get("joint_angles", {})
        if isinstance(raw_joint_angles, dict) and raw_joint_angles:
            joint_angles = {str(key): float(value) for key, value in raw_joint_angles.items()}
        else:
            joint_angles = self.angle_calculator.calculate(normalized_landmarks)

        return PoseFrame(
            frame=int(item["frame"]),
            timestamp=float(item["timestamp"]) * 1000.0,
            pose_detected=bool(item["pose_detected"]),
            landmarks=landmarks,
            normalized_landmarks=normalized_landmarks,
            joint_angles=joint_angles,
        )
