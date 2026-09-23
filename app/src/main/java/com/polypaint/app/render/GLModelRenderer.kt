package com.polypaint.app.render

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import com.polypaint.app.model.ObjMesh
import com.polypaint.app.model.PaintChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val FLOATS_PER_VERTEX = 12 // position3 + normal3 + uv2 + tangent4

class GLModelRenderer : GLSurfaceView.Renderer {

    val camera = OrbitCamera()

    private var program: ShaderProgram? = null
    private var vbo = 0
    private var ebo = 0
    private var vao = 0
    private var indexCount = 0
    private var meshUploaded = false

    private val textureIds = HashMap<PaintChannel, Int>()
    private var viewportWidth = 1
    private var viewportHeight = 1

    private val modelMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val normalMatrix = FloatArray(9)

    // Fixed key light + soft hemisphere ambient stands in for full IBL (see README).
    private val lightDir = floatArrayOf(-0.4f, -0.8f, -0.4f)
    private val lightColor = floatArrayOf(3.2f, 3.1f, 3.0f)
    private val ambientSky = floatArrayOf(0.35f, 0.38f, 0.45f)
    private val ambientGround = floatArrayOf(0.10f, 0.09f, 0.08f)

    // Deferred until the GL surface actually exists (e.g. a mesh imported
    // before the view has finished its first layout pass).
    @Volatile var pendingMesh: ObjMesh? = null
    @Volatile var pendingTextures: Map<PaintChannel, Bitmap>? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.09f, 0.10f, 0.12f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        program = ShaderProgram(PbrShaders.VERTEX_SHADER, PbrShaders.FRAGMENT_SHADER)

        val ids = IntArray(6)
        GLES30.glGenTextures(6, ids, 0)
        PaintChannel.values().forEachIndexed { i, ch -> textureIds[ch] = ids[i] }
        textureIds.values.forEach { id ->
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        }

        pendingTextures?.let { uploadAllTextures(it); pendingTextures = null }
        pendingMesh?.let { uploadMesh(it); pendingMesh = null }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingMesh?.let { uploadMesh(it); pendingMesh = null }
        pendingTextures?.let { uploadAllTextures(it); pendingTextures = null }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val prog = program ?: return
        if (!meshUploaded || indexCount == 0) return
        prog.use()

        val view = camera.viewMatrix()
        val proj = projectionMatrix(45f, viewportWidth.toFloat() / viewportHeight.toFloat(), 0.01f, 100f)
        computeNormalMatrix(modelMatrix, normalMatrix)

        GLES30.glUniformMatrix4fv(prog.uniformLoc("uModel"), 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(prog.uniformLoc("uView"), 1, false, view, 0)
        GLES30.glUniformMatrix4fv(prog.uniformLoc("uProjection"), 1, false, proj, 0)
        GLES30.glUniformMatrix3fv(prog.uniformLoc("uNormalMatrix"), 1, false, normalMatrix, 0)
        val eye = camera.eye()
        GLES30.glUniform3f(prog.uniformLoc("uCameraPos"), eye[0], eye[1], eye[2])
        GLES30.glUniform3f(prog.uniformLoc("uLightDir"), lightDir[0], lightDir[1], lightDir[2])
        GLES30.glUniform3f(prog.uniformLoc("uLightColor"), lightColor[0], lightColor[1], lightColor[2])
        GLES30.glUniform3f(prog.uniformLoc("uAmbientSky"), ambientSky[0], ambientSky[1], ambientSky[2])
        GLES30.glUniform3f(prog.uniformLoc("uAmbientGround"), ambientGround[0], ambientGround[1], ambientGround[2])

        bindChannel(prog, PaintChannel.ALBEDO, 0, "uAlbedoMap")
        bindChannel(prog, PaintChannel.NORMAL, 1, "uNormalMap")
        bindChannel(prog, PaintChannel.ROUGHNESS, 2, "uRoughnessMap")
        bindChannel(prog, PaintChannel.METALLIC, 3, "uMetallicMap")
        bindChannel(prog, PaintChannel.AO, 4, "uAoMap")
        bindChannel(prog, PaintChannel.EMISSIVE, 5, "uEmissiveMap")

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun bindChannel(prog: ShaderProgram, channel: PaintChannel, unit: Int, uniform: String) {
        val id = textureIds[channel] ?: return
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glUniform1i(prog.uniformLoc(uniform), unit)
    }

    /** Must run on the GL thread (e.g. via GLSurfaceView.queueEvent). */
    fun uploadMesh(mesh: ObjMesh) {
        if (mesh.vertices.isEmpty()) return
        val floats = FloatArray(mesh.vertices.size * FLOATS_PER_VERTEX)
        mesh.vertices.forEachIndexed { i, v ->
            val o = i * FLOATS_PER_VERTEX
            floats[o] = v.position.x; floats[o + 1] = v.position.y; floats[o + 2] = v.position.z
            floats[o + 3] = v.normal.x; floats[o + 4] = v.normal.y; floats[o + 5] = v.normal.z
            floats[o + 6] = v.uv.x; floats[o + 7] = v.uv.y
            floats[o + 8] = v.tangent.x; floats[o + 9] = v.tangent.y; floats[o + 10] = v.tangent.z
            floats[o + 11] = v.handedness
        }
        val indices = mesh.subMeshes.flatMap { it.indices.toList() }.toIntArray()
        indexCount = indices.size
        if (indexCount == 0) { meshUploaded = false; return }

        if (vao == 0) {
            val vaoArr = IntArray(1); GLES30.glGenVertexArrays(1, vaoArr, 0); vao = vaoArr[0]
            val buffers = IntArray(2); GLES30.glGenBuffers(2, buffers, 0); vbo = buffers[0]; ebo = buffers[1]
        }

        GLES30.glBindVertexArray(vao)

        val vBuffer = ByteBuffer.allocateDirect(floats.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vBuffer.put(floats).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats.size * 4, vBuffer, GLES30.GL_STATIC_DRAW)

        val iBuffer = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        iBuffer.put(indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, iBuffer, GLES30.GL_STATIC_DRAW)

        val stride = FLOATS_PER_VERTEX * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 8 * 4)

        GLES30.glBindVertexArray(0)
        meshUploaded = true

        Matrix.setIdentityM(modelMatrix, 0)
        camera.frame(floatArrayOf(mesh.center.x, mesh.center.y, mesh.center.z), mesh.radius.coerceAtLeast(0.1f))
    }

    /** Must run on the GL thread. */
    fun uploadAllTextures(textures: Map<PaintChannel, Bitmap>) {
        textures.forEach { (channel, bmp) -> uploadTexture(channel, bmp) }
    }

    /** Must run on the GL thread - call via glSurfaceView.queueEvent { ... }. */
    fun uploadTexture(channel: PaintChannel, bmp: Bitmap) {
        val id = textureIds[channel] ?: return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    private fun computeNormalMatrix(model: FloatArray, out9: FloatArray) {
        val inv = FloatArray(16)
        Matrix.invertM(inv, 0, model, 0)
        // Normal matrix = transpose(inverse(model)) upper-left 3x3.
        out9[0] = inv[0]; out9[1] = inv[4]; out9[2] = inv[8]
        out9[3] = inv[1]; out9[4] = inv[5]; out9[5] = inv[9]
        out9[6] = inv[2]; out9[7] = inv[6]; out9[8] = inv[10]
    }
}
