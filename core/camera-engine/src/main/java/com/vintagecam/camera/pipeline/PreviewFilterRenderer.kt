package com.vintagecam.camera.pipeline

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import androidx.camera.core.Preview
import com.vintagecam.profiles.CameraProfile
import com.vintagecam.profiles.ShaderType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

class PreviewFilterRenderer(
    private val onFrameAvailable: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val shaderChain = mutableListOf<FilterShader>()
    private val fbos = mutableListOf<FrameBufferObject>()
    private val textureTransform = FloatArray(16)
    private val directExecutor = Executor { command -> command.run() }

    private var cameraTextureId = 0
    private var copyProgramId = 0
    private var screenProgramId = 0
    private var cameraSurfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null
    private var requestRender: (() -> Unit)? = null
    private var activeProfile: CameraProfile? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surfaceReadyDispatched = false
    private var frameAvailable = false

    private val vertexBuffer: FloatBuffer = floatBuffer(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,
    )

    private val textureBuffer: FloatBuffer = floatBuffer(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f,
    )

    val previewSurfaceProvider: Preview.SurfaceProvider = Preview.SurfaceProvider { request ->
        val surfaceTexture = cameraSurfaceTexture
        if (surfaceTexture == null) {
            request.willNotProvideSurface()
            return@SurfaceProvider
        }

        surfaceTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
        val surface = Surface(surfaceTexture)
        cameraSurface = surface
        request.provideSurface(surface, directExecutor) { surface.release() }
    }

    fun attachRenderRequest(requestRender: () -> Unit) {
        this.requestRender = requestRender
    }

    fun setProfile(profile: CameraProfile) {
        activeProfile = profile
        rebuildShaderChain()
        recreateFrameBuffers()
    }

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        cameraTextureId = createExternalTexture()
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener(this@PreviewFilterRenderer)
        }
        copyProgramId = createProgram(QUAD_VERTEX_SHADER, CAMERA_COPY_FRAGMENT_SHADER)
        screenProgramId = createProgram(QUAD_VERTEX_SHADER, SIMPLE_TEXTURE_FRAGMENT_SHADER)

        rebuildShaderChain()
        if (!surfaceReadyDispatched) {
            surfaceReadyDispatched = true
            cameraSurfaceTexture?.let(onFrameAvailable)
        }
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        Matrix.setIdentityM(textureTransform, 0)
        recreateFrameBuffers()
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        val surfaceTexture = cameraSurfaceTexture ?: return
        if (frameAvailable) {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(textureTransform)
            frameAvailable = false
        }

        if (surfaceWidth == 0 || surfaceHeight == 0) return

        if (shaderChain.isEmpty()) {
            drawCameraCopyToScreen()
            return
        }

        renderToFbo(0, cameraTextureId, copyProgramId, externalInput = true, outputToScreen = false)

        for (index in shaderChain.indices) {
            val shader = shaderChain[index]
            val inputTexture = fbos[index].textureId
            val isLast = index == shaderChain.lastIndex
            val targetFbo = if (isLast) 0 else fbos[index + 1].frameBufferId
            renderShader(shader, inputTexture, targetFbo, isLast)
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        frameAvailable = true
        requestRender?.invoke()
    }

    private fun drawCameraCopyToScreen() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glUseProgram(copyProgramId)
        bindCommonAttributes(copyProgramId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(copyProgramId, "uTexture"), 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(copyProgramId, "uTexMatrix"), 1, false, textureTransform, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun renderToFbo(index: Int, textureId: Int, programId: Int, externalInput: Boolean, outputToScreen: Boolean) {
        val fbo = fbos.getOrNull(index) ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.frameBufferId)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glUseProgram(programId)
        bindCommonAttributes(programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (externalInput) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        }
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programId, "uTexture"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (externalInput) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
        if (outputToScreen) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
    }

    private fun renderShader(shader: FilterShader, inputTextureId: Int, targetFramebufferId: Int, outputToScreen: Boolean) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebufferId)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glUseProgram(shader.programId)
        bindCommonAttributes(shader.programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTextureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(shader.programId, "uTexture"), 0)

        when (shader.type) {
            ShaderType.COLOR_MATRIX -> {
                val profile = activeProfile ?: return
                val matrix = to3x3Matrix(profile.colorMatrix)
                GLES20.glUniformMatrix3fv(GLES20.glGetUniformLocation(shader.programId, "colorMatrix"), 1, false, matrix, 0)
                GLES20.glUniform3f(GLES20.glGetUniformLocation(shader.programId, "shadowTint"), 0.02f, 0.02f, 0.02f)
                GLES20.glUniform3f(GLES20.glGetUniformLocation(shader.programId, "highlightTint"), 0.02f, 0.02f, 0.02f)
            }
            ShaderType.VIGNETTE -> {
                val profile = activeProfile ?: return
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "intensity"), profile.vignetteStrength.coerceIn(0f, 1f))
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "feather"), 0.18f)
            }
            ShaderType.GRAIN -> {
                val profile = activeProfile ?: return
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "intensity"), profile.grainIntensity.coerceIn(0f, 1f))
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "size"), 1.0f)
            }
            ShaderType.SCANLINES -> {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "intensity"), 0.16f)
            }
            ShaderType.CHROMATIC_ABERRATION -> {
                val profile = activeProfile ?: return
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "amount"), profile.chromaticAberration.coerceIn(0f, 0.05f))
            }
            ShaderType.CRUSH_BLACKS -> {
                val profile = activeProfile ?: return
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "amount"), profile.crushedBlacks.coerceIn(0f, 1f))
            }
            ShaderType.SHARPEN -> {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "amount"), 0.35f)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(shader.programId, "texelSize"), 1f / surfaceWidth.toFloat(), 1f / surfaceHeight.toFloat())
            }
            ShaderType.SHADOW_NOISE -> {
                val profile = activeProfile ?: return
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "amount"), profile.shadowNoiseIntensity.coerceIn(0f, 1f))
            }
            ShaderType.DATE_STAMP -> {
                GLES20.glUniform1f(GLES20.glGetUniformLocation(shader.programId, "amount"), 1.0f)
            }
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        if (outputToScreen) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
    }

    private fun bindCommonAttributes(programId: Int) {
        val positionLocation = GLES20.glGetAttribLocation(programId, "aPosition")
        val textureLocation = GLES20.glGetAttribLocation(programId, "aTextureCoord")

        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(textureLocation)
        GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
    }

    private fun rebuildShaderChain() {
        shaderChain.forEach { GLES20.glDeleteProgram(it.programId) }
        shaderChain.clear()

        val profile = activeProfile ?: return
        for (shaderType in profile.shaderPipeline) {
            shaderChain += FilterShader(
                type = shaderType,
                programId = createProgram(QUAD_VERTEX_SHADER, fragmentShaderSourceFor(shaderType)),
            )
        }
    }

    private fun recreateFrameBuffers() {
        fbos.forEach {
            GLES20.glDeleteFramebuffers(1, intArrayOf(it.frameBufferId), 0)
            GLES20.glDeleteTextures(1, intArrayOf(it.textureId), 0)
        }
        fbos.clear()

        if (surfaceWidth == 0 || surfaceHeight == 0 || shaderChain.isEmpty()) return

        repeat(shaderChain.size) {
            fbos += createFrameBuffer(surfaceWidth, surfaceHeight)
        }
    }

    private fun createFrameBuffer(width: Int, height: Int): FrameBufferObject {
        val textureIdArray = IntArray(1)
        val frameBufferArray = IntArray(1)

        GLES20.glGenTextures(1, textureIdArray, 0)
        val textureId = textureIdArray[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null,
        )

        GLES20.glGenFramebuffers(1, frameBufferArray, 0)
        val framebufferId = frameBufferArray[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            textureId,
            0,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        return FrameBufferObject(framebufferId, textureId, width, height)
    }
}