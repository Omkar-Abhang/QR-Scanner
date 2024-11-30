package com.example.qrscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage


import java.io.IOException

@androidx.camera.core.ExperimentalGetImage
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var selectImageButton: Button

    // This will store the URI of the selected image
    private var imageUri: Uri? = null

//   check the last url
    private var lastScannedUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        selectImageButton = findViewById(R.id.selectImageButton)
        barcodeScanner = BarcodeScanning.getClient()



        // Check for camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission()
        }


        // Set click listener for the button to open gallery
        selectImageButton.setOnClickListener {
            openGallery()
        }
    }

    private fun requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            // Show an explanation to the user
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_SHORT).show()
        }
        // Request the camera permission
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Build the preview use case
                val preview = androidx.camera.core.Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Build the image analysis use case
                val imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder().build()
                imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    handleBarcode(barcode)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                            .addOnFailureListener { e ->
                                Log.e("QRCodeScanner", "Barcode scanning failed", e)
                            }
                    }
                }

                // Select back camera
                val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind and rebind use cases to lifecycle
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)

            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera: ${exc.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleBarcode(barcode: Barcode) {
        barcode.rawValue?.let { qrCode ->
            // Check if the QR code contains a URL
            if (qrCode.startsWith("http://") || qrCode.startsWith("https://")) {
                // Prevent opening the same URL multiple times
                if (qrCode != lastScannedUrl) {
                    lastScannedUrl = qrCode
                    openLinkInBrowser(qrCode)
                } else {
                    Toast.makeText(this, "This link is already open", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openLinkInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK // Forces opening a new browser task
            }
            startActivity(intent)  // Open the URL in the default browser
        } catch (e: Exception) {
            Log.e("QRCodeScanner", "Failed to open URL: $url", e)
            Toast.makeText(this, "Error opening URL", Toast.LENGTH_SHORT).show()
        }
    }


    // Open the Gallery and pick an image
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        galleryResultLauncher.launch(intent)
    }

    // Use ActivityResultContracts for modern activity result APIs
    private val galleryResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                imageUri = result.data?.data
                imageUri?.let {
                    scanImageFromGallery(it)
                }
            }
        }

    // Scan the selected image from gallery
    private fun scanImageFromGallery(imageUri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            val image = InputImage.fromBitmap(bitmap, 0)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        handleBarcode(barcode)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("QRCodeScanner", "Image scanning failed", e)
                    Toast.makeText(this, "Error scanning image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: IOException) {
            Log.e("QRCodeScanner", "Error loading image from gallery", e)
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

}
