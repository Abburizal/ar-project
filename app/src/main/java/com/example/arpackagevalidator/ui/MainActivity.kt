package com.example.arpackagevalidator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.arpackagevalidator.R
import com.example.arpackagevalidator.databinding.ActivityMainBinding
import com.example.arpackagevalidator.ui.viewmodel.MeasurementUiState
import com.example.arpackagevalidator.ui.viewmodel.MeasurementViewModel
import com.example.arpackagevalidator.util.ArInteractionManager
import com.example.arpackagevalidator.util.DialogHelper
import com.example.arpackagevalidator.util.FileExporter
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Plane
import com.google.ar.sceneform.ux.ArFragment
import com.google.android.material.button.MaterialButtonToggleGroup

enum class MeasurementMode { BOX, FREE }

/**
 * MainActivity bertindak sebagai 'View' dalam arsitektur MVVM.
 * Tugas utamanya hanya meneruskan event dan menampilkan state dari ViewModel.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MeasurementViewModel by viewModels()

    private lateinit var arInteractionManager: ArInteractionManager
    private lateinit var dialogHelper: DialogHelper
    private lateinit var fileExporter: FileExporter

    private var installRequested = false

    private val toggleModeListener =
        MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val newMode = if (checkedId == R.id.btn_mode_free) MeasurementMode.FREE else MeasurementMode.BOX
                if (viewModel.uiState.value?.mode != newMode) {
                    viewModel.onModeChanged(newMode)
                }
            }
        }

    // --- Lifecycle Methods ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkARCoreAvailability()) {
            return
        }
        checkAndRequestPermissions()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeCoreHelpers()
        initializeArRelatedComponents()

        setupClickListeners()
        setupObservers()
    }

    // --- Setup Methods ---

    private fun initializeCoreHelpers() {
        dialogHelper = DialogHelper(this, layoutInflater)
        fileExporter = FileExporter(this)
    }

    private fun initializeArRelatedComponents() {
        try {
            val arFragment = arFragment()
            arInteractionManager = ArInteractionManager(this, arFragment)
            setupArInteraction(arFragment)
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal menginisialisasi komponen AR: ${e.message}", e)
            dialogHelper.showMessage("Gagal memuat AR. Aplikasi akan ditutup.")
            finish()
        }
    }

    private fun setupArInteraction(arFragment: ArFragment) {
        arFragment.setOnTapArPlaneListener { hitResult, plane, _ ->
            if (viewModel.uiState.value?.isPlacing == true && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                val anchorNode = arInteractionManager.placeAnchorFromHit(hitResult)
                // PERBAIKAN UTAMA: Pastikan onArTap hanya dipanggil SATU KALI.
                viewModel.onArTap(anchorNode.worldPosition)
            }
        }
    }

    private fun setupClickListeners() {
        with(binding) {
            btnMeasure.setOnClickListener { viewModel.startMeasurement() }
            btnReset.setOnClickListener { viewModel.reset() }
            btnUndo.setOnClickListener { viewModel.undo() }
            btnSave.setOnClickListener { saveAndShowExportDialog() }
            btnHistory.setOnClickListener { showHistoryDialog() }

            btnCalibrate.setOnClickListener {
                dialogHelper.showCalibrationDialog { knownLength ->
                    viewModel.startCalibration(knownLength)
                }
            }

            toggleMode.removeOnButtonCheckedListener(toggleModeListener)
            toggleMode.addOnButtonCheckedListener(toggleModeListener)
        }
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            if (state == null) return@observe
            updateUiFromState(state)
            if (::arInteractionManager.isInitialized) {
                arInteractionManager.drawState(state)
            }
        }
        
        viewModel.calibrationFactor.observe(this) { factor ->
            binding.tvCalibrationFactor.text = "Faktor Kalibrasi: ${"%.3f".format(factor ?: 1.0f)}"
        }

        viewModel.userMessage.observe(this) { message ->
            if (message.isNotBlank()) {
                dialogHelper.showMessage(message)
                viewModel.onUserMessageShown()
            }
        }
    }

    // --- UI Update & Dialogs ---

    private fun updateUiFromState(state: MeasurementUiState) {
        with(binding) {
            btnMeasure.isEnabled = !state.isPlacing
            btnUndo.isEnabled = state.isUndoEnabled
            btnSave.isEnabled = state.isSaveEnabled

            tvMeasurement.text = if (state.mode == MeasurementMode.BOX) {
                """
                Panjang: ${"%.2f".format(state.length)} cm
                Lebar: ${"%.2f".format(state.width)} cm
                Tinggi: ${"%.2f".format(state.height)} cm
                """.trimIndent()
            } else {
                if (state.points.isNotEmpty()) "Mode Bebas: ${state.points.size} titik ditempatkan."
                else "Mode Bebas: Tap untuk memulai."
            }

            tvClassification.text = state.classification
            tvClassification.setTextColor(state.getClassificationColor())

            val targetButtonId = if (state.mode == MeasurementMode.BOX) R.id.btn_mode_box else R.id.btn_mode_free
            if (toggleMode.checkedButtonId != targetButtonId) {
                toggleMode.removeOnButtonCheckedListener(toggleModeListener)
                toggleMode.check(targetButtonId)
                toggleMode.addOnButtonCheckedListener(toggleModeListener)
            }
        }
    }

    private fun saveAndShowExportDialog() {
        val savedData = viewModel.saveCurrentMeasurement()
        if (savedData == null) {
            dialogHelper.showMessage("Tidak ada pengukuran valid untuk disimpan.")
            return
        }

        dialogHelper.showExportDialog(
            onCsvSelected = {
                val path = fileExporter.exportToCSV(listOf(savedData))
                dialogHelper.showMessage("CSV tersimpan di: $path")
            },
            onJsonSelected = {
                val path = fileExporter.exportToJSON(listOf(savedData))
                dialogHelper.showMessage("JSON tersimpan di: $path")
            }
        )
    }

    private fun showHistoryDialog() {
        dialogHelper.showMessage("Fitur riwayat belum diimplementasikan.")
        // TODO: Implementasi dialog riwayat
    }

    // --- ARCore & Permissions Utilities ---

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 101
    }

    private fun arFragment(): ArFragment {
        return supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                dialogHelper.showMessage("Izin kamera diperlukan. Aplikasi akan ditutup.")
                finish()
            }
        }
    }

    private fun checkARCoreAvailability(): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            Handler(Looper.getMainLooper()).postDelayed({ checkARCoreAvailability() }, 200)
            return true
        }

        return when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD, ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                try {
                    if (!installRequested) {
                        installRequested = true
                        ArCoreApk.getInstance().requestInstall(this, true)
                    }
                    false
                } catch (e: Exception) {
                    Log.e("MainActivity", "Gagal meminta instalasi ARCore: ${e.message}", e)
                    installRequested = false
                    false
                }
            }
            else -> {
                dialogHelper.showMessage("Perangkat ini tidak mendukung AR.")
                finish()
                false
            }
        }
    }
}