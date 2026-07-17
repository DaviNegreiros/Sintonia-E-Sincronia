package com.sintonia.sincronia.processing

import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.domain.DanceResult
import com.sintonia.sincronia.domain.Rank
import com.sintonia.sincronia.domain.ScoreFeedback
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

data class FrameEvaluation(
    val timestampMs: Double,
    val overallSimilarity: Double,
    val landmarkSimilarity: Double,
    val angleSimilarity: Double,
    val confidence: Double,
    val feedback: ScoreFeedback?
)

data class ScoreUpdate(
    val evaluation: FrameEvaluation,
    val currentScore: Double,
    val emittedFeedback: ScoreFeedback?
)

private data class PoseFrame(
    val frame: Int,
    val timestampMs: Double,
    val poseDetected: Boolean,
    val landmarks: List<PoseLandmark>,
    val normalizedLandmarks: List<PoseLandmark>,
    val jointAngles: Map<String, Double>,
    val comparableLandmarkIndices: IntArray
)

private data class AngleSpec(
    val key: String,
    val landmarkIndices: IntArray
)

private data class CandidateEvaluation(
    val referencePose: PoseFrame,
    val evaluation: FrameEvaluation
)

private data class BodyRegionSpec(
    val name: String,
    val landmarkIndices: IntArray
)

private data class LandmarkDebugScore(
    val index: Int,
    val score: Double,
    val distance: Double,
    val missingInPlayer: Boolean
)

private data class RegionDebugScore(
    val name: String,
    val comparisonCount: Int,
    val averageScore: Double
)

private data class FrameScoreSummary(
    val timestampMs: Double,
    val score: Double
)

private const val REPORT_MAX_POSE_LANDMARKS = 33
private const val REPORT_WORST_LANDMARKS_PER_FRAME = 6
private const val REPORT_MIN_REGION_COMPARISONS_FOR_DIAGNOSTIC = 10
private const val REPORT_TIMESTAMP_TOLERANCE_MS = 200.0
private const val REPORT_FEEDBACK_INTERVAL_MS = 5000.0
private const val REPORT_SLIDING_WINDOW_MS = 5000.0
private const val REPORT_LANDMARK_DISTANCE_THRESHOLD = 1.0
private const val REPORT_LANDMARK_VISIBILITY_THRESHOLD = 0.35f
private const val REPORT_LANDMARK_SIMILARITY_WEIGHT = 0.7
private const val REPORT_ANGLE_SIMILARITY_WEIGHT = 0.3
private val REPORT_EMPTY_INT_ARRAY = IntArray(0)

object RealtimeComparisonDebugConfig {
    const val WRITE_REALTIME_COMPARISON_REPORT = true
}

private val REPORT_BODY_REGIONS = listOf(
    BodyRegionSpec("Cabeça", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
    BodyRegionSpec("Tronco", intArrayOf(11, 12, 23, 24)),
    BodyRegionSpec("Braço esquerdo", intArrayOf(11, 13)),
    BodyRegionSpec("Braço direito", intArrayOf(12, 14)),
    BodyRegionSpec("Antebraço esquerdo", intArrayOf(13, 15)),
    BodyRegionSpec("Antebraço direito", intArrayOf(14, 16)),
    BodyRegionSpec("Mão esquerda", intArrayOf(15, 17, 19, 21)),
    BodyRegionSpec("Mão direita", intArrayOf(16, 18, 20, 22)),
    BodyRegionSpec("Quadril", intArrayOf(23, 24)),
    BodyRegionSpec("Perna esquerda", intArrayOf(23, 25, 27)),
    BodyRegionSpec("Perna direita", intArrayOf(24, 26, 28)),
    BodyRegionSpec("Pé esquerdo", intArrayOf(27, 29, 31)),
    BodyRegionSpec("Pé direito", intArrayOf(28, 30, 32))
)

private class RegionStats {
    var comparisons = 0
        private set
    var scoreSum = 0.0
        private set
    var bestScore = 0.0
        private set
    var worstScore = 1.0
        private set

    fun add(score: Double) {
        comparisons += 1
        scoreSum += score
        if (score > bestScore) bestScore = score
        if (score < worstScore) worstScore = score
    }

    fun average(): Double = if (comparisons == 0) 0.0 else scoreSum / comparisons
}

private class RealtimeComparisonDebugCollector(
    private val danceName: String,
    private val outputFile: File
) {
    private val startedAtMs = System.currentTimeMillis()
    private val timeline = mutableListOf<JSONObject>()
    private val regionStats = REPORT_BODY_REGIONS.associate { it.name to RegionStats() }
    private val ignoredLandmarkCounts = IntArray(REPORT_MAX_POSE_LANDMARKS)
    private val missingPlayerLandmarkCounts = IntArray(REPORT_MAX_POSE_LANDMARKS)
    private var frameCount = 0
    private var validComparisonFrames = 0
    private var framesWithoutComparison = 0
    private var totalExpectedLandmarks = 0
    private var totalIgnoredLandmarks = 0
    private var totalMissingPlayerLandmarks = 0
    private var totalTemporalDifferenceMs = 0.0
    private var maxTemporalDifferenceMs = 0.0
    private var firstTimestampMs: Double? = null
    private var lastTimestampMs: Double? = null
    private var firstComparedTimestampMs: Double? = null
    private var lastComparedTimestampMs: Double? = null
    private var bestFrame: FrameScoreSummary? = null
    private var worstFrame: FrameScoreSummary? = null
    private var reportWritten = false

    fun recordFrame(
        playbackTimestampMs: Double,
        realtimePose: PoseFrame,
        chosenCandidate: CandidateEvaluation?,
        candidateEvaluations: List<CandidateEvaluation>,
        evaluation: FrameEvaluation,
        accumulatedScore: Double
    ) {
        frameCount += 1
        firstTimestampMs = firstTimestampMs ?: playbackTimestampMs
        lastTimestampMs = playbackTimestampMs

        val referencePose = chosenCandidate?.referencePose
        val expectedIndices = referencePose?.comparableLandmarkIndices ?: REPORT_EMPTY_INT_ARRAY
        val ignoredIndices = if (referencePose == null) {
            REPORT_EMPTY_INT_ARRAY
        } else {
            ignoredLandmarkIndices(expectedIndices)
        }
        val missingPlayerIndices = if (referencePose == null) {
            REPORT_EMPTY_INT_ARRAY
        } else {
            missingPlayerLandmarkIndices(expectedIndices, realtimePose)
        }
        val landmarkScores = if (referencePose == null) {
            emptyList()
        } else {
            landmarkDebugScores(referencePose, realtimePose, expectedIndices)
        }
        val regionScores = regionDebugScores(landmarkScores)
        val hasValidComparison = referencePose != null && expectedIndices.isNotEmpty()

        if (hasValidComparison) {
            validComparisonFrames += 1
            firstComparedTimestampMs = firstComparedTimestampMs ?: playbackTimestampMs
            lastComparedTimestampMs = playbackTimestampMs
            totalExpectedLandmarks += expectedIndices.size
            totalIgnoredLandmarks += ignoredIndices.size
            totalMissingPlayerLandmarks += missingPlayerIndices.size
            for (index in ignoredIndices) ignoredLandmarkCounts[index] += 1
            for (index in missingPlayerIndices) missingPlayerLandmarkCounts[index] += 1

            val temporalDifferenceMs = kotlin.math.abs(playbackTimestampMs - referencePose.timestampMs)
            totalTemporalDifferenceMs += temporalDifferenceMs
            if (temporalDifferenceMs > maxTemporalDifferenceMs) maxTemporalDifferenceMs = temporalDifferenceMs

            val instantScore = evaluation.overallSimilarity * 100.0
            val summary = FrameScoreSummary(playbackTimestampMs, instantScore)
            if (bestFrame == null || instantScore > requireNotNull(bestFrame).score) bestFrame = summary
            if (worstFrame == null || instantScore < requireNotNull(worstFrame).score) worstFrame = summary
        } else {
            framesWithoutComparison += 1
        }

        for (regionScore in regionScores) {
            val stats = regionStats.getValue(regionScore.name)
            repeat(regionScore.comparisonCount) {
                stats.add(regionScore.averageScore)
            }
        }

        timeline += JSONObject()
            .put("timestamp_ms", playbackTimestampMs.roundReport())
            .put("instant_score", (evaluation.overallSimilarity * 100.0).roundReport())
            .put("accumulated_score", accumulatedScore.roundReport())
            .put("landmark_similarity", (evaluation.landmarkSimilarity * 100.0).roundReport())
            .put("angle_similarity", (evaluation.angleSimilarity * 100.0).roundReport())
            .put("confidence", evaluation.confidence.roundReport())
            .put("valid_landmarks", expectedIndices.size)
            .put("ignored_landmarks", ignoredIndices.size)
            .put("missing_player_landmarks", missingPlayerIndices.size)
            .put("moveset_frame", referencePose?.frame ?: JSONObject.NULL)
            .put("moveset_timestamp_ms", referencePose?.timestampMs?.roundReport() ?: JSONObject.NULL)
            .put(
                "temporal_difference_ms",
                referencePose?.let { kotlin.math.abs(playbackTimestampMs - it.timestampMs).roundReport() } ?: JSONObject.NULL
            )
            .put("candidate_count", candidateEvaluations.size)
            .put("candidate_selection_reason", if (candidateEvaluations.isEmpty()) "no_reference_frame_inside_tolerance" else "highest_overall_similarity_inside_tolerance_window")
            .put("candidates", candidateEvaluations.toCandidatesJson(playbackTimestampMs))
            .put("expected_landmarks", expectedIndices.toJsonArray())
            .put("ignored_landmarks_by_moveset", ignoredIndices.toJsonArray())
            .put("missing_landmarks_in_player", missingPlayerIndices.toJsonArray())
            .put("landmark_scores", landmarkScores.toLandmarkScoresJson())
            .put("region_scores", regionScores.toRegionScoresJson())
            .put(
                "replay",
                JSONObject()
                    .put("moveset_normalized_landmarks", referencePose?.normalizedLandmarks.toJsonLandmarks())
                    .put("player_normalized_landmarks", realtimePose.normalizedLandmarks.toJsonLandmarks())
                    .put("worst_landmarks", landmarkScores.sortedBy { it.score }.take(REPORT_WORST_LANDMARKS_PER_FRAME).toLandmarkScoresJson())
            )
    }

    fun writeReport(
        referenceFrames: List<PoseFrame>,
        result: DanceResult,
        similarityAverage: Double,
        feedbackScore: Double,
        finalScore: Double,
        feedbackHistory: List<ScoreFeedback>
    ): File? {
        if (reportWritten) return outputFile.takeIf { it.exists() }
        reportWritten = true

        val elapsedProcessingSeconds = ((System.currentTimeMillis() - startedAtMs) / 1000.0).coerceAtLeast(0.001)
        val timelineDurationSeconds = durationBetween(firstTimestampMs, lastTimestampMs)
        val comparedDurationSeconds = durationBetween(firstComparedTimestampMs, lastComparedTimestampMs)
        val avgCameraFps = if (timelineDurationSeconds > 0.0) frameCount / timelineDurationSeconds else 0.0
        val avgProcessingFps = frameCount / elapsedProcessingSeconds
        val avgTemporalDifferenceMs = if (validComparisonFrames > 0) {
            totalTemporalDifferenceMs / validComparisonFrames
        } else {
            0.0
        }

        val root = JSONObject()
            .put("schema_version", 1)
            .put("type", "realtime_comparison_debug_report")
            .put("generated_at_epoch_ms", System.currentTimeMillis())
            .put("dance_name", danceName)
            .put("debug_only", true)
            .put("algorithm_changed_by_report", false)
            .put(
                "configuration",
                JSONObject()
                    .put("timestamp_tolerance_ms", REPORT_TIMESTAMP_TOLERANCE_MS)
                    .put("sliding_window_ms", REPORT_SLIDING_WINDOW_MS)
                    .put("feedback_interval_ms", REPORT_FEEDBACK_INTERVAL_MS)
                    .put("landmark_distance_threshold", REPORT_LANDMARK_DISTANCE_THRESHOLD)
                    .put("landmark_visibility_threshold", REPORT_LANDMARK_VISIBILITY_THRESHOLD)
                    .put("landmark_similarity_weight", REPORT_LANDMARK_SIMILARITY_WEIGHT)
                    .put("angle_similarity_weight", REPORT_ANGLE_SIMILARITY_WEIGHT)
                    .put("candidate_strategy", "choose_reference_frame_with_highest_overall_similarity_inside_temporal_window")
            )
            .put(
                "general",
                JSONObject()
                    .put("dance_duration_seconds", referenceFrames.lastOrNull()?.timestampMs?.div(1000.0)?.roundReport() ?: 0.0)
                    .put("effectively_compared_seconds", comparedDurationSeconds.roundReport())
                    .put("total_frames_compared", frameCount)
                    .put("average_camera_fps", avgCameraFps.roundReport())
                    .put("average_processing_fps", avgProcessingFps.roundReport())
                    .put("discarded_frames_observable_by_scoring", 0)
                    .put("discarded_frames_note", "CameraX frames dropped before the scoring engine are not observable here.")
                    .put("frames_without_comparison", framesWithoutComparison)
                    .put("frames_with_valid_comparison", validComparisonFrames)
            )
            .put("timeline", JSONArray(timeline))
            .put("body_regions", bodyRegionStatsJson())
            .put("landmarks_ignored_by_moveset", landmarkCountsJson(ignoredLandmarkCounts))
            .put("landmarks_expected_but_missing_in_player", landmarkCountsJson(missingPlayerLandmarkCounts))
            .put(
                "temporal_synchronization",
                JSONObject()
                    .put("average_temporal_difference_ms", avgTemporalDifferenceMs.roundReport())
                    .put("max_temporal_difference_ms", maxTemporalDifferenceMs.roundReport())
                    .put("window_ms", REPORT_TIMESTAMP_TOLERANCE_MS)
                    .put("strategy", "all candidates in the temporal window are scored; the highest overall similarity is selected")
            )
            .put(
                "score_evolution",
                JSONObject()
                    .put("instant_score_source", "evaluation.overallSimilarity * 100")
                    .put("smoothed_score_source", "current sliding-window score used for feedback")
                    .put("final_score_formula", "70% similarity_average + 30% feedback_score")
                    .put("curve", JSONArray(timeline))
            )
            .put(
                "final_statistics",
                JSONObject()
                    .put("final_score", finalScore.roundReport())
                    .put("rank", result.rank.label)
                    .put("similarity_average", similarityAverage.roundReport())
                    .put("feedback_score", feedbackScore.roundReport())
                    .put("feedback_history", feedbackHistory.map { it.name }.toJsonArray())
                    .put("time_compared_seconds", comparedDurationSeconds.roundReport())
                    .put("time_ignored_seconds", (timelineDurationSeconds - comparedDurationSeconds).coerceAtLeast(0.0).roundReport())
                    .put("total_landmarks_compared", totalExpectedLandmarks)
                    .put("total_landmarks_ignored", totalIgnoredLandmarks)
                    .put("total_landmarks_missing_in_player", totalMissingPlayerLandmarks)
                    .put("total_landmarks_absent_in_moveset", totalIgnoredLandmarks)
                    .put("best_dance_section", bestFrame.toJson())
                    .put("worst_dance_section", worstFrame.toJson())
            )
            .put("possible_causes_for_score_loss", diagnostics(avgTemporalDifferenceMs))

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(root.toString(2), Charsets.UTF_8)
        return outputFile
    }

    private fun landmarkDebugScores(
        referencePose: PoseFrame,
        realtimePose: PoseFrame,
        expectedIndices: IntArray
    ): List<LandmarkDebugScore> = buildList(expectedIndices.size) {
        val reference = referencePose.normalizedLandmarks
        val realtime = realtimePose.normalizedLandmarks
        for (index in expectedIndices) {
            val ref = reference.getOrNull(index)
            val rt = realtime.getOrNull(index)
            val missing = !realtimePose.landmarks.isValidForReport(index) || ref == null || rt == null
            val distance = if (missing) {
                REPORT_LANDMARK_DISTANCE_THRESHOLD
            } else {
                val dx = ref.x - rt.x
                val dy = ref.y - rt.y
                val dz = ref.z - rt.z
                sqrt((dx * dx + dy * dy + dz * dz).toDouble())
            }
            val score = (1.0 - (distance / REPORT_LANDMARK_DISTANCE_THRESHOLD)).coerceIn(0.0, 1.0)
            add(LandmarkDebugScore(index, score, distance, missing))
        }
    }

    private fun regionDebugScores(landmarkScores: List<LandmarkDebugScore>): List<RegionDebugScore> {
        if (landmarkScores.isEmpty()) return emptyList()
        val byIndex = landmarkScores.associateBy { it.index }
        return buildList {
            for (region in REPORT_BODY_REGIONS) {
                var sum = 0.0
                var count = 0
                for (index in region.landmarkIndices) {
                    val score = byIndex[index]?.score ?: continue
                    sum += score
                    count += 1
                }
                if (count > 0) {
                    add(RegionDebugScore(region.name, count, sum / count))
                }
            }
        }
    }

    private fun ignoredLandmarkIndices(expectedIndices: IntArray): IntArray {
        val expected = BooleanArray(REPORT_MAX_POSE_LANDMARKS)
        for (index in expectedIndices) {
            if (index in 0 until REPORT_MAX_POSE_LANDMARKS) expected[index] = true
        }
        return IntArray(REPORT_MAX_POSE_LANDMARKS - expected.count { it }).also { output ->
            var cursor = 0
            for (index in 0 until REPORT_MAX_POSE_LANDMARKS) {
                if (!expected[index]) {
                    output[cursor] = index
                    cursor += 1
                }
            }
        }
    }

    private fun missingPlayerLandmarkIndices(expectedIndices: IntArray, realtimePose: PoseFrame): IntArray {
        val missing = IntArray(expectedIndices.size)
        var count = 0
        for (index in expectedIndices) {
            if (!realtimePose.landmarks.isValidForReport(index)) {
                missing[count] = index
                count += 1
            }
        }
        return if (count == 0) REPORT_EMPTY_INT_ARRAY else missing.copyOf(count)
    }

    private fun bodyRegionStatsJson(): JSONArray {
        val totalComparisons = regionStats.values.sumOf { it.comparisons }.coerceAtLeast(1)
        return JSONArray().also { array ->
            for (region in REPORT_BODY_REGIONS) {
                val stats = regionStats.getValue(region.name)
                val average = stats.average()
                val contribution = ((stats.comparisons / totalComparisons.toDouble()) * average * 100.0)
                array.put(
                    JSONObject()
                        .put("region", region.name)
                        .put("comparisons", stats.comparisons)
                        .put("average_score", (average * 100.0).roundReport())
                        .put("best_score", (if (stats.comparisons == 0) 0.0 else stats.bestScore * 100.0).roundReport())
                        .put("worst_score", (if (stats.comparisons == 0) 0.0 else stats.worstScore * 100.0).roundReport())
                        .put("contribution_to_landmark_component", contribution.roundReport())
                )
            }
        }
    }

    private fun landmarkCountsJson(counts: IntArray): JSONArray = JSONArray().also { array ->
        for (index in counts.indices) {
            if (counts[index] > 0) {
                array.put(
                    JSONObject()
                        .put("landmark", index)
                        .put("count", counts[index])
                )
            }
        }
    }

    private fun diagnostics(avgTemporalDifferenceMs: Double): JSONArray {
        val diagnostics = mutableListOf<String>()
        val missingRatio = if (totalExpectedLandmarks > 0) {
            totalMissingPlayerLandmarks / totalExpectedLandmarks.toDouble()
        } else {
            0.0
        }
        val ignoredRatio = if (validComparisonFrames > 0) {
            totalIgnoredLandmarks / (validComparisonFrames * REPORT_MAX_POSE_LANDMARKS).toDouble()
        } else {
            0.0
        }
        val noComparisonRatio = if (frameCount > 0) framesWithoutComparison / frameCount.toDouble() else 0.0

        if (missingRatio >= 0.20) {
            diagnostics += "Muitos landmarks esperados pelo moveset não foram detectados no jogador (${(missingRatio * 100.0).roundReport()}%)."
        }
        if (ignoredRatio >= 0.60) {
            diagnostics += "A comparação ficou concentrada em poucos landmarks porque muitos landmarks estavam ausentes no moveset (${(ignoredRatio * 100.0).roundReport()}% ignorados)."
        }
        if (noComparisonRatio >= 0.10) {
            diagnostics += "Muitos frames ficaram sem comparação temporal válida (${(noComparisonRatio * 100.0).roundReport()}%)."
        }
        if (avgTemporalDifferenceMs >= 120.0) {
            diagnostics += "A diferença temporal média entre jogador e moveset ficou alta (${avgTemporalDifferenceMs.roundReport()} ms)."
        }
        for ((name, stats) in regionStats) {
            if (stats.comparisons >= REPORT_MIN_REGION_COMPARISONS_FOR_DIAGNOSTIC && stats.average() < 0.50) {
                diagnostics += "Baixa similaridade concentrada em $name (${(stats.average() * 100.0).roundReport()}% médio)."
            }
        }
        worstFrame?.let {
            if (it.score < 35.0) {
                diagnostics += "Queda brusca de score perto de ${it.timestampMs.roundReport()} ms (${it.score.roundReport()}%)."
            }
        }
        if (diagnostics.isEmpty()) {
            diagnostics += "Nenhuma causa dominante foi detectada automaticamente; inspecione a timeline e o replay de landmarks."
        }
        return diagnostics.toJsonArray()
    }

    private fun durationBetween(startMs: Double?, endMs: Double?): Double =
        if (startMs == null || endMs == null) 0.0 else ((endMs - startMs) / 1000.0).coerceAtLeast(0.0)
}

private fun List<CandidateEvaluation>.toCandidatesJson(playbackTimestampMs: Double): JSONArray =
    JSONArray().also { array ->
        forEach { candidate ->
            array.put(
                JSONObject()
                    .put("moveset_frame", candidate.referencePose.frame)
                    .put("moveset_timestamp_ms", candidate.referencePose.timestampMs.roundReport())
                    .put("temporal_difference_ms", abs(playbackTimestampMs - candidate.referencePose.timestampMs).roundReport())
                    .put("score", (candidate.evaluation.overallSimilarity * 100.0).roundReport())
                    .put("landmark_similarity", (candidate.evaluation.landmarkSimilarity * 100.0).roundReport())
                    .put("angle_similarity", (candidate.evaluation.angleSimilarity * 100.0).roundReport())
                    .put("confidence", candidate.evaluation.confidence.roundReport())
                    .put("valid_landmarks", candidate.referencePose.comparableLandmarkIndices.size)
            )
        }
    }

private fun List<LandmarkDebugScore>.toLandmarkScoresJson(): JSONArray =
    JSONArray().also { array ->
        forEach { score ->
            array.put(
                JSONObject()
                    .put("landmark", score.index)
                    .put("score", (score.score * 100.0).roundReport())
                    .put("distance", score.distance.roundReport())
                    .put("missing_in_player", score.missingInPlayer)
            )
        }
    }

private fun List<RegionDebugScore>.toRegionScoresJson(): JSONArray =
    JSONArray().also { array ->
        forEach { score ->
            array.put(
                JSONObject()
                    .put("region", score.name)
                    .put("comparisons", score.comparisonCount)
                    .put("average_score", (score.averageScore * 100.0).roundReport())
            )
        }
    }

private fun IntArray.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach { array.put(it) } }

private fun List<String>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach { array.put(it) } }

private fun List<PoseLandmark>?.toJsonLandmarks(): Any =
    this?.let { landmarks ->
        JSONArray().also { array ->
            landmarks.forEachIndexed { index, landmark ->
                array.put(
                    JSONObject()
                        .put("index", index)
                        .put("x", landmark.x.toDouble().roundReport())
                        .put("y", landmark.y.toDouble().roundReport())
                        .put("z", landmark.z.toDouble().roundReport())
                        .put("visibility", landmark.visibility.toDouble().roundReport())
                )
            }
        }
    } ?: JSONObject.NULL

private fun FrameScoreSummary?.toJson(): Any =
    this?.let {
        JSONObject()
            .put("timestamp_ms", timestampMs.roundReport())
            .put("score", score.roundReport())
    } ?: JSONObject.NULL

private fun List<PoseLandmark>.isValidForReport(index: Int): Boolean =
    index in indices && this[index].visibility >= REPORT_LANDMARK_VISIBILITY_THRESHOLD

private fun Double.roundReport(): Double =
    kotlin.math.round(this * 1000.0) / 1000.0

class DanceScoringEngine(
    private val danceName: String,
    movesetFile: File,
    private val normalizer: PoseNormalizer = PoseNormalizer(),
    private val angleCalculator: JointAngleCalculator = JointAngleCalculator()
) {
    private val referenceFrames: List<PoseFrame>
    private val referenceTimestamps: DoubleArray
    private val scoreWindow = ArrayDeque<Pair<Double, Double>>()
    private val allScores = mutableListOf<Double>()
    private val feedbackHistory = mutableListOf<ScoreFeedback>()
    private var currentFeedback = ScoreFeedback.X
    private var lastFeedbackTimestampMs = 0.0
    private val debugCollector = if (RealtimeComparisonDebugConfig.WRITE_REALTIME_COMPARISON_REPORT) {
        RealtimeComparisonDebugCollector(
            danceName = danceName,
            outputFile = File(
                movesetFile.parentFile ?: File("."),
                REALTIME_COMPARISON_REPORT_FILE_NAME
            )
        )
    } else {
        null
    }

    init {
        referenceFrames = readMoveset(movesetFile).sortedBy { it.timestampMs }
        referenceTimestamps = DoubleArray(referenceFrames.size) { index -> referenceFrames[index].timestampMs }
    }

    fun compare(playbackTimestampMs: Double, landmarks: List<PoseLandmark>): ScoreUpdate {
        val normalized = if (landmarks.isNotEmpty()) {
            normalizer.normalize(landmarks).normalizedLandmarks
        } else {
            emptyList()
        }
        val jointAngles = if (normalized.isNotEmpty()) {
            angleCalculator.calculate(normalized)
        } else {
            emptyMap()
        }
        val realtimePose = PoseFrame(
            frame = allScores.size,
            timestampMs = playbackTimestampMs,
            poseDetected = landmarks.isNotEmpty(),
            landmarks = landmarks,
            normalizedLandmarks = normalized,
            jointAngles = jointAngles,
            comparableLandmarkIndices = EMPTY_INT_ARRAY
        )
        val candidates = posesNear(playbackTimestampMs)
        val debugCandidateEvaluations: List<CandidateEvaluation>?
        val chosenCandidate: CandidateEvaluation?
        val evaluation = if (debugCollector == null) {
            debugCandidateEvaluations = null
            chosenCandidate = null
            if (candidates.isEmpty()) {
                FrameEvaluation(playbackTimestampMs, 0.0, 0.0, 0.0, 0.0, currentFeedback)
            } else {
                candidates
                    .asSequence()
                    .map { reference -> compare(reference, realtimePose) }
                    .maxByOrNull { it.overallSimilarity }
                    ?: FrameEvaluation(playbackTimestampMs, 0.0, 0.0, 0.0, 0.0, currentFeedback)
            }
        } else {
            val evaluatedCandidates = candidates.map { reference ->
                CandidateEvaluation(reference, compare(reference, realtimePose))
            }
            val bestCandidate = evaluatedCandidates.maxByOrNull { it.evaluation.overallSimilarity }
            debugCandidateEvaluations = evaluatedCandidates
            chosenCandidate = bestCandidate
            bestCandidate?.evaluation
                ?: FrameEvaluation(playbackTimestampMs, 0.0, 0.0, 0.0, 0.0, currentFeedback)
        }
        val emittedFeedback = updateScoreWindow(evaluation)
        debugCollector?.recordFrame(
            playbackTimestampMs = playbackTimestampMs,
            realtimePose = realtimePose,
            chosenCandidate = chosenCandidate,
            candidateEvaluations = debugCandidateEvaluations.orEmpty(),
            evaluation = evaluation,
            accumulatedScore = currentScore()
        )
        return ScoreUpdate(
            evaluation = evaluation.copy(feedback = currentFeedback),
            currentScore = currentScore(),
            emittedFeedback = emittedFeedback
        )
    }

    fun finalResult(): DanceResult {
        val similarityAverage = if (allScores.isEmpty()) {
            0.0
        } else {
            allScores.average().coerceIn(0.0, 100.0)
        }
        val feedbackScore = feedbackScore()
        val finalScore = ((similarityAverage * 0.70) + (feedbackScore * 0.30)).coerceIn(0.0, 100.0)
        val result = DanceResult(
            danceName = danceName,
            rank = rankFor(finalScore),
            successPercentage = finalScore.roundToInt().coerceIn(0, 100)
        )
        val debugReportFile = debugCollector?.writeReport(
            referenceFrames = referenceFrames,
            result = result,
            similarityAverage = similarityAverage,
            feedbackScore = feedbackScore,
            finalScore = finalScore,
            feedbackHistory = feedbackHistory
        )
        return result.copy(debugReportPath = debugReportFile?.absolutePath)
    }

    private fun posesNear(timestampMs: Double): List<PoseFrame> {
        if (referenceFrames.isEmpty()) return emptyList()
        val start = timestampMs - TIMESTAMP_TOLERANCE_MS
        val end = timestampMs + TIMESTAMP_TOLERANCE_MS
        val startIndex = referenceTimestamps.lowerBound(start)
        val endIndex = referenceTimestamps.upperBound(end)
        if (startIndex >= endIndex) return emptyList()
        return referenceFrames.subList(startIndex, endIndex)
    }

    private fun compare(referencePose: PoseFrame, realtimePose: PoseFrame): FrameEvaluation {
        if (!referencePose.poseDetected || referencePose.comparableLandmarkIndices.isEmpty()) {
            return FrameEvaluation(realtimePose.timestampMs, 0.0, 0.0, 0.0, 0.0, null)
        }
        val landmarkSimilarity = landmarkSimilarity(referencePose, realtimePose)
        val angleSimilarity = angleSimilarity(referencePose, realtimePose)
        val confidence = confidence(referencePose, realtimePose)
        val overallSimilarity = (
            LANDMARK_SIMILARITY_WEIGHT * landmarkSimilarity +
                ANGLE_SIMILARITY_WEIGHT * angleSimilarity
            ).coerceIn(0.0, 1.0)
        return FrameEvaluation(
            timestampMs = realtimePose.timestampMs,
            overallSimilarity = overallSimilarity,
            landmarkSimilarity = landmarkSimilarity,
            angleSimilarity = angleSimilarity,
            confidence = confidence,
            feedback = null
        )
    }

    private fun landmarkSimilarity(referencePose: PoseFrame, realtimePose: PoseFrame): Double {
        val reference = referencePose.normalizedLandmarks
        val realtime = realtimePose.normalizedLandmarks
        val comparableIndices = referencePose.comparableLandmarkIndices
        if (reference.isEmpty() || comparableIndices.isEmpty()) return 0.0

        var distanceSum = 0.0
        for (index in comparableIndices) {
            val ref = reference.getOrNull(index)
            val rt = realtime.getOrNull(index)
            distanceSum += if (
                ref != null &&
                rt != null &&
                realtimePose.hasValidLandmark(index)
            ) {
                val dx = ref.x - rt.x
                val dy = ref.y - rt.y
                val dz = ref.z - rt.z
                sqrt((dx * dx + dy * dy + dz * dz).toDouble())
            } else {
                LANDMARK_DISTANCE_THRESHOLD
            }
        }
        val meanDistance = distanceSum / comparableIndices.size
        return (1.0 - (meanDistance / LANDMARK_DISTANCE_THRESHOLD)).coerceIn(0.0, 1.0)
    }

    private fun angleSimilarity(referencePose: PoseFrame, realtimePose: PoseFrame): Double {
        var differenceSum = 0.0
        var comparableAngles = 0

        for (spec in ANGLE_SPECS) {
            if (!referencePose.hasAllValidLandmarks(spec.landmarkIndices)) continue

            val referenceAngle = referencePose.jointAngles[spec.key] ?: continue
            val realtimeAngle = if (realtimePose.hasAllValidLandmarks(spec.landmarkIndices)) {
                realtimePose.jointAngles[spec.key]
            } else {
                null
            }

            differenceSum += if (realtimeAngle != null) {
                abs(referenceAngle - realtimeAngle)
            } else {
                MAX_ANGLE_DIFFERENCE
            }
            comparableAngles += 1
        }

        if (comparableAngles == 0) return 0.0
        val meanDifference = differenceSum / comparableAngles
        return (1.0 - (meanDifference / 180.0)).coerceIn(0.0, 1.0)
    }

    private fun confidence(referencePose: PoseFrame, realtimePose: PoseFrame): Double {
        val comparableIndices = referencePose.comparableLandmarkIndices
        if (comparableIndices.isEmpty()) return 0.0

        var referenceConfidenceSum = 0.0
        var realtimeConfidenceSum = 0.0
        for (index in comparableIndices) {
            referenceConfidenceSum += referencePose.landmarks[index].visibility.toDouble()
            realtimeConfidenceSum += if (realtimePose.hasValidLandmark(index)) {
                realtimePose.landmarks[index].visibility.toDouble()
            } else {
                0.0
            }
        }
        val referenceConfidence = referenceConfidenceSum / comparableIndices.size
        val realtimeConfidence = realtimeConfidenceSum / comparableIndices.size
        return minOf(referenceConfidence, realtimeConfidence).coerceIn(0.0, 1.0)
    }

    private fun PoseFrame.hasValidLandmark(index: Int): Boolean =
        landmarks.hasValidLandmark(index)

    private fun PoseFrame.hasAllValidLandmarks(indices: IntArray): Boolean {
        for (index in indices) {
            if (!hasValidLandmark(index)) return false
        }
        return true
    }

    private fun List<PoseLandmark>.hasValidLandmark(index: Int): Boolean =
        index in indices && this[index].visibility >= COMPARABLE_LANDMARK_VISIBILITY_THRESHOLD

    private fun updateScoreWindow(result: FrameEvaluation): ScoreFeedback? {
        val score = result.overallSimilarity * 100.0
        allScores += score
        scoreWindow.addLast(result.timestampMs to score)

        val minTimestamp = result.timestampMs - SLIDING_WINDOW_MS
        while (scoreWindow.isNotEmpty() && scoreWindow.first().first < minTimestamp) {
            scoreWindow.removeFirst()
        }

        if (result.timestampMs - lastFeedbackTimestampMs >= FEEDBACK_INTERVAL_MS) {
            currentFeedback = feedbackFor(currentScore())
            feedbackHistory += currentFeedback
            lastFeedbackTimestampMs = result.timestampMs
            return currentFeedback
        }
        return null
    }

    private fun currentScore(): Double =
        if (scoreWindow.isEmpty()) 0.0 else scoreWindow.sumOf { it.second } / scoreWindow.size

    private fun feedbackScore(): Double {
        if (feedbackHistory.isEmpty()) return 0.0
        val totalPoints = feedbackHistory.sumOf { feedbackPoints(it) }
        val maxPoints = feedbackHistory.size * MAX_FEEDBACK_POINTS
        return ((totalPoints / maxPoints.toDouble()) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun readMoveset(movesetFile: File): List<PoseFrame> {
        if (!movesetFile.exists()) return emptyList()
        val frames = JSONObject(movesetFile.readText()).optJSONArray("frames") ?: return emptyList()
        return buildList(frames.length()) {
            for (index in 0 until frames.length()) {
                val frame = frames.getJSONObject(index)
                val landmarks = frame.optJSONArray("landmarks").toLandmarks()
                val normalizedLandmarks = frame.optJSONArray("normalized_landmarks").toLandmarks()
                add(
                    PoseFrame(
                        frame = frame.optInt("frame", index),
                        timestampMs = frame.optDouble("timestamp", 0.0) * 1000.0,
                        poseDetected = frame.optBoolean("pose_detected", false),
                        landmarks = landmarks,
                        normalizedLandmarks = normalizedLandmarks,
                        jointAngles = frame.optJSONObject("joint_angles").toAngles(),
                        comparableLandmarkIndices = comparableLandmarkIndices(landmarks, normalizedLandmarks)
                    )
                )
            }
        }
    }

    private fun comparableLandmarkIndices(
        landmarks: List<PoseLandmark>,
        normalizedLandmarks: List<PoseLandmark>
    ): IntArray {
        val maxIndex = minOf(landmarks.size, normalizedLandmarks.size)
        if (maxIndex == 0) return EMPTY_INT_ARRAY

        val indices = IntArray(maxIndex)
        var count = 0
        for (index in 0 until maxIndex) {
            if (landmarks.hasValidLandmark(index)) {
                indices[count] = index
                count += 1
            }
        }
        return if (count == 0) {
            EMPTY_INT_ARRAY
        } else {
            indices.copyOf(count)
        }
    }

    private fun JSONArray?.toLandmarks(): List<PoseLandmark> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val values = getJSONArray(index)
                add(
                    PoseLandmark(
                        x = values.optDouble(0, 0.0).toFloat(),
                        y = values.optDouble(1, 0.0).toFloat(),
                        z = values.optDouble(2, 0.0).toFloat(),
                        visibility = values.optDouble(3, 0.0).toFloat()
                    )
                )
            }
        }
    }

    private fun JSONObject?.toAngles(): Map<String, Double> {
        if (this == null) return emptyMap()
        return buildMap {
            val keys = keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, optDouble(key, 0.0))
            }
        }
    }

    private fun DoubleArray.lowerBound(value: Double): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle] < value) low = middle + 1 else high = middle
        }
        return low
    }

    private fun DoubleArray.upperBound(value: Double): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle] <= value) low = middle + 1 else high = middle
        }
        return low
    }

    companion object {
        fun forDance(dance: Dance): DanceScoringEngine {
            val movesetFile = dance.metadata.moveset?.let { File(dance.folder, it) }
                ?: File(dance.folder, "moveset.json")
            return DanceScoringEngine(dance.name, movesetFile)
        }

        fun rankFor(score: Double): Rank =
            when {
                score >= 95.0 -> Rank.S
                score >= 90.0 -> Rank.A_PLUS
                score >= 80.0 -> Rank.A
                score >= 70.0 -> Rank.B
                score >= 60.0 -> Rank.C
                score >= 40.0 -> Rank.D
                else -> Rank.E
            }

        private fun feedbackFor(score: Double): ScoreFeedback =
            when {
                score < 25.0 -> ScoreFeedback.X
                score < 50.0 -> ScoreFeedback.OK
                score < 75.0 -> ScoreFeedback.OTIMO
                else -> ScoreFeedback.SS
            }

        private fun feedbackPoints(feedback: ScoreFeedback): Int =
            when (feedback) {
                ScoreFeedback.X -> 1
                ScoreFeedback.OK -> 2
                ScoreFeedback.OTIMO -> 3
                ScoreFeedback.SS -> 4
            }

        private const val TIMESTAMP_TOLERANCE_MS = 200.0
        private const val FEEDBACK_INTERVAL_MS = 5000.0
        private const val SLIDING_WINDOW_MS = 5000.0
        private const val LANDMARK_DISTANCE_THRESHOLD = 1.0
        private const val COMPARABLE_LANDMARK_VISIBILITY_THRESHOLD = 0.35f
        private const val MAX_ANGLE_DIFFERENCE = 180.0
        private const val LANDMARK_SIMILARITY_WEIGHT = 0.7
        private const val ANGLE_SIMILARITY_WEIGHT = 0.3
        private const val MAX_FEEDBACK_POINTS = 4
        private const val REALTIME_COMPARISON_REPORT_FILE_NAME = "realtime_comparison_debug_report.json"
        private val EMPTY_INT_ARRAY = IntArray(0)
        private val ANGLE_SPECS = arrayOf(
            AngleSpec("left_shoulder", intArrayOf(13, 11, 23)),
            AngleSpec("right_shoulder", intArrayOf(14, 12, 24)),
            AngleSpec("left_elbow", intArrayOf(11, 13, 15)),
            AngleSpec("right_elbow", intArrayOf(12, 14, 16)),
            AngleSpec("left_hip", intArrayOf(11, 23, 25)),
            AngleSpec("right_hip", intArrayOf(12, 24, 26)),
            AngleSpec("left_knee", intArrayOf(23, 25, 27)),
            AngleSpec("right_knee", intArrayOf(24, 26, 28)),
            AngleSpec("torso", intArrayOf(11, 12, 23, 24))
        )
    }
}
