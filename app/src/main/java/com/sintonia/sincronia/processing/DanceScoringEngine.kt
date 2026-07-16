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
        val evaluation = if (candidates.isEmpty()) {
            FrameEvaluation(playbackTimestampMs, 0.0, 0.0, 0.0, 0.0, currentFeedback)
        } else {
            candidates
                .asSequence()
                .map { reference -> compare(reference, realtimePose) }
                .maxByOrNull { it.overallSimilarity }
                ?: FrameEvaluation(playbackTimestampMs, 0.0, 0.0, 0.0, 0.0, currentFeedback)
        }
        val emittedFeedback = updateScoreWindow(evaluation)
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
        return DanceResult(
            danceName = danceName,
            rank = rankFor(finalScore),
            successPercentage = finalScore.roundToInt().coerceIn(0, 100)
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
