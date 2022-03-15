package com.able.androidcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 具有Camera2 设备文档中指定的设备级别要求 ：
 * Preview + VideoCapture + ImageCapture： LIMITED设备及以上。
 * Preview + VideoCapture + ImageAnalysis：（ LEVEL_3最高）设备添加到 Android 7(N)。
 * 预览 + VideoCapture + ImageAnalysis + ImageCapture：不支持。
 */
class MyCameraActivity : AppCompatActivity() {

    //TODO 以下值可能为空
    private lateinit var mCameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var mPvV: PreviewView
    private lateinit var mPv: Preview
    private lateinit var mProcessCameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture

    //分析器
    private lateinit var mImageAnalyzer: ImageAnalysis
    //TODO 以上值可能为空

    //相机执行器
    private lateinit var mCameraExecutor: ExecutorService

    //
//    private lateinit var mImageCapture: ImageCapture
    //录像
    private lateinit var mVideoCapture: VideoCapture<Recorder>
    private lateinit var mRecorder: Recorder
    private var mCurrentRecording: Recording? = null


    private var mCameraLensFacing: Int? = CameraSelector.LENS_FACING_BACK

    private var mBtnCatureVideo: Button? = null

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

    //录像 质量选择器
    val qualitySelector = QualitySelector.fromOrderedList(
        listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_camera)
        //获取设备级别
        /**
         * LEGACY 设备在旧 Android 设备的向后兼容模式下运行，并且功能非常有限。
         * LIMITED 设备代表基线功能集，还可能包括作为 FULL 子集的附加功能。
         * FULL 设备还支持每帧手动控制传感器、闪光灯、镜头和后处理设置，以及高速图像捕获。
         * LEVEL_3 设备还支持 YUV 再处理和 RAW 图像捕获，以及额外的输出流配置。
         */
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraManager.cameraIdList.map {
            val characteristics = cameraManager.getCameraCharacteristics(it)
           val deviceLevel= characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            Log.i("当前摄像头兼容级别$it---",""+deviceLevel)
        }







        mCameraExecutor = Executors.newSingleThreadExecutor()
        mPvV = findViewById(R.id.pv)
        mBtnCatureVideo = findViewById(R.id.btnCatureVideo)
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

        /**
         * 切换摄像头
         */
        findViewById<Button>(R.id.btnDirection).setOnClickListener {
            mCameraLensFacing.let {
                if (it == CameraSelector.LENS_FACING_FRONT)
                    bindPreview(
                        mProcessCameraProvider,
                        cameraBackSelector,
                        mImageAnalyzer, imageCapture
                    )
                else
                    bindPreview(
                        mProcessCameraProvider,
                        cameraFrontSelector,
                        mImageAnalyzer,
                        imageCapture
                    )
            }
        }

        /**
         * 拍照
         */
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            takePhoto()
        }
        /**
         * 录制
         */
        findViewById<Button>(R.id.btnCatureVideo).setOnClickListener {
            captureVideo()
        }

    }

//    private fun startRecording() {
//        // create MediaStoreOutputOptions for our recorder: resulting our recording!
//        val name = "CameraX-recording-" +
//                SimpleDateFormat(FILENAME_FORMAT, Locale.US)
//                    .format(System.currentTimeMillis()) + ".mp4"
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Video.Media.DISPLAY_NAME, name)
//        }
//        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
//            contentResolver,
//            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
//            .setContentValues(contentValues)
//            .build()
//
//        // configure Recorder and Start recording to the mediaStoreOutput.
//        mCurrentRecording = mVideoCapture.output
//            .prepareRecording(this, mediaStoreOutput)
//            .apply { if (audioEnabled) withAudioEnabled() }
//            .start(mainThreadExecutor, captureListener)
//
//        Log.i("当前录像", "Recording started")
//    }

    private fun captureVideo() {
        val videoCapture = this.mVideoCapture ?: return

        mBtnCatureVideo?.isEnabled = false

        val curRecording = mCurrentRecording
        if (curRecording != null) {
            // 停止当前录制会话。
            curRecording.stop()
            mCurrentRecording = null
            return
        }

        // 创建并开始一个新的录制会话
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        mCurrentRecording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        this@MyCameraActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        mBtnCatureVideo?.apply {
                            text = "停止录像"
                            isEnabled = true
                        }
//                        viewBinding.videoCaptureButton.apply {
//                            text = getString(R.string.stop_capture)
//                            isEnabled = true
//                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "视频拍摄成功: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d("video消息", msg)
                        } else {
                            mCurrentRecording?.close()
                            mCurrentRecording = null
                            Log.e(
                                "video消息", "视频捕获以错误结束: " +
                                        "${recordEvent.error}"
                            )
                        }
                        mBtnCatureVideo?.apply {
                            text = "开始录像"
                            isEnabled = true
                        }

                    }
                }
            }
    }

    private fun takePhoto() {
        //获取可修改图像捕获用例的稳定参考
        val imageCapture = imageCapture ?: return

        // 创建时间戳名称和 MediaStore 条目。
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // 创建包含文件 + 元数据的输出选项对象
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        //设置抓图监听器，拍照后触发
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("拍照", "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d("拍照", msg)
                }
            }
        )
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val tv = findViewById<TextView>(R.id.tv)

        //检查cameraProvider可用性
        mCameraProviderFuture.addListener(
            {
                mProcessCameraProvider = mCameraProviderFuture.get();
                mPv = Preview.Builder().build()
                //preview连接到PreviewView上
                mPv.setSurfaceProvider(mPvV.surfaceProvider)

                //分析器
                mImageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(mCameraExecutor, LuminosityAnalyzer { lumino ->
                            runOnUiThread {
                                tv.text = "图像平均亮度：$lumino"
                            }
                        })
                    }
                //拍照
                imageCapture = ImageCapture.Builder()
                    .build()
                //录像
                mRecorder = Recorder.Builder()
                    .setExecutor(mCameraExecutor).setQualitySelector(qualitySelector)
                    .build()
                mVideoCapture = VideoCapture.withOutput(mRecorder)

                try {
                    // 绑定录像功能
                    mProcessCameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, mPv, mVideoCapture, imageCapture
                    )
                } catch (exc: Exception) {
                    Log.e("绑定相机", "Use case binding failed", exc)
                }

//                imageCapture?.let {
//                    //标记方向
//                    bindPreview(
//                        mProcessCameraProvider,
//                        cameraBackSelector,
//                        mImageAnalyzer,
//                        it
//                    )
//                }


            },
            //分派主线程
            ContextCompat.getMainExecutor(this)
        )
    }


    private class LuminosityAnalyzer(private val listener: (double: Double) -> Unit) :
        ImageAnalysis.Analyzer {

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
            Log.i("亮度监听", "" + luma)
            //亮度监听器
            listener(luma)
            image.close()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun bindPreview(
        cameraProvider: ProcessCameraProvider,
        cameraSelector: CameraSelector,
        vararg useCases: UseCase
    ) {
        cameraProvider.unbindAll()
        if (useCases.size == 2) {


            //相机与用例绑定到生命周期上  cameraSelector用例
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                mPv,
                useCases[0],
                useCases[1],
                useCases[2]
            )
            mCameraLensFacing = cameraSelector.lensFacing
        }


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