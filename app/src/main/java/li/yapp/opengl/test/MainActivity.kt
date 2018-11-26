package li.yapp.opengl.test

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Bundle
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {

    private lateinit var preview: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preview = findViewById<GLSurfaceView>(R.id.preview).apply {
            setEGLContextClientVersion(3)
            setRenderer(Renderer(resources))
        }
    }

    override fun onResume() {
        super.onResume()
        preview.onResume()
    }

    override fun onPause() {
        super.onPause()
        preview.onPause()
    }

    class Renderer(private val resources: Resources) : GLSurfaceView.Renderer {

        // 動画テクスチャ
        private lateinit var videoSurfaceTexture: SurfaceTexture

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLSupport.initGl(resources)
            videoSurfaceTexture = SurfaceTexture(GLSupport.getVideoTextureId())
            MediaPlayer().apply {
                val fd = resources.assets.openFd("movie.mp4")
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                setSurface(Surface(videoSurfaceTexture))
                prepare()
            }.start()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLSupport.setViewPort(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLSupport.draw(videoSurfaceTexture)
        }

    }

    // OpenGLラッパー
    object GLSupport {

        // 頂点シェーダ
        private const val vertexShaderSource = "" +
                "#version 310 es\n" +
                "\n" +
                "in vec4 aPosition;\n" +
                "in vec2 aTexCoord;\n" +
                "out vec2 vTexCoord;\n" +
                "\n" +
                "void main() {\n" +
                "    gl_Position = aPosition;\n" +
                "    vTexCoord = aTexCoord;\n" +
                "}\n"
        // 動画フラグメントシェーダ
        private const val videoFragmentShaderSource = "" +
                "#version 310 es\n" +
                "#extension GL_OES_EGL_image_external_essl3 : require\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform samplerExternalOES uVideoTexture;\n" +
                "in vec2 vTexCoord;\n" +
                "out vec4 gl_FragColor;\n" +
                "\n" +
                "void main() {\n" +
                "    gl_FragColor = texture(uVideoTexture, vTexCoord);\n" +
                "}\n"
        // 画像フラグメントシェーダ
        private const val imageFragmentShaderSource = "" +
                "#version 310 es\n" +
                "\n" +
                "precision mediump float;\n" +
                "\n" +
                "uniform sampler2D uImageTexture;\n" +
                "in vec2 vTexCoord;\n" +
                "out vec4 gl_FragColor;\n" +
                "\n" +
                "void main() {\n" +
                "    gl_FragColor = texture(uImageTexture, vTexCoord);\n" +
                "}\n"
        // 動画シェーダ用
        private var videoProgram = 0
        private var videoTextureId = 0
        // 動画シェーダインデックス
        private var videoPosition = 0
        private var videoTexCoord = 0
        private var videoTexture = 0
        // 画像シェーダ用
        private var imageProgram = 0
        private var imageTextureId = 0
        // 画像シェーダインデックス
        private var imagePosition = 0
        private var imageTexCoord = 0
        private var imageTexture = 0
        // サンプラーID(動画と画像で共通)
        private var samplerId = 0
        // 動画頂点情報
        private val videoVertices = createBuffer(
            floatArrayOf(
                -1.0f, 1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                1.0f, -1.0f, 0.0f
            )
        )
        // 画像頂点情報
        private val imageVertices = createBuffer(
            floatArrayOf(
                -1.0f, 1.0f, 0.0f,
                -1.0f, -1.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                1.0f, -1.0f, 0.0f
            )
        )
        // テクスチャ座標情報
        private val texCoords = createBuffer(
            floatArrayOf(
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
            )
        )


        // Float型配列データをFloatBufferに変換する
        private fun createBuffer(array: FloatArray): FloatBuffer {
            return ByteBuffer.allocateDirect(array.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply {
                    put(array)
                    position(0)
                }
        }

        /**
         * OpenGL ESを初期化する
         */
        fun initGl(resources: Resources) {
            // シェーダのコンパイル
            val vertexShader = compileShader(GLES31.GL_VERTEX_SHADER, vertexShaderSource) ?: return
            val videoFragmentShader = compileShader(GLES31.GL_FRAGMENT_SHADER, videoFragmentShaderSource) ?: return
            val imageFragmentShader = compileShader(GLES31.GL_FRAGMENT_SHADER, imageFragmentShaderSource) ?: return
            videoProgram = createProgram(vertexShader, videoFragmentShader) ?: return
            imageProgram = createProgram(vertexShader, imageFragmentShader) ?: return
            // プログラム生成後はシェーダそのものは不要なので削除
            GLES31.glDeleteShader(vertexShader)
            GLES31.glDeleteShader(videoFragmentShader)
            GLES31.glDeleteShader(imageFragmentShader)
            // 変数取得
            videoPosition = GLES31.glGetAttribLocation(videoProgram, "aPosition")
            videoTexCoord = GLES31.glGetAttribLocation(videoProgram, "aTexCoord")
            videoTexture = GLES31.glGetUniformLocation(videoProgram, "uVideoTexture")
            imagePosition = GLES31.glGetAttribLocation(imageProgram, "aPosition")
            imageTexCoord = GLES31.glGetAttribLocation(imageProgram, "aTexCoord")
            imageTexture = GLES31.glGetUniformLocation(imageProgram, "uImageTexture")
            // サンプラーの設定
            setSampler()
            // フレーム画像テクスチャの設定
            setImageTexture(resources)
        }

        // シェーダをコンパイルする
        private fun compileShader(type: Int, source: String): Int? {
            val shader = GLES31.glCreateShader(type)
            GLES31.glShaderSource(shader, source)
            GLES31.glCompileShader(shader)
            val error = IntArray(1)
            GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, error, 0)
            return if (error[0] == GLES31.GL_INVALID_VALUE || error[0] == GLES31.GL_INVALID_OPERATION) {
                null
            } else {
                shader
            }
        }

        // プログラムを生成する
        private fun createProgram(vertexShader: Int, fragmentShader: Int): Int? {
            val program = GLES31.glCreateProgram()
            GLES31.glAttachShader(program, vertexShader)
            GLES31.glAttachShader(program, fragmentShader)
            GLES31.glLinkProgram(program)
            val error = IntArray(1)
            GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, error, 0)
            return if (error[0] == GLES31.GL_INVALID_VALUE || error[0] == GLES31.GL_INVALID_OPERATION) {
                null
            } else {
                program
            }
        }

        // サンプラーを設定する
        private fun setSampler() {
            val imageSamplerIds = IntArray(1)
            GLES31.glGenSamplers(imageSamplerIds.size, imageSamplerIds, 0)

            // 繰り返し設定
            GLES31.glSamplerParameteri(imageSamplerIds[0], GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE)
            GLES31.glSamplerParameteri(imageSamplerIds[0], GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE)
            GLES31.glSamplerParameteri(imageSamplerIds[0], GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
            GLES31.glSamplerParameteri(imageSamplerIds[0], GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)

            samplerId = imageSamplerIds[0]
        }

        /**
         * 動画テクスチャIDを取得する
         *
         * @return テクスチャID
         */
        fun getVideoTextureId(): Int {
            // テクスチャIDを取得
            val videoTextureIds = IntArray(1)
            GLES31.glGenTextures(videoTextureIds.size, videoTextureIds, 0)
            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureIds[0])

            videoTextureId = videoTextureIds[0]
            return videoTextureIds[0]
        }

        // 画像テクスチャを設定する
        private fun setImageTexture(resources: Resources) {
            // テクスチャIDを取得
            val imageTextureIds = IntArray(1)
            GLES31.glGenTextures(imageTextureIds.size, imageTextureIds, 0)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, imageTextureIds[0])

            // 画像設定
            resources.assets.open("frame.png").use {
                val imageBitmap = BitmapFactory.decodeStream(it)
                GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, imageBitmap, 0)
            }

            imageTextureId = imageTextureIds[0]
        }

        /**
         * ViewPortを設定する
         *
         * @param width 幅
         * @param height 高さ
         */
        fun setViewPort(width: Int, height: Int) {
            GLES31.glViewport(0, 0, width, height)
        }

        /**
         * 描画する
         *
         * @param videoTexture 動画テクスチャ
         */
        fun draw(videoTexture: SurfaceTexture) {
            // 背景クリア
            GLES31.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)

            // ブレンディング有効化
            GLES31.glEnable(GLES31.GL_BLEND)
            // ブレンディング設定
            GLES31.glBlendFunc(GLES31.GL_SRC_ALPHA, GLES31.GL_ONE_MINUS_SRC_ALPHA)

            drawVideoTexture(videoTexture)
            drawImageTexture()

            // ブレンディング無効化
            GLES31.glDisable(GLES31.GL_BLEND)
        }

        // 動画テクスチャを描画する
        private fun drawVideoTexture(videoSurfaceTexture: SurfaceTexture) {
            // 動画シェーダに切り替え
            GLES31.glUseProgram(videoProgram)

            // VAO(頂点属性配列)を有効化
            GLES31.glEnableVertexAttribArray(videoPosition)
            GLES31.glEnableVertexAttribArray(videoTexCoord)

            // 動画はテクスチャユニット0へ
            GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
            GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
            GLES31.glBindSampler(0, samplerId)

            // 動画の更新
            videoSurfaceTexture.updateTexImage()

            // 頂点情報、テクスチャ情報をシェーダに流し込む
            GLES31.glVertexAttribPointer(videoPosition, 3, GLES31.GL_FLOAT, false, 0, videoVertices)
            GLES31.glVertexAttribPointer(videoTexCoord, 2, GLES31.GL_FLOAT, false, 0, texCoords)
            GLES31.glUniform1i(videoTexture, 0)

            // 流し込んだデータで描画
            GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)

            // VAO(頂点属性配列)を無効化
            GLES31.glDisableVertexAttribArray(videoPosition)
            GLES31.glDisableVertexAttribArray(videoTexCoord)
        }

        // 画像テクスチャを描画する
        private fun drawImageTexture() {
            // 画像シェーダに切り替え
            GLES31.glUseProgram(imageProgram)

            // VAO(頂点属性配列)を有効化
            GLES31.glEnableVertexAttribArray(imagePosition)
            GLES31.glEnableVertexAttribArray(imageTexCoord)

            // 画像はテクスチャユニット1へ
            GLES31.glActiveTexture(GLES31.GL_TEXTURE1)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, imageTextureId)
            GLES31.glBindSampler(1, samplerId)

            // 頂点情報、テクスチャ情報をシェーダに流し込む
            GLES31.glVertexAttribPointer(imagePosition, 3, GLES31.GL_FLOAT, false, 0, imageVertices)
            GLES31.glVertexAttribPointer(imageTexCoord, 2, GLES31.GL_FLOAT, false, 0, texCoords)
            GLES31.glUniform1i(imageTexture, 1)

            // 流し込んだデータで描画
            GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4)

            // VAO(頂点属性配列)を無効化
            GLES31.glDisableVertexAttribArray(imagePosition)
            GLES31.glDisableVertexAttribArray(imageTexCoord)
        }

    }

}
