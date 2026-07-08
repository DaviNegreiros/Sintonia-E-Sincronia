from __future__ import annotations

from pathlib import Path

from storage.moveset_repository import MovesetRepository

from .dance_package import DancePackage


class DancePackageStore:
    """Locate and load local dance packages from the dances directory."""

    VIDEO_FILENAME = "dance.mp4"
    PREVIEW_FILENAME = "preview.webp"
    MOVESET_FILENAME = "moveset.json"

    def __init__(
        self,
        root: str | Path = "dances",
        moveset_repository: MovesetRepository | None = None,
    ) -> None:
        self.root = Path(root).expanduser()
        self.moveset_repository = moveset_repository or MovesetRepository()

    def list_ids(self) -> list[str]:
        """Return available dance package ids."""
        if not self.root.exists():
            return []
        return sorted(path.name for path in self.root.iterdir() if path.is_dir())

    def load(self, dance_id: str) -> DancePackage:
        """Load one dance package by id."""
        package_root = self.root / dance_id
        video_path = package_root / self.VIDEO_FILENAME
        preview_path = package_root / self.PREVIEW_FILENAME
        moveset_path = package_root / self.MOVESET_FILENAME

        self._ensure_file(video_path, dance_id)
        self._ensure_file(preview_path, dance_id)
        self._ensure_file(moveset_path, dance_id)

        return DancePackage(
            id=dance_id,
            root=package_root,
            video_path=video_path,
            preview_path=preview_path,
            moveset_path=moveset_path,
            moveset=self.moveset_repository.load_moveset(moveset_path),
        )

    @staticmethod
    def _ensure_file(path: Path, dance_id: str) -> None:
        if not path.is_file():
            raise FileNotFoundError(f"Dance package '{dance_id}' is missing {path.name}: {path}")
