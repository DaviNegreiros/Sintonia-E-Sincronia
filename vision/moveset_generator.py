from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import cv2
import mediapipe as mp
import numpy as np

from domain.pose import JointAngleCalculator, POSE_CONNECTIONS, PoseNormalizer
from vision.pose_detector import PoseDetector
from vision.pose_skeleton import PoseSkeletonDrawer


class MoveSetGenerator:
    """Generate an annotated pose video and a reusable temporal moveset."""

    LANDMARK_COUNT = 33
    POSE_CONNECTIONS = POSE_CONNECTIONS

    def __init__(self) -> None:
        self.video_path: Path | None = None
        self.model_path: Path | None = None
        self.annotated_video_path: Path | None = None
        self.moveset_path: Path | None = None
        self.capture: cv2.VideoCapture | None = None
        self.writer: cv2.VideoWriter | None = None
        self.pose_detector: PoseDetector | None = None
        self.detector: Any | None = None
        self.normalizer = PoseNormalizer()
        self.angle_calculator = JointAngleCalculator()
        self.skeleton_drawer = PoseSkeletonDrawer()
        self.fps = 0.0
        self.frame_count = 0
        self.width = 0
        self.height = 0
        self.duration = 0.0
        self.frames: list[dict[str, Any]] = []
        self.detected_frames = 0
        self.missing_pose_frames = 0

    def initialize_detector(self) -> None:
        """Load a local MediaPipe Pose Landmarker .task model."""
        self.model_path = self._find_task_model()
        self.pose_detector = PoseDetector(model_path=self.model_path)
        self.pose_detector.initialize()
        self.detector = self.pose_detector.detector

    def load_video(self, video_path: str | Path) -> None:
        """Open the source video and extract its metadata."""
        self.video_path = Path(video_path).expanduser().resolve()
        if not self.video_path.exists():
            raise FileNotFoundError(f"Video nao encontrado: {self.video_path}")

        self.capture = cv2.VideoCapture(str(self.video_path))
        if not self.capture.isOpened():
            raise RuntimeError(f"Nao foi possivel abrir o video: {self.video_path}")

        self.fps = float(self.capture.get(cv2.CAP_PROP_FPS))
        self.frame_count = int(self.capture.get(cv2.CAP_PROP_FRAME_COUNT))
        self.width = int(self.capture.get(cv2.CAP_PROP_FRAME_WIDTH))
        self.height = int(self.capture.get(cv2.CAP_PROP_FRAME_HEIGHT))

        if self.fps <= 0 or self.frame_count <= 0 or self.width <= 0 or self.height <= 0:
            raise ValueError("Metadados invalidos no video de entrada.")

        self.duration = self.frame_count / self.fps
        self.annotated_video_path = self.video_path.with_name(f"{self.video_path.stem}_pose.mp4")
        self.moveset_path = self.video_path.with_name(f"{self.video_path.stem}_moveset.json")

    def process_video(self, video_path: str | Path) -> None:
        """Process every video frame and write all requested outputs."""
        self.load_video(video_path)
        self.initialize_detector()
        self._initialize_writer()

        try:
            assert self.capture is not None
            for frame_index in range(self.frame_count):
                success, frame = self.capture.read()
                if not success:
                    break

                annotated_frame = self.process_frame(frame, frame_index)
                self.save_annotated_video(annotated_frame)
        finally:
            self._release_resources()

        self.save_json()
        self.print_summary()

    def process_frame(self, frame: np.ndarray, frame_index: int) -> np.ndarray:
        """Run pose detection for one frame and store its moveset data."""
        if self.detector is None:
            raise RuntimeError("Detector nao inicializado.")

        timestamp_ms = int(round(frame_index * 1000.0 / self.fps))
        pose_landmarks = self._detect_pose_landmarks(frame, timestamp_ms)

        if pose_landmarks:
            landmarks = [
                [round(point.x, 6), round(point.y, 6), round(point.z, 6), round(point.visibility, 6)]
                for point in pose_landmarks
            ]
            normalized_landmarks, joint_angles = self._normalize_pose(landmarks)
            self.detected_frames += 1
            annotated_frame = self.draw_pose(frame.copy(), pose_landmarks)
        else:
            landmarks = []
            normalized_landmarks = []
            joint_angles = {}
            self.missing_pose_frames += 1
            annotated_frame = frame

        self.frames.append(
            {
                "frame": frame_index,
                "timestamp": round(timestamp_ms / 1000.0, 3),
                "pose_detected": bool(pose_landmarks),
                "landmarks": landmarks,
                "normalized_landmarks": normalized_landmarks,
                "joint_angles": joint_angles,
            }
        )
        return annotated_frame

    def draw_pose(self, frame: np.ndarray, landmarks: list[Any]) -> np.ndarray:
        """Draw the official pose skeleton connections over a frame."""
        return self.skeleton_drawer.draw(frame, landmarks)

    def save_json(self) -> None:
        """Save the moveset JSON optimized for fast game reads."""
        if self.video_path is None or self.moveset_path is None:
            raise RuntimeError("Caminhos de saida nao inicializados.")

        moveset = {
            "schema_version": 1,
            "metadata": {
                "video": self.video_path.name,
                "fps": self.fps,
                "frame_count": self.frame_count,
                "width": self.width,
                "height": self.height,
                "duration": round(self.duration, 3),
                "landmark_count": self.LANDMARK_COUNT,
            },
            "frames": self.frames,
        }

        with self.moveset_path.open("w", encoding="utf-8") as file:
            json.dump(moveset, file, ensure_ascii=False, separators=(",", ":"))

    def save_annotated_video(self, frame: np.ndarray) -> None:
        """Append one annotated frame to the output video."""
        if self.writer is None:
            raise RuntimeError("VideoWriter nao inicializado.")
        self.writer.write(frame)

    def print_summary(self) -> None:
        """Print processing metadata and output locations."""
        print("Resumo do processamento")
        print(f"Video original: {self.video_path}")
        print(f"Video anotado: {self.annotated_video_path}")
        print(f"JSON gerado: {self.moveset_path}")
        print(f"Resolucao: {self.width}x{self.height}")
        print(f"FPS: {self.fps:.3f}")
        print(f"Duracao: {self.duration:.3f}s")
        print(f"Quantidade de frames: {self.frame_count}")
        print(f"Frames com pose detectada: {self.detected_frames}")
        print(f"Frames sem pose detectada: {self.missing_pose_frames}")

    def _find_task_model(self) -> Path:
        """Find a local .task model without requiring another user variable."""
        assert self.video_path is not None
        search_roots = [
            Path.cwd(),
            Path.cwd() / "models",
            self.video_path.parent,
            self.video_path.parent / "models",
        ]
        candidate_names = (
            "pose_landmarker_full.task",
            "pose_landmarker_lite.task",
            "pose_landmarker_heavy.task",
        )

        for root in search_roots:
            for name in candidate_names:
                candidate = root / name
                if candidate.exists():
                    return candidate.resolve()

        for root in search_roots:
            if root.exists():
                task_files = sorted(root.glob("*.task"))
                if task_files:
                    return task_files[0].resolve()

        raise FileNotFoundError(
            "Nenhum modelo .task foi encontrado. Coloque um modelo Pose Landmarker, "
            "como pose_landmarker_full.task, na pasta do projeto, em models/ ou junto ao video."
        )

    def _initialize_writer(self) -> None:
        """Create the MP4 writer using the original video properties."""
        if self.annotated_video_path is None:
            raise RuntimeError("Caminho do video anotado nao inicializado.")

        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        self.writer = cv2.VideoWriter(
            str(self.annotated_video_path),
            fourcc,
            self.fps,
            (self.width, self.height),
        )
        if not self.writer.isOpened():
            raise RuntimeError(f"Nao foi possivel criar o video: {self.annotated_video_path}")

    def _release_resources(self) -> None:
        """Release native resources even when processing fails."""
        if self.capture is not None:
            self.capture.release()
        if self.writer is not None:
            self.writer.release()
        if self.pose_detector is not None:
            self.pose_detector.close()
        self.detector = None

    def _detect_pose_landmarks(self, frame: np.ndarray, timestamp_ms: int) -> list[Any]:
        """Run PoseLandmarker.detect_for_video and return only the first person."""
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        result = self.detector.detect_for_video(mp_image, timestamp_ms)
        return result.pose_landmarks[0] if result.pose_landmarks else []

    def _normalize_pose(self, landmarks: list[list[float]]) -> tuple[list[list[float]], dict[str, float]]:
        """Apply the same normalization and angle pipeline used by real-time tracking."""
        landmark_array = np.array(landmarks, dtype=np.float32)
        normalization = self.normalizer.normalize(landmark_array)
        joint_angles = self.angle_calculator.calculate(normalization.normalized_landmarks)
        normalized_landmarks = normalization.normalized_landmarks.round(6).tolist()
        rounded_angles = {name: round(value, 6) for name, value in joint_angles.items()}
        return normalized_landmarks, rounded_angles
