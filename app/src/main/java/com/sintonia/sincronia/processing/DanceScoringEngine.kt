package com.sintonia.sincronia.processing

import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.domain.DanceResult
import com.sintonia.sincronia.domain.DanceSessionResult
import com.sintonia.sincronia.domain.Rank
import com.sintonia.sincronia.domain.ScoreFeedback
import java.io.File
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.pow
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
    val feedback: ScoreFeedback?,
    val poseSimilarity: Double = 0.0,
    val motionSimilarity: Double = 0.0,
    val expectedMotionEnergy: Double = 0.0,
    val playerMotionEnergy: Double = 0.0,
    val motionActive: Boolean = false,
    val motionPenaltyApplied: Boolean = false,
    val motionScoreCap: Double? = null,
    val executionSimilarity: Double = 0.0,
    val rawIntentScore: Double = 0.0,
    val intentScore: Double = 0.0,
    val intentCurve: String = "none",
    val intentCurveMaxScore: Double = 1.0,
    val trackingQuality: Double = 0.0,
    val intentReason: String = "not_evaluated",
    val participationScore: Double = 0.0,
    val participationScoreCap: Double? = null,
    val participationFeedbackCap: ScoreFeedback? = null,
    val participationReason: String = "not_evaluated",
    val lowParticipationRegions: List<String> = emptyList()
)

data class ScoreUpdate(
    val evaluation: FrameEvaluation,
    val currentScore: Double,
    val emittedFeedback: ScoreFeedback?,
    val skeletonConnectionScores: List<SkeletonConnectionScore> = emptyList()
)

data class SkeletonConnectionScore(
    val startIndex: Int,
    val endIndex: Int,
    val score: Float,
    val compared: Boolean
)

private data class PoseFrame(
    val frame: Int,
    val timestampMs: Double,
    val poseDetected: Boolean,
    val landmarks: List<PoseLandmark>,
    val normalizedLandmarks: List<PoseLandmark>,
    val jointAngles: Map<String, Double>,
    val comparableLandmarkIndices: IntArray,
    val headFacing: HeadFacingState?
)

private data class AngleSpec(
    val key: String,
    val landmarkIndices: IntArray,
    val weight: Double = 1.0
)

private data class CandidateEvaluation(
    val referencePose: PoseFrame,
    val comparison: PoseComparison
) {
    val evaluation: FrameEvaluation
        get() = comparison.evaluation
}

private data class PoseComparison(
    val evaluation: FrameEvaluation,
    val skeletonConnectionScores: List<SkeletonConnectionScore>,
    val motionRegionScores: List<MotionRegionScore> = emptyList()
)

private data class LandmarkSimilarityResult(
    val similarity: Double,
    val scores: FloatArray,
    val compared: BooleanArray,
    val connectionScores: List<SkeletonConnectionScore>
)

private data class MotionSimilarityResult(
    val similarity: Double,
    val expectedEnergy: Double,
    val playerEnergy: Double,
    val active: Boolean,
    val stationaryPenaltyApplied: Boolean,
    val scoreCap: Double?,
    val regionScores: List<MotionRegionScore>
)

private data class MotionRegionScore(
    val name: String,
    val comparisonCount: Int,
    val score: Double,
    val expectedEnergy: Double,
    val playerEnergy: Double
)

private data class GeometricPoseResult(
    val similarity: Double,
    val angleSimilarity: Double,
    val structureSimilarity: Double,
    val connectionScores: List<SkeletonConnectionScore>
)

private data class MotionSegmentSpec(
    val name: String,
    val startIndex: Int,
    val endIndex: Int,
    val weight: Double
)

private data class MotionVector(
    val x: Double,
    val y: Double
) {
    val speed: Double = sqrt(x * x + y * y)
}

private data class RelativeVectorSpec(
    val name: String,
    val fromIndex: Int,
    val toIndex: Int,
    val weight: Double
)

private data class DistanceSpec(
    val name: String,
    val firstIndex: Int,
    val secondIndex: Int,
    val weight: Double
)

private class FeatureAccumulator {
    private var scoreSum = 0.0
    private var weightSum = 0.0

    fun add(score: Double, weight: Double) {
        if (weight <= 0.0) return
        scoreSum += score.coerceIn(0.0, 1.0) * weight
        weightSum += weight
    }

    fun average(defaultValue: Double = 0.0): Double =
        if (weightSum > 0.0) (scoreSum / weightSum).coerceIn(0.0, 1.0) else defaultValue
}

private enum class HeadFacingState {
    FacingForward,
    FacingLeft,
    FacingRight,
    FacingBackward
}

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

private data class IntentFrame(
    val timestampMs: Double,
    val score: Double,
    val trackingQuality: Double,
    val expectedMotionEnergy: Double,
    val playerMotionEnergy: Double,
    val motionActive: Boolean
)

private data class IntentCurve(
    val name: String,
    val maxScore: Double,
    val exponent: Double
)

private data class ParticipationAnalysis(
    val score: Double,
    val scoreCap: Double?,
    val feedbackCap: ScoreFeedback?,
    val reason: String,
    val lowParticipationRegions: List<String>
)

private const val REPORT_MAX_POSE_LANDMARKS = 33
private const val REPORT_WORST_LANDMARKS_PER_FRAME = 6
private const val REPORT_MIN_REGION_COMPARISONS_FOR_DIAGNOSTIC = 10
private const val REPORT_TIMESTAMP_TOLERANCE_MS = 500.0
private const val REPORT_FEEDBACK_INTERVAL_MS = 5000.0
private const val REPORT_SLIDING_WINDOW_MS = 5000.0
private const val REPORT_LANDMARK_DISTANCE_THRESHOLD = 1.0
private const val REPORT_LANDMARK_VISIBILITY_THRESHOLD = 0.35f
private const val REPORT_LANDMARK_SIMILARITY_WEIGHT = 0.3
private const val REPORT_ANGLE_SIMILARITY_WEIGHT = 0.7
private const val REPORT_STATIC_POSE_WEIGHT_WHEN_ACTIVE = 0.60
private const val REPORT_MOTION_WEIGHT_WHEN_ACTIVE = 0.40
private const val REPORT_MOTION_ACTIVE_ENERGY_THRESHOLD = 0.08
private const val REPORT_STATIONARY_PLAYER_ENERGY_RATIO = 0.25
private const val REPORT_STATIONARY_SCORE_CAP = 0.55
private const val REPORT_STATIONARY_PENALTY_CONSECUTIVE_FRAMES = 10
private const val REPORT_INTENT_WINDOW_MS = 2500.0
private const val REPORT_INTENT_LOW_THRESHOLD = 0.35
private const val REPORT_INTENT_HIGH_THRESHOLD = 0.65
private const val REPORT_INTENT_LOW_TRACKING_CONFIDENCE = 0.45
private const val REPORT_INTENT_SEVERE_MAX_SCORE = 0.42
private const val REPORT_INTENT_PARTIAL_MAX_SCORE = 0.82
private const val REPORT_INTENT_COMPLETE_MAX_SCORE = 1.0
private const val REPORT_REGION_ACTIVE_ENERGY_THRESHOLD = 0.08
private const val REPORT_PARTICIPATION_LOW_THRESHOLD = 0.20
private const val REPORT_PARTICIPATION_MEDIUM_THRESHOLD = 0.40
private const val REPORT_PARTICIPATION_GOOD_THRESHOLD = 0.60
private const val REPORT_PARTICIPATION_LOW_CAP = 0.28
private const val REPORT_PARTICIPATION_MEDIUM_CAP = 0.52
private const val REPORT_PARTICIPATION_GOOD_CAP = 0.70
private const val REPORT_Z_DISTANCE_WEIGHT = 0.04
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

private fun SkeletonConnectionScore.belongsToRegion(region: BodyRegionSpec): Boolean {
    var hasStart = false
    var hasEnd = false
    for (index in region.landmarkIndices) {
        if (startIndex == index) hasStart = true
        if (endIndex == index) hasEnd = true
    }
    return hasStart && hasEnd
}

private class RealtimeComparisonDebugCollector(
    private val danceName: String,
    private val outputFile: File
) {
    private val startedAtMs = System.currentTimeMillis()
    private val timeline = mutableListOf<JSONObject>()
    private val regionStats = REPORT_BODY_REGIONS.associate { it.name to RegionStats() }
    private val motionRegionStats = REPORT_BODY_REGIONS.associate { it.name to RegionStats() }
    private val ignoredLandmarkCounts = IntArray(REPORT_MAX_POSE_LANDMARKS)
    private val missingPlayerLandmarkCounts = IntArray(REPORT_MAX_POSE_LANDMARKS)
    private var frameCount = 0
    private var validComparisonFrames = 0
    private var motionComparisonFrames = 0
    private var motionPenaltyFrames = 0
    private var framesWithoutComparison = 0
    private var totalExpectedLandmarks = 0
    private var totalIgnoredLandmarks = 0
    private var totalMissingPlayerLandmarks = 0
    private var totalPoseSimilarity = 0.0
    private var totalMotionSimilarity = 0.0
    private var totalExpectedMotionEnergy = 0.0
    private var totalPlayerMotionEnergy = 0.0
    private var totalRawIntentScore = 0.0
    private var totalIntentScore = 0.0
    private var totalParticipationScore = 0.0
    private var participationCapFrames = 0
    private var lowParticipationFrames = 0
    private val participationReasonCounts = mutableMapOf<String, Int>()
    private var lowIntentFrames = 0
    private var lowTrackingFrames = 0
    private val intentCurveCounts = mutableMapOf<String, Int>()
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
        val regionScores = regionDebugScores(
            landmarkScores = landmarkScores,
            connectionScores = chosenCandidate?.comparison?.skeletonConnectionScores.orEmpty()
        )
        val motionRegionScores = chosenCandidate?.comparison?.motionRegionScores.orEmpty()
        val hasValidComparison = referencePose != null && expectedIndices.isNotEmpty()

        if (hasValidComparison) {
            validComparisonFrames += 1
            totalPoseSimilarity += evaluation.poseSimilarity
            totalExpectedMotionEnergy += evaluation.expectedMotionEnergy
            totalPlayerMotionEnergy += evaluation.playerMotionEnergy
            totalRawIntentScore += evaluation.rawIntentScore
            totalIntentScore += evaluation.intentScore
            totalParticipationScore += evaluation.participationScore
            participationReasonCounts[evaluation.participationReason] =
                (participationReasonCounts[evaluation.participationReason] ?: 0) + 1
            if (evaluation.participationScoreCap != null) {
                participationCapFrames += 1
            }
            if (evaluation.participationScore < REPORT_PARTICIPATION_GOOD_THRESHOLD && evaluation.motionActive) {
                lowParticipationFrames += 1
            }
            intentCurveCounts[evaluation.intentCurve] = (intentCurveCounts[evaluation.intentCurve] ?: 0) + 1
            if (evaluation.intentScore < REPORT_INTENT_LOW_THRESHOLD) {
                lowIntentFrames += 1
            }
            if (evaluation.trackingQuality < REPORT_INTENT_LOW_TRACKING_CONFIDENCE) {
                lowTrackingFrames += 1
            }
            if (evaluation.motionActive) {
                motionComparisonFrames += 1
                totalMotionSimilarity += evaluation.motionSimilarity
            }
            if (evaluation.motionPenaltyApplied) {
                motionPenaltyFrames += 1
            }
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
        for (motionRegionScore in motionRegionScores) {
            val stats = motionRegionStats.getValue(motionRegionScore.name)
            repeat(motionRegionScore.comparisonCount) {
                stats.add(motionRegionScore.score)
            }
        }

        timeline += JSONObject()
            .put("timestamp_ms", playbackTimestampMs.roundReport())
            .put("instant_score", (evaluation.overallSimilarity * 100.0).roundReport())
            .put("accumulated_score", accumulatedScore.roundReport())
            .put("pose_similarity", (evaluation.poseSimilarity * 100.0).roundReport())
            .put("landmark_similarity", (evaluation.landmarkSimilarity * 100.0).roundReport())
            .put("angle_similarity", (evaluation.angleSimilarity * 100.0).roundReport())
            .put("motion_similarity", (evaluation.motionSimilarity * 100.0).roundReport())
            .put("expected_motion_energy", evaluation.expectedMotionEnergy.roundReport())
            .put("player_motion_energy", evaluation.playerMotionEnergy.roundReport())
            .put("motion_active", evaluation.motionActive)
            .put("motion_penalty_applied", evaluation.motionPenaltyApplied)
            .put("motion_score_cap", evaluation.motionScoreCap?.let { (it * 100.0).roundReport() } ?: JSONObject.NULL)
            .put("execution_similarity", (evaluation.executionSimilarity * 100.0).roundReport())
            .put("raw_intent_score", (evaluation.rawIntentScore * 100.0).roundReport())
            .put("intent_score", (evaluation.intentScore * 100.0).roundReport())
            .put("intent_curve", evaluation.intentCurve)
            .put("intent_curve_max_score", (evaluation.intentCurveMaxScore * 100.0).roundReport())
            .put("tracking_quality", evaluation.trackingQuality.roundReport())
            .put("intent_reason", evaluation.intentReason)
            .put("participation_score", (evaluation.participationScore * 100.0).roundReport())
            .put("participation_score_cap", evaluation.participationScoreCap?.let { (it * 100.0).roundReport() } ?: JSONObject.NULL)
            .put("participation_feedback_cap", evaluation.participationFeedbackCap?.name ?: JSONObject.NULL)
            .put("participation_reason", evaluation.participationReason)
            .put("low_participation_regions", evaluation.lowParticipationRegions.toJsonArray())
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
            .put("motion_region_scores", motionRegionScores.toMotionRegionScoresJson())
            .put("expected_active_regions", motionRegionScores.activeRegionNames { it.expectedEnergy })
            .put("player_active_regions", motionRegionScores.activeRegionNames { it.playerEnergy })
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
        val avgPoseSimilarity = if (validComparisonFrames > 0) {
            totalPoseSimilarity / validComparisonFrames
        } else {
            0.0
        }
        val avgMotionSimilarity = if (motionComparisonFrames > 0) {
            totalMotionSimilarity / motionComparisonFrames
        } else {
            0.0
        }
        val avgExpectedMotionEnergy = if (validComparisonFrames > 0) {
            totalExpectedMotionEnergy / validComparisonFrames
        } else {
            0.0
        }
        val avgPlayerMotionEnergy = if (validComparisonFrames > 0) {
            totalPlayerMotionEnergy / validComparisonFrames
        } else {
            0.0
        }
        val avgRawIntentScore = if (validComparisonFrames > 0) {
            totalRawIntentScore / validComparisonFrames
        } else {
            0.0
        }
        val avgIntentScore = if (validComparisonFrames > 0) {
            totalIntentScore / validComparisonFrames
        } else {
            0.0
        }
        val avgParticipationScore = if (validComparisonFrames > 0) {
            totalParticipationScore / validComparisonFrames
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
                    .put("static_pose_weight_when_motion_active", REPORT_STATIC_POSE_WEIGHT_WHEN_ACTIVE)
                    .put("motion_weight_when_motion_active", REPORT_MOTION_WEIGHT_WHEN_ACTIVE)
                    .put("motion_active_energy_threshold", REPORT_MOTION_ACTIVE_ENERGY_THRESHOLD)
                    .put("stationary_player_energy_ratio", REPORT_STATIONARY_PLAYER_ENERGY_RATIO)
                    .put("stationary_penalty_consecutive_frames", REPORT_STATIONARY_PENALTY_CONSECUTIVE_FRAMES)
                    .put("stationary_score_cap", (REPORT_STATIONARY_SCORE_CAP * 100.0).roundReport())
                    .put("intent_window_ms", REPORT_INTENT_WINDOW_MS)
                    .put("intent_low_threshold", (REPORT_INTENT_LOW_THRESHOLD * 100.0).roundReport())
                    .put("intent_high_threshold", (REPORT_INTENT_HIGH_THRESHOLD * 100.0).roundReport())
                    .put("intent_low_tracking_confidence", REPORT_INTENT_LOW_TRACKING_CONFIDENCE)
                    .put("intent_severe_max_score", (REPORT_INTENT_SEVERE_MAX_SCORE * 100.0).roundReport())
                    .put("intent_partial_max_score", (REPORT_INTENT_PARTIAL_MAX_SCORE * 100.0).roundReport())
                    .put("intent_complete_max_score", (REPORT_INTENT_COMPLETE_MAX_SCORE * 100.0).roundReport())
                    .put("participation_low_threshold", (REPORT_PARTICIPATION_LOW_THRESHOLD * 100.0).roundReport())
                    .put("participation_medium_threshold", (REPORT_PARTICIPATION_MEDIUM_THRESHOLD * 100.0).roundReport())
                    .put("participation_good_threshold", (REPORT_PARTICIPATION_GOOD_THRESHOLD * 100.0).roundReport())
                    .put("participation_low_cap", (REPORT_PARTICIPATION_LOW_CAP * 100.0).roundReport())
                    .put("participation_medium_cap", (REPORT_PARTICIPATION_MEDIUM_CAP * 100.0).roundReport())
                    .put("participation_good_cap", (REPORT_PARTICIPATION_GOOD_CAP * 100.0).roundReport())
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
            .put("motion_regions", motionRegionStatsJson())
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
                    .put("instant_score_source", "evaluation.overallSimilarity * 100 after intent curve")
                    .put("execution_score_source", "evaluation.executionSimilarity * 100 before intent curve")
                    .put("smoothed_score_source", "current sliding-window score used for feedback")
                    .put("final_score_formula", "70% similarity_average + 30% feedback_score")
                    .put("curve", JSONArray(timeline))
            )
            .put(
                "motion_analysis",
                JSONObject()
                    .put("average_pose_similarity", (avgPoseSimilarity * 100.0).roundReport())
                    .put("average_motion_similarity", (avgMotionSimilarity * 100.0).roundReport())
                    .put("average_expected_motion_energy", avgExpectedMotionEnergy.roundReport())
                    .put("average_player_motion_energy", avgPlayerMotionEnergy.roundReport())
                    .put("frames_with_motion_comparison", motionComparisonFrames)
                    .put("frames_with_stationary_penalty", motionPenaltyFrames)
                    .put(
                        "stationary_penalty_ratio",
                        if (validComparisonFrames > 0) {
                            (motionPenaltyFrames / validComparisonFrames.toDouble() * 100.0).roundReport()
                        } else {
                            0.0
                        }
                    )
            )
            .put(
                "intent_analysis",
                JSONObject()
                    .put("average_raw_intent_score", (avgRawIntentScore * 100.0).roundReport())
                    .put("average_intent_score", (avgIntentScore * 100.0).roundReport())
                    .put("low_intent_frames", lowIntentFrames)
                    .put("low_tracking_frames", lowTrackingFrames)
                    .put(
                        "low_intent_ratio",
                        if (validComparisonFrames > 0) {
                            (lowIntentFrames / validComparisonFrames.toDouble() * 100.0).roundReport()
                        } else {
                            0.0
                        }
                    )
                    .put("curve_usage", intentCurveCountsJson())
                    .put("description", "intentScore chooses the scoring curve; executionSimilarity remains the source score.")
            )
            .put(
                "participation_analysis",
                JSONObject()
                    .put("average_participation_score", (avgParticipationScore * 100.0).roundReport())
                    .put("frames_with_participation_cap", participationCapFrames)
                    .put("low_participation_frames", lowParticipationFrames)
                    .put(
                        "participation_cap_ratio",
                        if (validComparisonFrames > 0) {
                            (participationCapFrames / validComparisonFrames.toDouble() * 100.0).roundReport()
                        } else {
                            0.0
                        }
                    )
                    .put("reason_usage", participationReasonCountsJson())
                    .put("description", "participationScore limits scoring when the player does not move the regions that the moveset expects.")
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
                sqrt((dx * dx + dy * dy + REPORT_Z_DISTANCE_WEIGHT * dz * dz).toDouble())
            }
            val score = (1.0 - (distance / REPORT_LANDMARK_DISTANCE_THRESHOLD)).coerceIn(0.0, 1.0)
            add(LandmarkDebugScore(index, score, distance, missing))
        }
    }

    private fun regionDebugScores(
        landmarkScores: List<LandmarkDebugScore>,
        connectionScores: List<SkeletonConnectionScore>
    ): List<RegionDebugScore> {
        if (landmarkScores.isEmpty() && connectionScores.isEmpty()) return emptyList()
        val byIndex = landmarkScores.associateBy { it.index }
        return buildList {
            for (region in REPORT_BODY_REGIONS) {
                var sum = 0.0
                var count = 0
                for (connectionScore in connectionScores) {
                    if (!connectionScore.compared || !connectionScore.belongsToRegion(region)) continue
                    sum += connectionScore.score.toDouble()
                    count += 1
                }
                if (count == 0) {
                    for (index in region.landmarkIndices) {
                        val score = byIndex[index]?.score ?: continue
                        sum += score
                        count += 1
                    }
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

    private fun motionRegionStatsJson(): JSONArray {
        val totalComparisons = motionRegionStats.values.sumOf { it.comparisons }.coerceAtLeast(1)
        return JSONArray().also { array ->
            for (region in REPORT_BODY_REGIONS) {
                val stats = motionRegionStats.getValue(region.name)
                val average = stats.average()
                val contribution = ((stats.comparisons / totalComparisons.toDouble()) * average * 100.0)
                array.put(
                    JSONObject()
                        .put("region", region.name)
                        .put("comparisons", stats.comparisons)
                        .put("average_motion_score", (average * 100.0).roundReport())
                        .put("best_motion_score", (if (stats.comparisons == 0) 0.0 else stats.bestScore * 100.0).roundReport())
                        .put("worst_motion_score", (if (stats.comparisons == 0) 0.0 else stats.worstScore * 100.0).roundReport())
                        .put("contribution_to_motion_component", contribution.roundReport())
                )
            }
        }
    }

    private fun intentCurveCountsJson(): JSONObject =
        JSONObject().also { json ->
            for ((curve, count) in intentCurveCounts) {
                json.put(curve, count)
            }
        }

    private fun participationReasonCountsJson(): JSONObject =
        JSONObject().also { json ->
            for ((reason, count) in participationReasonCounts) {
                json.put(reason, count)
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
        if (motionPenaltyFrames > 0 && validComparisonFrames > 0) {
            diagnostics += "Movimento insuficiente do jogador em ${((motionPenaltyFrames / validComparisonFrames.toDouble()) * 100.0).roundReport()}% dos frames comparados."
        }
        if (lowIntentFrames > 0 && validComparisonFrames > 0) {
            diagnostics += "Baixa intenção de coreografia em ${((lowIntentFrames / validComparisonFrames.toDouble()) * 100.0).roundReport()}% dos frames comparados."
        }
        if (lowParticipationFrames > 0 && validComparisonFrames > 0) {
            diagnostics += "Baixa participação nas regiões ativas esperadas em ${((lowParticipationFrames / validComparisonFrames.toDouble()) * 100.0).roundReport()}% dos frames comparados."
        }
        if (lowTrackingFrames > 0 && validComparisonFrames > 0) {
            diagnostics += "Baixa confiança de rastreamento em ${((lowTrackingFrames / validComparisonFrames.toDouble()) * 100.0).roundReport()}% dos frames comparados; a análise de intenção foi suavizada nesses trechos."
        }
        for ((name, stats) in regionStats) {
            if (stats.comparisons >= REPORT_MIN_REGION_COMPARISONS_FOR_DIAGNOSTIC && stats.average() < 0.50) {
                diagnostics += "Baixa similaridade concentrada em $name (${(stats.average() * 100.0).roundReport()}% médio)."
            }
        }
        for ((name, stats) in motionRegionStats) {
            if (stats.comparisons >= REPORT_MIN_REGION_COMPARISONS_FOR_DIAGNOSTIC && stats.average() < 0.50) {
                diagnostics += "Baixa similaridade de movimento concentrada em $name (${(stats.average() * 100.0).roundReport()}% médio)."
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
                    .put("pose_similarity", (candidate.evaluation.poseSimilarity * 100.0).roundReport())
                    .put("landmark_similarity", (candidate.evaluation.landmarkSimilarity * 100.0).roundReport())
                    .put("angle_similarity", (candidate.evaluation.angleSimilarity * 100.0).roundReport())
                    .put("motion_similarity", (candidate.evaluation.motionSimilarity * 100.0).roundReport())
                    .put("expected_motion_energy", candidate.evaluation.expectedMotionEnergy.roundReport())
                    .put("player_motion_energy", candidate.evaluation.playerMotionEnergy.roundReport())
                    .put("motion_active", candidate.evaluation.motionActive)
                    .put("motion_penalty_applied", candidate.evaluation.motionPenaltyApplied)
                    .put("motion_score_cap", candidate.evaluation.motionScoreCap?.let { (it * 100.0).roundReport() } ?: JSONObject.NULL)
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

private fun List<MotionRegionScore>.toMotionRegionScoresJson(): JSONArray =
    JSONArray().also { array ->
        forEach { score ->
            array.put(
                JSONObject()
                    .put("region", score.name)
                    .put("comparisons", score.comparisonCount)
                    .put("motion_score", (score.score * 100.0).roundReport())
                    .put("expected_motion_energy", score.expectedEnergy.roundReport())
                    .put("player_motion_energy", score.playerEnergy.roundReport())
            )
        }
    }

private fun List<MotionRegionScore>.activeRegionNames(energySelector: (MotionRegionScore) -> Double): JSONArray =
    JSONArray().also { array ->
        forEach { score ->
            if (energySelector(score) >= REPORT_REGION_ACTIVE_ENERGY_THRESHOLD) {
                array.put(score.name)
            }
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
    private val feedbackCapWindow = ArrayDeque<Pair<Double, Int>>()
    private val allScores = mutableListOf<Double>()
    private val feedbackHistory = mutableListOf<ScoreFeedback>()
    private var currentFeedback = ScoreFeedback.X
    private var lastFeedbackTimestampMs = 0.0
    private var previousRealtimePose: PoseFrame? = null
    private var consecutiveStationaryMotionFrames = 0
    private val intentWindow = ArrayDeque<IntentFrame>()
    private var smoothedIntentScore: Double? = null
    private var currentIntentCurve = INTENT_COMPLETE_CURVE
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
            comparableLandmarkIndices = EMPTY_INT_ARRAY,
            headFacing = headFacingState(landmarks)
        )
        val candidates = posesNear(playbackTimestampMs)
        val debugCandidateEvaluations: List<CandidateEvaluation>?
        val chosenCandidate: CandidateEvaluation?
        val comparison = if (debugCollector == null) {
            debugCandidateEvaluations = null
            chosenCandidate = null
            if (candidates.isEmpty()) {
                noComparison(playbackTimestampMs)
            } else {
                candidates
                    .asSequence()
                    .map { reference -> compare(reference, realtimePose, previousRealtimePose) }
                    .maxByOrNull { it.evaluation.overallSimilarity }
                    ?: noComparison(playbackTimestampMs)
            }
        } else {
            val evaluatedCandidates = candidates.map { reference ->
                CandidateEvaluation(reference, compare(reference, realtimePose, previousRealtimePose))
            }
            val bestCandidate = evaluatedCandidates.maxByOrNull { it.evaluation.overallSimilarity }
            debugCandidateEvaluations = evaluatedCandidates
            chosenCandidate = bestCandidate
            bestCandidate?.comparison ?: noComparison(playbackTimestampMs)
        }
        val participationAdjustedComparison = applyParticipationAnalysis(comparison)
        val stationaryAdjustedComparison = applyStationaryMotionStreak(participationAdjustedComparison)
        val adjustedComparison = applyIntentAnalysis(stationaryAdjustedComparison)
        val adjustedChosenCandidate = chosenCandidate?.copy(comparison = adjustedComparison)
        val evaluation = adjustedComparison.evaluation
        val emittedFeedback = updateScoreWindow(evaluation)
        debugCollector?.recordFrame(
            playbackTimestampMs = playbackTimestampMs,
            realtimePose = realtimePose,
            chosenCandidate = adjustedChosenCandidate,
            candidateEvaluations = debugCandidateEvaluations.orEmpty(),
            evaluation = evaluation,
            accumulatedScore = currentScore()
        )
        previousRealtimePose = realtimePose
        return ScoreUpdate(
            evaluation = evaluation.copy(feedback = currentFeedback),
            currentScore = currentScore(),
            emittedFeedback = emittedFeedback,
            skeletonConnectionScores = adjustedComparison.skeletonConnectionScores
        )
    }

    private fun applyStationaryMotionStreak(comparison: PoseComparison): PoseComparison {
        val evaluation = comparison.evaluation
        val stationaryCandidate = evaluation.motionActive &&
            evaluation.expectedMotionEnergy >= MOTION_ACTIVE_ENERGY_THRESHOLD &&
            evaluation.playerMotionEnergy < evaluation.expectedMotionEnergy * STATIONARY_PLAYER_ENERGY_RATIO

        consecutiveStationaryMotionFrames = if (stationaryCandidate) {
            consecutiveStationaryMotionFrames + 1
        } else {
            0
        }

        val shouldApplyCap = consecutiveStationaryMotionFrames >= STATIONARY_PENALTY_CONSECUTIVE_FRAMES
        val cap = if (shouldApplyCap) STATIONARY_SCORE_CAP else null
        val cappedSimilarity = cap?.let { minOf(evaluation.overallSimilarity, it) } ?: evaluation.overallSimilarity
        if (
            cappedSimilarity == evaluation.overallSimilarity &&
            evaluation.motionPenaltyApplied == shouldApplyCap &&
            evaluation.motionScoreCap == cap
        ) {
            return comparison
        }

        return comparison.copy(
            evaluation = evaluation.copy(
                overallSimilarity = cappedSimilarity,
                motionPenaltyApplied = shouldApplyCap,
                motionScoreCap = cap
            )
        )
    }

    private fun applyParticipationAnalysis(comparison: PoseComparison): PoseComparison {
        val evaluation = comparison.evaluation
        val executionSimilarity = if (evaluation.executionSimilarity > 0.0) {
            evaluation.executionSimilarity
        } else {
            evaluation.overallSimilarity
        }.coerceIn(0.0, 1.0)
        val analysis = participationAnalysis(evaluation, comparison.motionRegionScores)
        val cappedSimilarity = analysis.scoreCap?.let { minOf(evaluation.overallSimilarity, it) }
            ?: evaluation.overallSimilarity

        return comparison.copy(
            evaluation = evaluation.copy(
                overallSimilarity = cappedSimilarity,
                executionSimilarity = executionSimilarity,
                participationScore = analysis.score,
                participationScoreCap = analysis.scoreCap,
                participationFeedbackCap = analysis.feedbackCap,
                participationReason = analysis.reason,
                lowParticipationRegions = analysis.lowParticipationRegions
            )
        )
    }

    private fun participationAnalysis(
        evaluation: FrameEvaluation,
        motionRegionScores: List<MotionRegionScore>
    ): ParticipationAnalysis {
        if (!evaluation.motionActive || motionRegionScores.isEmpty()) {
            return ParticipationAnalysis(
                score = 1.0,
                scoreCap = null,
                feedbackCap = null,
                reason = "moveset_static_or_no_motion_regions",
                lowParticipationRegions = emptyList()
            )
        }

        val hasPrimaryActiveRegion = motionRegionScores.any { region ->
            region.expectedEnergy >= PARTICIPATION_REGION_ACTIVE_ENERGY_THRESHOLD &&
                !region.name.isSupportParticipationRegion()
        }
        var weightedParticipationSum = 0.0
        var weightSum = 0.0
        val lowRegions = mutableListOf<String>()

        for (region in motionRegionScores) {
            if (region.expectedEnergy < PARTICIPATION_REGION_ACTIVE_ENERGY_THRESHOLD) continue

            val regionWeight = participationRegionWeight(region.name, hasPrimaryActiveRegion)
            if (regionWeight <= 0.0) continue

            val energyRatio = if (region.expectedEnergy > 1e-6) {
                (region.playerEnergy / region.expectedEnergy).coerceIn(0.0, 1.0)
            } else {
                1.0
            }
            val energyParticipation = energyRatio.pow(PARTICIPATION_ENERGY_EXPONENT)
            val regionParticipation = (
                PARTICIPATION_ENERGY_WEIGHT * energyParticipation +
                    PARTICIPATION_REGION_MOTION_SCORE_WEIGHT * region.score
                ).coerceIn(0.0, 1.0)
            val weight = region.expectedEnergy
                .coerceIn(PARTICIPATION_MIN_REGION_WEIGHT, PARTICIPATION_MAX_REGION_WEIGHT) * regionWeight

            weightedParticipationSum += regionParticipation * weight
            weightSum += weight

            if (
                regionParticipation < PARTICIPATION_LOW_REGION_THRESHOLD &&
                (!region.name.isSupportParticipationRegion() || !hasPrimaryActiveRegion)
            ) {
                lowRegions += region.name
            }
        }

        if (weightSum <= 0.0) {
            return ParticipationAnalysis(
                score = 1.0,
                scoreCap = null,
                feedbackCap = null,
                reason = "no_expected_active_participation_regions",
                lowParticipationRegions = emptyList()
            )
        }

        val score = (weightedParticipationSum / weightSum).coerceIn(0.0, 1.0)
        val cap = when {
            score < PARTICIPATION_LOW_THRESHOLD -> PARTICIPATION_LOW_SCORE_CAP
            score < PARTICIPATION_MEDIUM_THRESHOLD -> PARTICIPATION_MEDIUM_SCORE_CAP
            score < PARTICIPATION_GOOD_THRESHOLD -> PARTICIPATION_GOOD_SCORE_CAP
            else -> null
        }
        val feedbackCap = when {
            score < PARTICIPATION_LOW_THRESHOLD -> ScoreFeedback.X
            score < PARTICIPATION_MEDIUM_THRESHOLD -> ScoreFeedback.OK
            score < PARTICIPATION_GOOD_THRESHOLD -> ScoreFeedback.OTIMO
            else -> null
        }
        val reason = when {
            cap == null -> "participation_sufficient"
            lowRegions.isNotEmpty() -> "expected_active_regions_missing_participation"
            evaluation.playerMotionEnergy < evaluation.expectedMotionEnergy * PARTICIPATION_STILL_ENERGY_RATIO ->
                "player_nearly_still_during_active_moveset"
            else -> "participation_below_expected_energy"
        }

        return ParticipationAnalysis(
            score = score,
            scoreCap = cap,
            feedbackCap = feedbackCap,
            reason = reason,
            lowParticipationRegions = lowRegions
        )
    }

    private fun participationRegionWeight(regionName: String, hasPrimaryActiveRegion: Boolean): Double =
        when (regionName) {
            "Mão esquerda", "Mão direita" -> 1.45
            "Antebraço esquerdo", "Antebraço direito" -> 1.30
            "Braço esquerdo", "Braço direito" -> 1.15
            "Pé esquerdo", "Pé direito" -> 1.15
            "Perna esquerda", "Perna direita" -> 1.05
            "Tronco", "Quadril", "Cabeça" -> if (hasPrimaryActiveRegion) 0.18 else 0.75
            else -> 0.85
        }

    private fun String.isSupportParticipationRegion(): Boolean =
        this == "Tronco" || this == "Quadril" || this == "Cabeça"

    private fun applyIntentAnalysis(comparison: PoseComparison): PoseComparison {
        val evaluation = comparison.evaluation
        val executionSimilarity = if (evaluation.executionSimilarity > 0.0) {
            evaluation.executionSimilarity
        } else {
            evaluation.overallSimilarity
        }.coerceIn(0.0, 1.0)
        val curveInputSimilarity = evaluation.overallSimilarity.coerceIn(0.0, 1.0)
        if (
            comparison.skeletonConnectionScores.isEmpty() &&
            evaluation.confidence <= 0.0 &&
            evaluation.poseSimilarity <= 0.0 &&
            evaluation.motionSimilarity <= 0.0
        ) {
            return comparison.copy(
                evaluation = evaluation.copy(
                    executionSimilarity = executionSimilarity,
                    rawIntentScore = 0.0,
                    intentScore = 0.0,
                    intentCurve = INTENT_NO_COMPARISON_CURVE.name,
                    intentCurveMaxScore = INTENT_NO_COMPARISON_CURVE.maxScore,
                    trackingQuality = 0.0,
                    intentReason = "no_valid_reference_comparison",
                    participationScore = 0.0,
                    participationScoreCap = null,
                    participationFeedbackCap = null,
                    participationReason = "no_valid_reference_comparison",
                    lowParticipationRegions = emptyList()
                )
            )
        }

        val trackingQuality = evaluation.confidence.coerceIn(0.0, 1.0)
        val rawIntentScore = rawIntentScore(evaluation, trackingQuality)
        intentWindow.addLast(
            IntentFrame(
                timestampMs = evaluation.timestampMs,
                score = rawIntentScore,
                trackingQuality = trackingQuality,
                expectedMotionEnergy = evaluation.expectedMotionEnergy,
                playerMotionEnergy = evaluation.playerMotionEnergy,
                motionActive = evaluation.motionActive
            )
        )
        val minIntentTimestamp = evaluation.timestampMs - INTENT_WINDOW_MS
        while (intentWindow.isNotEmpty() && intentWindow.first().timestampMs < minIntentTimestamp) {
            intentWindow.removeFirst()
        }

        val windowIntentScore = intentWindowScore(rawIntentScore)
        val previousSmoothedIntent = smoothedIntentScore
        val intentScore = if (previousSmoothedIntent == null) {
            windowIntentScore
        } else {
            previousSmoothedIntent + INTENT_SMOOTHING_ALPHA * (windowIntentScore - previousSmoothedIntent)
        }.coerceIn(0.0, 1.0)
        smoothedIntentScore = intentScore

        val curve = selectIntentCurve(intentScore)
        currentIntentCurve = curve
        val curvedSimilarity = scoreThroughIntentCurve(curveInputSimilarity, curve)
        return comparison.copy(
            evaluation = evaluation.copy(
                overallSimilarity = curvedSimilarity,
                executionSimilarity = executionSimilarity,
                rawIntentScore = rawIntentScore,
                intentScore = intentScore,
                intentCurve = curve.name,
                intentCurveMaxScore = curve.maxScore,
                trackingQuality = trackingQuality,
                intentReason = intentReason(evaluation, rawIntentScore, intentScore, curve, trackingQuality)
            )
        )
    }

    private fun rawIntentScore(evaluation: FrameEvaluation, trackingQuality: Double): Double {
        val poseIntent = relaxedIntentScore(
            value = evaluation.poseSimilarity,
            low = INTENT_POSE_LOW_SIMILARITY,
            high = INTENT_POSE_HIGH_SIMILARITY
        )
        val structureIntent = relaxedIntentScore(
            value = (evaluation.landmarkSimilarity + evaluation.angleSimilarity) / 2.0,
            low = INTENT_STRUCTURE_LOW_SIMILARITY,
            high = INTENT_STRUCTURE_HIGH_SIMILARITY
        )
        val motionIntent = if (evaluation.motionActive) {
            val energyRatio = if (evaluation.expectedMotionEnergy > 1e-6) {
                (evaluation.playerMotionEnergy / evaluation.expectedMotionEnergy).coerceIn(0.0, INTENT_MAX_ENERGY_RATIO)
            } else {
                1.0
            }
            val energyIntent = sqrt(energyRatio.coerceAtMost(1.0))
            val directionIntent = relaxedIntentScore(
                value = evaluation.motionSimilarity,
                low = INTENT_MOTION_LOW_SIMILARITY,
                high = INTENT_MOTION_HIGH_SIMILARITY
            )
            INTENT_MOTION_DIRECTION_WEIGHT * directionIntent +
                INTENT_MOTION_ENERGY_WEIGHT * energyIntent
        } else {
            1.0
        }

        val rawScore = if (evaluation.motionActive) {
            INTENT_ACTIVE_POSE_WEIGHT * poseIntent +
                INTENT_ACTIVE_MOTION_WEIGHT * motionIntent +
                INTENT_ACTIVE_STRUCTURE_WEIGHT * structureIntent
        } else {
            INTENT_STATIC_POSE_WEIGHT * poseIntent +
                INTENT_STATIC_STRUCTURE_WEIGHT * structureIntent
        }.coerceIn(0.0, 1.0)

        return if (trackingQuality < INTENT_LOW_TRACKING_CONFIDENCE) {
            maxOf(rawScore, INTENT_LOW_TRACKING_SCORE_FLOOR)
        } else {
            rawScore
        }.coerceIn(0.0, 1.0)
    }

    private fun relaxedIntentScore(value: Double, low: Double, high: Double): Double {
        if (value <= low) return 0.0
        if (value >= high) return 1.0
        val t = ((value - low) / (high - low)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun intentWindowScore(defaultScore: Double): Double {
        if (intentWindow.isEmpty()) return defaultScore
        var scoreSum = 0.0
        var weightSum = 0.0
        for (frame in intentWindow) {
            val trackingWeight = INTENT_LOW_TRACKING_WINDOW_WEIGHT +
                (1.0 - INTENT_LOW_TRACKING_WINDOW_WEIGHT) * frame.trackingQuality.coerceIn(0.0, 1.0)
            val motionWeight = if (frame.motionActive) {
                1.0 + frame.expectedMotionEnergy.coerceIn(0.0, 1.0) * INTENT_ACTIVE_WINDOW_WEIGHT_BOOST
            } else {
                1.0
            }
            val weight = trackingWeight * motionWeight
            scoreSum += frame.score * weight
            weightSum += weight
        }
        return if (weightSum > 0.0) (scoreSum / weightSum).coerceIn(0.0, 1.0) else defaultScore
    }

    private fun selectIntentCurve(intentScore: Double): IntentCurve {
        val currentName = currentIntentCurve.name
        return when (currentName) {
            INTENT_SEVERE_CURVE.name -> when {
                intentScore >= INTENT_HIGH_ENTER_THRESHOLD -> INTENT_COMPLETE_CURVE
                intentScore >= INTENT_LOW_EXIT_THRESHOLD -> INTENT_PARTIAL_CURVE
                else -> INTENT_SEVERE_CURVE
            }
            INTENT_PARTIAL_CURVE.name -> when {
                intentScore <= INTENT_LOW_ENTER_THRESHOLD -> INTENT_SEVERE_CURVE
                intentScore >= INTENT_HIGH_ENTER_THRESHOLD -> INTENT_COMPLETE_CURVE
                else -> INTENT_PARTIAL_CURVE
            }
            else -> when {
                intentScore <= INTENT_LOW_ENTER_THRESHOLD -> INTENT_SEVERE_CURVE
                intentScore <= INTENT_HIGH_EXIT_THRESHOLD -> INTENT_PARTIAL_CURVE
                else -> INTENT_COMPLETE_CURVE
            }
        }
    }

    private fun scoreThroughIntentCurve(executionSimilarity: Double, curve: IntentCurve): Double =
        (curve.maxScore * executionSimilarity.coerceIn(0.0, 1.0).pow(curve.exponent))
            .coerceIn(0.0, curve.maxScore)

    private fun intentReason(
        evaluation: FrameEvaluation,
        rawIntentScore: Double,
        intentScore: Double,
        curve: IntentCurve,
        trackingQuality: Double
    ): String =
        when {
            trackingQuality < INTENT_LOW_TRACKING_CONFIDENCE -> "low_tracking_confidence_intent_softened:${curve.name}"
            evaluation.motionActive &&
                evaluation.playerMotionEnergy < evaluation.expectedMotionEnergy * INTENT_LOW_PLAYER_ENERGY_RATIO ->
                "expected_motion_but_low_player_energy:${curve.name}"
            evaluation.motionActive && evaluation.motionSimilarity < INTENT_LOW_MOTION_SIMILARITY ->
                "movement_direction_or_rhythm_mismatch:${curve.name}"
            rawIntentScore < INTENT_LOW_ENTER_THRESHOLD || intentScore < INTENT_LOW_ENTER_THRESHOLD ->
                "low_choreography_intent:${curve.name}"
            curve.name == INTENT_COMPLETE_CURVE.name -> "choreography_intent_detected:${curve.name}"
            else -> "partial_choreography_intent:${curve.name}"
        }

    fun finalSessionResult(): DanceSessionResult {
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
        return DanceSessionResult(
            result = result,
            debugReportPath = debugReportFile?.absolutePath
        )
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

    private fun noComparison(timestampMs: Double): PoseComparison =
        PoseComparison(
            evaluation = FrameEvaluation(timestampMs, 0.0, 0.0, 0.0, 0.0, currentFeedback),
            skeletonConnectionScores = emptyList()
        )

    private fun previousReferencePose(referencePose: PoseFrame, realtimeIntervalMs: Double): PoseFrame? {
        if (referenceFrames.isEmpty()) return null
        val currentIndex = referenceTimestamps.lowerBound(referencePose.timestampMs)
        if (currentIndex <= 0) return null

        val intervalMs = realtimeIntervalMs
            .coerceAtLeast(MIN_MOTION_INTERVAL_MS)
            .coerceAtMost(MAX_MOTION_INTERVAL_MS)
        val targetTimestampMs = referencePose.timestampMs - intervalMs
        val previousIndex = (referenceTimestamps.upperBound(targetTimestampMs) - 1)
            .coerceIn(0, currentIndex - 1)
        return referenceFrames[previousIndex].takeIf { it.poseDetected }
    }

    private fun compare(
        referencePose: PoseFrame,
        realtimePose: PoseFrame,
        previousRealtimePose: PoseFrame?
    ): PoseComparison {
        if (!referencePose.poseDetected || referencePose.comparableLandmarkIndices.isEmpty()) {
            return PoseComparison(
                evaluation = FrameEvaluation(realtimePose.timestampMs, 0.0, 0.0, 0.0, 0.0, null),
                skeletonConnectionScores = emptyList()
            )
        }
        val realtimeIntervalMs = previousRealtimePose?.let { realtimePose.timestampMs - it.timestampMs } ?: 0.0
        val previousReferencePose = previousReferencePose(referencePose, realtimeIntervalMs)
        val poseResult = geometricPoseSimilarity(
            previousReferencePose = previousReferencePose,
            referencePose = referencePose,
            realtimePose = realtimePose
        )
        val landmarkSimilarity = poseResult.structureSimilarity
        val angleSimilarity = poseResult.angleSimilarity
        val confidence = confidence(referencePose, realtimePose)
        val motionResult = motionSimilarity(
            previousReferencePose = previousReferencePose,
            referencePose = referencePose,
            previousRealtimePose = previousRealtimePose,
            realtimePose = realtimePose
        )
        val poseSimilarity = poseResult.similarity
        val motionWeight = if (motionResult.active) MOTION_SIMILARITY_WEIGHT_WHEN_ACTIVE else 0.0
        val poseWeight = 1.0 - motionWeight
        val uncappedSimilarity = (
            poseWeight * poseSimilarity +
                motionWeight * motionResult.similarity
            ).coerceIn(0.0, 1.0)
        val overallSimilarity = motionResult.scoreCap
            ?.let { minOf(uncappedSimilarity, it) }
            ?: uncappedSimilarity
        return PoseComparison(
            evaluation = FrameEvaluation(
                timestampMs = realtimePose.timestampMs,
                overallSimilarity = overallSimilarity,
                landmarkSimilarity = landmarkSimilarity,
                angleSimilarity = angleSimilarity,
                confidence = confidence,
                feedback = null,
                poseSimilarity = poseSimilarity,
                motionSimilarity = motionResult.similarity,
                expectedMotionEnergy = motionResult.expectedEnergy,
                playerMotionEnergy = motionResult.playerEnergy,
                motionActive = motionResult.active,
                motionPenaltyApplied = motionResult.stationaryPenaltyApplied,
                motionScoreCap = motionResult.scoreCap
            ),
            skeletonConnectionScores = poseResult.connectionScores,
            motionRegionScores = motionResult.regionScores
        )
    }

    private fun geometricPoseSimilarity(
        previousReferencePose: PoseFrame?,
        referencePose: PoseFrame,
        realtimePose: PoseFrame
    ): GeometricPoseResult {
        val angleScores = FeatureAccumulator()
        val vectorScores = FeatureAccumulator()
        val relationScores = FeatureAccumulator()
        val globalScores = FeatureAccumulator()

        fun dynamicWeight(baseWeight: Double, activity: Double): Double =
            baseWeight * (STATIC_FEATURE_WEIGHT_FLOOR + FEATURE_ACTIVITY_WEIGHT_BOOST * activity.coerceIn(0.0, 1.0))

        fun addHandZoneFeature(wristIndex: Int, shoulderIndex: Int, hipIndex: Int, isLeftSide: Boolean) {
            if (
                !referencePose.hasValidLandmark(wristIndex) ||
                !referencePose.hasValidLandmark(shoulderIndex) ||
                !referencePose.hasValidLandmark(hipIndex)
            ) {
                return
            }

            val activity = previousReferencePose
                ?.let { landmarkDelta(it, referencePose, wristIndex) / FEATURE_ACTIVE_DISTANCE }
                ?.coerceIn(0.0, 1.0)
                ?: 0.0
            val weight = dynamicWeight(HAND_ZONE_WEIGHT, activity)
            val zoneScore = handZoneSimilarity(
                referencePose = referencePose,
                realtimePose = realtimePose,
                wristIndex = wristIndex,
                shoulderIndex = shoulderIndex,
                hipIndex = hipIndex,
                isLeftSide = isLeftSide
            )
            if (zoneScore != null) {
                relationScores.add(score = zoneScore, weight = weight)
            } else if (activity >= ACTIVE_FEATURE_IMPORTANCE_THRESHOLD) {
                relationScores.add(score = MISSING_ACTIVE_FEATURE_SCORE, weight = weight)
            }
        }

        for (spec in ANGLE_SPECS) {
            if (!referencePose.hasAllValidLandmarks(spec.landmarkIndices)) continue
            val referenceAngle = referencePose.angleForSpec(spec) ?: continue
            val activity = angleActivity(previousReferencePose, referencePose, spec)
            val weight = dynamicWeight(spec.weight, activity)
            val realtimeAngle = if (realtimePose.hasAllValidLandmarks(spec.landmarkIndices)) {
                realtimePose.angleForSpec(spec)
            } else {
                null
            }

            if (realtimeAngle != null) {
                angleScores.add(
                    score = smoothErrorScore(
                        error = angularDifference(referenceAngle, realtimeAngle),
                        good = ANGLE_GOOD_ERROR_DEGREES,
                        bad = ANGLE_BAD_ERROR_DEGREES
                    ),
                    weight = weight
                )
            } else if (activity >= ACTIVE_FEATURE_IMPORTANCE_THRESHOLD) {
                angleScores.add(score = MISSING_ACTIVE_FEATURE_SCORE, weight = weight)
            }
        }

        for (segment in MOTION_SEGMENTS) {
            if (!referencePose.hasValidLandmark(segment.startIndex) || !referencePose.hasValidLandmark(segment.endIndex)) continue
            val referenceAngle = referencePose.lineAngle(segment.startIndex, segment.endIndex) ?: continue
            val activity = segmentActivity(previousReferencePose, referencePose, segment)
            val weight = dynamicWeight(segment.weight, activity)
            val realtimeAngle = if (
                realtimePose.hasValidLandmark(segment.startIndex) &&
                realtimePose.hasValidLandmark(segment.endIndex)
            ) {
                realtimePose.lineAngle(segment.startIndex, segment.endIndex)
            } else {
                null
            }

            if (realtimeAngle != null) {
                vectorScores.add(
                    score = smoothErrorScore(
                        error = angularDifference(referenceAngle, realtimeAngle),
                        good = VECTOR_GOOD_ERROR_DEGREES,
                        bad = VECTOR_BAD_ERROR_DEGREES
                    ),
                    weight = weight
                )
            } else if (activity >= ACTIVE_FEATURE_IMPORTANCE_THRESHOLD) {
                vectorScores.add(score = MISSING_ACTIVE_FEATURE_SCORE, weight = weight)
            }
        }

        for (relation in RELATIVE_VECTOR_SPECS) {
            if (!referencePose.hasValidLandmark(relation.fromIndex) || !referencePose.hasValidLandmark(relation.toIndex)) continue
            val referenceVector = relativeVector(referencePose, relation.fromIndex, relation.toIndex) ?: continue
            val activity = relativeVectorActivity(previousReferencePose, referencePose, relation)
            val weight = dynamicWeight(relation.weight, activity)
            val realtimeVector = if (
                realtimePose.hasValidLandmark(relation.fromIndex) &&
                realtimePose.hasValidLandmark(relation.toIndex)
            ) {
                relativeVector(realtimePose, relation.fromIndex, relation.toIndex)
            } else {
                null
            }

            if (realtimeVector != null) {
                relationScores.add(
                    score = smoothErrorScore(
                        error = vectorDistance(referenceVector, realtimeVector),
                        good = RELATION_GOOD_DISTANCE,
                        bad = RELATION_BAD_DISTANCE
                    ),
                    weight = weight
                )
            } else if (activity >= ACTIVE_FEATURE_IMPORTANCE_THRESHOLD) {
                relationScores.add(score = MISSING_ACTIVE_FEATURE_SCORE, weight = weight)
            }
        }

        for (distance in DISTANCE_SPECS) {
            if (!referencePose.hasValidLandmark(distance.firstIndex) || !referencePose.hasValidLandmark(distance.secondIndex)) continue
            val referenceDistance = normalizedDistance(referencePose, distance.firstIndex, distance.secondIndex) ?: continue
            val activity = distanceActivity(previousReferencePose, referencePose, distance)
            val weight = dynamicWeight(distance.weight, activity)
            val realtimeDistance = if (
                realtimePose.hasValidLandmark(distance.firstIndex) &&
                realtimePose.hasValidLandmark(distance.secondIndex)
            ) {
                normalizedDistance(realtimePose, distance.firstIndex, distance.secondIndex)
            } else {
                null
            }

            if (realtimeDistance != null) {
                relationScores.add(
                    score = smoothErrorScore(
                        error = abs(referenceDistance - realtimeDistance),
                        good = DISTANCE_GOOD_ERROR,
                        bad = DISTANCE_BAD_ERROR
                    ),
                    weight = weight
                )
            } else if (activity >= ACTIVE_FEATURE_IMPORTANCE_THRESHOLD) {
                relationScores.add(score = MISSING_ACTIVE_FEATURE_SCORE, weight = weight)
            }
        }

        addHandZoneFeature(wristIndex = 15, shoulderIndex = 11, hipIndex = 23, isLeftSide = true)
        addHandZoneFeature(wristIndex = 16, shoulderIndex = 12, hipIndex = 24, isLeftSide = false)

        for (globalSpec in GLOBAL_ORIENTATION_SPECS) {
            if (!referencePose.hasValidLandmark(globalSpec.startIndex) || !referencePose.hasValidLandmark(globalSpec.endIndex)) continue
            val referenceAngle = referencePose.lineAngle(globalSpec.startIndex, globalSpec.endIndex) ?: continue
            val realtimeAngle = if (
                realtimePose.hasValidLandmark(globalSpec.startIndex) &&
                realtimePose.hasValidLandmark(globalSpec.endIndex)
            ) {
                realtimePose.lineAngle(globalSpec.startIndex, globalSpec.endIndex)
            } else {
                null
            }
            if (realtimeAngle != null) {
                globalScores.add(
                    score = smoothErrorScore(
                        error = angularDifference(referenceAngle, realtimeAngle),
                        good = GLOBAL_GOOD_ERROR_DEGREES,
                        bad = GLOBAL_BAD_ERROR_DEGREES
                    ),
                    weight = globalSpec.weight
                )
            }
        }

        val angleSimilarity = angleScores.average(defaultValue = 0.0)
        val vectorSimilarity = vectorScores.average(defaultValue = angleSimilarity)
        val relationSimilarity = relationScores.average(defaultValue = vectorSimilarity)
        val globalSimilarity = globalScores.average(defaultValue = angleSimilarity)
        val structureSimilarity = (
            VECTOR_POSE_WEIGHT * vectorSimilarity +
                RELATION_POSE_WEIGHT * relationSimilarity +
                GLOBAL_POSE_WEIGHT * globalSimilarity
            ) / (VECTOR_POSE_WEIGHT + RELATION_POSE_WEIGHT + GLOBAL_POSE_WEIGHT)
        val similarity = (
            ANGLE_POSE_WEIGHT * angleSimilarity +
                STRUCTURE_POSE_WEIGHT * structureSimilarity
            ).coerceIn(0.0, 1.0)

        return GeometricPoseResult(
            similarity = similarity,
            angleSimilarity = angleSimilarity,
            structureSimilarity = structureSimilarity,
            connectionScores = geometricConnectionScores(referencePose, realtimePose)
        )
    }

    private fun motionSimilarity(
        previousReferencePose: PoseFrame?,
        referencePose: PoseFrame,
        previousRealtimePose: PoseFrame?,
        realtimePose: PoseFrame
    ): MotionSimilarityResult {
        if (
            previousReferencePose == null ||
            previousRealtimePose == null ||
            !previousReferencePose.poseDetected ||
            !referencePose.poseDetected ||
            !previousRealtimePose.poseDetected ||
            !realtimePose.poseDetected
        ) {
            return inactiveMotionSimilarity()
        }

        val referenceDeltaMs = referencePose.timestampMs - previousReferencePose.timestampMs
        val realtimeDeltaMs = realtimePose.timestampMs - previousRealtimePose.timestampMs
        if (
            referenceDeltaMs < MIN_MOTION_INTERVAL_MS ||
            realtimeDeltaMs < MIN_MOTION_INTERVAL_MS ||
            referenceDeltaMs > MAX_MOTION_INTERVAL_MS ||
            realtimeDeltaMs > MAX_MOTION_INTERVAL_MS
        ) {
            return inactiveMotionSimilarity()
        }

        val referenceDeltaSeconds = referenceDeltaMs / 1000.0
        val realtimeDeltaSeconds = realtimeDeltaMs / 1000.0
        val regionScoreSums = DoubleArray(REPORT_BODY_REGIONS.size)
        val regionExpectedEnergySums = DoubleArray(REPORT_BODY_REGIONS.size)
        val regionPlayerEnergySums = DoubleArray(REPORT_BODY_REGIONS.size)
        val regionWeightSums = DoubleArray(REPORT_BODY_REGIONS.size)
        val regionComparisonCounts = IntArray(REPORT_BODY_REGIONS.size)

        var scoreSum = 0.0
        var scoreWeightSum = 0.0
        var expectedEnergySum = 0.0
        var playerEnergySum = 0.0
        var energyWeightSum = 0.0

        fun regionContains(region: BodyRegionSpec, firstIndex: Int, secondIndex: Int = -1, thirdIndex: Int = -1): Boolean {
            for (index in region.landmarkIndices) {
                if (index == firstIndex || index == secondIndex || index == thirdIndex) return true
            }
            return false
        }

        fun addFeature(
            score: Double,
            weight: Double,
            expectedEnergy: Double,
            playerEnergy: Double,
            firstIndex: Int,
            secondIndex: Int = -1,
            thirdIndex: Int = -1
        ) {
            if (weight <= 0.0) return
            scoreSum += score.coerceIn(0.0, 1.0) * weight
            scoreWeightSum += weight
            expectedEnergySum += expectedEnergy * weight
            playerEnergySum += playerEnergy * weight
            energyWeightSum += weight

            for (regionIndex in REPORT_BODY_REGIONS.indices) {
                val region = REPORT_BODY_REGIONS[regionIndex]
                if (!regionContains(region, firstIndex, secondIndex, thirdIndex)) continue
                regionScoreSums[regionIndex] += score.coerceIn(0.0, 1.0) * weight
                regionExpectedEnergySums[regionIndex] += expectedEnergy * weight
                regionPlayerEnergySums[regionIndex] += playerEnergy * weight
                regionWeightSums[regionIndex] += weight
                regionComparisonCounts[regionIndex] += 1
            }
        }

        for (index in MOTION_LANDMARKS) {
            val expectedVelocity = landmarkVelocity(
                previousPose = previousReferencePose,
                currentPose = referencePose,
                index = index,
                deltaSeconds = referenceDeltaSeconds
            ) ?: continue
            val expectedEnergy = expectedVelocity.speed / MOTION_LANDMARK_SPEED_SCALE
            if (expectedEnergy < MOTION_FEATURE_ACTIVE_ENERGY_THRESHOLD) continue

            val playerVelocity = landmarkVelocity(
                previousPose = previousRealtimePose,
                currentPose = realtimePose,
                index = index,
                deltaSeconds = realtimeDeltaSeconds
            )
            val playerEnergy = (playerVelocity?.speed ?: 0.0) / MOTION_LANDMARK_SPEED_SCALE
            val score = vectorMotionScore(expectedVelocity, playerVelocity)
            val weight = motionLandmarkWeight(index) * expectedEnergy.coerceIn(MOTION_MIN_FEATURE_WEIGHT, MOTION_MAX_FEATURE_WEIGHT)
            addFeature(
                score = score,
                weight = weight,
                expectedEnergy = expectedEnergy,
                playerEnergy = playerEnergy,
                firstIndex = index
            )
        }

        for (spec in ANGLE_SPECS) {
            if (
                !previousReferencePose.hasAllValidLandmarks(spec.landmarkIndices) ||
                !referencePose.hasAllValidLandmarks(spec.landmarkIndices)
            ) {
                continue
            }
            val previousReferenceAngle = previousReferencePose.angleForSpec(spec) ?: continue
            val referenceAngle = referencePose.angleForSpec(spec) ?: continue
            val expectedVelocity = signedAngularDifference(previousReferenceAngle, referenceAngle) / referenceDeltaSeconds
            val expectedEnergy = abs(expectedVelocity) / MOTION_ANGLE_SPEED_SCALE
            if (expectedEnergy < MOTION_FEATURE_ACTIVE_ENERGY_THRESHOLD) continue

            val playerVelocity = if (
                previousRealtimePose.hasAllValidLandmarks(spec.landmarkIndices) &&
                realtimePose.hasAllValidLandmarks(spec.landmarkIndices)
            ) {
                val previousRealtimeAngle = previousRealtimePose.angleForSpec(spec)
                val realtimeAngle = realtimePose.angleForSpec(spec)
                if (previousRealtimeAngle != null && realtimeAngle != null) {
                    signedAngularDifference(previousRealtimeAngle, realtimeAngle) / realtimeDeltaSeconds
                } else {
                    0.0
                }
            } else {
                0.0
            }
            val playerEnergy = abs(playerVelocity) / MOTION_ANGLE_SPEED_SCALE
            val score = scalarMotionScore(expectedVelocity, playerVelocity)
            val weight = spec.weight * expectedEnergy.coerceIn(MOTION_MIN_FEATURE_WEIGHT, MOTION_MAX_FEATURE_WEIGHT)
            val firstIndex = spec.landmarkIndices.getOrElse(0) { -1 }
            val secondIndex = spec.landmarkIndices.getOrElse(1) { -1 }
            val thirdIndex = spec.landmarkIndices.getOrElse(2) { -1 }
            addFeature(
                score = score,
                weight = weight,
                expectedEnergy = expectedEnergy,
                playerEnergy = playerEnergy,
                firstIndex = firstIndex,
                secondIndex = secondIndex,
                thirdIndex = thirdIndex
            )
        }

        for (segment in MOTION_SEGMENTS) {
            if (
                !previousReferencePose.hasValidLandmark(segment.startIndex) ||
                !previousReferencePose.hasValidLandmark(segment.endIndex) ||
                !referencePose.hasValidLandmark(segment.startIndex) ||
                !referencePose.hasValidLandmark(segment.endIndex)
            ) {
                continue
            }
            val previousReferenceAngle = previousReferencePose.lineAngle(segment.startIndex, segment.endIndex) ?: continue
            val referenceAngle = referencePose.lineAngle(segment.startIndex, segment.endIndex) ?: continue
            val expectedVelocity = signedAngularDifference(previousReferenceAngle, referenceAngle) / referenceDeltaSeconds
            val expectedEnergy = abs(expectedVelocity) / MOTION_SEGMENT_ANGLE_SPEED_SCALE
            if (expectedEnergy < MOTION_FEATURE_ACTIVE_ENERGY_THRESHOLD) continue

            val playerVelocity = if (
                previousRealtimePose.hasValidLandmark(segment.startIndex) &&
                previousRealtimePose.hasValidLandmark(segment.endIndex) &&
                realtimePose.hasValidLandmark(segment.startIndex) &&
                realtimePose.hasValidLandmark(segment.endIndex)
            ) {
                val previousRealtimeAngle = previousRealtimePose.lineAngle(segment.startIndex, segment.endIndex)
                val realtimeAngle = realtimePose.lineAngle(segment.startIndex, segment.endIndex)
                if (previousRealtimeAngle != null && realtimeAngle != null) {
                    signedAngularDifference(previousRealtimeAngle, realtimeAngle) / realtimeDeltaSeconds
                } else {
                    0.0
                }
            } else {
                0.0
            }
            val playerEnergy = abs(playerVelocity) / MOTION_SEGMENT_ANGLE_SPEED_SCALE
            val score = scalarMotionScore(expectedVelocity, playerVelocity)
            val weight = segment.weight * expectedEnergy.coerceIn(MOTION_MIN_FEATURE_WEIGHT, MOTION_MAX_FEATURE_WEIGHT)
            addFeature(
                score = score,
                weight = weight,
                expectedEnergy = expectedEnergy,
                playerEnergy = playerEnergy,
                firstIndex = segment.startIndex,
                secondIndex = segment.endIndex
            )
        }

        if (scoreWeightSum <= 0.0 || energyWeightSum <= 0.0) {
            return inactiveMotionSimilarity()
        }

        val similarity = (scoreSum / scoreWeightSum).coerceIn(0.0, 1.0)
        val expectedEnergy = expectedEnergySum / energyWeightSum
        val playerEnergy = playerEnergySum / energyWeightSum
        val active = expectedEnergy >= MOTION_ACTIVE_ENERGY_THRESHOLD
        val stationaryPenaltyApplied = active && playerEnergy < expectedEnergy * STATIONARY_PLAYER_ENERGY_RATIO
        val regionScores = buildList {
            for (regionIndex in REPORT_BODY_REGIONS.indices) {
                val weightSum = regionWeightSums[regionIndex]
                if (weightSum > 0.0) {
                    add(
                        MotionRegionScore(
                            name = REPORT_BODY_REGIONS[regionIndex].name,
                            comparisonCount = regionComparisonCounts[regionIndex],
                            score = (regionScoreSums[regionIndex] / weightSum).coerceIn(0.0, 1.0),
                            expectedEnergy = regionExpectedEnergySums[regionIndex] / weightSum,
                            playerEnergy = regionPlayerEnergySums[regionIndex] / weightSum
                        )
                    )
                }
            }
        }

        return MotionSimilarityResult(
            similarity = similarity,
            expectedEnergy = expectedEnergy,
            playerEnergy = playerEnergy,
            active = active,
            stationaryPenaltyApplied = stationaryPenaltyApplied,
            scoreCap = null,
            regionScores = regionScores
        )
    }

    private fun inactiveMotionSimilarity(): MotionSimilarityResult =
        MotionSimilarityResult(
            similarity = 1.0,
            expectedEnergy = 0.0,
            playerEnergy = 0.0,
            active = false,
            stationaryPenaltyApplied = false,
            scoreCap = null,
            regionScores = emptyList()
        )

    private fun landmarkSimilarity(referencePose: PoseFrame, realtimePose: PoseFrame): LandmarkSimilarityResult {
        val reference = referencePose.normalizedLandmarks
        val realtime = realtimePose.normalizedLandmarks
        val comparableIndices = referencePose.comparableLandmarkIndices
        val scores = FloatArray(MAX_POSE_LANDMARKS) { SKELETON_NOT_COMPARED_SCORE }
        val compared = BooleanArray(MAX_POSE_LANDMARKS)
        if (reference.isEmpty() && referencePose.headFacing == null) {
            return LandmarkSimilarityResult(0.0, scores, compared, emptyList())
        }

        var weightedScoreSum = 0.0
        var weightSum = 0.0
        for (index in comparableIndices) {
            val weight = landmarkWeight(index)
            if (weight <= 0.0) continue

            val ref = reference.getOrNull(index)
            val rt = realtime.getOrNull(index)
            val distance = if (
                ref != null &&
                rt != null &&
                realtimePose.hasValidLandmark(index)
            ) {
                val dx = ref.x - rt.x
                val dy = ref.y - rt.y
                val dz = ref.z - rt.z
                sqrt((dx * dx + dy * dy + Z_DISTANCE_WEIGHT * dz * dz).toDouble())
            } else {
                LANDMARK_DISTANCE_THRESHOLD
            }
            val score = (1.0 - (distance / LANDMARK_DISTANCE_THRESHOLD)).coerceIn(0.0, 1.0)
            scores[index] = score.toFloat()
            compared[index] = true
            weightedScoreSum += score * weight
            weightSum += weight
        }

        val headScore = headFacingSimilarity(referencePose, realtimePose)
        if (headScore != null) {
            for (index in FACE_LANDMARKS) {
                scores[index] = headScore.toFloat()
                compared[index] = true
            }
            weightedScoreSum += headScore * HEAD_ORIENTATION_WEIGHT
            weightSum += HEAD_ORIENTATION_WEIGHT
        }

        val absoluteSimilarity = if (weightSum > 0.0) weightedScoreSum / weightSum else 0.0
        val connectionScores = skeletonConnectionScores(referencePose, realtimePose, scores, compared)
        var segmentScoreSum = 0.0
        var segmentWeightSum = 0.0
        for (connectionScore in connectionScores) {
            if (!connectionScore.compared) continue
            val weight = connectionWeight(connectionScore.startIndex, connectionScore.endIndex)
            segmentScoreSum += connectionScore.score.toDouble() * weight
            segmentWeightSum += weight
        }
        val segmentSimilarity = if (segmentWeightSum > 0.0) segmentScoreSum / segmentWeightSum else 0.0
        val similarity = when {
            weightSum > 0.0 && segmentWeightSum > 0.0 ->
                ABSOLUTE_LANDMARK_SUPPORT_WEIGHT * absoluteSimilarity +
                    SEGMENT_ORIENTATION_SUPPORT_WEIGHT * segmentSimilarity
            segmentWeightSum > 0.0 -> segmentSimilarity
            else -> absoluteSimilarity
        }
        return LandmarkSimilarityResult(
            similarity = similarity.coerceIn(0.0, 1.0),
            scores = scores,
            compared = compared,
            connectionScores = connectionScores
        )
    }

    private fun headFacingSimilarity(referencePose: PoseFrame, realtimePose: PoseFrame): Double? {
        val referenceFacing = referencePose.headFacing ?: return null
        val realtimeFacing = realtimePose.headFacing ?: return 0.0
        if (referenceFacing == realtimeFacing) return 1.0
        if (referenceFacing == HeadFacingState.FacingBackward || realtimeFacing == HeadFacingState.FacingBackward) {
            return 0.0
        }
        return if (
            referenceFacing == HeadFacingState.FacingForward ||
            realtimeFacing == HeadFacingState.FacingForward
        ) {
            0.45
        } else {
            0.2
        }
    }

    private fun skeletonConnectionScores(
        referencePose: PoseFrame,
        realtimePose: PoseFrame,
        landmarkScores: FloatArray,
        comparedLandmarks: BooleanArray
    ): List<SkeletonConnectionScore> =
        PoseConnections.connections.map { (startIndex, endIndex) ->
            val startCompared = comparedLandmarks.getOrElse(startIndex) { false }
            val endCompared = comparedLandmarks.getOrElse(endIndex) { false }
            if (!startCompared && !endCompared) {
                SkeletonConnectionScore(startIndex, endIndex, SKELETON_NOT_COMPARED_SCORE, compared = false)
            } else {
                var endpointScoreSum = 0.0
                var scoreCount = 0
                if (startCompared) {
                    endpointScoreSum += landmarkScores.getOrElse(startIndex) { 0f }.toDouble()
                    scoreCount += 1
                }
                if (endCompared) {
                    endpointScoreSum += landmarkScores.getOrElse(endIndex) { 0f }.toDouble()
                    scoreCount += 1
                }
                val endpointScore = endpointScoreSum / scoreCount.coerceAtLeast(1)
                val score = if (startCompared && endCompared && !isFaceConnection(startIndex, endIndex)) {
                    val orientationScore = segmentDirectionScore(referencePose, realtimePose, startIndex, endIndex)
                    SEGMENT_ORIENTATION_OVERLAY_WEIGHT * orientationScore +
                        ENDPOINT_OVERLAY_WEIGHT * endpointScore
                } else {
                    endpointScore
                }
                SkeletonConnectionScore(
                    startIndex = startIndex,
                    endIndex = endIndex,
                    score = score.coerceIn(0.0, 1.0).toFloat(),
                    compared = true
                )
            }
        }

    private fun angleSimilarity(referencePose: PoseFrame, realtimePose: PoseFrame): Double {
        var differenceSum = 0.0
        var weightSum = 0.0

        for (spec in ANGLE_SPECS) {
            if (!referencePose.hasAllValidLandmarks(spec.landmarkIndices)) continue

            val referenceAngle = referencePose.angleForSpec(spec) ?: continue
            val realtimeAngle = if (realtimePose.hasAllValidLandmarks(spec.landmarkIndices)) {
                realtimePose.angleForSpec(spec)
            } else {
                null
            }

            differenceSum += if (realtimeAngle != null) {
                angularDifference(referenceAngle, realtimeAngle)
            } else {
                MAX_ANGLE_DIFFERENCE
            } * spec.weight
            weightSum += spec.weight
        }

        if (weightSum == 0.0) return 0.0
        val meanDifference = differenceSum / weightSum
        return (1.0 - (meanDifference / 180.0)).coerceIn(0.0, 1.0)
    }

    private fun PoseFrame.angleForSpec(spec: AngleSpec): Double? =
        when (spec.key) {
            "shoulder_line" -> lineAngle(11, 12)
            "hip_line" -> lineAngle(23, 24)
            "torso" -> torsoLineAngle()
            else -> if (spec.landmarkIndices.size == 3) {
                jointAngle2d(
                    firstIndex = spec.landmarkIndices[0],
                    middleIndex = spec.landmarkIndices[1],
                    lastIndex = spec.landmarkIndices[2]
                )
            } else {
                jointAngles[spec.key]
            }
        }

    private fun PoseFrame.jointAngle2d(firstIndex: Int, middleIndex: Int, lastIndex: Int): Double? {
        val first = normalizedLandmarks.getOrNull(firstIndex) ?: return null
        val middle = normalizedLandmarks.getOrNull(middleIndex) ?: return null
        val last = normalizedLandmarks.getOrNull(lastIndex) ?: return null

        val firstX = first.x - middle.x
        val firstY = first.y - middle.y
        val lastX = last.x - middle.x
        val lastY = last.y - middle.y
        val denominator = sqrt((firstX * firstX + firstY * firstY).toDouble()) *
            sqrt((lastX * lastX + lastY * lastY).toDouble())
        if (denominator <= 1e-8) return null

        val cosine = ((firstX * lastX + firstY * lastY) / denominator).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun PoseFrame.torsoLineAngle(): Double? {
        val leftShoulder = normalizedLandmarks.getOrNull(11) ?: return null
        val rightShoulder = normalizedLandmarks.getOrNull(12) ?: return null
        val leftHip = normalizedLandmarks.getOrNull(23) ?: return null
        val rightHip = normalizedLandmarks.getOrNull(24) ?: return null
        val shoulderCenterX = (leftShoulder.x + rightShoulder.x) / 2f
        val shoulderCenterY = (leftShoulder.y + rightShoulder.y) / 2f
        val hipCenterX = (leftHip.x + rightHip.x) / 2f
        val hipCenterY = (leftHip.y + rightHip.y) / 2f
        return Math.toDegrees(
            atan2(
                (shoulderCenterY - hipCenterY).toDouble(),
                (shoulderCenterX - hipCenterX).toDouble()
            )
        )
    }

    private fun segmentDirectionScore(
        referencePose: PoseFrame,
        realtimePose: PoseFrame,
        startIndex: Int,
        endIndex: Int
    ): Double {
        if (!realtimePose.hasValidLandmark(startIndex) || !realtimePose.hasValidLandmark(endIndex)) return 0.0

        val referenceStart = referencePose.normalizedLandmarks.getOrNull(startIndex) ?: return 0.0
        val referenceEnd = referencePose.normalizedLandmarks.getOrNull(endIndex) ?: return 0.0
        val realtimeStart = realtimePose.normalizedLandmarks.getOrNull(startIndex) ?: return 0.0
        val realtimeEnd = realtimePose.normalizedLandmarks.getOrNull(endIndex) ?: return 0.0

        val referenceX = referenceEnd.x - referenceStart.x
        val referenceY = referenceEnd.y - referenceStart.y
        val realtimeX = realtimeEnd.x - realtimeStart.x
        val realtimeY = realtimeEnd.y - realtimeStart.y
        val referenceLength = sqrt((referenceX * referenceX + referenceY * referenceY).toDouble())
        val realtimeLength = sqrt((realtimeX * realtimeX + realtimeY * realtimeY).toDouble())
        val denominator = referenceLength * realtimeLength
        if (denominator <= 1e-8) return 0.0

        val cosine = ((referenceX * realtimeX + referenceY * realtimeY) / denominator).coerceIn(-1.0, 1.0)
        return ((cosine + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    private fun geometricConnectionScores(referencePose: PoseFrame, realtimePose: PoseFrame): List<SkeletonConnectionScore> =
        PoseConnections.connections.map { (startIndex, endIndex) ->
            if (!referencePose.hasValidLandmark(startIndex) || !referencePose.hasValidLandmark(endIndex)) {
                SkeletonConnectionScore(startIndex, endIndex, SKELETON_NOT_COMPARED_SCORE, compared = false)
            } else if (!realtimePose.hasValidLandmark(startIndex) || !realtimePose.hasValidLandmark(endIndex)) {
                SkeletonConnectionScore(startIndex, endIndex, MISSING_ACTIVE_FEATURE_SCORE.toFloat(), compared = true)
            } else if (isFaceConnection(startIndex, endIndex)) {
                val headScore = headFacingSimilarity(referencePose, realtimePose) ?: 0.65
                SkeletonConnectionScore(startIndex, endIndex, headScore.toFloat().coerceIn(0f, 1f), compared = true)
            } else {
                val referenceAngle = referencePose.lineAngle(startIndex, endIndex)
                val realtimeAngle = realtimePose.lineAngle(startIndex, endIndex)
                val score = if (referenceAngle != null && realtimeAngle != null) {
                    smoothErrorScore(
                        error = angularDifference(referenceAngle, realtimeAngle),
                        good = VECTOR_GOOD_ERROR_DEGREES,
                        bad = VECTOR_BAD_ERROR_DEGREES
                    )
                } else {
                    MISSING_ACTIVE_FEATURE_SCORE
                }
                SkeletonConnectionScore(
                    startIndex = startIndex,
                    endIndex = endIndex,
                    score = score.toFloat().coerceIn(0f, 1f),
                    compared = true
                )
            }
        }

    private fun angleActivity(previousReferencePose: PoseFrame?, referencePose: PoseFrame, spec: AngleSpec): Double {
        val previousAngle = previousReferencePose?.takeIf { it.hasAllValidLandmarks(spec.landmarkIndices) }?.angleForSpec(spec)
            ?: return 0.0
        val currentAngle = referencePose.angleForSpec(spec) ?: return 0.0
        return (angularDifference(previousAngle, currentAngle) / FEATURE_ACTIVE_ANGLE_DEGREES).coerceIn(0.0, 1.0)
    }

    private fun segmentActivity(previousReferencePose: PoseFrame?, referencePose: PoseFrame, segment: MotionSegmentSpec): Double {
        val previous = previousReferencePose ?: return 0.0
        if (
            !previous.hasValidLandmark(segment.startIndex) ||
            !previous.hasValidLandmark(segment.endIndex) ||
            !referencePose.hasValidLandmark(segment.startIndex) ||
            !referencePose.hasValidLandmark(segment.endIndex)
        ) {
            return 0.0
        }
        val previousAngle = previous.lineAngle(segment.startIndex, segment.endIndex) ?: return 0.0
        val currentAngle = referencePose.lineAngle(segment.startIndex, segment.endIndex) ?: return 0.0
        val angularActivity = angularDifference(previousAngle, currentAngle) / FEATURE_ACTIVE_ANGLE_DEGREES
        val startMovement = landmarkDelta(previous, referencePose, segment.startIndex) / FEATURE_ACTIVE_DISTANCE
        val endMovement = landmarkDelta(previous, referencePose, segment.endIndex) / FEATURE_ACTIVE_DISTANCE
        return maxOf(angularActivity, startMovement, endMovement).coerceIn(0.0, 1.0)
    }

    private fun relativeVectorActivity(
        previousReferencePose: PoseFrame?,
        referencePose: PoseFrame,
        relation: RelativeVectorSpec
    ): Double {
        val previousVector = previousReferencePose?.let { relativeVector(it, relation.fromIndex, relation.toIndex) } ?: return 0.0
        val currentVector = relativeVector(referencePose, relation.fromIndex, relation.toIndex) ?: return 0.0
        return (vectorDistance(previousVector, currentVector) / FEATURE_ACTIVE_DISTANCE).coerceIn(0.0, 1.0)
    }

    private fun distanceActivity(previousReferencePose: PoseFrame?, referencePose: PoseFrame, distance: DistanceSpec): Double {
        val previousDistance = previousReferencePose?.let { normalizedDistance(it, distance.firstIndex, distance.secondIndex) }
            ?: return 0.0
        val currentDistance = normalizedDistance(referencePose, distance.firstIndex, distance.secondIndex) ?: return 0.0
        return (abs(currentDistance - previousDistance) / FEATURE_ACTIVE_DISTANCE).coerceIn(0.0, 1.0)
    }

    private fun handZoneSimilarity(
        referencePose: PoseFrame,
        realtimePose: PoseFrame,
        wristIndex: Int,
        shoulderIndex: Int,
        hipIndex: Int,
        isLeftSide: Boolean
    ): Double? {
        if (
            !realtimePose.hasValidLandmark(wristIndex) ||
            !realtimePose.hasValidLandmark(shoulderIndex) ||
            !realtimePose.hasValidLandmark(hipIndex)
        ) {
            return null
        }

        val referenceWrist = referencePose.normalizedLandmarks.getOrNull(wristIndex) ?: return null
        val referenceShoulder = referencePose.normalizedLandmarks.getOrNull(shoulderIndex) ?: return null
        val referenceHip = referencePose.normalizedLandmarks.getOrNull(hipIndex) ?: return null
        val realtimeWrist = realtimePose.normalizedLandmarks.getOrNull(wristIndex) ?: return null
        val realtimeShoulder = realtimePose.normalizedLandmarks.getOrNull(shoulderIndex) ?: return null
        val realtimeHip = realtimePose.normalizedLandmarks.getOrNull(hipIndex) ?: return null

        val referenceCenterX = bodyCenterX(referencePose) ?: ((referenceShoulder.x.toDouble() + referenceHip.x.toDouble()) / 2.0)
        val realtimeCenterX = bodyCenterX(realtimePose) ?: ((realtimeShoulder.x.toDouble() + realtimeHip.x.toDouble()) / 2.0)
        val referenceVertical = referenceWrist.y.toDouble() - referenceShoulder.y.toDouble()
        val realtimeVertical = realtimeWrist.y.toDouble() - realtimeShoulder.y.toDouble()
        val verticalScore = smoothErrorScore(
            error = abs(referenceVertical - realtimeVertical),
            good = HAND_ZONE_VERTICAL_GOOD_ERROR,
            bad = HAND_ZONE_VERTICAL_BAD_ERROR
        )

        val referenceCrossedCenter = if (isLeftSide) {
            referenceWrist.x.toDouble() > referenceCenterX
        } else {
            referenceWrist.x.toDouble() < referenceCenterX
        }
        val realtimeCrossedCenter = if (isLeftSide) {
            realtimeWrist.x.toDouble() > realtimeCenterX
        } else {
            realtimeWrist.x.toDouble() < realtimeCenterX
        }
        val crossScore = if (referenceCrossedCenter == realtimeCrossedCenter) 1.0 else HAND_ZONE_CROSS_MISMATCH_SCORE

        val referenceShoulderDistance = normalizedDistance(referencePose, shoulderIndex, wristIndex) ?: return null
        val realtimeShoulderDistance = normalizedDistance(realtimePose, shoulderIndex, wristIndex) ?: return null
        val shoulderAnchorScore = if (referenceShoulderDistance <= HAND_ZONE_ANCHOR_DISTANCE) {
            smoothErrorScore(
                error = realtimeShoulderDistance,
                good = HAND_ZONE_ANCHOR_GOOD_DISTANCE,
                bad = HAND_ZONE_ANCHOR_BAD_DISTANCE
            )
        } else {
            1.0
        }

        val referenceHipDistance = normalizedDistance(referencePose, hipIndex, wristIndex) ?: return null
        val realtimeHipDistance = normalizedDistance(realtimePose, hipIndex, wristIndex) ?: return null
        val hipAnchorScore = if (referenceHipDistance <= HAND_ZONE_ANCHOR_DISTANCE) {
            smoothErrorScore(
                error = realtimeHipDistance,
                good = HAND_ZONE_ANCHOR_GOOD_DISTANCE,
                bad = HAND_ZONE_ANCHOR_BAD_DISTANCE
            )
        } else {
            1.0
        }

        return (
            HAND_ZONE_VERTICAL_WEIGHT * verticalScore +
                HAND_ZONE_CROSS_WEIGHT * crossScore +
                HAND_ZONE_SHOULDER_WEIGHT * shoulderAnchorScore +
                HAND_ZONE_HIP_WEIGHT * hipAnchorScore
            ).coerceIn(0.0, 1.0)
    }

    private fun bodyCenterX(pose: PoseFrame): Double? {
        var sum = 0.0
        var count = 0
        for (index in TORSO_LANDMARKS) {
            if (!pose.hasValidLandmark(index)) continue
            val landmark = pose.normalizedLandmarks.getOrNull(index) ?: continue
            sum += landmark.x.toDouble()
            count += 1
        }
        return if (count > 0) sum / count else null
    }

    private fun landmarkDelta(previousPose: PoseFrame, currentPose: PoseFrame, index: Int): Double {
        val previous = previousPose.normalizedLandmarks.getOrNull(index) ?: return 0.0
        val current = currentPose.normalizedLandmarks.getOrNull(index) ?: return 0.0
        val dx = current.x - previous.x
        val dy = current.y - previous.y
        return sqrt((dx * dx + dy * dy).toDouble())
    }

    private fun relativeVector(pose: PoseFrame, fromIndex: Int, toIndex: Int): MotionVector? {
        if (!pose.hasValidLandmark(fromIndex) || !pose.hasValidLandmark(toIndex)) return null
        val from = pose.normalizedLandmarks.getOrNull(fromIndex) ?: return null
        val to = pose.normalizedLandmarks.getOrNull(toIndex) ?: return null
        return MotionVector(
            x = (to.x - from.x).toDouble(),
            y = (to.y - from.y).toDouble()
        )
    }

    private fun normalizedDistance(pose: PoseFrame, firstIndex: Int, secondIndex: Int): Double? {
        val vector = relativeVector(pose, firstIndex, secondIndex) ?: return null
        return vector.speed
    }

    private fun vectorDistance(first: MotionVector, second: MotionVector): Double {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun smoothErrorScore(error: Double, good: Double, bad: Double): Double {
        if (error <= good) return 1.0
        if (error >= bad) return 0.0
        val t = ((error - good) / (bad - good)).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)
        return (1.0 - smooth).coerceIn(0.0, 1.0)
    }

    private fun landmarkVelocity(
        previousPose: PoseFrame,
        currentPose: PoseFrame,
        index: Int,
        deltaSeconds: Double
    ): MotionVector? {
        if (
            deltaSeconds <= 0.0 ||
            !previousPose.hasValidLandmark(index) ||
            !currentPose.hasValidLandmark(index)
        ) {
            return null
        }
        val previous = previousPose.normalizedLandmarks.getOrNull(index) ?: return null
        val current = currentPose.normalizedLandmarks.getOrNull(index) ?: return null
        val dx = current.x - previous.x
        val dy = current.y - previous.y
        val distance = sqrt((dx * dx + dy * dy).toDouble())
        if (distance <= MOTION_LANDMARK_DEADZONE) return MotionVector(0.0, 0.0)

        val adjustedDistance = distance - MOTION_LANDMARK_DEADZONE
        val scale = adjustedDistance / distance
        return MotionVector(
            x = dx * scale / deltaSeconds,
            y = dy * scale / deltaSeconds
        )
    }

    private fun vectorMotionScore(expectedVelocity: MotionVector, playerVelocity: MotionVector?): Double {
        val expectedSpeed = expectedVelocity.speed
        val playerSpeed = playerVelocity?.speed ?: 0.0
        if (expectedSpeed <= 1e-8 || playerVelocity == null || playerSpeed <= MOTION_PLAYER_SPEED_EPSILON) {
            return 0.0
        }

        val magnitudeRatio = minOf(expectedSpeed, playerSpeed) / maxOf(expectedSpeed, playerSpeed)
        val magnitudeScore = sqrt(magnitudeRatio).coerceIn(0.0, 1.0)
        val cosine = (
            (expectedVelocity.x * playerVelocity.x + expectedVelocity.y * playerVelocity.y) /
                (expectedSpeed * playerSpeed)
            ).coerceIn(-1.0, 1.0)
        val directionScore = ((cosine + 1.0) / 2.0).coerceIn(0.0, 1.0)
        return (
            MOTION_DIRECTION_SCORE_WEIGHT * directionScore +
                MOTION_MAGNITUDE_SCORE_WEIGHT * magnitudeScore
            ).coerceIn(0.0, 1.0)
    }

    private fun scalarMotionScore(expectedVelocity: Double, playerVelocity: Double): Double {
        val expectedSpeed = abs(expectedVelocity)
        val playerSpeed = abs(playerVelocity)
        if (expectedSpeed <= 1e-8 || playerSpeed <= MOTION_SCALAR_SPEED_EPSILON) return 0.0

        val directionScore = if (expectedVelocity * playerVelocity > 0.0) 1.0 else 0.0
        val magnitudeRatio = minOf(expectedSpeed, playerSpeed) / maxOf(expectedSpeed, playerSpeed)
        val magnitudeScore = sqrt(magnitudeRatio).coerceIn(0.0, 1.0)
        val tolerance = maxOf(MOTION_MIN_SCALAR_SPEED_TOLERANCE, expectedSpeed * MOTION_SCALAR_SPEED_TOLERANCE_FACTOR)
        val velocityScore = (1.0 - (abs(expectedVelocity - playerVelocity) / tolerance)).coerceIn(0.0, 1.0)
        return (
            MOTION_DIRECTION_SCORE_WEIGHT * maxOf(directionScore, velocityScore) +
                MOTION_MAGNITUDE_SCORE_WEIGHT * magnitudeScore
            ).coerceIn(0.0, 1.0)
    }

    private fun signedAngularDifference(first: Double, second: Double): Double {
        var difference = (second - first) % 360.0
        if (difference > 180.0) difference -= 360.0
        if (difference < -180.0) difference += 360.0
        return difference
    }

    private fun connectionWeight(startIndex: Int, endIndex: Int): Double =
        when {
            startIndex in TORSO_LANDMARKS && endIndex in TORSO_LANDMARKS -> TORSO_CONNECTION_WEIGHT
            startIndex in PRIMARY_MOTION_LANDMARKS || endIndex in PRIMARY_MOTION_LANDMARKS -> PRIMARY_MOTION_CONNECTION_WEIGHT
            startIndex in HAND_DETAIL_LANDMARKS || endIndex in HAND_DETAIL_LANDMARKS -> DETAIL_CONNECTION_WEIGHT
            startIndex in FOOT_DETAIL_LANDMARKS || endIndex in FOOT_DETAIL_LANDMARKS -> DETAIL_CONNECTION_WEIGHT
            isFaceConnection(startIndex, endIndex) -> HEAD_CONNECTION_WEIGHT
            else -> DEFAULT_CONNECTION_WEIGHT
        }

    private fun isFaceConnection(startIndex: Int, endIndex: Int): Boolean =
        startIndex in FACE_LANDMARKS && endIndex in FACE_LANDMARKS

    private fun PoseFrame.lineAngle(firstIndex: Int, secondIndex: Int): Double? {
        val first = normalizedLandmarks.getOrNull(firstIndex) ?: return null
        val second = normalizedLandmarks.getOrNull(secondIndex) ?: return null
        return Math.toDegrees(
            atan2(
                (second.y - first.y).toDouble(),
                (second.x - first.x).toDouble()
            )
        )
    }

    private fun angularDifference(first: Double, second: Double): Double {
        val raw = abs(first - second) % 360.0
        return minOf(raw, 360.0 - raw)
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

    private fun landmarkWeight(index: Int): Double =
        when (index) {
            in FACE_LANDMARKS -> 0.0
            in HAND_DETAIL_LANDMARKS -> DETAIL_LANDMARK_WEIGHT
            in FOOT_DETAIL_LANDMARKS -> DETAIL_LANDMARK_WEIGHT
            in PRIMARY_MOTION_LANDMARKS -> PRIMARY_MOTION_LANDMARK_WEIGHT
            in TORSO_LANDMARKS -> TORSO_LANDMARK_WEIGHT
            else -> DEFAULT_LANDMARK_WEIGHT
        }

    private fun motionLandmarkWeight(index: Int): Double =
        when (index) {
            15, 16 -> WRIST_MOTION_WEIGHT
            13, 14 -> ELBOW_MOTION_WEIGHT
            27, 28, 29, 30, 31, 32 -> FOOT_MOTION_WEIGHT
            25, 26 -> KNEE_MOTION_WEIGHT
            11, 12, 23, 24 -> TORSO_MOTION_WEIGHT
            else -> DEFAULT_MOTION_LANDMARK_WEIGHT
        }

    private fun headFacingState(landmarks: List<PoseLandmark>): HeadFacingState? {
        if (landmarks.isEmpty()) return null

        val validFaceCount = FACE_LANDMARKS.count { landmarks.hasValidLandmark(it) }
        val hasUpperBody = landmarks.hasValidLandmark(11) && landmarks.hasValidLandmark(12)
        val nose = landmarks.getOrNull(0)
        val hasNose = nose != null && landmarks.hasValidLandmark(0)

        if (!hasNose && validFaceCount < MIN_FACE_LANDMARKS_FOR_ORIENTATION) {
            return if (hasUpperBody) HeadFacingState.FacingBackward else null
        }
        if (nose == null || !hasNose) return null

        val leftFaceX = landmarks.averageVisibleX(LEFT_FACE_SIDE_LANDMARKS)
        val rightFaceX = landmarks.averageVisibleX(RIGHT_FACE_SIDE_LANDMARKS)
        if (leftFaceX == null || rightFaceX == null) {
            return HeadFacingState.FacingForward
        }

        val faceWidth = abs(rightFaceX - leftFaceX).takeIf { it > 1e-4f } ?: return HeadFacingState.FacingForward
        val faceCenterX = (leftFaceX + rightFaceX) / 2f
        val offset = (nose.x - faceCenterX) / faceWidth
        return when {
            offset <= -HEAD_TURN_OFFSET_THRESHOLD -> HeadFacingState.FacingLeft
            offset >= HEAD_TURN_OFFSET_THRESHOLD -> HeadFacingState.FacingRight
            else -> HeadFacingState.FacingForward
        }
    }

    private fun List<PoseLandmark>.averageVisibleX(indices: IntArray): Float? {
        var sum = 0f
        var count = 0
        for (index in indices) {
            val landmark = getOrNull(index)
            if (landmark != null && hasValidLandmark(index)) {
                sum += landmark.x
                count += 1
            }
        }
        return if (count > 0) sum / count else null
    }

    private fun updateScoreWindow(result: FrameEvaluation): ScoreFeedback? {
        val score = result.overallSimilarity * 100.0
        allScores += score
        scoreWindow.addLast(result.timestampMs to score)
        feedbackCapWindow.addLast(
            result.timestampMs to feedbackPoints(result.participationFeedbackCap ?: ScoreFeedback.SS)
        )

        val minTimestamp = result.timestampMs - SLIDING_WINDOW_MS
        while (scoreWindow.isNotEmpty() && scoreWindow.first().first < minTimestamp) {
            scoreWindow.removeFirst()
        }
        while (feedbackCapWindow.isNotEmpty() && feedbackCapWindow.first().first < minTimestamp) {
            feedbackCapWindow.removeFirst()
        }

        if (result.timestampMs - lastFeedbackTimestampMs >= FEEDBACK_INTERVAL_MS) {
            currentFeedback = capFeedback(
                feedback = feedbackFor(currentScore()),
                maxFeedback = currentFeedbackCap()
            )
            feedbackHistory += currentFeedback
            lastFeedbackTimestampMs = result.timestampMs
            return currentFeedback
        }
        return null
    }

    private fun currentScore(): Double =
        if (scoreWindow.isEmpty()) 0.0 else scoreWindow.sumOf { it.second } / scoreWindow.size

    private fun currentFeedbackCap(): ScoreFeedback {
        if (feedbackCapWindow.isEmpty()) return ScoreFeedback.SS
        val averageCapPoints = feedbackCapWindow.sumOf { it.second } / feedbackCapWindow.size.toDouble()
        return when {
            averageCapPoints <= 1.45 -> ScoreFeedback.X
            averageCapPoints <= 2.35 -> ScoreFeedback.OK
            averageCapPoints <= 3.35 -> ScoreFeedback.BOM
            averageCapPoints <= 4.35 -> ScoreFeedback.OTIMO
            else -> ScoreFeedback.SS
        }
    }

    private fun capFeedback(feedback: ScoreFeedback, maxFeedback: ScoreFeedback): ScoreFeedback =
        if (feedbackPoints(feedback) <= feedbackPoints(maxFeedback)) feedback else maxFeedback

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
                        comparableLandmarkIndices = comparableLandmarkIndices(landmarks, normalizedLandmarks),
                        headFacing = headFacingState(landmarks)
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
            if (landmarks.hasValidLandmark(index) && landmarkWeight(index) > 0.0) {
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
                score >= 85.0 -> Rank.S
                score >= 80.0 -> Rank.A_PLUS
                score >= 75.0 -> Rank.A
                score >= 70.0 -> Rank.B
                score >= 65.0 -> Rank.C
                else -> Rank.F
            }

        private fun feedbackFor(score: Double): ScoreFeedback =
            when {
                score >= 80.0 -> ScoreFeedback.SS
                score >= 75.0 -> ScoreFeedback.OTIMO
                score >= 70.0 -> ScoreFeedback.BOM
                score >= 65.0 -> ScoreFeedback.OK
                else -> ScoreFeedback.X
            }

        private fun feedbackPoints(feedback: ScoreFeedback): Int =
            when (feedback) {
                ScoreFeedback.X -> 1
                ScoreFeedback.OK -> 2
                ScoreFeedback.BOM -> 3
                ScoreFeedback.OTIMO -> 4
                ScoreFeedback.SS -> 5
            }

        private const val TIMESTAMP_TOLERANCE_MS = 500.0
        private const val FEEDBACK_INTERVAL_MS = 5000.0
        private const val SLIDING_WINDOW_MS = 5000.0
        private const val LANDMARK_DISTANCE_THRESHOLD = 1.0
        private const val Z_DISTANCE_WEIGHT = 0.04
        private const val ABSOLUTE_LANDMARK_SUPPORT_WEIGHT = 0.25
        private const val SEGMENT_ORIENTATION_SUPPORT_WEIGHT = 0.75
        private const val SEGMENT_ORIENTATION_OVERLAY_WEIGHT = 0.8
        private const val ENDPOINT_OVERLAY_WEIGHT = 0.2
        private const val DETAIL_CONNECTION_WEIGHT = 0.35
        private const val DEFAULT_CONNECTION_WEIGHT = 0.8
        private const val HEAD_CONNECTION_WEIGHT = 0.55
        private const val TORSO_CONNECTION_WEIGHT = 1.15
        private const val PRIMARY_MOTION_CONNECTION_WEIGHT = 1.25
        private const val COMPARABLE_LANDMARK_VISIBILITY_THRESHOLD = 0.35f
        private const val MAX_POSE_LANDMARKS = 33
        private const val DETAIL_LANDMARK_WEIGHT = 0.12
        private const val DEFAULT_LANDMARK_WEIGHT = 0.85
        private const val TORSO_LANDMARK_WEIGHT = 0.95
        private const val PRIMARY_MOTION_LANDMARK_WEIGHT = 1.45
        private const val HEAD_ORIENTATION_WEIGHT = 0.55
        private const val HEAD_TURN_OFFSET_THRESHOLD = 0.18f
        private const val MIN_FACE_LANDMARKS_FOR_ORIENTATION = 2
        private const val SKELETON_NOT_COMPARED_SCORE = -1f
        private const val MAX_ANGLE_DIFFERENCE = 180.0
        private const val LANDMARK_SIMILARITY_WEIGHT = 0.3
        private const val ANGLE_SIMILARITY_WEIGHT = 0.7
        private const val MOTION_SIMILARITY_WEIGHT_WHEN_ACTIVE = 0.40
        private const val ANGLE_POSE_WEIGHT = 0.42
        private const val STRUCTURE_POSE_WEIGHT = 0.58
        private const val VECTOR_POSE_WEIGHT = 0.42
        private const val RELATION_POSE_WEIGHT = 0.42
        private const val GLOBAL_POSE_WEIGHT = 0.16
        private const val STATIC_FEATURE_WEIGHT_FLOOR = 0.42
        private const val FEATURE_ACTIVITY_WEIGHT_BOOST = 1.45
        private const val ACTIVE_FEATURE_IMPORTANCE_THRESHOLD = 0.35
        private const val MISSING_ACTIVE_FEATURE_SCORE = 0.35
        private const val FEATURE_ACTIVE_ANGLE_DEGREES = 55.0
        private const val FEATURE_ACTIVE_DISTANCE = 0.45
        private const val ANGLE_GOOD_ERROR_DEGREES = 10.0
        private const val ANGLE_BAD_ERROR_DEGREES = 48.0
        private const val VECTOR_GOOD_ERROR_DEGREES = 8.0
        private const val VECTOR_BAD_ERROR_DEGREES = 42.0
        private const val GLOBAL_GOOD_ERROR_DEGREES = 8.0
        private const val GLOBAL_BAD_ERROR_DEGREES = 38.0
        private const val RELATION_GOOD_DISTANCE = 0.18
        private const val RELATION_BAD_DISTANCE = 0.95
        private const val DISTANCE_GOOD_ERROR = 0.16
        private const val DISTANCE_BAD_ERROR = 0.80
        private const val HAND_ZONE_WEIGHT = 0.95
        private const val HAND_ZONE_VERTICAL_WEIGHT = 0.45
        private const val HAND_ZONE_CROSS_WEIGHT = 0.25
        private const val HAND_ZONE_SHOULDER_WEIGHT = 0.15
        private const val HAND_ZONE_HIP_WEIGHT = 0.15
        private const val HAND_ZONE_VERTICAL_GOOD_ERROR = 0.18
        private const val HAND_ZONE_VERTICAL_BAD_ERROR = 0.85
        private const val HAND_ZONE_ANCHOR_DISTANCE = 0.70
        private const val HAND_ZONE_ANCHOR_GOOD_DISTANCE = 0.48
        private const val HAND_ZONE_ANCHOR_BAD_DISTANCE = 1.35
        private const val HAND_ZONE_CROSS_MISMATCH_SCORE = 0.45
        private const val MIN_MOTION_INTERVAL_MS = 40.0
        private const val MAX_MOTION_INTERVAL_MS = 500.0
        private const val MOTION_LANDMARK_DEADZONE = 0.018
        private const val MOTION_LANDMARK_SPEED_SCALE = 0.62
        private const val MOTION_ANGLE_SPEED_SCALE = 95.0
        private const val MOTION_SEGMENT_ANGLE_SPEED_SCALE = 95.0
        private const val MOTION_FEATURE_ACTIVE_ENERGY_THRESHOLD = 0.045
        private const val MOTION_ACTIVE_ENERGY_THRESHOLD = 0.08
        private const val STATIONARY_PLAYER_ENERGY_RATIO = 0.25
        private const val STATIONARY_SCORE_CAP = 0.55
        private const val STATIONARY_PENALTY_CONSECUTIVE_FRAMES = 10
        private const val MOTION_PLAYER_SPEED_EPSILON = 0.05
        private const val MOTION_SCALAR_SPEED_EPSILON = 8.0
        private const val MOTION_MIN_SCALAR_SPEED_TOLERANCE = 35.0
        private const val MOTION_SCALAR_SPEED_TOLERANCE_FACTOR = 1.25
        private const val MOTION_MIN_FEATURE_WEIGHT = 0.2
        private const val MOTION_MAX_FEATURE_WEIGHT = 2.4
        private const val MOTION_DIRECTION_SCORE_WEIGHT = 0.72
        private const val MOTION_MAGNITUDE_SCORE_WEIGHT = 0.28
        private const val INTENT_WINDOW_MS = 2500.0
        private const val INTENT_SMOOTHING_ALPHA = 0.32
        private const val INTENT_LOW_ENTER_THRESHOLD = 0.32
        private const val INTENT_LOW_EXIT_THRESHOLD = 0.43
        private const val INTENT_HIGH_ENTER_THRESHOLD = 0.70
        private const val INTENT_HIGH_EXIT_THRESHOLD = 0.58
        private const val INTENT_LOW_TRACKING_CONFIDENCE = 0.45
        private const val INTENT_LOW_TRACKING_SCORE_FLOOR = 0.62
        private const val INTENT_LOW_TRACKING_WINDOW_WEIGHT = 0.35
        private const val INTENT_ACTIVE_WINDOW_WEIGHT_BOOST = 0.45
        private const val INTENT_POSE_LOW_SIMILARITY = 0.28
        private const val INTENT_POSE_HIGH_SIMILARITY = 0.70
        private const val INTENT_STRUCTURE_LOW_SIMILARITY = 0.25
        private const val INTENT_STRUCTURE_HIGH_SIMILARITY = 0.68
        private const val INTENT_MOTION_LOW_SIMILARITY = 0.22
        private const val INTENT_MOTION_HIGH_SIMILARITY = 0.66
        private const val INTENT_MAX_ENERGY_RATIO = 1.6
        private const val INTENT_LOW_PLAYER_ENERGY_RATIO = 0.35
        private const val INTENT_LOW_MOTION_SIMILARITY = 0.36
        private const val INTENT_ACTIVE_POSE_WEIGHT = 0.40
        private const val INTENT_ACTIVE_MOTION_WEIGHT = 0.50
        private const val INTENT_ACTIVE_STRUCTURE_WEIGHT = 0.10
        private const val INTENT_STATIC_POSE_WEIGHT = 0.70
        private const val INTENT_STATIC_STRUCTURE_WEIGHT = 0.30
        private const val INTENT_MOTION_DIRECTION_WEIGHT = 0.56
        private const val INTENT_MOTION_ENERGY_WEIGHT = 0.44
        private const val PARTICIPATION_REGION_ACTIVE_ENERGY_THRESHOLD = 0.08
        private const val PARTICIPATION_LOW_THRESHOLD = 0.20
        private const val PARTICIPATION_MEDIUM_THRESHOLD = 0.40
        private const val PARTICIPATION_GOOD_THRESHOLD = 0.60
        private const val PARTICIPATION_LOW_SCORE_CAP = 0.28
        private const val PARTICIPATION_MEDIUM_SCORE_CAP = 0.52
        private const val PARTICIPATION_GOOD_SCORE_CAP = 0.70
        private const val PARTICIPATION_LOW_REGION_THRESHOLD = 0.45
        private const val PARTICIPATION_STILL_ENERGY_RATIO = 0.22
        private const val PARTICIPATION_ENERGY_EXPONENT = 0.85
        private const val PARTICIPATION_ENERGY_WEIGHT = 0.75
        private const val PARTICIPATION_REGION_MOTION_SCORE_WEIGHT = 0.25
        private const val PARTICIPATION_MIN_REGION_WEIGHT = 0.15
        private const val PARTICIPATION_MAX_REGION_WEIGHT = 2.4
        private const val WRIST_MOTION_WEIGHT = 1.8
        private const val ELBOW_MOTION_WEIGHT = 1.5
        private const val FOOT_MOTION_WEIGHT = 1.15
        private const val KNEE_MOTION_WEIGHT = 1.05
        private const val TORSO_MOTION_WEIGHT = 0.55
        private const val DEFAULT_MOTION_LANDMARK_WEIGHT = 0.85
        private const val MAX_FEEDBACK_POINTS = 5
        private const val REALTIME_COMPARISON_REPORT_FILE_NAME = "realtime_comparison_debug_report.json"
        private val INTENT_SEVERE_CURVE = IntentCurve("severe", maxScore = 0.42, exponent = 1.35)
        private val INTENT_PARTIAL_CURVE = IntentCurve("partial", maxScore = 0.82, exponent = 1.05)
        private val INTENT_COMPLETE_CURVE = IntentCurve("complete", maxScore = 1.0, exponent = 0.78)
        private val INTENT_NO_COMPARISON_CURVE = IntentCurve("no_comparison", maxScore = 0.0, exponent = 1.0)
        private val EMPTY_INT_ARRAY = IntArray(0)
        private val FACE_LANDMARKS = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        private val LEFT_FACE_SIDE_LANDMARKS = intArrayOf(1, 2, 3, 7, 9)
        private val RIGHT_FACE_SIDE_LANDMARKS = intArrayOf(4, 5, 6, 8, 10)
        private val HAND_DETAIL_LANDMARKS = intArrayOf(17, 18, 19, 20, 21, 22)
        private val FOOT_DETAIL_LANDMARKS = intArrayOf(29, 30, 31, 32)
        private val TORSO_LANDMARKS = intArrayOf(11, 12, 23, 24)
        private val PRIMARY_MOTION_LANDMARKS = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
        private val MOTION_LANDMARKS = intArrayOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)
        private val GLOBAL_ORIENTATION_SPECS = arrayOf(
            MotionSegmentSpec("shoulders", 11, 12, weight = 0.75),
            MotionSegmentSpec("hips", 23, 24, weight = 0.65),
            MotionSegmentSpec("left_torso", 23, 11, weight = 0.80),
            MotionSegmentSpec("right_torso", 24, 12, weight = 0.80)
        )
        private val MOTION_SEGMENTS = arrayOf(
            MotionSegmentSpec("left_upper_arm", 11, 13, weight = 1.45),
            MotionSegmentSpec("left_forearm", 13, 15, weight = 1.65),
            MotionSegmentSpec("right_upper_arm", 12, 14, weight = 1.45),
            MotionSegmentSpec("right_forearm", 14, 16, weight = 1.65),
            MotionSegmentSpec("left_thigh", 23, 25, weight = 1.0),
            MotionSegmentSpec("left_shin", 25, 27, weight = 1.05),
            MotionSegmentSpec("right_thigh", 24, 26, weight = 1.0),
            MotionSegmentSpec("right_shin", 26, 28, weight = 1.05),
            MotionSegmentSpec("shoulder_line", 11, 12, weight = 0.7),
            MotionSegmentSpec("hip_line", 23, 24, weight = 0.65),
            MotionSegmentSpec("left_torso", 11, 23, weight = 0.75),
            MotionSegmentSpec("right_torso", 12, 24, weight = 0.75)
        )
        private val RELATIVE_VECTOR_SPECS = arrayOf(
            RelativeVectorSpec("left_hand_to_left_shoulder", 11, 15, weight = 1.55),
            RelativeVectorSpec("right_hand_to_right_shoulder", 12, 16, weight = 1.55),
            RelativeVectorSpec("left_hand_to_left_hip", 23, 15, weight = 1.35),
            RelativeVectorSpec("right_hand_to_right_hip", 24, 16, weight = 1.35),
            RelativeVectorSpec("left_elbow_to_left_shoulder", 11, 13, weight = 1.15),
            RelativeVectorSpec("right_elbow_to_right_shoulder", 12, 14, weight = 1.15),
            RelativeVectorSpec("left_foot_to_left_hip", 23, 27, weight = 0.85),
            RelativeVectorSpec("right_foot_to_right_hip", 24, 28, weight = 0.85)
        )
        private val DISTANCE_SPECS = arrayOf(
            DistanceSpec("hands_distance", 15, 16, weight = 1.25),
            DistanceSpec("feet_distance", 27, 28, weight = 0.75),
            DistanceSpec("left_hand_to_right_shoulder", 12, 15, weight = 1.00),
            DistanceSpec("right_hand_to_left_shoulder", 11, 16, weight = 1.00)
        )
        private val ANGLE_SPECS = arrayOf(
            AngleSpec("left_shoulder", intArrayOf(13, 11, 23), weight = 1.35),
            AngleSpec("right_shoulder", intArrayOf(14, 12, 24), weight = 1.35),
            AngleSpec("left_elbow", intArrayOf(11, 13, 15), weight = 1.45),
            AngleSpec("right_elbow", intArrayOf(12, 14, 16), weight = 1.45),
            AngleSpec("left_hip", intArrayOf(11, 23, 25), weight = 1.25),
            AngleSpec("right_hip", intArrayOf(12, 24, 26), weight = 1.25),
            AngleSpec("left_knee", intArrayOf(23, 25, 27), weight = 1.35),
            AngleSpec("right_knee", intArrayOf(24, 26, 28), weight = 1.35),
            AngleSpec("torso", intArrayOf(11, 12, 23, 24), weight = 1.2),
            AngleSpec("shoulder_line", intArrayOf(11, 12), weight = 0.9),
            AngleSpec("hip_line", intArrayOf(23, 24), weight = 0.9)
        )
    }
}
