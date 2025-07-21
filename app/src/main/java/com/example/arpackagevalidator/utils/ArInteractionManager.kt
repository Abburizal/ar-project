package com.example.arpackagevalidator.util

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import com.example.arpackagevalidator.R
import com.example.arpackagevalidator.ui.MeasurementMode
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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class ArInteractionManager(
    private val context: Context,
    private val arFragment: ArFragment
) {
    // PERBAIKAN: Akses scene yang aman dengan null safety
    private val scene get() = arFragment.arSceneView?.scene
    private val managedNodes = mutableListOf<Node>()

    private val sphereMaterial: CompletableFuture<Material>
    private val lineMaterial: CompletableFuture<Material>
    private val heightMaterial: CompletableFuture<Material>
    private val freeLineMaterial: CompletableFuture<Material>
    private val boxMaterial: CompletableFuture<Material>
    
    // PERBAIKAN: Lazy load ViewRenderable untuk mencegah crash
    private var labelRenderable: CompletableFuture<ViewRenderable>? = null
    private var isViewRenderableSupported = true
    private var isSceneReady = false

    private val allAssetsFuture: CompletableFuture<Void>

    companion object {
        private const val SPHERE_RADIUS = 0.01f
        private const val LINE_THICKNESS = 0.005f
        private const val LABEL_Y_OFFSET = 0.03f
        private const val TAG = "ArInteractionManager"
        private const val SCENE_CHECK_DELAY = 100L
        private const val MAX_SCENE_CHECK_ATTEMPTS = 20
    }

    init {
        // Material initialization yang aman
        sphereMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.RED))
        lineMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.BLUE))
        heightMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.GREEN))
        freeLineMaterial = MaterialFactory.makeOpaqueWithColor(context, ArColor(Color.CYAN))
        boxMaterial = MaterialFactory.makeTransparentWithColor(context, ArColor(Color.argb(120, 0, 255, 0)))
        
        allAssetsFuture = CompletableFuture.allOf(
            sphereMaterial, lineMaterial, heightMaterial, freeLineMaterial, boxMaterial
        )

        // PERBAIKAN: Setup scene listener dengan retry mechanism
        setupSceneUpdateListenerWithRetry(0)
    }

    // PERBAIKAN: Setup scene listener yang robust dengan retry mechanism
    private fun setupSceneUpdateListenerWithRetry(attempt: Int) {
        if (attempt >= MAX_SCENE_CHECK_ATTEMPTS) {
            Log.e(TAG, "Failed to setup scene after $MAX_SCENE_CHECK_ATTEMPTS attempts")
            return
        }

        try {
            val sceneView = arFragment.arSceneView
            if (sceneView == null) {
                Log.w(TAG, "ArSceneView is null, retrying... (attempt ${attempt + 1})")
                Handler(Looper.getMainLooper()).postDelayed({
                    setupSceneUpdateListenerWithRetry(attempt + 1)
                }, SCENE_CHECK_DELAY)
                return
            }

            val currentScene = sceneView.scene
            if (currentScene == null) {
                Log.w(TAG, "Scene is null, retrying... (attempt ${attempt + 1})")
                Handler(Looper.getMainLooper()).postDelayed({
                    setupSceneUpdateListenerWithRetry(attempt + 1)
                }, SCENE_CHECK_DELAY)
                return
            }

            // Scene ready, setup listener
            currentScene.addOnUpdateListener {
                if (!isSceneReady) {
                    isSceneReady = true
                    Log.d(TAG, "Scene is now ready for operations")
                }
                
                try {
                    val cameraPosition = currentScene.camera.worldPosition
                    for (node in managedNodes) {
                        if (node.renderable is ViewRenderable) {
                            val direction = Vector3.subtract(cameraPosition, node.worldPosition)
                            node.worldRotation = Quaternion.lookRotation(direction, Vector3.up())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in scene update listener: ${e.message}")
                }
            }
            
            Log.d(TAG, "Scene update listener setup successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up scene update listener (attempt ${attempt + 1}): ${e.message}")
            Handler(Looper.getMainLooper()).postDelayed({
                setupSceneUpdateListenerWithRetry(attempt + 1)
            }, SCENE_CHECK_DELAY)
        }
    }

    // PERBAIKAN: Safe method untuk mendapatkan ViewRenderable
    private fun getLabelRenderable(): CompletableFuture<ViewRenderable>? {
        if (!isViewRenderableSupported) return null
        
        if (labelRenderable == null) {
            try {
                labelRenderable = ViewRenderable.builder()
                    .setView(context, R.layout.distance_text_layout)
                    .build()
                    .exceptionally { throwable ->
                        Log.e(TAG, "Failed to create ViewRenderable: ${throwable.message}")
                        isViewRenderableSupported = false
                        null
                    }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "ViewRenderable not supported on this device: ${e.message}")
                isViewRenderableSupported = false
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create ViewRenderable: ${e.message}")
                isViewRenderableSupported = false
                return null
            }
        }
        return labelRenderable
    }

    // PERBAIKAN: Safe drawing dengan scene check
    fun drawState(state: MeasurementUiState) {
        if (!isSceneReady) {
            Log.w(TAG, "Scene not ready yet, skipping draw")
            return
        }

        allAssetsFuture.thenRun {
            try {
                reset()
                state.points.forEach { point ->
                    val anchorNode = createAnchorAt(point)
                    anchorNode?.let { drawSphereOn(it) }
                }
                
                if (state.mode == MeasurementMode.BOX) {
                    drawBoxMeasurement(state)
                } else {
                    drawFreeMeasurement(state)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in drawState: ${e.message}")
            }
        }.exceptionally { throwable ->
            Log.e(TAG, "Failed to load AR assets: ${throwable.message}")
            null
        }
    }

    fun placeAnchorFromHit(hitResult: HitResult): AnchorNode {
        val anchor = hitResult.createAnchor()
        return AnchorNode(anchor).apply {
            scene?.let { sceneInstance ->
                setParent(sceneInstance)
                managedNodes.add(this)
            } ?: run {
                Log.e(TAG, "Scene is null when placing anchor")
            }
        }
    }

    // PERBAIKAN: Safe reset dengan null checks
    fun reset() {
        try {
            managedNodes.forEach { node ->
                try {
                    node.setParent(null)
                    if (node is AnchorNode) {
                        node.anchor?.detach()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing node: ${e.message}")
                }
            }
            managedNodes.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error in reset: ${e.message}")
        }
    }

    private fun drawBoxMeasurement(state: MeasurementUiState) {
        try {
            if (state.points.size >= 2) {
                drawLine(state.points[0], state.points[1], lineMaterial.get(), state.length)
            }
            if (state.points.size >= 3) {
                drawLine(state.points[1], state.points[2], lineMaterial.get(), state.width)
            }
            if (state.points.size >= 4) {
                drawLine(state.points[2], state.points[3], heightMaterial.get(), state.height)
                drawPreviewBox(state)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in drawBoxMeasurement: ${e.message}")
        }
    }

    private fun drawFreeMeasurement(state: MeasurementUiState) {
        try {
            state.points.windowed(size = 2, step = 2).forEach { (p1, p2) ->
                val distance = calculateDistance(p1, p2) * 100f
                drawLine(p1, p2, freeLineMaterial.get(), distance)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in drawFreeMeasurement: ${e.message}")
        }
    }

    private fun calculateDistance(p1: Vector3, p2: Vector3) =
        sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2) + (p1.z - p2.z).pow(2))

    // PERBAIKAN: Safe anchor creation
    private fun createAnchorAt(position: Vector3): AnchorNode? {
        return try {
            val session = arFragment.arSceneView?.session
            if (session == null) {
                Log.e(TAG, "AR Session is null")
                return null
            }
            
            val pose = com.google.ar.core.Pose(
                floatArrayOf(position.x, position.y, position.z), 
                floatArrayOf(0f, 0f, 0f, 1f)
            )
            val anchor = session.createAnchor(pose)
            
            AnchorNode(anchor).apply {
                scene?.let { sceneInstance ->
                    setParent(sceneInstance)
                    managedNodes.add(this)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create anchor: ${e.message}")
            null
        }
    }

    private fun drawSphereOn(anchorNode: AnchorNode) {
        try {
            val sphereModel = ShapeFactory.makeSphere(SPHERE_RADIUS, Vector3.zero(), sphereMaterial.get())
            addNodeToScene(sphereModel, parent = anchorNode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sphere: ${e.message}")
        }
    }

    private fun drawLine(p1: Vector3, p2: Vector3, material: Material, distance: Float) {
        try {
            val difference = Vector3.subtract(p2, p1)
            if (difference.length() == 0f) return
            
            // Manual calculation untuk position
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
            
            // Safe label creation
            lineNode?.let { addDistanceLabelSafe(it, distance) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create line: ${e.message}")
        }
    }

    private fun drawPreviewBox(state: MeasurementUiState) {
        try {
            if (state.points.size < 4) return
            
            val size = Vector3(state.width / 100f, state.height / 100f, state.length / 100f)
            val p0 = state.points[0]
            val p1 = state.points[1]
            val p2 = state.points[2]
            
            val vecLength = Vector3.subtract(p1, p0)
            val vecWidth = Vector3.subtract(p2, p1)
            
            // Manual calculation untuk center
            val center = Vector3(
                p0.x + vecLength.x * 0.5f + vecWidth.x * 0.5f,
                p0.y + vecLength.y * 0.5f + vecWidth.y * 0.5f + state.height / 200f,
                p0.z + vecLength.z * 0.5f + vecWidth.z * 0.5f
            )
            
            val boxModel = ShapeFactory.makeCube(size, Vector3.zero(), boxMaterial.get())
            addNodeToScene(boxModel, worldPosition = center)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create preview box: ${e.message}")
        }
    }

    // PERBAIKAN: Safe label creation
    private fun addDistanceLabelSafe(parentNode: Node, distance: Float) {
        val labelFuture = getLabelRenderable()
        if (labelFuture == null) {
            Log.w(TAG, "ViewRenderable not supported, skipping label for distance: $distance cm")
            return
        }
        
        labelFuture.thenAccept { renderable ->
            try {
                if (renderable != null) {
                    val labelView = renderable.makeCopy()
                    (labelView.view as? TextView)?.text = "%.2f cm".format(distance)
                    addNodeToScene(
                        labelView, 
                        parent = parentNode, 
                        localPosition = Vector3(0f, LABEL_Y_OFFSET, 0f)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add distance label: ${e.message}")
            }
        }.exceptionally { throwable ->
            Log.e(TAG, "Failed to create distance label: ${throwable.message}")
            null
        }
    }

    // PERBAIKAN: Safe node addition
    private fun addNodeToScene(
        renderable: Renderable,
        parent: Node? = getSceneRoot(),
        worldPosition: Vector3? = null,
        worldRotation: Quaternion? = null,
        localPosition: Vector3? = null
    ): Node? {
        return try {
            if (parent == null) {
                Log.e(TAG, "Parent node is null, cannot add node to scene")
                return null
            }
            
            val node = Node().apply {
                setParent(parent)
                this.renderable = renderable
                worldPosition?.let { this.worldPosition = it }
                worldRotation?.let { this.worldRotation = it }
                localPosition?.let { this.localPosition = it }
            }
            managedNodes.add(node)
            node
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add node to scene: ${e.message}")
            null
        }
    }

    // PERBAIKAN: Safe method untuk mendapatkan scene root
    private fun getSceneRoot(): Node? {
        return try {
            scene?.root ?: scene?.camera?.parent
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get scene root: ${e.message}")
            null
        }
    }
}