"""Compatibility imports for final score domain services."""

from contracts.result import GameResult
from domain.ranking import FinalScoreCalculator

__all__ = ["FinalScoreCalculator", "GameResult"]
