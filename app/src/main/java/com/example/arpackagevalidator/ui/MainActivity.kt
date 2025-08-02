package com.example.arpackagevalidator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.arpackagevalidator.R
import com.example.arpackagevalidator.data.MeasurementData
import com.example.arpackagevalidator.databinding.ActivityMainBinding
import com.example.arpackagevalidator.ui.dialog.DialogHelper
import com.example.arpackagevalidator.ui.viewmodel.MeasurementUiState
import com.example.arpackagevalidator.ui.viewmodel.MeasurementViewModel
import com.example.arpackagevalidator.utils.ArInteractionManager
import com.example.arpackagevalidator.utils.FileExporter
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ux.ArFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MeasurementViewModel by viewModels()

    private var arInteractionManager: ArInteractionManager? = null
    private var dialogHelper: DialogHelper? = null
    private var fileExporter: FileExporter? = null
    private var arFragment: ArFragment? = null

    private var installRequested = false
    private var permissionRequestInProgress = false
    private var isArComponentsInitialized = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 101
        private const val TAG = "MainActivity"
        private const val AR_INIT_DELAY = 100L
        private const val AR_INIT_RETRY_DELAY = 200L
    }

    private val toggleModeListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && checkedId == R.id.btn_mode_box) {
                if (viewModel.uiState.value?.mode != MeasurementMode.BOX) {
                    viewModel.onModeChanged(MeasurementMode.BOX)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        if (!checkARCoreAvailability()) {
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeCoreHelpers()
        setupClickListeners()
        setupObservers()

        Handler(Looper.getMainLooper()).postDelayed({ checkAndRequestPermissions() }, 50)
        Handler(Looper.getMainLooper()).postDelayed({ initializeArRelatedComponentsSafe() }, AR_INIT_DELAY)
        Log.d(TAG, "onCreate completed")
    }

    override fun onResume() {
        super.onResume()
        if (!hasAllRequiredPermissions()) {
            checkAndRequestPermissions()
        }
    }

    private fun initializeCoreHelpers() {
        dialogHelper = DialogHelper(this, layoutInflater)
        fileExporter = FileExporter(this)
    }

    private fun initializeArRelatedComponentsSafe() {
        if (isArComponentsInitialized || !hasAllRequiredPermissions()) return

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as? ArFragment
        if (arFragment == null) {
            Log.e(TAG, "ArFragment is null")
            dialogHelper?.showMessage("Gagal memuat AR Fragment.")
            return
        }
        checkArSceneViewReady(arFragment!!, 0)
    }

    private fun checkArSceneViewReady(arFragment: ArFragment, attempt: Int) {
        if (attempt >= 20) {
            Log.e(TAG, "ArSceneView not ready after multiple attempts")
            dialogHelper?.showMessage("AR tidak dapat dimuat.")
            return
        }

        if (arFragment.arSceneView?.scene == null) {
            Handler(Looper.getMainLooper()).postDelayed({ checkArSceneViewReady(arFragment, attempt + 1) }, AR_INIT_RETRY_DELAY)
            return
        }

        arInteractionManager = ArInteractionManager(this, arFragment)
        setupArInteraction(arFragment)
        isArComponentsInitialized = true
        Log.d(TAG, "AR components initialized successfully")
    }

    private fun setupArInteraction(arFragment: ArFragment) {
        arFragment.setOnTapArPlaneListener { hitResult, plane, _ ->
            if (viewModel.uiState.value?.isPlacing != true) return@setOnTapArPlaneListener

            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING || plane.trackingState != TrackingState.TRACKING) {
                dialogHelper?.showMessage("Arahkan ke permukaan horizontal yang stabil.")
                return@setOnTapArPlaneListener
            }

            arInteractionManager?.placeAnchorFromHit(hitResult)?.let { anchorNode ->
                viewModel.onArTap(anchorNode.worldPosition)
            } ?: dialogHelper?.showMessage("Gagal menempatkan titik. Coba lagi.")
        }

        arFragment.arSceneView.scene.addOnUpdateListener {
            updateTrackingStatus()
        }
    }

    private fun updateTrackingStatus() {
        val frame = arFragment?.arSceneView?.arFrame ?: return
        // PERBAIKAN: Menggunakan properti tvStatus yang sudah diinisialisasi
        if (frame.camera.trackingState == TrackingState.TRACKING) {
            if (frame.getUpdatedTrackables(Plane::class.java).isNotEmpty()) {
                binding.tvStatus.visibility = View.GONE
            } else {
                binding.tvStatus.text = "Arahkan kamera ke permukaan bertekstur..."
                binding.tvStatus.visibility = View.VISIBLE
            }
        } else {
            binding.tvStatus.text = "Gerakkan perangkat perlahan..."
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            btnMeasure.setOnClickListener { viewModel.startMeasurement() }
            btnReset.setOnClickListener {
                viewModel.reset()
                arInteractionManager?.reset()
            }
            btnUndo.setOnClickListener { viewModel.undo() }
            btnSave.setOnClickListener { saveMeasurement() }
            btnCalibrate.setOnClickListener { showCalibrationDialog() }
            btnHistory.setOnClickListener { dialogHelper?.showMessage("Fitur history akan segera tersedia.") }
            toggleMode.addOnButtonCheckedListener(toggleModeListener)
        }
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            state?.let { updateUI(it) }
        }
    }

    private fun updateUI(state: MeasurementUiState) {
        binding.apply {
            btnMeasure.text = if (state.isPlacing) "Stop" else "Mulai Ukur"
            btnReset.isEnabled = !state.isPlacing
            btnUndo.isEnabled = state.isUndoEnabled
            btnSave.isEnabled = state.isSaveEnabled

            tvMeasurement.text = state.userMessage
            tvMeasurement.visibility = View.VISIBLE

            if (state.classification.isNotEmpty()) {
                tvClassification.text = "Klasifikasi: ${state.classification}"
                tvClassification.setTextColor(getClassificationColor(state.classification))
                tvClassification.visibility = View.VISIBLE
            } else {
                tvClassification.visibility = View.GONE
            }

            toggleMode.removeOnButtonCheckedListener(toggleModeListener)
            toggleMode.check(R.id.btn_mode_box)
            toggleMode.addOnButtonCheckedListener(toggleModeListener)
        }
        arInteractionManager?.drawState(state)
    }

    private fun getClassificationColor(classification: String): Int {
        return when (classification) {
            "SMALL" -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            "MEDIUM" -> ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            "LARGE" -> ContextCompat.getColor(this, android.R.color.holo_purple)
            "EXTRA LARGE" -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
            else -> ContextCompat.getColor(this, android.R.color.black)
        }
    }

    private fun saveMeasurement() {
        val measurementData = viewModel.saveCurrentMeasurement()
        if (measurementData == null) {
            dialogHelper?.showMessage("Tidak ada data untuk disimpan.")
            return
        }

        dialogHelper?.showExportDialog(
            onCsvSelected = { exportToFile(measurementData, "CSV") },
            onJsonSelected = { exportToFile(measurementData, "JSON") }
        )
    }

    private fun exportToFile(data: MeasurementData, format: String) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                if (format == "CSV") fileExporter?.exportToCSV(listOf(data)) != null
                else fileExporter?.exportToJSON(listOf(data)) != null
            }
            val message = if (success) "Data berhasil disimpan ke $format" else "Gagal menyimpan data ke $format"
            dialogHelper?.showMessage(message)
        }
    }

    private fun showCalibrationDialog() {
        dialogHelper?.showCalibrationInputDialog { knownDistance ->
            if (knownDistance > 0) {
                viewModel.startCalibration(knownDistance)
            } else {
                dialogHelper?.showMessage("Jarak harus lebih dari 0.")
            }
        }
    }

    private fun checkARCoreAvailability(): Boolean {
        return when (val availability = ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                if (!installRequested) {
                    ArCoreApk.getInstance().requestInstall(this, true).also { installRequested = true }
                }
                false
            }
            else -> {
                Log.e(TAG, "ARCore availability: $availability")
                dialogHelper?.showMessage("ARCore tidak didukung pada device ini.")
                finish()
                false
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (permissionRequestInProgress) return

        val missingPermissions = getMissingPermissions()
        if (missingPermissions.isNotEmpty()) {
            permissionRequestInProgress = true
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    private fun hasAllRequiredPermissions(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    private fun getMissingPermissions(): List<String> {
        val requiredPermissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionRequestInProgress = false

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (!isArComponentsInitialized) {
                    initializeArRelatedComponentsSafe()
                }
            } else {
                dialogHelper?.showMessage("Izin kamera dan penyimpanan diperlukan untuk AR.")
            }
        }
    }
}
