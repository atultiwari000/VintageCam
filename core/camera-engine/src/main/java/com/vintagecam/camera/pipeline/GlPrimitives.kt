package com.vintagecam.camera.pipeline

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal data class FilterShader(
    val type: com.vintagecam.profiles.ShaderType,
    val programId: Int,
)

internal data class FrameBufferObject(
    val frameBufferId: Int,
    val textureId: Int,
    val width: Int,
    val height: Int,
)

internal fun createProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    return program
}

internal fun floatBuffer(vararg values: Float): FloatBuffer {
    return ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
}

internal fun createExternalTexture(): Int {
    val textureIdArray = IntArray(1)
    GLES20.glGenTextures(1, textureIdArray, 0)
    val textureId = textureIdArray[0]
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    return textureId
}

internal fun to3x3Matrix(colorMatrix: FloatArray): FloatArray {
    val matrix = FloatArray(9)
    for (index in 0 until 9) {
        matrix[index] = colorMatrix.getOrElse(index) { if (index % 4 == 0) 1f else 0f }
    }
    return matrix
}

private fun compileShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    return shader
}
