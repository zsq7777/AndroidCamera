package com.able.androidcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture

class MyCameraActivity : AppCompatActivity() {

    private lateinit var mCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var mPvV: PreviewView
    private lateinit var mPv: Preview
    private lateinit var mProcessCameraProvider: ProcessCameraProvider

    //
//    private lateinit var mImageCapture: ImageCapture
    private var mCameraLensFacing: Int? = CameraSelector.LENS_FACING_BACK

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
                        cameraBackSelector
                    )
                else
                    bindPreview(mProcessCameraProvider, cameraFrontSelector)
            }
        }
    }

    private fun startCamera() {

        //检查cameraProvider可用性
        mCameraProviderFuture.addListener(
            {
                mProcessCameraProvider = mCameraProviderFuture.get();
                mPv = Preview.Builder().build()
                //preview连接到PreviewView上
                mPv.setSurfaceProvider(mPvV.surfaceProvider)


                //标记方向
                bindPreview(mProcessCameraProvider, cameraBackSelector)
            },
            //分派主线程
            ContextCompat.getMainExecutor(this)
        )
    }

    @SuppressLint("RestrictedApi")
    private fun bindPreview(cameraProvider: ProcessCameraProvider, cameraSelector: CameraSelector) {
        cameraProvider.unbindAll()
        //相机与用例绑定到生命周期上  cameraSelector用例
        cameraProvider.bindToLifecycle(this, cameraSelector, mPv)
        mCameraLensFacing = cameraSelector.lensFacing

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
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