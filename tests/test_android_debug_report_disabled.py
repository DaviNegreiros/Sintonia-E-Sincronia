from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_SRC = ROOT / "app" / "src" / "main" / "java" / "com" / "sintonia" / "sincronia"


def _active_source(path: Path) -> str:
    source = path.read_text(encoding="utf-8")
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    source = re.sub(r"//.*", "", source)
    return source


class AndroidDebugReportDisabledTest(unittest.TestCase):
    def test_scoring_engine_final_session_result_does_not_emit_debug_report(self) -> None:
        source = _active_source(ANDROID_SRC / "processing" / "DanceScoringEngine.kt")

        self.assertIn("const val WRITE_REALTIME_COMPARISON_REPORT = false", source)
        self.assertIn("private val debugCollector: RealtimeComparisonDebugCollector? = null", source)
        self.assertNotIn("debugCollector?.recordFrame(", source)
        self.assertNotIn("debugCollector?.writeReport(", source)
        self.assertNotIn("debugReportPath =", source)
        self.assertIn("return DanceSessionResult(", source)
        self.assertIn("result = result", source)

    def test_dance_library_view_model_finishes_without_debug_report_path(self) -> None:
        source = _active_source(ANDROID_SRC / "viewmodel" / "DanceLibraryViewModel.kt")

        self.assertNotIn("debugReportPath", source)
        self.assertIn("repository.updateBestRank(selectedDance.id, result.rank)", source)
        self.assertIn("result = result", source)

    def test_dance_session_result_no_longer_transports_debug_report_path(self) -> None:
        source = _active_source(ANDROID_SRC / "domain" / "DanceResult.kt")

        self.assertIn("data class DanceSessionResult", source)
        self.assertIn("val result: DanceResult", source)
        self.assertNotIn("debugReportPath", source)

    def test_scoring_engine_basic_result_path_still_returns_ranked_percentage(self) -> None:
        source = _active_source(ANDROID_SRC / "processing" / "DanceScoringEngine.kt")

        self.assertIn(
            "val finalScore = ((similarityAverage * 0.70) + (feedbackScore * 0.30)).coerceIn(0.0, 100.0)",
            source,
        )
        self.assertIn("rank = rankFor(finalScore)", source)
        self.assertIn("successPercentage = finalScore.roundToInt().coerceIn(0, 100)", source)
        self.assertIn("return DanceSessionResult(", source)
        self.assertIn("result = result", source)

    def test_debug_report_button_is_not_called_by_any_active_screen(self) -> None:
        active_sources = "\n".join(
            _active_source(path)
            for path in (ANDROID_SRC / "ui" / "screens").glob("*.kt")
        )

        self.assertNotIn("DebugReportButton(", active_sources)
        self.assertNotIn("Ver relatório debug", active_sources)

    def test_debug_report_button_file_is_preserved_but_inactive(self) -> None:
        source = _active_source(ANDROID_SRC / "ui" / "screens" / "DebugReportButton.kt")

        self.assertIn("package com.sintonia.sincronia.ui.screens", source)
        self.assertNotIn("fun DebugReportButton", source)
        self.assertNotIn("AlertDialog", source)


if __name__ == "__main__":
    unittest.main()
