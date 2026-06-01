package com.mary.mypx.sdk

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL滤镜渲染器 - 使用GPU加速的图像处理
 * 
 * 【为什么用OpenGL？】
 * 相比CPU处理，OpenGL有以下优势：
 * 1. GPU加速：GPU专门用于图形处理，比CPU快很多
 * 2. 并行处理：GPU可以同时处理多个像素
 * 3. 实时性：适合实时视频处理
 * 4. 功耗低：GPU处理比CPU更省电
 * 
 * 【渲染流程】
 * 1. 创建着色器程序（顶点着色器 + 片段着色器）
 * 2. 加载纹理（将Bitmap上传到GPU）
 * 3. 执行着色器程序（应用滤镜效果）
 * 4. 输出到屏幕
 * 
 * 【着色器说明】
 * - 顶点着色器：处理顶点位置和纹理坐标
 * - 片段着色器：处理每个像素的颜色（滤镜效果在这里实现）
 * 
 * 【使用场景】
 * 1. 实时相机预览滤镜
 * 2. 视频处理
 * 3. 图像编辑应用
 */
class OpenGLFilterRenderer : GLSurfaceView.Renderer {
    
    // ==================== OpenGL资源 ====================
    
    /**
     * 纹理ID - GPU中的纹理标识符
     * 
     * 纹理是GPU中的图像数据，用于存储和处理图像
     */
    private var textureId = 0
    
    /**
     * 着色器程序 - GPU程序
     * 
     * 包含顶点着色器和片段着色器
     */
    private var program = 0
    
    /**
     * 顶点缓冲区 - 存储顶点坐标
     * 
     * 顶点坐标定义了渲染区域（一个矩形）
     */
    private var vertexBuffer: FloatBuffer? = null
    
    /**
     * 纹理坐标缓冲区 - 存储纹理映射坐标
     * 
     * 纹理坐标定义了如何将纹理映射到顶点上
     */
    private var texCoordBuffer: FloatBuffer? = null
    
    // ==================== 状态变量 ====================
    
    /**
     * 当前要渲染的Bitmap
     * 
     * 每帧渲染前更新
     */
    private var currentBitmap: Bitmap? = null
    
    /**
     * 当前选择的滤镜类型
     * 
     * 决定片段着色器使用哪种滤镜效果
     */
    private var currentFilter: FilterType = FilterType.NONE
    
    // ==================== 着色器代码 ====================
    
    /**
     * 顶点着色器代码 - GLSL语言编写
     * 
     * 【功能说明】
     * 1. 接收顶点位置（position）和纹理坐标（texCoord）
     * 2. 将纹理坐标传递给片段着色器
     * 3. 输出顶点位置
     * 
     * 【变量说明】
     * - attribute: 从外部传入的顶点属性
     * - varying: 传递给片段着色器的变量
     * - gl_Position: 内置变量，输出顶点位置
     */
    private val vertexShaderCode = """
        attribute vec4 position;
        attribute vec2 texCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = position;
            vTexCoord = texCoord;
        }
    """.trimIndent()
    
    /**
     * 片段着色器代码 - GLSL语言编写
     * 
     * 【功能说明】
     * 1. 接收纹理坐标
     * 2. 从纹理中采样颜色
     * 3. 根据滤镜类型修改颜色
     * 4. 输出最终颜色
     * 
     * 【滤镜实现】
     * - 美颜：提高亮度和对比度
     * - 夜景：大幅提高亮度
     * 
     * 【变量说明】
     * - uniform: 从外部传入的统一变量
     * - sampler2D: 2D纹理采样器
     * - gl_FragColor: 内置变量，输出片段颜色
     */
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D texture;
        uniform int filterType;
        
        void main() {
            // 从纹理中采样颜色
            vec4 color = texture2D(texture, vTexCoord);
            
            // 根据滤镜类型修改颜色
            if (filterType == 1) { // Beauty
                // 美颜滤镜：提高亮度和对比度
                color.rgb = color.rgb * 1.1;  // 提高亮度10%
                color.rgb = (color.rgb - 0.5) * 1.2 + 0.5;  // 提高对比度20%
            } else if (filterType == 2) { // Night mode
                // 夜景模式：大幅提高亮度
                color.rgb = color.rgb * 1.8;  // 提高亮度80%
                color.rgb = color.rgb * 0.9 + 0.1;  // 降噪处理
            }
            
            // 输出最终颜色
            gl_FragColor = color;
        }
    """.trimIndent()
    
    // ==================== 顶点数据 ====================
    
    /**
     * 顶点坐标 - 定义渲染区域（全屏矩形）
     * 
     * 使用归一化设备坐标（NDC），范围[-1, 1]
     * 四个顶点定义一个矩形
     */
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f,  // 左下角
        1.0f, -1.0f,   // 右下角
        -1.0f, 1.0f,   // 左上角
        1.0f, 1.0f     // 右上角
    )
    
    /**
     * 纹理坐标 - 定义纹理映射
     * 
     * 范围[0, 1]，定义如何将纹理映射到顶点上
     * 注意：纹理坐标的Y轴是反的（0在顶部，1在底部）
     */
    private val texCoords = floatArrayOf(
        0.0f, 1.0f,  // 左下角（纹理的左上角）
        1.0f, 1.0f,  // 右下角（纹理的右上角）
        0.0f, 0.0f,  // 左上角（纹理的左下角）
        1.0f, 0.0f   // 右上角（纹理的右下角）
    )
    
    // ==================== 初始化 ====================
    
    /**
     * 初始化块 - 创建缓冲区
     * 
     * 【功能说明】
     * 在对象创建时，将顶点坐标和纹理坐标数据加载到缓冲区中。
     * 缓冲区是GPU可以访问的内存区域。
     * 
     * 【为什么用ByteBuffer？】
     * ByteBuffer是直接内存，GPU可以直接访问，避免数据复制。
     */
    init {
        // 创建顶点缓冲区
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())  // 设置字节顺序
            .asFloatBuffer()  // 转换为FloatBuffer
            .apply {
                put(vertexCoords)  // 写入数据
                position(0)  // 重置位置
            }
        
        // 创建纹理坐标缓冲区
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }
    }
    
    // ==================== Renderer接口实现 ====================
    
    /**
     * Surface创建时调用 - 初始化OpenGL资源
     * 
     * 【功能说明】
     * 1. 设置背景颜色
     * 2. 编译着色器
     * 3. 创建着色器程序
     * 4. 链接着色器程序
     * 
     * 【调用时机】
     * GLSurfaceView创建时调用一次
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景颜色为黑色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // 编译顶点着色器
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        // 编译片段着色器
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        // 创建着色器程序
        program = GLES20.glCreateProgram().apply {
            // 附加顶点着色器
            GLES20.glAttachShader(this, vertexShader)
            // 附加片段着色器
            GLES20.glAttachShader(this, fragmentShader)
            // 链接程序
            GLES20.glLinkProgram(this)
        }
    }
    
    /**
     * Surface大小改变时调用 - 设置视口
     * 
     * 【功能说明】
     * 设置OpenGL的视口大小，通常与GLSurfaceView大小一致。
     * 
     * 【调用时机】
     * 1. GLSurfaceView创建时
     * 2. GLSurfaceView大小改变时（如屏幕旋转）
     * 
     * @param width 新的宽度
     * @param height 新的高度
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // 设置视口大小
        GLES20.glViewport(0, 0, width, height)
    }
    
    /**
     * 每帧绘制时调用 - 渲染图像
     * 
     * 【功能说明】
     * 这是渲染的核心方法，每帧调用一次：
     * 1. 清除屏幕
     * 2. 使用着色器程序
     * 3. 设置顶点属性
     * 4. 加载纹理
     * 5. 设置滤镜类型
     * 6. 绘制矩形
     * 7. 清理资源
     * 
     * 【渲染流程详解】
     * 1. glClear: 清除颜色缓冲区
     * 2. glUseProgram: 使用着色器程序
     * 3. 获取变量位置
     * 4. 设置顶点属性
     * 5. 创建和加载纹理
     * 6. 设置uniform变量
     * 7. glDrawArrays: 绘制矩形
     * 8. 清理资源
     */
    override fun onDrawFrame(gl: GL10?) {
        // 清除颜色缓冲区
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        // 如果有图像要渲染
        currentBitmap?.let { bitmap ->
            // 使用着色器程序
            GLES20.glUseProgram(program)
            
            // 获取变量位置
            val positionHandle = GLES20.glGetAttribLocation(program, "position")
            val texCoordHandle = GLES20.glGetAttribLocation(program, "texCoord")
            val textureHandle = GLES20.glGetUniformLocation(program, "texture")
            val filterTypeHandle = GLES20.glGetUniformLocation(program, "filterType")
            
            // 设置顶点属性
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            
            // 设置纹理坐标属性
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            
            // ==================== 加载纹理 ====================
            // 创建纹理
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            
            // 绑定纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            
            // 设置纹理过滤参数
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            
            // 将Bitmap数据复制到缓冲区
            val bitmapBuffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(bitmapBuffer)
            bitmapBuffer.position(0)
            
            // 上传纹理到GPU
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                bitmap.width, bitmap.height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer
            )
            
            // ==================== 设置uniform变量 ====================
            // 绑定纹理到纹理单元0
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            // 设置纹理采样器使用纹理单元0
            GLES20.glUniform1i(textureHandle, 0)
            
            // 设置滤镜类型
            val filterTypeValue = when (currentFilter) {
                FilterType.NONE -> 0
                FilterType.BEAUTY -> 1
                FilterType.SUPER_RESOLUTION -> 2
                FilterType.NIGHT_MODE -> 3
            }
            GLES20.glUniform1i(filterTypeHandle, filterTypeValue)
            
            // ==================== 绘制 ====================
            // 绘制三角形带（4个顶点形成一个矩形）
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            // ==================== 清理 ====================
            // 禁用顶点属性
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
            
            // 删除纹理
            GLES20.glDeleteTextures(1, textures, 0)
        }
    }
    
    // ==================== 公开方法 ====================
    
    /**
     * 更新要渲染的Bitmap
     * 
     * 【功能说明】
     * 设置下一帧要渲染的图像。
     * 每次相机帧更新时调用。
     * 
     * @param bitmap 要渲染的Bitmap
     */
    fun updateBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
    }
    
    /**
     * 设置滤镜类型
     * 
     * 【功能说明】
     * 设置当前应用的滤镜类型。
     * 片段着色器会根据这个值应用不同的效果。
     * 
     * @param filterType 滤镜类型
     */
    fun setFilter(filterType: FilterType) {
        currentFilter = filterType
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 加载着色器 - 编译GLSL着色器代码
     * 
     * 【功能说明】
     * 1. 创建着色器对象
     * 2. 设置着色器源代码
     * 3. 编译着色器
     * 
     * @param type 着色器类型（GL_VERTEX_SHADER或GL_FRAGMENT_SHADER）
     * @param shaderCode GLSL着色器代码
     * @return 编译后的着色器ID
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).apply {
            GLES20.glShaderSource(this, shaderCode)
            GLES20.glCompileShader(this)
        }
    }
}
