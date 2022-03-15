package com.able.androidcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MyCameraActivity : AppCompatActivity() {

    private lateinit var mCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var mPvV: PreviewView
    private lateinit var mPv: Preview
    private lateinit var mProcessCameraProvider: ProcessCameraProvider
    //相机执行器
    private lateinit var mCameraExecutor: ExecutorService

    //
//    private lateinit var mImageCapture: ImageCapture
    private var mCameraLensFacing: Int? = CameraSelector.LENS_FACING_BACK

    //分析器
    private lateinit var mImageAnalyzer: ImageAnalysis

    //前置摄像头
    private val cameraFrontSelector =
        CameraSelector.Builder()
            //镜头方向
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

    //后置摄像头
    private val cameraBackSelector =
        CameraSelector.Builder()
            //镜头方向
            .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_camera)
        mCameraExecutor = Executors.newSingleThreadExecutor()


        mPvV = findViewById(R.id.pv)
        mPvV.apply {
            implementationMode =
                    //性能模式
                PreviewView.ImplementationMode.PERFORMANCE
            //兼容模式
//            PreviewView.ImplementationMode.COMPATIBLE
            //使用不同分辨率时，控制缩放模式
//            scaleType=PreviewView.ScaleType.FIT_CENTER
        }

        //检查权限
        if (allPermissionsGranted()) {
            mCameraProviderFuture = ProcessCameraProvider.getInstance(this)
            startCamera()
        } else {
            //权限申请
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }


        findViewById<Button>(R.id.btnDirection).setOnClickListener {
            mCameraLensFacing.let {
                if (it == CameraSelector.LENS_FACING_FRONT)
                    bindPreview(
                        mProcessCameraProvider,
                        cameraBackSelector,
                        mImageAnalyzer
                    )
                else
                    bindPreview(mProcessCameraProvider, cameraFrontSelector,mImageAnalyzer)
            }
        }
    }

    private fun startCamera() {
        val tv = findViewById<TextView>(R.id.tv)

        //检查cameraProvider可用性
        mCameraProviderFuture.addListener(
            {
                mProcessCameraProvider = mCameraProviderFuture.get();
                mPv = Preview.Builder().build()
                //preview连接到PreviewView上
                mPv.setSurfaceProvider(mPvV.surfaceProvider)

                mImageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(mCameraExecutor, LuminosityAnalyzer{ lumino->
                            runOnUiThread {
                                tv.text = "图像平均亮度：$lumino"
                            }
                        })
                    }


                //标记方向
                bindPreview(mProcessCameraProvider, cameraBackSelector,mImageAnalyzer)
            },
            //分派主线程
            ContextCompat.getMainExecutor(this)
        )
    }



    private class LuminosityAnalyzer(private val listener: (double: Double) -> Unit) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()
            Log.i("亮度监听",""+luma)
            //亮度监听器
            listener(luma)
            image.close()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun bindPreview(cameraProvider: ProcessCameraProvider, cameraSelector: CameraSelector,imageAnalyzer:ImageAnalysis) {
        cameraProvider.unbindAll()
        //相机与用例绑定到生命周期上  cameraSelector用例
        cameraProvider.bindToLifecycle(this, cameraSelector, mPv,imageAnalyzer)
        mCameraLensFacing = cameraSelector.lensFacing

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        mCameraExecutor.shutdown()
    }


    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}