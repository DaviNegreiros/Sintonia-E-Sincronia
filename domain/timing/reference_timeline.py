from __future__ import annotations

import bisect

from contracts.pose import PoseFrame


class ReferenceTimeline:
    """Timestamp lookup for reference poses."""

    def __init__(self, frames: list[PoseFrame] | tuple[PoseFrame, ...]) -> None:
        self.frames = tuple(sorted(frames, key=lambda pose_frame: pose_frame.timestamp))
        self.timestamps = [pose_frame.timestamp for pose_frame in self.frames]
        self._cursor = 0

    def get_next_pose(self) -> PoseFrame:
        """Return poses sequentially for compatibility with pose sources."""
        if not self.frames:
            raise RuntimeError("Moveset has no pose frames.")

        pose_frame = self.frames[min(self._cursor, len(self.frames) - 1)]
        self._cursor += 1
        return pose_frame

    def get_poses_near(self, timestamp_ms: float, tolerance_ms: float) -> list[PoseFrame]:
        """Return all poses inside a timestamp tolerance window."""
        start = timestamp_ms - tolerance_ms
        end = timestamp_ms + tolerance_ms
        start_index = bisect.bisect_left(self.timestamps, start)
        end_index = bisect.bisect_right(self.timestamps, end)
        return list(self.frames[start_index:end_index])
