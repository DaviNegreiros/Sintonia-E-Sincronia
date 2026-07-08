from __future__ import annotations


class FeedbackMapper:
    """Map a 0-100 score to the visible feedback label."""

    def map_score(self, score: float) -> str:
        """Return one of the four configured feedback classes."""
        if score < 25.0:
            return "Eita!"
        if score < 50.0:
            return "Ok"
        if score < 75.0:
            return "Ótimo!"
        return "Perfeito!"
