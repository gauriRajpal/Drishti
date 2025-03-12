package com.example.drishti

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import android.media.Image
import java.nio.ByteBuffer
import android.graphics.ImageFormat
import android.graphics.BitmapFactory
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import org.tensorflow.lite.support.image.TensorImage

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var previewView: PreviewView
    private lateinit var objectDetector: ObjectDetector
    private var isProcessing = false
    private lateinit var labels: List<String>
    // Prevent multiple detections at once

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.cameraPreview)
        val captureButton: Button = findViewById(R.id.captureButton)

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this, this)
        speak("Hello, testing voice output!")

        initializeObjectDetector()

        // Initialize CameraX
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        fun allPermissionsGranted(): Boolean {
            return REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        captureButton.setOnClickListener {
            isProcessing = false // Reset flag for next detection
        }
    }

    fun processDetectedObjects(detectedObjects: List<String>) {
        if (detectedObjects.isNotEmpty()) {
            val detectedObjectLabel = detectedObjects[0] // Get the first detected object label
            onObjectDetected(detectedObjectLabel)
        }
    }

    fun onObjectDetected(objectLabel: String) {
        // Speak the detected object label using Text-to-Speech
        textToSpeech.speak("Detected: $objectLabel", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun initializeObjectDetector() {
        try {
            val modelFile = "ssd_mobilenet_v1_1_metadata_1.tflite" // Ensure this file is in `assets`
            labels = loadLabels() // Load labels from `labelmap.txt`

            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
            val objectDetector = ObjectDetection.getClient(options)


            Log.d("ObjectDetector", "Model loaded successfully with labels: $labels") // Debugging
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Failed to load model", e)
        }
    }

    private fun loadLabels(): List<String> {
        val labels = mutableListOf<String>()
        assets.open("labelmap.txt").bufferedReader().useLines { lines ->
            lines.forEach { labels.add(it) }
        }
        Log.d("ObjectDetection", "Loaded labels: $labels")
        return labels
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val previewView = findViewById<PreviewView>(R.id.cameraPreview)

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // ✅ Image Analysis Setup
                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (!isProcessing) {
                        isProcessing = true
                        analyzeImage(imageProxy)
                    }
                }

                // ✅ Bind CameraX to Lifecycle
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

            } catch (exc: Exception) {
                Log.e("CameraX", "Failed to start camera", exc)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun yuvToByteArray(image: Image): ByteArray {
        val yuvImage = YuvImage(
            image.planes[0].buffer.array(),
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, outputStream)
        return outputStream.toByteArray()
    }
    private fun mediaImageToBitmap(image: Image, rotationDegrees: Int): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate the bitmap to match camera rotation
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true // Prevent multiple detections at once

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = mediaImageToBitmap(mediaImage, rotationDegrees)

            Log.d("ObjectDetection", "Running object detection on the image...") // Debugging

            try {
                // Convert bitmap to TensorFlow format
                val tensorImage = TensorImage.fromBitmap(bitmap)
                val results = objectDetector.detect(tensorImage)

                runOnUiThread {
                    if (results.isNotEmpty()) {
                        val detectedObjects = mutableListOf<String>()

                        for (result in results) {
                            val category = result.categories.firstOrNull()
                            val objectName = category?.label ?: "Unknown object"
                            val confidence = category?.score ?: 0f

                            detectedObjects.add("$objectName (${(confidence * 100).toInt()}%)")
                            Log.d("ObjectDetection", "Detected: $objectName with confidence: ${(confidence * 100).toInt()}%")
                        }

                        val detectedText = detectedObjects.joinToString(", ")
                        speak("Detected objects: $detectedText") // Speak detected objects
                    } else {
                        Log.d("ObjectDetection", "No objects detected")
                        speak("No objects detected")
                    }
                }

            } catch (e: Exception) {
                Log.e("ObjectDetection", "Failed to detect objects", e)
                speak("Detection failed")
            } finally {
                imageProxy.close()
                isProcessing = false
            }
        }
    }


    // Text-to-Speech Output
    private fun speak(text: String) {
        Log.d("TextToSpeech", "Speaking: $text")
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // Permission Checking Function
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // ✅ Added this line

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US
            speak("Hello, Welcome to Drishti") // ✅ Moved to onInit()
        } else {
            Log.e("TextToSpeech", "Initialization failed!")
            Toast.makeText(this, "TTS Initialization failed!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}