package com.example.arpackagevalidator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MeasurementMode { BOX, FREE }

/**
 * MainActivity bertindak sebagai 'View' dalam arsitektur MVVM.
 * Tugas utamanya hanya meneruskan event dan menampilkan state dari ViewModel.
 * 
 * PERBAIKAN: 
 * - Fixed null pointer issues with ArInteractionManager
 * - Improved performance by reducing main thread work
 * - Better permission handling to avoid conflicts
 * - Added proper error handling and logging
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MeasurementViewModel by viewModels()

    // PERBAIKAN: ArInteractionManager dibuat nullable untuk safe initialization
    private var arInteractionManager: ArInteractionManager? = null
    private lateinit var dialogHelper: DialogHelper
    private lateinit var fileExporter: FileExporter

    private var installRequested = false
    private var permissionRequestInProgress = false
    private var isArComponentsInitialized = false

    // PERBAIKAN: Permission constants yang terpisah
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 101
        private const val STORAGE_PERMISSION_REQUEST_CODE = 102
        private const val TAG = "MainActivity"
        private const val AR_INIT_DELAY = 100L
        private const val AR_INIT_RETRY_DELAY = 200L
    }

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
        Log.d(TAG, "onCreate started")

        if (!checkARCoreAvailability()) {
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // PERBAIKAN: Inisialisasi yang bertahap untuk mengurangi load main thread
        initializeCoreHelpers()
        setupClickListeners()
        setupObservers()

        // PERBAIKAN: Check dan request permissions dengan delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkAndRequestPermissions()
        }, 50)

        // PERBAIKAN: Initialize AR components dengan delay yang lebih lama
        Handler(Looper.getMainLooper()).postDelayed({
            initializeArRelatedComponentsSafe()
        }, AR_INIT_DELAY)

        Log.d(TAG, "onCreate completed")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        
        // Verifikasi ulang permissions saat resume
        if (!hasAllRequiredPermissions()) {
            Log.w(TAG, "Missing required permissions on resume")
            checkAndRequestPermissions()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    // --- Setup Methods ---

    private fun initializeCoreHelpers() {
        try {
            dialogHelper = DialogHelper(this, layoutInflater)
            fileExporter = FileExporter(this)
            Log.d(TAG, "Core helpers initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize core helpers: ${e.message}")
        }
    }

    // PERBAIKAN: Inisialisasi AR yang lebih aman dan robust
    private fun initializeArRelatedComponentsSafe() {
        if (isArComponentsInitialized) {
            Log.d(TAG, "AR components already initialized")
            return
        }

        if (!hasAllRequiredPermissions()) {
            Log.w(TAG, "Cannot initialize AR components - missing permissions")
            return
        }

        try {
            val arFragment = arFragment()
            Log.d(TAG, "ArFragment obtained, checking ArSceneView...")
            
            // PERBAIKAN: Check ArSceneView dengan retry mechanism
            checkArSceneViewReady(arFragment, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get ArFragment: ${e.message}")
            dialogHelper.showMessage("Gagal memuat AR Fragment. Silakan restart aplikasi.")
        }
    }

    // PERBAIKAN: Retry mechanism untuk ArSceneView readiness
    private fun checkArSceneViewReady(arFragment: ArFragment, attempt: Int) {
        val maxAttempts = 20
        
        if (attempt >= maxAttempts) {
            Log.e(TAG, "ArSceneView not ready after $maxAttempts attempts")
            dialogHelper.showMessage("AR tidak dapat dimuat. Silakan restart aplikasi.")
            return
        }

        val sceneView = arFragment.arSceneView
        if (sceneView == null) {
            Log.w(TAG, "ArSceneView is null, retrying... (attempt ${attempt + 1})")
            Handler(Looper.getMainLooper()).postDelayed({
                checkArSceneViewReady(arFragment, attempt + 1)
            }, AR_INIT_RETRY_DELAY)
            return
        }

        // PERBAIKAN: Setup callback untuk scene readiness
        if (sceneView.scene == null) {
            Log.w(TAG, "Scene is null, waiting for scene initialization... (attempt ${attempt + 1})")
            Handler(Looper.getMainLooper()).postDelayed({
                checkArSceneViewReady(arFragment, attempt + 1)
            }, AR_INIT_RETRY_DELAY)
            return
        }

        // Scene is ready, initialize ArInteractionManager
        try {
            Log.d(TAG, "ArSceneView ready, initializing ArInteractionManager...")
            arInteractionManager = ArInteractionManager(this, arFragment)
            setupArInteraction(arFragment)
            isArComponentsInitialized = true
            Log.d(TAG, "AR components initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ArInteractionManager: ${e.message}")
            dialogHelper.showMessage("Gagal menginisialisasi AR. Silakan restart aplikasi.")
        }
    }

    private fun setupArInteraction(arFragment: ArFragment) {
        try {
            arFragment.setOnTapArPlaneListener { hitResult, plane, _ ->
                if (viewModel.uiState.value?.isPlacing == true && 
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                    
                    arInteractionManager?.let { manager ->
                        try {
                            val anchorNode = manager.placeAnchorFromHit(hitResult)
                            // PERBAIKAN: Pastikan onArTap hanya dipanggil SATU KALI
                            viewModel.onArTap(anchorNode.worldPosition)
                            Log.d(TAG, "Anchor placed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error placing anchor: ${e.message}")
                            dialogHelper.showMessage("Gagal menempatkan titik. Coba lagi.")
                        }
                    } ?: run {
                        Log.w(TAG, "ArInteractionManager is null when trying to place anchor")
                    }
                }
            }
            Log.d(TAG, "AR interaction setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup AR interaction: ${e.message}")
        }
    }

    private fun setupClickListeners() {
        try {
            with(binding) {
                btnMeasure.setOnClickListener { 
                    Log.d(TAG, "Measure button clicked")
                    viewModel.startMeasurement() 
                }
                btnReset.setOnClickListener { 
                    Log.d(TAG, "Reset button clicked")
                    viewModel.reset() 
                }
                btnUndo.setOnClickListener { 
                    Log.d(TAG, "Undo button clicked")
                    viewModel.undo() 
                }
                btnSave.setOnClickListener { 
                    Log.d(TAG, "Save button clicked")
                    saveAndShowExportDialog() 
                }
                btnHistory.setOnClickListener { 
                    Log.d(TAG, "History button clicked")
                    showHistoryDialog() 
                }

                btnCalibrate.setOnClickListener {
                    Log.d(TAG, "Calibrate button clicked")
                    dialogHelper.showCalibrationDialog { knownLength ->
                        viewModel.startCalibration(knownLength)
                    }
                }

                toggleMode.removeOnButtonCheckedListener(toggleModeListener)
                toggleMode.addOnButtonCheckedListener(toggleModeListener)
            }
            Log.d(TAG, "Click listeners setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup click listeners: ${e.message}")
        }
    }

    private fun setupObservers() {
        try {
            viewModel.uiState.observe(this) { state ->
                if (state == null) return@observe
                updateUiFromState(state)
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
            Log.d(TAG, "Observers setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup observers: ${e.message}")
        }
    }

    // --- UI Update & Dialogs ---

    // PERBAIKAN: Update UI yang lebih efisien dengan batch operations
    private fun updateUiFromState(state: MeasurementUiState) {
        try {
            with(binding) {
                // Batch update UI untuk mengurangi redraws
                btnMeasure.isEnabled = !state.isPlacing
                btnUndo.isEnabled = state.isUndoEnabled
                btnSave.isEnabled = state.isSaveEnabled

                // Update measurement text
                val measurementText = if (state.mode == MeasurementMode.BOX) {
                    """
                    Panjang: ${"%.2f".format(state.length)} cm
                    Lebar: ${"%.2f".format(state.width)} cm
                    Tinggi: ${"%.2f".format(state.height)} cm
                    """.trimIndent()
                } else {
                    if (state.points.isNotEmpty()) "Mode Bebas: ${state.points.size} titik ditempatkan."
                    else "Mode Bebas: Tap untuk memulai."
                }
                tvMeasurement.text = measurementText

                // Update classification
                tvClassification.text = state.classification
                tvClassification.setTextColor(state.getClassificationColor())

                // Update mode toggle dengan pengecekan untuk menghindari loop
                val targetButtonId = if (state.mode == MeasurementMode.BOX) R.id.btn_mode_box else R.id.btn_mode_free
                if (toggleMode.checkedButtonId != targetButtonId) {
                    toggleMode.removeOnButtonCheckedListener(toggleModeListener)
                    toggleMode.check(targetButtonId)
                    toggleMode.addOnButtonCheckedListener(toggleModeListener)
                }
            }

            // PERBAIKAN: Update AR visualization di background untuk mengurangi main thread load
            updateArVisualization(state)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI from state: ${e.message}")
        }
    }

    // PERBAIKAN: AR visualization update yang async
    private fun updateArVisualization(state: MeasurementUiState) {
        arInteractionManager?.let { manager ->
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    withContext(Dispatchers.Main) {
                        manager.drawState(state)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating AR visualization: ${e.message}")
                }
            }
        }
    }

    private fun saveAndShowExportDialog() {
        try {
            val savedData = viewModel.saveCurrentMeasurement()
            if (savedData == null) {
                dialogHelper.showMessage("Tidak ada pengukuran valid untuk disimpan.")
                return
            }

            dialogHelper.showExportDialog(
                onCsvSelected = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val path = fileExporter.exportToCSV(listOf(savedData))
                            withContext(Dispatchers.Main) {
                                dialogHelper.showMessage("CSV tersimpan di: $path")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error exporting CSV: ${e.message}")
                            withContext(Dispatchers.Main) {
                                dialogHelper.showMessage("Gagal mengekspor CSV: ${e.message}")
                            }
                        }
                    }
                },
                onJsonSelected = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val path = fileExporter.exportToJSON(listOf(savedData))
                            withContext(Dispatchers.Main) {
                                dialogHelper.showMessage("JSON tersimpan di: $path")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error exporting JSON: ${e.message}")
                            withContext(Dispatchers.Main) {
                                dialogHelper.showMessage("Gagal mengekspor JSON: ${e.message}")
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveAndShowExportDialog: ${e.message}")
            dialogHelper.showMessage("Gagal menyimpan data: ${e.message}")
        }
    }

    private fun showHistoryDialog() {
        try {
            dialogHelper.showMessage("Fitur riwayat belum diimplementasikan.")
            // TODO: Implementasi dialog riwayat
        } catch (e: Exception) {
            Log.e(TAG, "Error showing history dialog: ${e.message}")
        }
    }

    // --- Permission Handling ---

    // PERBAIKAN: Permission handling yang terstruktur dan aman
    private fun checkAndRequestPermissions() {
        if (permissionRequestInProgress) {
            Log.d(TAG, "Permission request already in progress")
            return
        }

        val requiredPermissions = mutableListOf<String>()
        
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }
        
        // Check storage permission (for Android 10 and below)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (requiredPermissions.isNotEmpty()) {
            permissionRequestInProgress = true
            Log.d(TAG, "Requesting permissions: $requiredPermissions")
            
            // PERBAIKAN: Request permissions satu per satu untuk menghindari conflict
            requestPermissionSequentially(requiredPermissions, 0)
        } else {
            Log.d(TAG, "All permissions already granted")
            onAllPermissionsGranted()
        }
    }

    private fun requestPermissionSequentially(permissions: List<String>, index: Int) {
        if (index >= permissions.size) {
            permissionRequestInProgress = false
            Log.d(TAG, "All permission requests completed")
            onAllPermissionsGranted()
            return
        }

        val permission = permissions[index]
        val requestCode = when (permission) {
            Manifest.permission.CAMERA -> CAMERA_PERMISSION_REQUEST_CODE
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> STORAGE_PERMISSION_REQUEST_CODE
            else -> CAMERA_PERMISSION_REQUEST_CODE
        }

        Log.d(TAG, "Requesting permission: $permission")
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<out String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        Log.d(TAG, "Permission result: requestCode=$requestCode, granted=${grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED}")
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission granted")
                    // Continue dengan permission lain yang mungkin dibutuhkan
                    checkAndRequestPermissions()
                } else {
                    permissionRequestInProgress = false
                    dialogHelper.showMessage("Izin kamera diperlukan untuk menggunakan AR. Aplikasi akan ditutup.")
                    finish()
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                permissionRequestInProgress = false
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Storage permission granted")
                } else {
                    Log.w(TAG, "Storage permission denied - export feature may not work")
                    dialogHelper.showMessage("Fitur export mungkin tidak berfungsi tanpa izin storage.")
                }
                onAllPermissionsGranted()
            }
        }
    }

    private fun onAllPermissionsGranted() {
        Log.d(TAG, "All required permissions granted")
        if (!isArComponentsInitialized) {
            initializeArRelatedComponentsSafe()
        }
    }

    private fun hasAllRequiredPermissions(): Boolean {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        
        val storageGranted = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Storage permission tidak diperlukan untuk Android 11+
        }
        
        return cameraGranted && storageGranted
    }

    // --- ARCore Utilities ---

    private fun arFragment(): ArFragment {
        return supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment
    }

    private fun checkARCoreAvailability(): Boolean {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            Handler(Looper.getMainLooper()).postDelayed({ checkARCoreAvailability() }, 200)
            return true
        }

        return when (availability) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                Log.d(TAG, "ARCore is supported and installed")
                true
            }
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD, 
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                try {
                    if (!installRequested) {
                        installRequested = true
                        Log.d(TAG, "Requesting ARCore installation")
                        ArCoreApk.getInstance().requestInstall(this, true)
                    }
                    false
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request ARCore installation: ${e.message}")
                    installRequested = false
                    false
                }
            }
            else -> {
                Log.e(TAG, "ARCore not supported on this device")
                dialogHelper.showMessage("Perangkat ini tidak mendukung AR.")
                finish()
                false
            }
        }
    }
}