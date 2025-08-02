package com.example.arpackagevalidator.ui.viewmodel // Sesuaikan package Anda

import com.example.arpackagevalidator.ui.MeasurementMode
import com.google.ar.sceneform.math.Vector3

data class MeasurementUiState(
    val mode: MeasurementMode = MeasurementMode.BOX,
    val isPlacing: Boolean = false,
    val isCalibrating: Boolean = false,
    val points: List<Vector3> = emptyList(),
    val length: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f,
    val classification: String = "",
    val isUndoEnabled: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val userMessage: String = "Selamat datang! Tekan 'Mulai Ukur'.",
    val boxStep: BoxMeasurementStep = BoxMeasurementStep.SET_ORIGIN
)