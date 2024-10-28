package com.example.test_3d_new

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.*
import com.google.android.filament.utils.*
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import io.flutter.plugin.platform.PlatformView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.Matrix4



@SuppressLint("ClickableViewAccessibility")
class MyThreediView(
    private val context: Context,
    private val viewId: Int,
    private val creationParams: Map<String?, Any?>?,
    private val activity: MainActivity,
) : PlatformView {

    companion object {
        init {
            Utils.init()
        }
    }

    private var fileName = ""
    private var animationIndex = 0
    private var previousX: Float = 0f
    private var previousY: Float = 0f

    // Choreographer is used to schedule new frames
    private var choreographer: Choreographer
    private val frameScheduler = FrameCallback()

    private var modelViewer: AppModelViewer
    private var uiHelper: UiHelper
    var currentRotation = Quaternion(0f, 0f, 0f, 1f)

    private val surfaceView: SurfaceView = SurfaceView(context)
    private var initialDistance: Float = 0f
    private var currentScale: Float = 1f
    private var test: TransformResult? = null

    init {
        fileName = creationParams?.get("fileNameWithExtension").toString()
        animationIndex = Integer.parseInt(creationParams?.get("animationIndex").toString())

        val layoutParams: ViewGroup.LayoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

        surfaceView.layoutParams = layoutParams

        choreographer = Choreographer.getInstance()
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }

        modelViewer = AppModelViewer(surfaceView = surfaceView, uiHelper = uiHelper)
//
//         surfaceView.setOnTouchListener { _, event ->
//             modelViewer.onTouchEvent(event)
//             true
//         }

                surfaceView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Store the initial touch positions for orbit
                    previousX = event.x
                    previousY = event.y
                    true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    // Reset zoom variables when the second pointer is lifted
                    initialDistance = 0f
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Reset when gesture ends
                    initialDistance = 0f
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    when (event.pointerCount) {
                        1 -> {
                            // Orbit (rotate) gesture with one finger
                            val deltaX = event.x - previousX
                            val deltaY = event.y - previousY

                            val sensitivity = 0.2f

                            // Apply pitch (rotation around X-axis) and yaw (rotation around Y-axis)
                            val rotationX = Quaternion(Vector3(1f, 0f, 0f), deltaY * sensitivity)
                            val rotationY = Quaternion(Vector3(0f, 1f, 0f), deltaX * sensitivity)

                            // Update current rotation by combining new rotations
                            currentRotation = rotationY.mul(currentRotation).mul(rotationX).nor()

                            Log.d("Test", test?.center!!.toString())

                            // Apply both scaling and rotation transformations together
                            applyTransformations(Matrix4().setToScaling(test?.center!!.x, test?.center!!.y, test?.center!!.z), currentRotation)

                            // Update previous touch positions
                            previousX = event.x
                            previousY = event.y
                        }
                        2 -> {
                            // Pinch-to-zoom gesture with two fingers
                            val x0 = event.getX(0)
                            val y0 = event.getY(0)
                            val x1 = event.getX(1)
                            val y1 = event.getY(1)

                            val distance = kotlin.math.sqrt(
                                ((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).toDouble()
                            ).toFloat()

                            // Calculate the scale factor based on distance between two fingers
                            if (initialDistance == 0f) {
                                initialDistance = distance
                            } else {
                                val scaleFactor = distance / initialDistance
                                currentScale *= scaleFactor
                                initialDistance = distance
                                Log.d("CurrentScale", currentScale.toString())
                                Log.d("currentRotation", currentRotation.toString())
                                // Apply both scaling and rotation transformations together
                                applyTransformations(Matrix4().setToScaling(currentScale, currentScale, currentScale), currentRotation)
                            }
                        }
                    }
                    true
                }
            }
            true
        }



        createRenderables()
        createIndirectLight()
        configureViewer()
    }

    private fun applyTransformations(scaleMatrix: Matrix4, rotation: Quaternion) {
        // Combine rotation and scaling into a single matrix
        Log.d("Scale", scaleMatrix.toString())
        val combinedMatrix = Matrix4().set(rotation).mul(scaleMatrix)

        // Apply the transformation to the model
        val tm = modelViewer.engine.transformManager
        val entity = modelViewer.asset?.root ?: error("Model root entity is null")
        val instance = tm.getInstance(entity)
        tm.setTransform(instance, combinedMatrix.values)
    }

    override fun getView(): android.view.View {
        choreographer.postFrameCallback(frameScheduler)
        return surfaceView
    }

    override fun dispose() {
        choreographer.removeFrameCallback(frameScheduler)
    }

    fun onFlutterViewAttached(flutterView: View) {
        choreographer.postFrameCallback(frameScheduler)
    }

    override fun onFlutterViewDetached() {
        choreographer.removeFrameCallback(frameScheduler)
    }

    private fun createRenderables() {
        val buffer = activity.assets.open(fileName).use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            ByteBuffer.wrap(bytes)
        }
        modelViewer.loadModelGlb(buffer)
     test =   modelViewer.transformToUnitCube()
    }

    private fun createIndirectLight() {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl = "venetian_crossroads_2k"
        readCompressedAsset("${ibl}_ibl.ktx").let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
            scene.indirectLight!!.intensity = 30_000.0f
        }
        readCompressedAsset("${ibl}_skybox.ktx").let {
            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }
    }

    private fun configureViewer() {
        modelViewer.view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
        modelViewer.renderer.clearOptions = modelViewer.renderer.clearOptions.apply { clear = true }
        modelViewer.view.apply {
            renderQuality = renderQuality.apply {
                hdrColorBuffer = com.google.android.filament.View.QualityLevel.MEDIUM
            }
            dynamicResolutionOptions = dynamicResolutionOptions.apply {
                enabled = true
                quality = com.google.android.filament.View.QualityLevel.MEDIUM
            }
            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply { enabled = true }
            antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
            ambientOcclusionOptions = ambientOcclusionOptions.apply { enabled = true }
            bloomOptions = bloomOptions.apply { enabled = true }
        }
    }

    private fun readCompressedAsset(assetName: String): ByteBuffer {
        val input = activity.assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)

            modelViewer.animator?.apply {
                if (animationCount > 0 && animationIndex <= animationCount - 1) {
                    val elapsedTimeSeconds = (frameTimeNanos - startTime).toDouble() / 1_000_000_000
                    applyAnimation(0, elapsedTimeSeconds.toFloat())
                }
                updateBoneMatrices()
            }

            modelViewer.render(frameTimeNanos)
        }
    }
}



//        surfaceView.setOnTouchListener { _, event ->
//            when (event.actionMasked) {
//                MotionEvent.ACTION_DOWN -> {
//                    // Store the initial touch positions for orbit
//                    previousX = event.x
//                    previousY = event.y
//                    true
//                }
//
//                MotionEvent.ACTION_POINTER_UP -> {
//                    // Reset zoom variables when the second pointer is lifted
//                    initialDistance = 0f
//                    true
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    // Reset when gesture ends
//                    initialDistance = 0f
//                    true
//                }
//
//                MotionEvent.ACTION_MOVE -> {
//                    when (event.pointerCount) {
//                        1 -> {
//                            // Orbit (rotate) gesture with one finger
//                            val deltaX = event.x - previousX
//                            val deltaY = event.y - previousY
//
//                            val sensitivity = 0.2f
//
//                            // Apply pitch (rotation around X-axis) and yaw (rotation around Y-axis)
//                            val rotationX = Quaternion(Vector3(1f, 0f, 0f), deltaY * sensitivity)
//                            val rotationY = Quaternion(Vector3(0f, 1f, 0f), deltaX * sensitivity)
//
//                            // Update current rotation by combining new rotations
//                            currentRotation = rotationY.mul(currentRotation).mul(rotationX).nor()
//
//                            // Apply both scaling and rotation transformations together
//                            applyTransformations(Matrix4().setToScaling(currentScale, currentScale, currentScale), currentRotation)
//
//                            // Update previous touch positions
//                            previousX = event.x
//                            previousY = event.y
//                        }
//                        2 -> {
//                            // Pinch-to-zoom gesture with two fingers
//                            val x0 = event.getX(0)
//                            val y0 = event.getY(0)
//                            val x1 = event.getX(1)
//                            val y1 = event.getY(1)
//
//                            val distance = kotlin.math.sqrt(
//                                ((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).toDouble()
//                            ).toFloat()
//
//                            // Calculate the scale factor based on distance between two fingers
//                            if (initialDistance == 0f) {
//                                initialDistance = distance
//                            } else {
//                                val scaleFactor = distance / initialDistance
//                                currentScale *= scaleFactor
//                                initialDistance = distance
//
//                                // Apply both scaling and rotation transformations together
//                                applyTransformations(Matrix4().setToScaling(currentScale, currentScale, currentScale), currentRotation)
//                            }
//                        }
//                    }
//                    true
//                }
//            }
//            true
//        }


//package com.example.test_3d_new
//
//import android.annotation.SuppressLint
//import android.content.Context
//import android.view.*
//import com.google.android.filament.View
//import com.google.android.filament.android.UiHelper
//import com.google.android.filament.utils.*
//
//import io.flutter.plugin.platform.PlatformView
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import com.badlogic.gdx.math.Quaternion
//import com.badlogic.gdx.math.Vector3
//import com.badlogic.gdx.math.Matrix4
//
//@SuppressLint("ClickableViewAccessibility")
//class MyThreediView(
//    private val context: Context,
//    private val viewId: Int,
//    private val creationParams: Map<String?, Any?>?,
//    private val activity: MainActivity,
//) : PlatformView {
//
//    companion object {
//        init {
//            Utils.init()
//        }
//    }
//
//    private var fileName = ""
//    private var animationIndex = 0
//    private var previousX: Float = 0f
//    private var previousY: Float = 0f
//
//    // Choreographer is used to schedule new frames
//    private var choreographer: Choreographer
//
//    private var modelViewer: ModelViewer
//    private var uiHelper: UiHelper
//    var currentRotation = Quaternion(0f, 0f, 0f, 1f)
//
//    // Performs the rendering and schedules new frames
//    private val frameScheduler = FrameCallback()
//    private val viewerContent = AutomationEngine.ViewerContent()
//    private val surfaceView: SurfaceView = SurfaceView(context)
//    private var initialDistance: Float = 0f
//    private var currentScale: Float = 1f
//
//    init {
//        fileName = creationParams?.get("fileNameWithExtension").toString()
//        animationIndex = Integer.parseInt(creationParams?.get("animationIndex").toString())
//
//        val layoutParams: ViewGroup.LayoutParams =
//            ViewGroup.LayoutParams(
//                ViewGroup.LayoutParams.MATCH_PARENT,
//                ViewGroup.LayoutParams.MATCH_PARENT
//            )
//
//        surfaceView.layoutParams = layoutParams
//
//        choreographer = Choreographer.getInstance()
//        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
//
//        modelViewer = ModelViewer(surfaceView = surfaceView, uiHelper = uiHelper)
//
//
//        surfaceView.setOnTouchListener { _, event ->
//            when (event.actionMasked) {
//                MotionEvent.ACTION_DOWN -> {
//                    // Store the initial touch positions for orbit
//                    previousX = event.x
//                    previousY = event.y
//                    true
//                }
//
//                MotionEvent.ACTION_POINTER_UP -> {
//                    // Reset zoom variables when the second pointer is lifted
//                    initialDistance = 0f
//                    true
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    // Reset when gesture ends
//                    initialDistance = 0f
//                    true
//                }
//
//                MotionEvent.ACTION_MOVE -> {
//                    when (event.pointerCount) {
//                        1 -> {
//                            // Orbit (rotate) gesture with one finger
//                            val deltaX = event.x - previousX
//                            val deltaY = event.y - previousY
//
//                            val sensitivity = 0.2f
//
//                            // Apply pitch (rotation around X-axis) and yaw (rotation around Y-axis)
//                            val rotationX = Quaternion(Vector3(1f, 0f, 0f), deltaY * sensitivity)
//                            val rotationY = Quaternion(Vector3(0f, 1f, 0f), deltaX * sensitivity)
//
//                            // Update current rotation by combining new rotations
//                            currentRotation = rotationY.mul(currentRotation).mul(rotationX).nor()
//
//                            // Apply both scaling and rotation transformations together
//                            applyTransformations(Matrix4().setToScaling(currentScale, currentScale, currentScale), currentRotation)
//
//                            // Update previous touch positions
//                            previousX = event.x
//                            previousY = event.y
//                        }
//                        2 -> {
//                            // Pinch-to-zoom gesture with two fingers
//                            val x0 = event.getX(0)
//                            val y0 = event.getY(0)
//                            val x1 = event.getX(1)
//                            val y1 = event.getY(1)
//
//                            val distance = kotlin.math.sqrt(
//                                ((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).toDouble()
//                            ).toFloat()
//
//                            // Calculate the scale factor based on distance between two fingers
//                            if (initialDistance == 0f) {
//                                initialDistance = distance
//                            } else {
//                                val scaleFactor = distance / initialDistance
//                                currentScale *= scaleFactor
//                                initialDistance = distance
//
//                                // Apply both scaling and rotation transformations together
//                                applyTransformations(Matrix4().setToScaling(currentScale, currentScale, currentScale), currentRotation)
//                            }
//                        }
//                    }
//                    true
//                }
//            }
//            true
//        }
//
//        createRenderables()
//        createIndirectLight()
//        configureViewer()
//    }
//
//    private fun applyTransformations(scaleMatrix: Matrix4, rotation: Quaternion) {
//        // Combine rotation and scaling into a single matrix
//        val combinedMatrix = Matrix4().set(rotation).mul(scaleMatrix)
//
//        // Apply the transformation to the model
//        val tm = modelViewer.engine.transformManager
//        val entity = modelViewer.asset?.root ?: error("Model root entity is null")
//        val instance = tm.getInstance(entity)
//        tm.setTransform(instance, combinedMatrix.values)
//    }
//
//    override fun getView(): android.view.View {
//        choreographer.postFrameCallback(frameScheduler)
//        return surfaceView
//    }
//
//    override fun dispose() {
//        choreographer.removeFrameCallback(frameScheduler)
//    }
//
//    fun onFlutterViewAttached(flutterView: View) {
//        choreographer.postFrameCallback(frameScheduler)
//    }
//
//    override fun onFlutterViewDetached() {
//        choreographer.removeFrameCallback(frameScheduler)
//    }
//
//    private fun createRenderables() {
//        val buffer = activity.assets.open(fileName).use { input ->
//            val bytes = ByteArray(input.available())
//            input.read(bytes)
//            ByteBuffer.allocateDirect(bytes.size).apply {
//                order(ByteOrder.nativeOrder())
//                put(bytes)
//                rewind()
//            }
//            ByteBuffer.wrap(bytes)
//        }
//        modelViewer.loadModelGlb(buffer)
//        modelViewer.transformToUnitCube()
//    }
//
//    private fun createIndirectLight() {
//        val engine = modelViewer.engine
//        val scene = modelViewer.scene
//        val ibl = "venetian_crossroads_2k"
//        readCompressedAsset("${ibl}_ibl.ktx").let {
//            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
//            scene.indirectLight!!.intensity = 30_000.0f
//            viewerContent.indirectLight = modelViewer.scene.indirectLight
//        }
//        readCompressedAsset("${ibl}_skybox.ktx").let {
//            scene.skybox = KTX1Loader.createSkybox(engine, it)
//        }
//    }
//
//    private fun configureViewer() {
//        modelViewer.view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
//        modelViewer.renderer.clearOptions = modelViewer.renderer.clearOptions.apply { clear = true }
//        modelViewer.view.apply {
//            renderQuality = renderQuality.apply {
//                hdrColorBuffer = com.google.android.filament.View.QualityLevel.MEDIUM
//            }
//            dynamicResolutionOptions = dynamicResolutionOptions.apply {
//                enabled = true
//                quality = com.google.android.filament.View.QualityLevel.MEDIUM
//            }
//            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply { enabled = true }
//            antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
//            ambientOcclusionOptions = ambientOcclusionOptions.apply { enabled = true }
//            bloomOptions = bloomOptions.apply { enabled = true }
//        }
//    }
//
//    private fun readCompressedAsset(assetName: String): ByteBuffer {
//        val input = activity.assets.open(assetName)
//        val bytes = ByteArray(input.available())
//        input.read(bytes)
//        return ByteBuffer.wrap(bytes)
//    }
//
//
//
//    inner class FrameCallback : Choreographer.FrameCallback {
//        private val startTime = System.nanoTime()
//        override fun doFrame(frameTimeNanos: Long) {
//            choreographer.postFrameCallback(this)
//
//            modelViewer.animator?.apply {
//                if (animationCount > 0 && animationIndex <= animationCount - 1) {
//                    val elapsedTimeSeconds = (frameTimeNanos - startTime).toDouble() / 1_000_000_000
//                    applyAnimation(0, elapsedTimeSeconds.toFloat())
//                }
//                updateBoneMatrices()
//            }
//
//            modelViewer.render(frameTimeNanos)
//        }
//    }
//}




//        surfaceView.setOnTouchListener { _, event ->
//            when (event.actionMasked) {
//                MotionEvent.ACTION_DOWN -> {
//                    // Store initial touch positions for orbit (rotation)
//                    previousX = event.x
//                    previousY = event.y
//                    true
//                }
//
//                MotionEvent.ACTION_POINTER_UP -> {
//                    // Reset zoom variables when the second pointer is lifted
//                    initialDistance = 0f
//                    true
//                }
//
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    // Reset when gesture ends
//                    initialDistance = 0f
//                    true
//                }
//
//                MotionEvent.ACTION_MOVE -> {
//                    when (event.pointerCount) {
//                        1 -> {
//                            // Only move when there is a valid move, ignore small taps or insignificant movement
//                            val deltaX = event.x - previousX
//                            val deltaY = event.y - previousY
//
//                            // If movement is significant, then rotate
//                            if (kotlin.math.abs(deltaX) > 2 || kotlin.math.abs(deltaY) > 2) {
//                                val sensitivity = 0.2f
//
//                                // Apply pitch (rotation around X-axis) and yaw (rotation around Y-axis)
//                                val rotationX = Quaternion(Vector3(1f, 0f, 0f), deltaY * sensitivity)
//                                val rotationY = Quaternion(Vector3(0f, 1f, 0f), deltaX * sensitivity)
//
//                                // Update current rotation by combining new rotations
//                                currentRotation = rotationY.mul(currentRotation).mul(rotationX).nor()
//
//                                // Apply both scaling and rotation transformations together
//                                applyTransformations(
//                                    Matrix4().setToScaling(currentScale, currentScale, currentScale),
//                                    currentRotation
//                                )
//
//                                // Update previous touch positions
//                                previousX = event.x
//                                previousY = event.y
//                            }
//                        }
//                        2 -> {
//                            // Handle pinch-to-zoom gesture with two fingers
//                            val x0 = event.getX(0)
//                            val y0 = event.getY(0)
//                            val x1 = event.getX(1)
//                            val y1 = event.getY(1)
//
//                            val distance = kotlin.math.sqrt(
//                                ((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0)).toDouble()
//                            ).toFloat()
//
//                            // If initialDistance is 0, initialize it
//                            if (initialDistance == 0f) {
//                                initialDistance = distance
//                            } else {
//                                // Calculate the scale factor based on distance between two fingers
//                                val scaleFactor = distance / initialDistance
//                                currentScale *= scaleFactor
//                                initialDistance = distance
//
//                                // Apply both scaling and rotation transformations together
//                                applyTransformations(
//                                    Matrix4().setToScaling(currentScale, currentScale, currentScale),
//                                    currentRotation
//                                )
//                            }
//                        }
//                    }
//                    true
//                }
//            }
//            true
//        }
//