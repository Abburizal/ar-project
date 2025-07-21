package com.example.arpackagevalidator.util

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.widget.TextView
import com.example.arpackagevalidator.R
import com.example.arpackagevalidator.ui.MeasurementMode
import com.example.arpackagevalidator.ui.viewmodel.MeasurementUiState
import com.google.ar.core.HitResult
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
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
import kotlin.math.pow
import kotlin.math.sqrt

class ArInteractionManager(
    private val context: Context,
    private val arFragment: ArFragment
) {
    // FIX #1: Akses scene dan root node yang benar
    private val scene: Scene get() = arFragment.arSceneView.scene
    private val rootNode: Node get() = scene.root ?: scene.camera.parent ?: Node()
    
    private val managedNodes = mutableListOf<Node>()

    private val sphereMaterial: CompletableFuture<Material>
    private val lineMaterial: CompletableFuture<Material>
    private val heightMaterial: CompletableFuture<Material>
    private val freeLineMaterial: CompletableFuture<Material>
    private val boxMaterial: CompletableFuture<Material>
    private val labelRenderable: CompletableFuture<ViewRenderable>

    private val allAssetsFuture: CompletableFuture<Void>

    companion object {
        private const val SPHERE_RADIUS = 0.01f
        private const val LINE_THICKNESS = 0.005f
        private const val LABEL_Y_OFFSET = 0.03f
        private const val TAG = "ArInteractionManager"
    }

    init {
        sphereMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.RED))
        lineMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.BLUE))
        heightMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.GREEN))
        freeLineMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.CYAN))
        boxMaterial = MaterialFactory.makeTransparentWithColor(context, ArColor(Color.argb(120, 0, 255, 0)))
        labelRenderable = ViewRenderable.builder().setView(context, R.layout.distance_text_layout).build()

        allAssetsFuture = CompletableFuture.allOf(
            sphereMaterial, lineMaterial, heightMaterial, freeLineMaterial, boxMaterial, labelRenderable
        )

        scene.addOnUpdateListener {
            val cameraPosition = scene.camera.worldPosition
            for (node in managedNodes) {
                if (node.renderable is ViewRenderable) {
                    // FIX: Gunakan method yang tersedia untuk Vector3
                    val direction = Vector3.subtract(cameraPosition, node.worldPosition)
                    node.worldRotation = Quaternion.lookRotation(direction, Vector3.up())
                }
            }
        }
    }

    fun drawState(state: MeasurementUiState) {
        allAssetsFuture.thenRun {
            reset()
            state.points.forEach { point ->
                val anchorNode = createAnchorAt(point)
                drawSphereOn(anchorNode)
            }
            if (state.mode == MeasurementMode.BOX) {
                drawBoxMeasurement(state)
            } else {
                drawFreeMeasurement(state)
            }
        }.exceptionally {
            Log.e(TAG, "Gagal memuat aset AR.", it)
            null
        }
    }

    fun placeAnchorFromHit(hitResult: HitResult): AnchorNode {
        val anchor = hitResult.createAnchor()
        return AnchorNode(anchor).apply {
            setParent(scene)
            managedNodes.add(this)
        }
    }

    fun reset() {
        managedNodes.forEach { node ->
            node.setParent(null)
            if (node is AnchorNode) node.anchor?.detach()
        }
        managedNodes.clear()
    }

    private fun drawBoxMeasurement(state: MeasurementUiState) {
        if (state.points.size >= 2) drawLine(state.points[0], state.points[1], lineMaterial.get(), state.length)
        if (state.points.size >= 3) drawLine(state.points[1], state.points[2], lineMaterial.get(), state.width)
        if (state.points.size >= 4) {
            drawLine(state.points[2], state.points[3], heightMaterial.get(), state.height)
            drawPreviewBox(state)
        }
    }

    private fun drawFreeMeasurement(state: MeasurementUiState) {
        state.points.windowed(size = 2, step = 2).forEach { (p1, p2) ->
            val distance = calculateDistance(p1, p2) * 100f
            drawLine(p1, p2, freeLineMaterial.get(), distance)
        }
    }

    private fun calculateDistance(p1: Vector3, p2: Vector3) =
        sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2) + (p1.z - p2.z).pow(2))

    private fun createAnchorAt(position: Vector3): AnchorNode {
        val session = arFragment.arSceneView.session ?: return AnchorNode()
        val pose = com.google.ar.core.Pose(
            floatArrayOf(position.x, position.y, position.z), 
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        val anchor = session.createAnchor(pose)
        return AnchorNode(anchor).apply {
            setParent(scene)
            managedNodes.add(this)
        }
    }

    private fun drawSphereOn(anchorNode: AnchorNode) {
        val sphereModel = ShapeFactory.makeSphere(SPHERE_RADIUS, Vector3.zero(), sphereMaterial.get())
        addNodeToScene(sphereModel, parent = anchorNode)
    }

    private fun drawLine(p1: Vector3, p2: Vector3, material: Material, distance: Float) {
        // FIX: Gunakan Vector3.subtract yang tersedia
        val difference = Vector3.subtract(p2, p1)
        if (difference.length() == 0f) return
        
        // FIX #2: Ganti Vector3.add dengan kalkulasi manual
        val position = Vector3(
            p1.x + difference.x * 0.5f,
            p1.y + difference.y * 0.5f,
            p1.z + difference.z * 0.5f
        )
        
        val rotation = Quaternion.lookRotation(difference, Vector3.up())
        val lineModel = ShapeFactory.makeCube(
            Vector3(LINE_THICKNESS, LINE_THICKNESS, difference.length()), 
            Vector3.zero(), 
            material
        )
        val lineNode = addNodeToScene(lineModel, worldPosition = position, worldRotation = rotation)
        addDistanceLabel(lineNode, distance)
    }

    private fun drawPreviewBox(state: MeasurementUiState) {
        if (state.points.size < 4) return
        val size = Vector3(state.width / 100f, state.height / 100f, state.length / 100f)
        val p0 = state.points[0]
        val p1 = state.points[1]
        val p2 = state.points[2]
        
        // FIX: Gunakan Vector3.subtract yang tersedia
        val vecLength = Vector3.subtract(p1, p0)
        val vecWidth = Vector3.subtract(p2, p1)
        
        // FIX #2: Ganti Vector3.add dengan kalkulasi manual
        val center = Vector3(
            p0.x + vecLength.x * 0.5f + vecWidth.x * 0.5f,
            p0.y + vecLength.y * 0.5f + vecWidth.y * 0.5f + state.height / 200f,
            p0.z + vecLength.z * 0.5f + vecWidth.z * 0.5f
        )
        
        val boxModel = ShapeFactory.makeCube(size, Vector3.zero(), boxMaterial.get())
        addNodeToScene(boxModel, worldPosition = center)
    }

    private fun addDistanceLabel(parentNode: Node, distance: Float) {
        labelRenderable.thenAccept { renderable ->
            val labelView = renderable.makeCopy()
            (labelView.view as TextView).text = "%.2f cm".format(distance)
            addNodeToScene(
                labelView, 
                parent = parentNode, 
                localPosition = Vector3(0f, LABEL_Y_OFFSET, 0f)
            )
        }
    }

    // FIX #1: Gunakan rootNode yang sudah didefinisikan
    private fun addNodeToScene(
        renderable: Renderable,
        parent: Node = rootNode,
        worldPosition: Vector3? = null,
        worldRotation: Quaternion? = null,
        localPosition: Vector3? = null
    ): Node {
        val node = Node().apply {
            setParent(parent)
            this.renderable = renderable
            worldPosition?.let { this.worldPosition = it }
            worldRotation?.let { this.worldRotation = it }
            localPosition?.let { this.localPosition = it }
        }
        managedNodes.add(node)
        return node
    }
}