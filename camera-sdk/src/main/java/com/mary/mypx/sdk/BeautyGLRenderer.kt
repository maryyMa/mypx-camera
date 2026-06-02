package com.mary.mypx.sdk

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 美颜 OpenGL 渲染器 - 使用 GPU 加速的实时美颜
 * 
 * 【架构设计】
 * 
 * CameraX (YUV) 
 *     ↓
 * SurfaceTexture (OES纹理，本类创建)
 *     ↓
 * OpenGL 片段着色器 (美颜处理)
 *     ↓
 * 屏幕显示
 * 
 * 【为什么用 OpenGL？】
 * 1. GPU 加速：比 CPU 处理快 10-100 倍
 * 2. 实时性：可以处理 30fps 视频流
 * 3. 功耗低：GPU 处理比 CPU 更省电
 * 4. 效果好：可以实现复杂的图像算法
 * 
 * 【美颜算法】
 * 使用双边滤波（Bilateral Filter）实现磨皮：
 * - 保留边缘（重要特征）
 * - 平滑皮肤（去除噪点）
 * - 可调节强度（0.0 - 1.0）
 */
class BeautyGLRenderer(
    private val onSurfaceCreated: ((SurfaceTexture) -> Unit)? = null,
    private val onSurfaceChanged: ((Int, Int) -> Unit)? = null
) : GLSurfaceView.Renderer {
    
    // ==================== 纹理和缓冲区 ====================
    
    /** OES 纹理 ID - 用于接收相机数据的特殊纹理类型 */
    private var oesTextureId = 0
    
    /** SurfaceTexture - 接收相机帧数据，绑定到 OES 纹理 */
    private var surfaceTexture: SurfaceTexture? = null
    
    /** 着色器程序 - 包含顶点着色器和片段着色器 */
    private var program = 0
    
    /** 顶点缓冲区 - 存储矩形顶点坐标 */
    private var vertexBuffer: FloatBuffer? = null
    
    /** 纹理坐标缓冲区 - 存储纹理映射坐标 */
    private var texCoordBuffer: FloatBuffer? = null
    
    /** 变换矩阵 - 处理相机旋转和翻转 */
    private val transformMatrix = FloatArray(16)
    
    /** 美颜强度 (0.0 - 1.0) */
    private var _beautyLevel = 0.5f
    var beautyLevel: Float
        get() = _beautyLevel
        set(value) { _beautyLevel = value.coerceIn(0.0f, 1.0f) }
    
    /** 渲染画面尺寸 */
    private var width = 0
    private var height = 0
    
    // ==================== 顶点和纹理坐标 ====================
    
    /**
     * 顶点坐标 - 定义一个全屏矩形（两个三角形组成）
     * OpenGL 坐标系：中心为原点，范围 -1 到 1
     */
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f,  // 左下
         1.0f, -1.0f,  // 右下
        -1.0f,  1.0f,  // 左上
         1.0f,  1.0f   // 右上
    )
    
    /**
     * 纹理坐标 - 定义纹理如何映射到矩形上
     * 范围 0 到 1，(0,0) 是左下角，(1,1) 是右上角
     */
    private val texCoords = floatArrayOf(
        0.0f, 0.0f,  // 左下
        1.0f, 0.0f,  // 右下
        0.0f, 1.0f,  // 左上
        1.0f, 1.0f   // 右上
    )
    
    // ==================== 着色器代码 ====================
    
    /**
     * 顶点着色器 - 处理每个顶点的位置
     * 
     * 功能：
     * 1. 接收顶点位置和纹理坐标
     * 2. 应用变换矩阵（处理相机旋转）
     * 3. 将纹理坐标传递给片段着色器
     */
    private val vertexShaderCode = """
        attribute vec4 position;
        attribute vec2 texCoord;
        uniform mat4 transformMatrix;
        varying vec2 vTexCoord;
        
        void main() {
            gl_Position = position;
            vTexCoord = (transformMatrix * vec4(texCoord, 0.0, 1.0)).xy;
        }
    """.trimIndent()
    
    /**
     * 片段着色器 - 处理每个像素的颜色（美颜效果在这里实现）
     * 
     * 【美颜算法】
     * 1. 双边滤波：保留边缘的同时平滑皮肤
     * 2. 亮度调整：提亮肤色
     * 3. 对比度调整：增强立体感
     * 4. 饱和度调整：让肤色更红润
     * 
     * 【uniform 变量】
     * - texture: OES 纹理采样器（相机画面）
     * - beautyLevel: 美颜强度 (0.0 - 1.0)
     * - texelSize: 纹素大小（用于模糊计算）
     */
    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        
        varying vec2 vTexCoord;
        uniform samplerExternalOES texture;
        uniform float beautyLevel;
        uniform vec2 texelSize;
        
        // 双边滤波函数 - 保留边缘的同时平滑皮肤
        vec4 bilateralFilter(vec2 uv) {
            vec4 center = texture2D(texture, uv);
            vec3 sum = center.rgb;
            float totalWeight = 1.0;
            
            // 3x3 采样邻域
            for (float x = -1.0; x <= 1.0; x += 1.0) {
                for (float y = -1.0; y <= 1.0; y += 1.0) {
                    if (x == 0.0 && y == 0.0) continue;
                    
                    vec2 offset = vec2(x, y) * texelSize * 3.0;
                    vec4 sample = texture2D(texture, uv + offset);
                    
                    // 空间权重：距离越近权重越大
                    float spatialWeight = 1.0 / (1.0 + x*x + y*y);
                    
                    // 颜色权重：颜色越接近权重越大（这是保留边缘的关键）
                    float colorDiff = length(sample.rgb - center.rgb);
                    float colorWeight = exp(-colorDiff * colorDiff * 10.0);
                    
                    float weight = spatialWeight * colorWeight;
                    sum += sample.rgb * weight;
                    totalWeight += weight;
                }
            }
            
            return vec4(sum / totalWeight, center.a);
        }
        
        void main() {
            // 1. 应用双边滤波（磨皮）
            vec4 blurred = bilateralFilter(vTexCoord);
            vec4 original = texture2D(texture, vTexCoord);
            
            // 2. 混合原图和模糊图（根据美颜强度控制磨皮程度）
            vec4 color = mix(original, blurred, beautyLevel * 0.8);
            
            // 3. 提亮肤色
            float brightness = 1.0 + beautyLevel * 0.1;
            color.rgb *= brightness;
            
            // 4. 增强对比度（让五官更立体）
            float contrast = 1.0 + beautyLevel * 0.15;
            color.rgb = (color.rgb - 0.5) * contrast + 0.5;
            
            // 5. 增强红色通道（让肤色更红润）
            color.r *= (1.0 + beautyLevel * 0.08);
            
            // 6. 限制颜色范围到 [0, 1]
            color.rgb = clamp(color.rgb, 0.0, 1.0);
            
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // ==================== 初始化 ====================
    
    init {
        // 初始化顶点缓冲区（全屏矩形的四个顶点）
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexCoords)
                position(0)
            }
        
        // 初始化纹理坐标缓冲区
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }
    }
    
    // ==================== GLSurfaceView.Renderer 生命周期 ====================
    
    /**
     * Surface 创建时调用（在这里初始化 OpenGL 资源）
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景颜色为黑色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // 创建 OES 纹理（外部纹理，用于接收相机数据）
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        oesTextureId = textures[0]
        
        // 绑定并配置 OES 纹理参数
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        
        // 创建 SurfaceTexture，绑定到 OES 纹理
        // CameraX 的帧数据会写入这个 SurfaceTexture
        surfaceTexture = SurfaceTexture(oesTextureId)
        
        // 编译顶点着色器和片段着色器
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        // 创建着色器程序，链接两个着色器
        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
        
        // 初始化单位矩阵
        Matrix.setIdentityM(transformMatrix, 0)
        
        // 通知外部 SurfaceTexture 已创建
        surfaceTexture?.let { onSurfaceCreated?.invoke(it) }
    }
    
    /**
     * Surface 尺寸变化时调用
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        // 设置 OpenGL 视口大小
        GLES20.glViewport(0, 0, width, height)
        onSurfaceChanged?.invoke(width, height)
    }
    
    /**
     * 每帧绘制时调用（渲染循环的核心）
     * 
     * 【执行流程】
     * 1. 清除屏幕
     * 2. 从 SurfaceTexture 获取最新帧
     * 3. 应用美颜着色器
     * 4. 绘制到屏幕
     */
    override fun onDrawFrame(gl: GL10?) {
        // 清除颜色缓冲区
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        // 从 SurfaceTexture 更新纹理（获取最新相机帧）
        surfaceTexture?.updateTexImage()
        
        // 获取纹理变换矩阵（处理相机旋转）
        surfaceTexture?.getTransformMatrix(transformMatrix)
        
        // 使用美颜着色器程序
        GLES20.glUseProgram(program)
        
        // 获取着色器中的变量位置
        val positionHandle = GLES20.glGetAttribLocation(program, "position")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "texCoord")
        val textureHandle = GLES20.glGetUniformLocation(program, "texture")
        val beautyLevelHandle = GLES20.glGetUniformLocation(program, "beautyLevel")
        val texelSizeHandle = GLES20.glGetUniformLocation(program, "texelSize")
        val transformMatrixHandle = GLES20.glGetUniformLocation(program, "transformMatrix")
        
        // 设置顶点属性
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        
        // 绑定 OES 纹理到纹理单元 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(textureHandle, 0)
        
        // 设置美颜强度
        GLES20.glUniform1f(beautyLevelHandle, beautyLevel)
        
        // 设置纹素大小（用于双边滤波的采样偏移）
        if (width > 0 && height > 0) {
            GLES20.glUniform2f(texelSizeHandle, 1.0f / width, 1.0f / height)
        }
        
        // 设置变换矩阵
        GLES20.glUniformMatrix4fv(transformMatrixHandle, 1, false, transformMatrix, 0)
        
        // 绘制全屏矩形（三角形条带模式，4个顶点形成2个三角形）
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // 禁用顶点属性
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 获取 SurfaceTexture（用于连接 CameraX）
     */
    fun getSurfaceTexture(): SurfaceTexture? = surfaceTexture
    
    /**
     * 截取当前帧 - 从帧缓冲区读取像素并生成 Bitmap
     * 
     * 【注意】
     * - 必须在渲染线程调用
     * - 必须在 onDrawFrame 之后调用（确保帧缓冲区有内容）
     * - 返回的 Bitmap 已经包含美颜效果
     * 
     * @return 带美颜效果的 Bitmap，失败返回 null
     */
    fun captureCurrentFrame(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        
        // 分配缓冲区用于读取像素数据（RGBA = 4字节/像素）
        val buffer = ByteBuffer.allocateDirect(width * height * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        
        // 从帧缓冲区读取像素
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        buffer.rewind()
        
        // 创建 Bitmap 并复制像素数据
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        
        // OpenGL 坐标系 Y 轴朝上，Android 坐标系 Y 轴朝下，需要上下镜像翻转
        val matrix = android.graphics.Matrix().apply { postScale(1f, -1f, width / 2f, height / 2f) }
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        bitmap.recycle()  // 释放原始 Bitmap
        
        return flipped
    }
    
    /**
     * 释放资源
     */
    fun release() {
        surfaceTexture?.release()
        surfaceTexture = null
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 加载并编译着色器
     * @param type 着色器类型：GL_VERTEX_SHADER 或 GL_FRAGMENT_SHADER
     * @param shaderCode 着色器源代码
     * @return 着色器 ID
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).apply {
            GLES20.glShaderSource(this, shaderCode)
            GLES20.glCompileShader(this)
        }
    }
}
