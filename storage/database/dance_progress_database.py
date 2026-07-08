from __future__ import annotations

import sqlite3
from pathlib import Path


class DanceProgressDatabase:
    """SQLite storage for the best rank achieved per dance."""

    def __init__(self, database_path: str | Path) -> None:
        self.database_path = Path(database_path).expanduser()

    def initialize(self) -> None:
        """Create the minimal DanceProgress table if needed."""
        self.database_path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS DanceProgress (
                    id TEXT PRIMARY KEY,
                    bestRank TEXT
                )
                """
            )

    def get_best_rank(self, dance_id: str) -> str | None:
        """Return the stored best rank for a dance, if present."""
        with self._connect() as connection:
            row = connection.execute(
                "SELECT bestRank FROM DanceProgress WHERE id = ?",
                (dance_id,),
            ).fetchone()
        return None if row is None else str(row[0])

    def save_best_rank(self, dance_id: str, best_rank: str) -> None:
        """Insert or update the best rank for a dance."""
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO DanceProgress (id, bestRank)
                VALUES (?, ?)
                ON CONFLICT(id) DO UPDATE SET bestRank = excluded.bestRank
                """,
                (dance_id, best_rank),
            )

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self.database_path)
