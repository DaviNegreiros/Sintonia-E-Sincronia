"""Legacy compatibility package for ApenasDance.

New code should prefer contracts, domain, engine, vision, storage and observability.
"""

from engine import GameEngine, GameSession

__all__ = ["GameEngine", "GameSession"]
