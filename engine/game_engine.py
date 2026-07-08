from __future__ import annotations

from pathlib import Path

from config.defaults import DEFAULT_SESSION_CONFIG
from contracts.moveset import Moveset
from contracts.session import SessionConfig
from contracts.versions import API_VERSION
from storage import DanceRepository, MovesetRepository

from .game_session import GameSession


class GameEngine:
    """Public API v1 for loading dances and creating game sessions."""

    api_version = API_VERSION

    def __init__(self, repository: DanceRepository | None = None) -> None:
        self.repository = repository or MovesetRepository()
        self._loaded_moveset: Moveset | None = None

    def load_dance(self, moveset_path: str | Path) -> Moveset:
        """Load a local reference dance moveset."""
        self._loaded_moveset = self.repository.load_moveset(moveset_path)
        return self._loaded_moveset

    def create_session(
        self,
        moveset: Moveset | None = None,
        config: SessionConfig = DEFAULT_SESSION_CONFIG,
    ) -> GameSession:
        """Create a session from a provided or previously loaded moveset."""
        selected_moveset = moveset or self._loaded_moveset
        if selected_moveset is None:
            raise RuntimeError("No dance loaded. Call load_dance() or pass a moveset.")
        return GameSession(selected_moveset, config)
