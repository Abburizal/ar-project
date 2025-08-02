package com.example.arpackagevalidator.utils

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.TextView
import com.example.arpackagevalidator.R
import com.example.arpackagevalidator.ui.viewmodel.BoxMeasurementStep
import com.example.arpackagevalidator.ui.viewmodel.MeasurementUiState
import com.google.ar.core.HitResult
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color as ArColor
import com.google.ar.sceneform.rendering.Material
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import java.util.concurrent.CompletableFuture

class ArInteractionManager(
    private val context: Context,
    private val arFragment: ArFragment
) {
    private val scene get() = arFragment.arSceneView.scene
    private val managedNodes = mutableListOf<Node>()

    private val sphereMaterial: CompletableFuture<Material>
    private val lineMaterial: CompletableFuture<Material>
    private val heightMaterial: CompletableFuture<Material>
    private val boxMaterial: CompletableFuture<Material>

    private var labelRenderable: CompletableFuture<ViewRenderable>? = null
    private var isViewRenderableSupported = true

    companion object {
        private const val SPHERE_RADIUS = 0.01f
        private const val LINE_THICKNESS = 0.005f
        private const val LABEL_Y_OFFSET = 0.03f
        private const val TAG = "ArInteractionManager"
    }

    init {
        sphereMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.RED))
        lineMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.BLUE))
        heightMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.CYAN))
        boxMaterial = MaterialFactory.makeTransparentWithColor(context, ArColor(Color.argb(100, 0, 255, 0)))

        CompletableFuture.allOf(sphereMaterial, lineMaterial, heightMaterial, boxMaterial)
            .exceptionally { throwable ->
                Log.e(TAG, "Gagal memuat material AR dasar", throwable)
                null
            }
    }

    private fun getLabelRenderable(): CompletableFuture<ViewRenderable>? {
        if (!isViewRenderableSupported) {
            return null
        }
        if (labelRenderable == null) {
            try {
                labelRenderable = ViewRenderable.builder()
                    .setView(context, R.layout.distance_text_layout)
                    .build()
                    .exceptionally { throwable ->
                        Log.e(TAG, "Gagal membuat ViewRenderable: ${throwable.message}")
                        isViewRenderableSupported = false
                        null
                    }
            } catch (e: Exception) {
                Log.e(TAG, "ViewRenderable tidak didukung di perangkat ini: ${e.message}")
                isViewRenderableSupported = false
                return null
            }
        }
        return labelRenderable
    }

    fun drawState(state: MeasurementUiState) {
        Log.d(TAG, "drawState dipanggil dengan ${state.points.size} titik.")
        reset()
        state.points.forEach { point ->
            drawSphereAt(point)
        }
        if (state.mode == com.example.arpackagevalidator.ui.MeasurementMode.BOX) {
            drawBoxMeasurement(state)
        }
    }

    fun placeAnchorFromHit(hitResult: HitResult): AnchorNode? {
        return try {
            val anchor = hitResult.createAnchor()
            val anchorNode = AnchorNode(anchor)
            anchorNode.setParent(scene)
            anchorNode
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menempatkan anchor", e)
            null
        }
    }

    fun reset() {
        Log.d(TAG, "Resetting scene, menghapus ${managedNodes.size} node.")
        managedNodes.forEach { node ->
            node.setParent(null)
            node.renderable = null
        }
        managedNodes.clear()
    }

    private fun drawBoxMeasurement(state: MeasurementUiState) {
        if (state.points.size >= 2) {
            drawLine(state.points[0], state.points[1], lineMaterial.get(), state.length, "Panjang")
        }
        if (state.points.size >= 3) {
            drawLine(state.points[0], state.points[2], lineMaterial.get(), state.width, "Lebar")
        }
        if (state.points.size >= 4) {
            val basePointForHeight = state.points[0]
            val topPointForHeight = Vector3(basePointForHeight.x, state.points[3].y, basePointForHeight.z)
            drawLine(basePointForHeight, topPointForHeight, heightMaterial.get(), state.height, "Tinggi")

            if (state.boxStep == BoxMeasurementStep.DONE) {
                drawPreviewBox(state)
            }
        }
    }

    /**
     * PERBAIKAN: Fungsi ini sekarang dengan benar menempatkan bola di posisi dunia yang tepat.
     */
    private fun drawSphereAt(position: Vector3) {
        try {
            Log.d(TAG, "Menggambar bola di posisi: $position")
            // Buat model bola di titik asal (0,0,0)
            val sphereRenderable = ShapeFactory.makeSphere(SPHERE_RADIUS, Vector3.zero(), sphereMaterial.get())
            // Tambahkan node ke scene dan atur posisi dunianya
            addNodeToScene(sphereRenderable, worldPosition = position)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat bola", e)
        }
    }

    private fun drawLine(p1: Vector3, p2: Vector3, material: Material, distanceInCm: Float, title: String) {
        try {
            val difference = Vector3.subtract(p1, p2)
            if (difference.length() < 0.001f) return

            Log.d(TAG, "Menggambar garis '$title' dari $p1 ke $p2")
            val rotation = Quaternion.lookRotation(difference, Vector3.up())
            val position = Vector3.add(p1, p2).scaled(0.5f)

            val lineModel = ShapeFactory.makeCube(
                Vector3(LINE_THICKNESS, LINE_THICKNESS, difference.length()),
                Vector3.zero(),
                material
            )

            val lineNode = addNodeToScene(lineModel, worldPosition = position, worldRotation = rotation)
            addDistanceLabel(lineNode, distanceInCm, title)

        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat garis", e)
        }
    }

    private fun drawPreviewBox(state: MeasurementUiState) {
        try {
            if (state.points.size < 4) return
            Log.d(TAG, "Menggambar kotak preview.")

            val p0 = state.points[0]
            val p1 = state.points[1]
            val p2 = state.points[2]

            val vecLength = Vector3.subtract(p1, p0)
            val vecWidth = Vector3.subtract(p2, p0)

            val halfLength = vecLength.scaled(0.5f)
            val halfWidth = vecWidth.scaled(0.5f)
            val halfHeight = Vector3(0f, state.height / 200f, 0f)

            val tempCenter = Vector3.add(p0, halfLength)
            val tempCenter2 = Vector3.add(tempCenter, halfWidth)
            val center = Vector3.add(tempCenter2, halfHeight)

            val boxModel = ShapeFactory.makeCube(
                Vector3(state.width / 100f, state.height / 100f, state.length / 100f),
                Vector3.zero(),
                boxMaterial.get()
            )

            addNodeToScene(boxModel, worldPosition = center)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat kotak", e)
        }
    }

    private fun addDistanceLabel(parentNode: Node, distanceInCm: Float, title: String) {
        val labelFuture = getLabelRenderable()
        if (labelFuture == null) {
            Log.w(TAG, "ViewRenderable tidak didukung, label dilewati.")
            return
        }

        labelFuture.thenAccept { renderable ->
            val labelNode = Node()
            labelNode.setParent(parentNode)
            labelNode.renderable = renderable.makeCopy()
            labelNode.localPosition = Vector3(0f, LABEL_Y_OFFSET, 0f)

            scene.addOnUpdateListener {
                try {
                    val cameraPosition = scene.camera.worldPosition
                    val direction = Vector3.subtract(cameraPosition, labelNode.worldPosition)
                    labelNode.worldRotation = Quaternion.lookRotation(direction, Vector3.up())
                } catch (e: Exception) {
                    // Hindari crash jika node sudah dilepas
                }
            }

            val view = (labelNode.renderable as ViewRenderable).view
            view.findViewById<TextView>(R.id.titleTextView).text = title
            view.findViewById<TextView>(R.id.distanceTextView).text = "%.1f cm".format(distanceInCm)

            managedNodes.add(labelNode)
        }
    }

    private fun addNodeToScene(
        renderable: Renderable,
        worldPosition: Vector3? = null,
        worldRotation: Quaternion? = null
    ): Node {
        val node = Node().apply {
            setParent(scene)
            this.renderable = renderable
            worldPosition?.let { this.worldPosition = it }
            worldRotation?.let { this.worldRotation = it }
        }
        managedNodes.add(node)
        return node
    }
}
