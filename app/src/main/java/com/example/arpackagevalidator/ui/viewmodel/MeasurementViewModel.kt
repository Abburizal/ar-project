package com.example.arpackagevalidator.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.arpackagevalidator.data.MeasurementData
import com.example.arpackagevalidator.ui.MeasurementMode
import com.google.ar.sceneform.math.Vector3
import java.util.Date
import kotlin.math.abs
import kotlin.math.sqrt

// Move BoxMeasurementStep enum outside the class so it can be accessed by other files
enum class BoxMeasurementStep {
    SET_ORIGIN,
    SET_LENGTH,
    SET_WIDTH,
    SET_HEIGHT,
    DONE
}

class MeasurementViewModel : ViewModel() {

    private val _uiState = MutableLiveData<MeasurementUiState>()
    val uiState: LiveData<MeasurementUiState> = _uiState

    private var calibrationFactor = 1.0f
    private var currentPoints = mutableListOf<Vector3>()
    private var knownCalibrationDistance = 0f

    init {
        _uiState.value = MeasurementUiState()
    }

    fun onModeChanged(mode: MeasurementMode) {
        reset()
        _uiState.value = _uiState.value?.copy(mode = mode)
    }

    fun onArTap(position: Vector3) {
        val state = _uiState.value ?: return
        if (!state.isPlacing) return

        currentPoints.add(position)

        if (state.isCalibrating) {
            handleCalibrationStep()
        } else {
            // Only handle BOX mode since FREE mode is removed
            handleBoxMeasurement()
        }
    }

    private fun handleCalibrationStep() {
        if (currentPoints.size >= 2) {
            val measuredDistance = calculateDistance(currentPoints[0], currentPoints[1])
            if (measuredDistance > 0.001f && knownCalibrationDistance > 0) {
                this.calibrationFactor = knownCalibrationDistance / (measuredDistance * 100f)
                val message = "Kalibrasi berhasil! Faktor baru: %.3f".format(this.calibrationFactor)
                _uiState.value = _uiState.value?.copy(
                    isCalibrating = false,
                    isPlacing = false,
                    userMessage = message,
                    points = currentPoints.toList()
                )
            } else {
                reset()
                _uiState.value = _uiState.value?.copy(userMessage = "Error kalibrasi. Jarak tidak valid.")
            }
        } else {
            _uiState.value = _uiState.value?.copy(userMessage = "Tap titik kedua untuk menyelesaikan kalibrasi.")
        }
    }

    private fun handleBoxMeasurement() {
        val state = _uiState.value ?: return
        var length = state.length
        var width = state.width
        var height = state.height
        var classification = state.classification
        var nextStep = state.boxStep

        when (state.boxStep) {
            BoxMeasurementStep.SET_ORIGIN -> nextStep = BoxMeasurementStep.SET_LENGTH
            BoxMeasurementStep.SET_LENGTH -> {
                length = calculateDistance(currentPoints[0], currentPoints[1]) * 100f * this.calibrationFactor
                nextStep = BoxMeasurementStep.SET_WIDTH
            }
            BoxMeasurementStep.SET_WIDTH -> {
                width = calculateDistance(currentPoints[0], currentPoints[2]) * 100f * this.calibrationFactor
                nextStep = BoxMeasurementStep.SET_HEIGHT
            }
            BoxMeasurementStep.SET_HEIGHT -> {
                height = abs(currentPoints[3].y - currentPoints[0].y) * 100f * this.calibrationFactor
                classification = classifyPackage(length, width, height)
                nextStep = BoxMeasurementStep.DONE
            }
            BoxMeasurementStep.DONE -> { /* Do nothing */ }
        }

        _uiState.value = state.copy(
            points = currentPoints.toList(),
            length = length,
            width = width,
            height = height,
            classification = classification,
            boxStep = nextStep,
            isPlacing = nextStep != BoxMeasurementStep.DONE,
            isUndoEnabled = currentPoints.isNotEmpty(),
            isSaveEnabled = nextStep == BoxMeasurementStep.DONE,
            userMessage = generateUserMessage(nextStep, state.mode)
        )
    }

    fun startMeasurement() {
        val state = _uiState.value ?: return
        if (state.isPlacing) {
            reset()
        } else {
            reset()
            _uiState.value = _uiState.value?.copy(
                isPlacing = true,
                userMessage = generateUserMessage(BoxMeasurementStep.SET_ORIGIN, state.mode)
            )
        }
    }

    fun startCalibration(knownDistanceCm: Float) {
        reset()
        this.knownCalibrationDistance = knownDistanceCm
        _uiState.value = _uiState.value?.copy(
            isCalibrating = true,
            isPlacing = true,
            userMessage = "Tap dua titik yang berjarak %.1f cm".format(knownDistanceCm)
        )
    }

    fun reset() {
        currentPoints.clear()
        _uiState.value = _uiState.value?.copy(
            isPlacing = false,
            isCalibrating = false,
            points = emptyList(),
            length = 0f,
            width = 0f,
            height = 0f,
            classification = "",
            isUndoEnabled = false,
            isSaveEnabled = false,
            boxStep = BoxMeasurementStep.SET_ORIGIN,
            userMessage = "Pengukuran direset. Tekan 'Mulai Ukur'."
        )
    }

    fun undo() {
        if (currentPoints.isNotEmpty()) {
            val state = _uiState.value ?: return
            currentPoints.removeAt(currentPoints.lastIndex)

            val prevStep = when (state.boxStep) {
                BoxMeasurementStep.DONE -> BoxMeasurementStep.SET_HEIGHT
                BoxMeasurementStep.SET_HEIGHT -> BoxMeasurementStep.SET_WIDTH
                BoxMeasurementStep.SET_WIDTH -> BoxMeasurementStep.SET_LENGTH
                BoxMeasurementStep.SET_LENGTH -> BoxMeasurementStep.SET_ORIGIN
                BoxMeasurementStep.SET_ORIGIN -> BoxMeasurementStep.SET_ORIGIN
            }

            _uiState.value = state.copy(
                points = currentPoints.toList(),
                boxStep = prevStep,
                isUndoEnabled = currentPoints.isNotEmpty(),
                isSaveEnabled = false,
                userMessage = "Titik terakhir dihapus"
            )
        }
    }

    fun saveCurrentMeasurement(): MeasurementData? {
        val state = uiState.value ?: return null
        if (!state.isSaveEnabled) return null

        return MeasurementData(
            id = System.currentTimeMillis(),
            timestamp = Date(),
            mode = state.mode.name,
            length = state.length,
            width = state.width,
            height = state.height,
            volume = calculateVolume(state.length, state.width, state.height),
            classification = state.classification,
            points = state.points.map { vector -> listOf(vector.x, vector.y, vector.z) }
        )
    }

    private fun calculateVolume(length: Float, width: Float, height: Float): Float {
        return length * width * height
    }

    private fun generateUserMessage(step: BoxMeasurementStep, mode: MeasurementMode): String {
        return when (step) {
            BoxMeasurementStep.SET_ORIGIN -> "Tap untuk menentukan titik AWAL kotak."
            BoxMeasurementStep.SET_LENGTH -> "Tap untuk menentukan PANJANG."
            BoxMeasurementStep.SET_WIDTH -> "Tap untuk menentukan LEBAR."
            BoxMeasurementStep.SET_HEIGHT -> "Tap untuk menentukan TINGGI."
            BoxMeasurementStep.DONE -> "Pengukuran kotak selesai!"
        }
    }

    private fun calculateDistance(p1: Vector3, p2: Vector3): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        return sqrt((dx * dx) + (dy * dy) + (dz * dz)).toFloat()
    }

    private fun classifyPackage(length: Float, width: Float, height: Float): String {
        val maxDimension = maxOf(length, width, height)
        val volumeCm3 = length * width * height
        return when {
            maxDimension <= 20 && volumeCm3 <= 5000 -> "SMALL"
            maxDimension <= 40 && volumeCm3 <= 30000 -> "MEDIUM"
            maxDimension <= 60 && volumeCm3 <= 100000 -> "LARGE"
            else -> "EXTRA LARGE"
        }
    }
}
