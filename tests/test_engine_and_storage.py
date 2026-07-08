from __future__ import annotations

import json

import numpy as np

from contracts.pose import PoseFrame
from contracts.session import GameSessionStatus
from engine import GameEngine
from storage import MovesetRepository


def test_moveset_repository_loads_current_json_contract(tmp_path) -> None:
    moveset_path = tmp_path / "sample_moveset.json"
    moveset_path.write_text(
        json.dumps(
            {
                "metadata": {
                    "video": "sample.mp4",
                    "fps": 30.0,
                    "frame_count": 1,
                    "width": 100,
                    "height": 100,
                    "duration": 1.0,
                    "landmark_count": 33,
                },
                "frames": [
                    {
                        "frame": 0,
                        "timestamp": 0.5,
                        "pose_detected": False,
                        "landmarks": [],
                        "normalized_landmarks": [],
                        "joint_angles": {},
                    }
                ],
            }
        ),
        encoding="utf-8",
    )

    moveset = MovesetRepository().load_moveset(moveset_path)

    assert moveset.schema_version == 1
    assert moveset.metadata.video == "sample.mp4"
    assert moveset.frames[0].timestamp == 500.0


def test_game_engine_creates_session_and_processes_pose(tmp_path) -> None:
    moveset_path = tmp_path / "sample_moveset.json"
    landmarks = np.zeros((33, 4), dtype=float).tolist()
    normalized = np.zeros((33, 4), dtype=float).tolist()
    moveset_path.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "metadata": {
                    "video": "sample.mp4",
                    "fps": 30.0,
                    "frame_count": 1,
                    "width": 100,
                    "height": 100,
                    "duration": 1.0,
                    "landmark_count": 33,
                },
                "frames": [
                    {
                        "frame": 0,
                        "timestamp": 0.0,
                        "pose_detected": True,
                        "landmarks": landmarks,
                        "normalized_landmarks": normalized,
                        "joint_angles": {"torso": 0.0},
                    }
                ],
            }
        ),
        encoding="utf-8",
    )

    engine = GameEngine()
    moveset = engine.load_dance(moveset_path)
    session = engine.create_session(moveset)
    state = session.start()

    assert state.status == GameSessionStatus.PLAYING

    pose = PoseFrame(
        frame=0,
        timestamp=0.0,
        pose_detected=True,
        landmarks=np.array(landmarks, dtype=np.float32),
        normalized_landmarks=np.array(normalized, dtype=np.float32),
        joint_angles={"torso": 0.0},
    )
    state = session.process_pose(pose)
    result = session.finish()

    assert state.current_score == 100.0
    assert result.final_score == 70.0
    assert session.log.frames
