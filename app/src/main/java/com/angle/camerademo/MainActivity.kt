package com.angle.camerademo

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.contentValuesOf
import com.angle.camerademo.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mainBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    // 默认后置摄像头
    private var selectCameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    companion object {
        // 这个是请求权限的请求码
        private const val REQUEST_PERMISSION_CODE = 0x10

        private const val FILE_NAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        // 关于权限
        // camera , record audio , write
        // 其实前两个权限是不区分版本的,
        // 而write是有版本问题的,如果版本大于12的情况下不需要申请,小于12的情况下需要申请.
        // 而且好好看看这里面的写法, 很不错的

        private val REQUEST_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置布局
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)

        if (allPermissionGranted()) {
            //有权限
            startCamera()
        } else {
            //这个只是针对于当前页面申请的权限
            ActivityCompat.requestPermissions(this, REQUEST_PERMISSIONS, REQUEST_PERMISSION_CODE)
        }

        mainBinding.takePhotoBtn.setOnClickListener {
            takePhoto()
        }

        mainBinding.switchCamera.setOnClickListener {
            switchCamera()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (allPermissionGranted()) {
                //申请好了相关的权限
                Log.e("CameraXDemo", "onRequestPermissionsResult: 权限已经申请完成")
                startCamera()
            } else {
                Toast.makeText(this, "权限没有申请成功哦!!!", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 申请权限
     * 这里的这个all其实就是一个遍历.kotlin的扩展方法.
     * 这里面有必要说明一个事情,关于第一个参数传入的内容对权限的影响.
     * 1. Activity -> 只针对与当前页面授权有效
     * 2. AppContext  -> 整个app的授权都有效
     * 3. BaseContext  ->  介于两者中间.是让你选一次还是永久
     */
    private fun allPermissionGranted(): Boolean = REQUEST_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun takePhoto() {
        Log.e("CameraXDemo", "takePhoto: ~~~~")

        val mImageCapture = imageCapture ?: return

        val name =
            SimpleDateFormat(FILE_NAME_FORMAT, Locale.CHINA).format(System.currentTimeMillis())

        // 3. 创建配置参数
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // 2. 创建输出的OutputFileOptions对象,
        // 参数1 contentResolver 对象
        // 参数2 saveCollection 保存的uri
        // 参数3 contentValues 配置的参数
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // 1. 使用takePicture对图片进行保存
        mImageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),//使用主线程
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.e("CameraXDemo", "onImageSaved: 保存图片成功 ${outputFileResults.savedUri}")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraXDemo", "onError: 保存图片失败 $exception")
                }
            })
    }

    private fun switchCamera() {
        // 1. 切换摄像头

        selectCameraSelector = if (selectCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // 2. 重新调用startCamera
        startCamera()
    }

    private fun startCamera() {
        // 1. 获取CameraProviderFuture
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        //2. 设置监听器
        cameraProviderFuture.addListener({
            //3. 获取CameraProvider对象
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 获取preView对象
            val preView = Preview.Builder().build().also {
                // 把相机的内容输入到Provider中
                it.setSurfaceProvider(mainBinding.viewFinder.surfaceProvider)
            }

            // 创建ImageCapture
            imageCapture = ImageCapture.Builder().build()

            try {
                // 解绑, 这里解绑所有的内容,避免其他程序绑定
                cameraProvider.unbindAll()
                // 4. 绑定到哪个Lifecycle上. 获取Camera对象
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    selectCameraSelector,
                    preView,
                    imageCapture
                )

                // 以下是参数设置......通过Camera
                // 1. 获取CameraControl对象
                val cameraControl = camera.cameraControl

                // 获取CameraInfo 这个里面可以获取到一些配置信息
                val cameraInfo = camera.cameraInfo

                // 闪光灯的操作
                // cameraControl.enableTorch(true)

                // 关于手势的识别
                // 1. 创建手势的代理对象
                val gestureDetector =
                    GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                        override fun onScroll(
                            e1: MotionEvent?, // 手指1的motionEvent
                            e2: MotionEvent, // 手指2的motionEvent
                            distanceX: Float,
                            distanceY: Float
                        ): Boolean {
//                            return super.onScroll(e1, e2, distanceX, distanceY)

                            // 获取原始的缩放比例
                            val curZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1f

                            // 获取y轴上的距离
                            val delta = (e1?.y ?: 0f) - e2.y

                            // 获取缩放/放大的比例
                            val scale = delta / mainBinding.viewFinder.height.toFloat()

                            // 计算出新的缩放比例
                            val zoomRatio = curZoomRatio + scale

                            // 从cameraInfo中获取最大的缩放比
                            val maxZoomRatio = cameraInfo.zoomState.value?.maxZoomRatio ?: 1f

                            // 设置缩放比例, 确保此值在这个0和最大的缩放比中间
                            cameraControl.setZoomRatio(zoomRatio.coerceIn(0f, maxZoomRatio))

                            return true
                        }
                    })

                // 2. Preview 设置时间监听
                mainBinding.viewFinder.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    true
                }
            } catch (e: Exception) {
                Log.e("CameraXDemo", "startCamera: 绑定异常")
            }
        }, ContextCompat.getMainExecutor(this))
    }
}