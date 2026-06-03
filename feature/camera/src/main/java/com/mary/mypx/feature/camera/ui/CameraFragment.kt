package com.mary.mypx.feature.camera.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.mary.mypx.domain.model.CameraState
import com.mary.mypx.feature.camera.R
import com.mary.mypx.feature.camera.databinding.FragmentCameraBinding
import com.mary.mypx.feature.camera.viewmodel.CameraViewModel
import com.mary.mypx.sdk.BeautyTextureView
import com.mary.mypx.sdk.CameraManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 相机 Fragment - 使用 CameraX + OpenGL + TextureView 架构
 * 
 * 【架构设计】
 * 
 * ┌─────────────────────────────────────────────────────────┐
 * │                    CameraX                              │
 * │                       │                                │
 * │                       ▼                                │
 * │              SurfaceTexture (OES)                      │
 * │                       │                                │
 * │                       ▼                                │
 * │         ┌─────────────────────────┐                   │
 * │         │   BeautyTextureView     │                   │
 * │         │   (OpenGL ES 2.0)       │                   │
 * │         │                         │                   │
 * │         │  ┌───────────────────┐ │                   │
 * │         │  │  美颜着色器        │ │                   │
 * │         │  │  - 双边滤波       │ │                   │
 * │         │  │  - 亮度调整       │ │                   │
 * │         │  │  - 对比度调整     │ │                   │
 * │         │  │  - 饱和度调整     │ │                   │
 * │         │  └───────────────────┘ │                   │
 * │         │                         │                   │
 * │         │      显示到屏幕         │                   │
 * │         └─────────────────────────┘                   │
 * └─────────────────────────────────────────────────────────┘
 * 
 * 【美颜控制】
 * 使用 SeekBar 控制美颜强度：
 * - 0%: 无美颜
 * - 50%: 中等美颜
 * - 100%: 最大美颜
 */
class CameraFragment : Fragment() {
    
    companion object {
        private const val TAG = "CameraFragment"
    }
    
    // ==================== 视图绑定 ====================
    
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    
    // ==================== ViewModel ====================
    
    private val viewModel: CameraViewModel by viewModels()
    
    // ==================== 相机管理 ====================
    
    /** 相机管理器 - 封装 CameraX API */
    private var cameraManager: CameraManager? = null
    
    /** 美颜 TextureView - 自定义视图，集成 OpenGL 渲染 */
    private var beautyTextureView: BeautyTextureView? = null
    
    // ==================== 权限请求 ====================
    
    /**
     * 相机权限请求器
     * 使用 Activity Result API 请求相机权限
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "需要相机权限才能使用", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== 生命周期 ====================
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hideStatusBar() // 隐藏系统状态栏
        setupUI()       // 设置 UI 交互
        checkPermissions() // 检查并请求相机权限
    }
    
    /**
     * 隐藏系统状态栏
     */
    private fun hideStatusBar() {
        val window = requireActivity().window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cameraManager?.release()
        _binding = null
    }
    
    // ==================== UI 设置 ====================
    
    /**
     * 设置 UI 组件的交互逻辑
     */
    private fun setupUI() {
        // 获取美颜 TextureView 引用
        beautyTextureView = binding.beautyTextureView
        
        // 返回按钮
        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }
        
        // 美颜强度滑块
        binding.seekbarBeauty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val level = progress / 100.0f  // 转换为 0.0 - 1.0
                beautyTextureView?.setBeautyLevel(level)
                binding.textBeautyLevel.text = "${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 拍照按钮
        binding.buttonTakePhoto.setOnClickListener {
            takePhoto()
        }
        
        // 闪光灯按钮
        binding.buttonFlash.setOnClickListener {
            toggleFlash()
        }
        
        // 切换摄像头按钮
        binding.buttonSwitchCamera.setOnClickListener {
            switchCamera()
        }
    }
    
    // ==================== 权限处理 ====================
    
    /**
     * 检查相机权限，如果已授权则启动相机
     */
    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    // ==================== 相机控制 ====================
    
    /**
     * 启动相机预览
     * 
     * 【执行流程】
     * 1. 设置 SurfaceTexture 就绪回调
     * 2. BeautyTextureView 初始化完成后会回调此方法
     * 3. 收到回调后创建 CameraManager 并启动预览
     * 4. CameraX 的帧数据会自动流入 OpenGL 管道进行美颜处理
     */
    private fun startCamera() {
        Log.d(TAG, "startCamera")
        
        // 设置回调：当 BeautyTextureView 的 SurfaceTexture 准备好时触发
        beautyTextureView?.onSurfaceTextureReady = { surfaceTexture ->
            Log.d(TAG, "SurfaceTexture ready, starting camera preview")
            
            // 创建相机管理器
            cameraManager = CameraManager(requireContext())
            
            // 使用 OpenGL 的 SurfaceTexture 启动相机预览
            // 相机帧会写入这个 SurfaceTexture，然后被 OpenGL 处理
            cameraManager?.startPreviewWithSurface(
                lifecycleOwner = viewLifecycleOwner,
                surfaceTexture = surfaceTexture,
                width = 1280,
                height = 720
            )
            
            // 更新相机状态
            viewModel.setCameraState(CameraState.Preview)
        }
    }
    
    /**
     * 拍照 - 截取当前美颜渲染帧
     * 
     * 【执行流程】
     * 1. 调用 BeautyTextureView.takePhoto() 触发 OpenGL 截帧
     * 2. 渲染线程从帧缓冲区读取像素（已包含美颜效果）
     * 3. 回调返回 Bitmap
     * 4. 保存到临时文件
     * 5. 导航到预览页面（用户确认后再保存到相册）
     */
    private fun takePhoto() {
        Log.d(TAG, "takePhoto")
        
        // 调用 BeautyTextureView 的拍照方法
        // 回调在主线程执行，返回的 Bitmap 已包含美颜效果
        beautyTextureView?.takePhoto { bitmap ->
            // 保存到临时文件
            val tempUri = saveToTempFile(bitmap)
            if (tempUri != null) {
                Log.d(TAG, "Photo saved to temp: $tempUri")
                
                // 导航到预览页面，传递临时文件 URI
                val bundle = Bundle().apply {
                    putString("photoUriString", tempUri.toString())
                }
                findNavController().navigate(R.id.action_camera_to_preview, bundle)
            } else {
                Toast.makeText(requireContext(), "拍照失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 保存照片到临时文件
     * 
     * @param bitmap 要保存的 Bitmap
     * @return 临时文件的 URI，失败返回 null
     */
    private fun saveToTempFile(bitmap: Bitmap): android.net.Uri? {
        return try {
            val tempFile = java.io.File(requireContext().cacheDir, "temp_photo.jpg")
            val outputStream = java.io.FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.flush()
            outputStream.close()
            android.net.Uri.fromFile(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save temp file", e)
            null
        }
    }
    
    /**
     * 保存照片到系统相册
     * 
     * 使用 MediaStore API（Android 10+）保存到 Pictures/MyPx 目录
     * 
     * @param bitmap 要保存的 Bitmap（已包含美颜效果）
     * @return 照片的 content:// URI，失败返回 null
     */
    private suspend fun savePhotoToStorage(bitmap: Bitmap): android.net.Uri? {
        // 切换到 IO 线程执行文件操作
        return withContext(Dispatchers.IO) {
            try {
                // 生成带时间戳的文件名
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "IMG_$timeStamp.jpg"
                
                // 设置照片元数据
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    // Android 10+ 使用 RELATIVE_PATH 指定保存目录
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyPx")
                    }
                }
                
                // 在 MediaStore 中创建记录，获取 URI
                val uri = requireContext().contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                // 将 Bitmap 写入 URI
                uri?.let { imageUri ->
                    requireContext().contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }
                }
                
                uri
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save photo", e)
                null
            }
        }
    }
    
    /**
     * 切换闪光灯开关
     */
    private fun toggleFlash() {
        cameraManager?.toggleFlash()
        val isFlashOn = cameraManager?.isFlashOn() ?: false
        // 更新闪光灯按钮图标
        binding.buttonFlash.setImageResource(
            if (isFlashOn) R.drawable.ic_flash_on
            else R.drawable.ic_flash_off
        )
        Log.d(TAG, "Flash toggled: $isFlashOn")
    }
    
    /**
     * 切换前后摄像头
     */
    private fun switchCamera() {
        cameraManager?.switchCamera()
        // 重新启动相机预览（使用新的摄像头）
        beautyTextureView?.getCameraSurfaceTexture()?.let { surfaceTexture ->
            cameraManager?.startPreviewWithSurface(
                lifecycleOwner = viewLifecycleOwner,
                surfaceTexture = surfaceTexture,
                width = 1280,
                height = 720
            )
        }
        Log.d(TAG, "Camera switched")
    }
}
