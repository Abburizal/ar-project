package com.example.arpackagevalidator

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlin.math.pow
import kotlin.math.sqrt
import com.google.ar.sceneform.math.Quaternion

class MainActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var btnMeasure: Button
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var tvMeasurement: TextView
    private lateinit var tvClassification: TextView

    private val anchorNodes = mutableListOf<AnchorNode>()
    private val measurementPoints = mutableListOf<Vector3>()
    private var isPlacing = false
    private var currentMeasurementStep = 0

    private var packageLength = 0f
    private var packageWidth = 0f
    private var packageHeight = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupArFragment()
        setupClickListeners()
    }

    private fun initializeViews() {
        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment
        btnMeasure = findViewById(R.id.btn_measure)
        btnSave = findViewById(R.id.btn_save)
        btnReset = findViewById(R.id.btn_reset)
        tvMeasurement = findViewById(R.id.tv_measurement)
        tvClassification = findViewById(R.id.tv_classification)

        btnSave.isEnabled = false
    }

    private fun setupArFragment() {
        arFragment.setOnTapArPlaneListener { hitResult, plane, _ ->
            if (isPlacing && plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
                placeAnchor(hitResult)
            }
        }
    }

    private fun setupClickListeners() {
        btnMeasure.setOnClickListener {
            startMeasurement()
        }

        btnReset.setOnClickListener {
            resetMeasurement()
        }

        btnSave.setOnClickListener {
            saveMeasurement()
        }
    }

    private fun startMeasurement() {
        isPlacing = true
        currentMeasurementStep = 0
        btnMeasure.isEnabled = false
        updateInstructions()
        Toast.makeText(this, "Ketuk pada sudut paket untuk memulai pengukuran", Toast.LENGTH_LONG).show()
    }

    private fun placeAnchor(hitResult: HitResult) {
        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor).apply {
            setParent(arFragment.arSceneView.scene)
        }

        // Tambahkan sphere kecil sebagai penanda
        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED))
            .thenAccept { material ->
                val sphere = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material)
                val node = Node().apply {
                    renderable = sphere
                    setParent(anchorNode)
                }
            }

        anchorNodes.add(anchorNode)
        measurementPoints.add(anchorNode.worldPosition)

        currentMeasurementStep++

        when (currentMeasurementStep) {
            1 -> {
                Toast.makeText(this, "Titik A ditempatkan. Tempatkan titik B untuk panjang", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                calculateLength()
                Toast.makeText(this, "Panjang diukur. Tempatkan titik C untuk lebar", Toast.LENGTH_SHORT).show()
            }
            3 -> {
                calculateWidth()
                Toast.makeText(this, "Lebar diukur. Tempatkan titik D untuk tinggi", Toast.LENGTH_SHORT).show()
            }
            4 -> {
                calculateHeight()
                finalizeMeasurement()
            }
        }

        updateInstructions()
    }

    private fun calculateLength() {
        if (measurementPoints.size >= 2) {
            packageLength = calculateDistance(measurementPoints[0], measurementPoints[1]) * 100 // konversi ke cm
            drawLine(anchorNodes[0], anchorNodes[1])
        }
    }

    private fun calculateWidth() {
        if (measurementPoints.size >= 3) {
            packageWidth = calculateDistance(measurementPoints[1], measurementPoints[2]) * 100 // konversi ke cm
            drawLine(anchorNodes[1], anchorNodes[2])
        }
    }

    private fun calculateHeight() {
        if (measurementPoints.size >= 4) {
            // Hitung tinggi dari titik tengah (D) ke bidang yang dibentuk oleh A, B, C
            val midPoint = measurementPoints[3]
            val basePoint = measurementPoints[2]
            packageHeight = kotlin.math.abs(midPoint.y - basePoint.y) * 100 // konversi ke cm

            // Buat garis vertikal untuk visualisasi tinggi
            val heightStart = Vector3(measurementPoints[2].x, measurementPoints[2].y, measurementPoints[2].z)
            val heightEnd = Vector3(measurementPoints[2].x, measurementPoints[3].y, measurementPoints[2].z)
            drawVerticalLine(heightStart, heightEnd)
        }
    }

    private fun calculateDistance(point1: Vector3, point2: Vector3): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        val dz = point1.z - point2.z
        return sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))
    }

    private fun drawLine(node1: AnchorNode, node2: AnchorNode) {
        val point1 = node1.worldPosition
        val point2 = node2.worldPosition
        val difference = Vector3.subtract(point2, point1)
        val directionFromTopToBottom = difference.normalized()
        val rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())

        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.BLUE))
            .thenAccept { material ->
                val model = ShapeFactory.makeCube(
                    Vector3(0.01f, 0.01f, difference.length()),
                    Vector3.zero(), material
                )

                val lineNode = Node().apply {
                    setParent(node1)
                    renderable = model
                    worldPosition = Vector3.add(point1, difference.scaled(0.5f))
                    worldRotation = rotationFromAToB
                }
            }
    }

    private fun drawVerticalLine(start: Vector3, end: Vector3) {
        // Implementasi untuk menggambar garis vertikal
        // Serupa dengan drawLine tapi untuk arah vertikal
    }

    private fun finalizeMeasurement() {
        isPlacing = false
        btnMeasure.isEnabled = true
        btnSave.isEnabled = true

        updateMeasurementDisplay()
        classifyPackage()
    }

    private fun updateMeasurementDisplay() {
        val measurementText = """
            Panjang: ${String.format("%.2f", packageLength)} cm
            Lebar: ${String.format("%.2f", packageWidth)} cm
            Tinggi: ${String.format("%.2f", packageHeight)} cm
        """.trimIndent()

        tvMeasurement.text = measurementText
    }

    private fun classifyPackage() {
        val classification = when {
            packageLength <= 30 && packageWidth <= 20 && packageHeight <= 10 -> {
                "SMALL"
            }
            packageLength <= 50 && packageWidth <= 40 && packageHeight <= 30 -> {
                "MEDIUM"
            }
            packageLength <= 100 && packageWidth <= 80 && packageHeight <= 60 -> {
                "LARGE"
            }
            else -> {
                "CUSTOM"
            }
        }

        tvClassification.text = "Klasifikasi: $classification"

        // Ubah warna teks berdasarkan klasifikasi
        val color = when (classification) {
            "SMALL" -> android.graphics.Color.GREEN
            "MEDIUM" -> android.graphics.Color.BLUE
            "LARGE" -> android.graphics.Color.MAGENTA
            "CUSTOM" -> android.graphics.Color.RED
            else -> android.graphics.Color.BLACK
        }
        tvClassification.setTextColor(color)
    }

    private fun updateInstructions() {
        val instruction = when (currentMeasurementStep) {
            0 -> "Tekan 'Mulai Ukur' untuk memulai"
            1 -> "Tempatkan titik B untuk mengukur panjang"
            2 -> "Tempatkan titik C untuk mengukur lebar"
            3 -> "Tempatkan titik D untuk mengukur tinggi"
            else -> "Pengukuran selesai"
        }

        runOnUiThread {
            Toast.makeText(this, instruction, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetMeasurement() {
        // Hapus semua anchor nodes
        anchorNodes.forEach { it.anchor?.detach() }
        anchorNodes.clear()
        measurementPoints.clear()

        // Reset variabel
        currentMeasurementStep = 0
        packageLength = 0f
        packageWidth = 0f
        packageHeight = 0f
        isPlacing = false

        // Update UI
        tvMeasurement.text = "Belum ada pengukuran"
        tvClassification.text = ""
        btnMeasure.isEnabled = true
        btnSave.isEnabled = false
    }

    private fun saveMeasurement() {
        // Implementasi penyimpanan hasil pengukuran
        // Bisa menggunakan SharedPreferences, SQLite, atau file
        val measurementData = """
            Timestamp: ${System.currentTimeMillis()}
            Panjang: $packageLength cm
            Lebar: $packageWidth cm
            Tinggi: $packageHeight cm
            Klasifikasi: ${tvClassification.text}
        """.trimIndent()

        // Contoh: simpan ke SharedPreferences
        val sharedPref = getSharedPreferences("measurements", MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("last_measurement_${System.currentTimeMillis()}", measurementData)
        editor.apply()

        Toast.makeText(this, "Pengukuran berhasil disimpan", Toast.LENGTH_SHORT).show()
    }
}