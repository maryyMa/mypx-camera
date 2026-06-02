package com.mary.mypx.sdk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.TextureView

/**
 * 美颜 TextureView - 核心预览视图
 * 
 * 【架构设计】
 * 
 * CameraX 相机帧
 *     ↓
 * SurfaceTexture (OES纹理，由 BeautyGLRenderer 创建)
 *     ↓
 * OpenGL 美颜渲染 (在渲染线程中执行)
 *     ↓
 * TextureView 显示到屏幕
 * 
 * 【线程模型】
 * - 主线程：处理 UI 事件、拍照回调
 * - 渲染线程：EGL 初始化、OpenGL 渲染、截帧
 * - 两个线程通过 @Volatile 标志通信
 */
class BeautyTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    // ==================== EGL 环境 ====================
    // EGL 是 OpenGL ES 与 Android 显示系统之间的桥梁
    // 必须先创建 EGL 环境才能使用 OpenGL 命令
    
    /** EGL 显示连接 - 连接到设备的显示系统 */
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    
    /** EGL 渲染上下文 - 存储 OpenGL 状态 */
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    
    /** EGL 渲染表面 - OpenGL 绘制的目标（这里绑定到 TextureView） */
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    
    /** EGL 配置 - 颜色深度、渲染类型等参数 */
    private var eglConfig: EGLConfig? = null

    // ==================== 渲染器和线程 ====================
    
    /** 美颜渲染器 - 负责 OpenGL 绘制和美颜处理 */
    private var renderer: BeautyGLRenderer? = null
    
    /** 渲染线程 - 所有 OpenGL 操作必须在同一个线程执行 */
    private var renderThread: Thread? = null

    /** 渲染循环是否运行中 */
    @Volatile
    private var isRendering = false

    // ==================== 相机相关 ====================
    
    /**
     * SurfaceTexture 就绪回调
     * 当渲染线程创建好 SurfaceTexture 后，通过此回调通知 CameraFragment
     * CameraFragment 收到后会启动 CameraX 预览
     */
    var onSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null
    
    /** 相机的 SurfaceTexture - 接收 CameraX 输出的帧数据 */
    private var cameraSurfaceTexture: SurfaceTexture? = null

    /** 画面尺寸 */
    private var width: Int = 0
    private var height: Int = 0

    // ==================== 跨线程通信标志 ====================
    
    /**
     * 是否有新帧到达
     * - 由 OnFrameAvailableListener 在主线程设置为 true
     * - 由渲染循环在渲染线程读取并重置为 false
     * - @Volatile 保证多线程可见性
     */
    @Volatile
    private var frameAvailable = false

    /**
     * 是否有待处理的拍照请求
     * - 由 takePhoto() 在主线程设置为 true
     * - 由渲染循环在渲染线程读取并重置为 false
     */
    @Volatile
    private var capturePending = false

    /**
     * 拍照回调函数
     * - 由 takePhoto() 在主线程设置
     * - 由渲染线程在截帧完成后，通过 Handler(Looper.getMainLooper()) 回调到主线程
     */
    @Volatile
    private var captureCallback: ((Bitmap) -> Unit)? = null

    // ==================== 初始化 ====================
    
    init {
        // 设置 TextureView 的 SurfaceTexture 监听器
        // 当 SurfaceTexture 可用时会回调 onSurfaceTextureAvailable
        surfaceTextureListener = this
    }

    // ==================== TextureView 生命周期回调 ====================
    
    /**
     * SurfaceTexture 可用时调用
     * 这是渲染的起点：创建 EGL 环境 → 创建渲染器 → 启动渲染循环
     */
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        this.width = width
        this.height = height
        startRendering(surface)
    }

    /**
     * SurfaceTexture 尺寸变化时调用
     */
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /**
     * SurfaceTexture 销毁时调用
     * 停止渲染并释放所有资源
     */
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRendering()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    // ==================== 公开方法 ====================
    
    /**
     * 设置美颜强度
     * @param level 0.0（无美颜）到 1.0（最大美颜）
     */
    fun setBeautyLevel(level: Float) {
        renderer?.beautyLevel = level
    }

    /**
     * 获取相机的 SurfaceTexture
     * CameraFragment 用它来连接 CameraX 预览
     */
    fun getCameraSurfaceTexture(): SurfaceTexture? = cameraSurfaceTexture

    /**
     * 拍照 - 截取当前渲染帧（带美颜效果）
     * 
     * 【执行流程】
     * 1. 主线程调用此方法，设置回调和标志
     * 2. 渲染循环检测到 capturePending = true
     * 3. 渲染线程执行 onDrawFrame（更新纹理）
     * 4. 渲染线程调用 captureCurrentFrame() 截取帧缓冲区
     * 5. 通过 Handler 将 Bitmap 回调到主线程
     * 
     * @param callback 拍照完成回调，在主线程执行，返回带美颜效果的 Bitmap
     */
    fun takePhoto(callback: (Bitmap) -> Unit) {
        captureCallback = callback
        capturePending = true
    }

    // ==================== 渲染循环 ====================
    
    /**
     * 启动渲染线程
     * 
     * 【渲染线程职责】
     * 1. 初始化 EGL 环境（OpenGL 必须在使用它的线程中初始化）
     * 2. 创建 BeautyGLRenderer 并初始化
     * 3. 启动渲染循环，持续处理相机帧
     * 4. 响应拍照请求，截取当前帧
     * 
     * @param displaySurface TextureView 的 SurfaceTexture，作为 EGL 的渲染目标
     */
    private fun startRendering(displaySurface: SurfaceTexture) {
        isRendering = true

        renderThread = Thread({
            // ===== 初始化阶段 =====
            
            // 在渲染线程创建 EGL 环境（OpenGL 要求在使用它的线程中初始化）
            initEGL(displaySurface, width, height)
            makeCurrent()

            // 创建美颜渲染器
            renderer = BeautyGLRenderer()
            // 手动调用生命周期方法（因为不是用 GLSurfaceView，不会自动调用）
            renderer?.onSurfaceCreated(null, null)
            renderer?.onSurfaceChanged(null, width, height)

            // 获取渲染器创建的相机 SurfaceTexture
            cameraSurfaceTexture = renderer?.getSurfaceTexture()
            
            // 设置帧可用监听器
            // 当 CameraX 发送新帧到 SurfaceTexture 时，此回调被触发
            // 使用主线程 Handler，因为渲染线程的 while 循环会阻塞 Looper
            cameraSurfaceTexture?.setOnFrameAvailableListener({
                frameAvailable = true  // 通知渲染循环有新帧
            }, Handler(Looper.getMainLooper()))

            // 通知 CameraFragment：SurfaceTexture 已就绪，可以启动相机了
            onSurfaceTextureReady?.invoke(cameraSurfaceTexture!!)

            // ===== 渲染循环 =====
            // 持续运行直到 isRendering = false
            
            while (isRendering) {
                // 检查是否有新帧或待处理的拍照请求
                if (frameAvailable || capturePending) {
                    frameAvailable = false

                    // 确保 EGL 上下文是当前线程的
                    makeCurrent()
                    
                    // 执行 OpenGL 渲染（更新纹理、应用美颜着色器、绘制到后缓冲区）
                    renderer?.onDrawFrame(null)

                    // 如果有待处理的拍照请求，在交换缓冲区之前截帧
                    // 必须在 swapBuffers 之前读取，否则后缓冲区内容未定义
                    if (capturePending) {
                        capturePending = false
                        val bitmap = renderer?.captureCurrentFrame()
                        if (bitmap != null) {
                            val cb = captureCallback
                            captureCallback = null
                            Handler(Looper.getMainLooper()).post { cb?.invoke(bitmap) }
                        }
                    }
                    
                    // 交换前后缓冲区，将渲染结果显示到屏幕
                    swapBuffers()
                } else {
                    // 无新帧且无拍照请求，短暂休眠避免 CPU 空转
                    try {
                        Thread.sleep(5)
                    } catch (_: InterruptedException) {
                        break  // 线程被中断，退出循环
                    }
                }
            }
        }, "BeautyRenderThread").apply { start() }
    }

    /**
     * 停止渲染并释放资源
     */
    private fun stopRendering() {
        isRendering = false
        renderThread?.interrupt()
        try {
            renderThread?.join(1000)  // 等待渲染线程结束，最多 1 秒
        } catch (_: InterruptedException) {}
        renderThread = null

        releaseEGL()
        renderer?.release()
        renderer = null
    }

    // ==================== EGL 管理 ====================
    
    /**
     * 初始化 EGL 环境
     * 
     * EGL 是 OpenGL ES 与原生窗口系统之间的接口
     * 必须先创建 EGL 环境才能调用任何 OpenGL 命令
     * 
     * @param surface 渲染目标表面（TextureView 的 SurfaceTexture）
     * @param width 表面宽度
     * @param height 表面高度
     */
    private fun initEGL(surface: SurfaceTexture, width: Int, height: Int) {
        // 1. 获取默认显示连接
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("Unable to get EGL display")

        // 2. 初始化 EGL
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) throw RuntimeException("Unable to initialize EGL")

        // 3. 选择 EGL 配置（颜色深度、渲染类型等）
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
            throw RuntimeException("Unable to choose EGL config")
        }
        eglConfig = configs[0]

        // 4. 创建 OpenGL ES 2.0 渲染上下文
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) throw RuntimeException("Unable to create EGL context")

        // 5. 创建窗口表面（绑定到 TextureView）
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) throw RuntimeException("Unable to create EGL surface")

        // 6. 设置为当前上下文
        makeCurrent()
    }

    /**
     * 设置当前 EGL 上下文
     * OpenGL 命令只会作用于当前线程的当前上下文
     */
    private fun makeCurrent() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }
    }

    /**
     * 交换前后缓冲区
     * OpenGL 使用双缓冲：后台绘制完成后交换到前台显示
     */
    private fun swapBuffers() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }
    }

    /**
     * 释放 EGL 资源
     */
    private fun releaseEGL() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) { EGL14.eglDestroySurface(eglDisplay, eglSurface); eglSurface = EGL14.EGL_NO_SURFACE }
            if (eglContext != EGL14.EGL_NO_CONTEXT) { EGL14.eglDestroyContext(eglDisplay, eglContext); eglContext = EGL14.EGL_NO_CONTEXT }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}
