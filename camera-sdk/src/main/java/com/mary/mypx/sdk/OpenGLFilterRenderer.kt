package com.mary.mypx.sdk

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGLFilterRenderer : GLSurfaceView.Renderer {
    
    private var textureId = 0
    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var texCoordBuffer: FloatBuffer? = null
    
    private var currentBitmap: Bitmap? = null
    private var currentFilter: FilterType = FilterType.NONE
    
    private val vertexShaderCode = """
        attribute vec4 position;
        attribute vec2 texCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = position;
            vTexCoord = texCoord;
        }
    """.trimIndent()
    
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D texture;
        uniform int filterType;
        
        void main() {
            vec4 color = texture2D(texture, vTexCoord);
            
            if (filterType == 1) { // Beauty
                // Simple beauty filter: brightness + contrast
                color.rgb = color.rgb * 1.1;
                color.rgb = (color.rgb - 0.5) * 1.2 + 0.5;
            } else if (filterType == 2) { // Night mode
                // Night mode: increase brightness
                color.rgb = color.rgb * 1.8;
                color.rgb = color.rgb * 0.9 + 0.1;
            }
            
            gl_FragColor = color;
        }
    """.trimIndent()
    
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    )
    
    private val texCoords = floatArrayOf(
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f
    )
    
    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexCoords)
                position(0)
            }
        
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        
        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        currentBitmap?.let { bitmap ->
            GLES20.glUseProgram(program)
            
            val positionHandle = GLES20.glGetAttribLocation(program, "position")
            val texCoordHandle = GLES20.glGetAttribLocation(program, "texCoord")
            val textureHandle = GLES20.glGetUniformLocation(program, "texture")
            val filterTypeHandle = GLES20.glGetUniformLocation(program, "filterType")
            
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            
            // Load texture
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            
            val bitmapBuffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(bitmapBuffer)
            bitmapBuffer.position(0)
            
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                bitmap.width, bitmap.height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer
            )
            
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(textureHandle, 0)
            
            // Set filter type
            val filterTypeValue = when (currentFilter) {
                FilterType.NONE -> 0
                FilterType.BEAUTY -> 1
                FilterType.SUPER_RESOLUTION -> 2
                FilterType.NIGHT_MODE -> 3
            }
            GLES20.glUniform1i(filterTypeHandle, filterTypeValue)
            
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
            
            // Cleanup texture
            GLES20.glDeleteTextures(1, textures, 0)
        }
    }
    
    fun updateBitmap(bitmap: Bitmap) {
        currentBitmap = bitmap
    }
    
    fun setFilter(filterType: FilterType) {
        currentFilter = filterType
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).apply {
            GLES20.glShaderSource(this, shaderCode)
            GLES20.glCompileShader(this)
        }
    }
}
