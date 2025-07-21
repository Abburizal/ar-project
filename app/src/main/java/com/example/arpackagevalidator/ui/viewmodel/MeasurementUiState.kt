package com.example.arpackagevalidator.ui.viewmodel

import android.graphics.Color
import com.example.arpackagevalidator.ui.MeasurementMode
import com.google.ar.sceneform.math.Vector3

data class MeasurementUiState(
    val mode: MeasurementMode = MeasurementMode.BOX,
    val isPlacing: Boolean = false,
    val points: List<Vector3> = emptyList(),
    val length: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val classification: String = "",
    val isUndoEnabled: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val isCalibrating: Boolean = false,
    val calibrationKnownLength: Float = 0f
) {
    fun getClassificationColor(): Int {
        return when (classification) {
            "SMALL" -> Color.parseColor("#4CAF50")
            "MEDIUM" -> Color.parseColor("#2196F3")
            "LARGE" -> Color.parseColor("#9C27B0")
            "CUSTOM" -> Color.parseColor("#F44336")
            else -> Color.BLACK
        }
    }
}