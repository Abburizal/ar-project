package com.example.arpackagevalidator.ui.viewmodel
import com.example.arpackagevalidator.ui.MeasurementMode


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.arpackagevalidator.data.MeasurementData
import com.google.ar.sceneform.math.Vector3
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MeasurementViewModel : ViewModel() {

    private val _uiState = MutableLiveData(MeasurementUiState())
    val uiState: LiveData<MeasurementUiState> = _uiState

    private val _userMessage = MutableLiveData("")
    val userMessage: LiveData<String> = _userMessage

    private val _calibrationFactor = MutableLiveData(1.0f)
    val calibrationFactor: LiveData<Float> = _calibrationFactor

    private val _measurementHistory = MutableLiveData<List<MeasurementData>>(emptyList())
    val measurementHistory: LiveData<List<MeasurementData>> = _measurementHistory

    fun onModeChanged(newMode: MeasurementMode) {
        _uiState.value = MeasurementUiState(mode = newMode)
        _userMessage.value = "Mode diubah ke ${if (newMode == MeasurementMode.BOX) "Kotak" else "Bebas"}"
    }

    fun startMeasurement() {
        _uiState.value = _uiState.value?.copy(isPlacing = true)
        _userMessage.value = "Tempatkan titik pertama."
    }

    fun onArTap(point: Vector3) {
        val currentState = _uiState.value ?: return
        if (!currentState.isPlacing) return
        val newPoints = currentState.points + point
        updateStateWithNewPoints(newPoints)
    }

    fun undo() {
        val currentState = _uiState.value ?: return
        if (currentState.points.isEmpty()) return
        val newPoints = currentState.points.dropLast(1)
        updateStateWithNewPoints(newPoints)
        _userMessage.value = "Titik terakhir dibatalkan."
    }

    fun reset() {
        _uiState.value = MeasurementUiState(mode = _uiState.value?.mode ?: MeasurementMode.BOX)
        _userMessage.value = "Pengukuran direset."
    }

    fun startCalibration(knownLength: Float) {
        _uiState.value = MeasurementUiState(
            isPlacing = true,
            isCalibrating = true,
            calibrationKnownLength = knownLength
        )
        _userMessage.value = "Kalibrasi: Tempatkan 2 titik pada objek referensi."
    }

    // --- FUNGSI YANG DITAMBAHKAN ---
    fun saveCurrentMeasurement(): MeasurementData? {
        val currentState = uiState.value
        if (currentState == null || !currentState.isSaveEnabled) {
            return null
        }

        val data = MeasurementData(
            timestamp = System.currentTimeMillis(),
            length = currentState.length,
            width = currentState.width,
            height = currentState.height,
            volume = (currentState.length / 100f) * (currentState.width / 100f) * (currentState.height / 100f),
            classification = currentState.classification
        )
        addMeasurement(data)
        return data
    }
    // ----------------------------------

    fun onUserMessageShown() { _userMessage.value = "" }

    private fun updateStateWithNewPoints(newPoints: List<Vector3>) {
        val currentState = _uiState.value ?: return
        val calFactor = _calibrationFactor.value ?: 1.0f

        if (currentState.isCalibrating && newPoints.size == 2) {
            val measuredLength = calculateDistance(newPoints[0], newPoints[1]) * 100f
            if (measuredLength > 0) {
                val newFactor = currentState.calibrationKnownLength / measuredLength
                _calibrationFactor.value = newFactor
                _userMessage.value = "Kalibrasi selesai! Faktor baru: ${"%.3f".format(newFactor)}"
            } else {
                _userMessage.value = "Error Kalibrasi: Jarak terukur tidak valid."
            }
            reset()
            return
        }

        var length = 0f; var width = 0f; var height = 0f
        var classification = ""; var isPlacing = currentState.isPlacing; var isSaveEnabled = false

        if (currentState.mode == MeasurementMode.BOX) {
            if (newPoints.size >= 2) length = calculateDistance(newPoints[0], newPoints[1]) * 100f * calFactor
            if (newPoints.size >= 3) width = calculateDistance(newPoints[1], newPoints[2]) * 100f * calFactor
            if (newPoints.size >= 4) {
                height = abs(newPoints[3].y - newPoints[2].y) * 100f * calFactor
                classification = classifyPackage(length, width, height)
                isPlacing = false
                isSaveEnabled = true
                _userMessage.value = "Pengukuran kotak selesai!"
            }
        }

        _uiState.value = currentState.copy(
            points = newPoints, length = length, width = width, height = height,
            classification = classification, isPlacing = isPlacing,
            isUndoEnabled = newPoints.isNotEmpty(), isSaveEnabled = isSaveEnabled
        )
    }
    
    // --- FUNGSI PENDUKUNG YANG DITAMBAHKAN ---
    private fun addMeasurement(data: MeasurementData) {
        val currentList = _measurementHistory.value?.toMutableList() ?: mutableListOf()
        currentList.add(0, data)
        _measurementHistory.value = currentList
    }
    // -----------------------------------------

    private fun calculateDistance(p1: Vector3, p2: Vector3) =
        sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2) + (p1.z - p2.z).pow(2))

    private fun classifyPackage(l: Float, w: Float, h: Float) = when {
        l <= 30 && w <= 20 && h <= 10 -> "SMALL"
        l <= 50 && w <= 40 && h <= 30 -> "MEDIUM"
        l <= 100 && w <= 80 && h <= 60 -> "LARGE"
        else -> "CUSTOM"
    }
}