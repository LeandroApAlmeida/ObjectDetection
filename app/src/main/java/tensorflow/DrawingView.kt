package tensorflow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.tensorflow.lite.examples.objectdetection.R
import org.tensorflow.lite.task.vision.detector.Detection
import kotlin.math.max

/**
 * Componente personalizado para desenhar o retângulo ao redor do objeto identificado pelo
 * detector de objetos do TensorFlow e, quando produz saída, o personagem no canto superior
 * esquerdo da tela. Este componente forma uma segunda camada sobre o componente que está
 * exibindo a stream de vídeo capturada pela câmera.
 */
class DrawingView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    /**Dados do objeto detectado na imagem.*/
    private var detection: Detection? = null
    /**Parâmetros para o desenho do retângulo ao redor do objeto identificado.*/
    private var boxPaint = Paint()
    /**Ajuste de escala da imagem.*/
    private var scaleFactor: Float = 1f
    /**Modo de detecção de objetos. Se true, está detectando. Se false, está exibindo a saída.*/
    private var detectionMode: Boolean = true

    init {
        initPaint()
    }

    /**
     * Limpar os parâmetros.
     */
    fun clear() {
        boxPaint.reset()
        invalidate()
        initPaint()
    }

    /**
     * Inicializar os parâmetros.
     */
    private fun initPaint() {
        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    /**
     * Método que desenha na tela.
     * @param canvas objeto para acesso ao componente de desenho na tela.
     */
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (detectionMode) {
            // Modo de detecção. Neste caso, obtém as coordenadas do retângulo ao redor do objeto
            // na imagem. O próprio componente de detecção de objetos do TensorFlow retorna estas
            // coordenadas, ficando a cargo aqui apenas criar o retângulo visualmente para exibir
            // na segunda camada.
            if (detection != null) {
                val boundingBox = detection!!.boundingBox // Coordenadas retornadas pelo detector
                val top = boundingBox.top * scaleFactor
                val bottom = boundingBox.bottom * scaleFactor
                val left = boundingBox.left * scaleFactor
                val right = boundingBox.right * scaleFactor
                val drawableRect = RectF(left, top, right, bottom)
                canvas.drawRect(drawableRect, boxPaint)
            }
        } else {
            // Modo de saída. Exibe o personagem no canto superior esquerdo da tela.
            val drawable = resources.getDrawable(R.drawable.mule, null)
            val side = this.width - (this.width / 5)
            drawable.setBounds(30, 30, side, side)
            drawable.draw(canvas)
        }
    }

    /**
     * Definir dados do objeto detectado.
     * @param detection dados do objeto.
     */
    fun setDetection(detection: Detection) {
        this.detection = detection
    }

    /**
     * Definir dimensões da imagem.
     * @param imageHeight altura da imagem.
     * @param imageWidth largura da imagem.
     */
    fun setImageBounds(imageHeight: Int, imageWidth: Int) {
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
    }

    /**
     * Definir o status do modo de detecção.
     * @param detectionMode Se true, está no modo de detecção. Se false, não está no modo de detecção.
     */
    fun setDetectionMode(detectionMode: Boolean) {
        this.detectionMode = detectionMode
    }

}