package com.sintonia.sincronia.processing

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MovesetMetadata(
    val video: String,
    val fps: Double,
    val frameCount: Int,
    val width: Int,
    val height: Int,
    val duration: Double,
    val landmarkCount: Int = 33
)

data class MovesetFrame(
    val frame: Int,
    val timestamp: Double,
    val poseDetected: Boolean,
    val landmarks: List<PoseLandmark>,
    val normalizedLandmarks: List<PoseLandmark>,
    val jointAngles: Map<String, Double>
)

class MovesetJsonWriter {
    fun write(outputFile: File, metadata: MovesetMetadata, frames: List<MovesetFrame>) {
        val root = JSONObject()
            .put("schema_version", 1)
            .put(
                "metadata",
                JSONObject()
                    .put("video", metadata.video)
                    .put("fps", metadata.fps)
                    .put("frame_count", metadata.frameCount)
                    .put("width", metadata.width)
                    .put("height", metadata.height)
                    .put("duration", metadata.duration)
                    .put("landmark_count", metadata.landmarkCount)
            )
            .put("frames", JSONArray().apply {
                frames.forEach { frame ->
                    put(
                        JSONObject()
                            .put("frame", frame.frame)
                            .put("timestamp", frame.timestamp)
                            .put("pose_detected", frame.poseDetected)
                            .put("landmarks", frame.landmarks.toJsonArray())
                            .put("normalized_landmarks", frame.normalizedLandmarks.toJsonArray())
                            .put("joint_angles", JSONObject().apply {
                                frame.jointAngles.forEach { (key, value) -> put(key, value) }
                            })
                    )
                }
            })

        outputFile.writeText(root.toString(), Charsets.UTF_8)
    }

    private fun List<PoseLandmark>.toJsonArray(): JSONArray = JSONArray().also { array ->
        forEach { landmark ->
            array.put(
                JSONArray()
                    .put(landmark.x.toDouble())
                    .put(landmark.y.toDouble())
                    .put(landmark.z.toDouble())
                    .put(landmark.visibility.toDouble())
            )
        }
    }
}

