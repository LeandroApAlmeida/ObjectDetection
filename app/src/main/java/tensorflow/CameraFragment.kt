package tensorflow

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.google.android.material.snackbar.Snackbar
import org.tensorflow.lite.examples.objectdetection.R
import org.tensorflow.lite.examples.objectdetection.databinding.FragmentCameraBinding
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * Fragment para exibição de imagens obtidas da câmera, destaque de objetos na cena e configuração
 * de parâmetros do Detector de objetos.
 */
class CameraFragment: Fragment(), TextToSpeech.OnInitListener {

    /**Delega o processamento da imagem à CPU*/
    private val DELEGATE_CPU = 0
    /**Delega o processamento da imagem à GPU*/
    private val DELEGATE_GPU = 1
    /**Delega o processamento da imagem à NNAPI*/
    private val DELEGATE_NNAPI = 2

    /**Modelo <i>"../assets/mobilenetv1.tflite"</i>*/
    private val MODEL_MOBILENETV1 = 0
    /**Modelo <i>"../assets/efficientdet-lite0.tflite"</i>*/
    private val MODEL_EFFICIENTDETV0 = 1
    /**Modela <i>"../assets/efficientdet-lite1.tflite"</i>*/
    private val MODEL_EFFICIENTDETV1 = 2
    /**Modelo <i>"../assets/efficientdet-lite2.tflite"</i>*/
    private val MODEL_EFFICIENTDETV2 = 3

    /**Detector de objetos numa imagem implementado pelo TensorFlow.*/
    private var objectDetector: ObjectDetector? = null
    /**Parâmetro Threshold para o detector de objetos na imagem.*/
    private var threshold: Float = 0.5f
    /**Parâmetro NumThreads para o detector de objetos na imagem.*/
    private var numThreads: Int = 2
    /**Parâmetro que determina se o processamento da imagem pelo TensorFlow será delegado à CPU, GPU, etc.*/
    private var currentDelegate: Int = 0
    /**Arquivo de banco de dados do TensorFlow que está no diretório assets.*/
    private var currentModel: Int = 0

    /**Buffer para a imagem capturada pela câmera a ser analisado pelo detector de objetos.*/
    private lateinit var bitmapBuffer: Bitmap
    /**Fornece a visualização do stream de vídeo capturado pela câmera em uma view.*/
    private var preview: Preview? = null
    /**Fornece as imagens para o processamento de imagens.*/
    private var imageAnalyzer: ImageAnalysis? = null
    /**Fornece acesso à câmera do aparelho.*/
    private var camera: Camera? = null
    /**Controle do ciclo de vida das câmeras dentro do processo do applicativo.*/
    private var cameraProvider: ProcessCameraProvider? = null
    /**Executor para controle de tarefas assíncronas relacionadas com a câmera.*/
    private lateinit var cameraExecutor: ExecutorService

    /**Rótulo do objeto reconhecido pelo detector de objetos.*/
    private var controlLabel: String = ""
    /**Momento da última atualização do rótulo do objeto.*/
    private var lastUpdateLabel: Long = 0L
    /**Modo de detecção de objetos. Se true, está detectando. Se false, está exibindo a saída.*/
    private var detectionMode: Boolean = true
    /**Dicionário para tradução dos rótulos de objetos do inglês para o português.*/
    private lateinit var dictionary: Dictionary

    /**Provê o acesso ao sintetizador de fala do sistema.*/
    private lateinit var textToSpeech: TextToSpeech
    /**Status de acesso ao sintetizador de fala. Se true, o sintetizador de fala está disponível, se false, não está.*/
    private var isTTSEnabled = true

    /**Acesso aos views do Fragment.*/
    private lateinit var binding: FragmentCameraBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Inicializar a captura de vídeo pela câmera e o reconhecimento de objetos, bem como configurar
     * os controles de interface gráfica de usuário.
     */
    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        textToSpeech = TextToSpeech(context, this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        dictionary = Dictionary(this.requireContext())

        // Configura os controles de threshold.
        binding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (threshold >= 0.1) {
                threshold -= 0.1f
                configObjectDetector()
            }
        }
        binding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (threshold <= 0.8) {
                threshold += 0.1f
                configObjectDetector()
            }
        }
        // Configura os controles de Threads.
        binding.bottomSheetLayout.threadsMinus.setOnClickListener {
            if (numThreads > 1) {
                numThreads--
                configObjectDetector()
            }
        }
        binding.bottomSheetLayout.threadsPlus.setOnClickListener {
            if (numThreads < 4) {
                numThreads++
                configObjectDetector()
            }
        }
        // Configura os controles de delegação de execução.
        binding.bottomSheetLayout.spinnerDelegate.setSelection(0, false)
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener = object : AdapterView
        .OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                currentDelegate = p2
                configObjectDetector()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }
        // Configura os controles de seleção do arquivo de modelo do TensorFlow.
        binding.bottomSheetLayout.spinnerModel.setSelection(0, false)
        binding.bottomSheetLayout.spinnerModel.onItemSelectedListener = object : AdapterView
        .OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                currentModel = p2
                configObjectDetector()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }

        // Configura o detector de objetos.
        configObjectDetector()

        // Configura o componente de exibição da stream de vídeo da câmera.
        binding.cameraView.post {
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
                cameraProvider = cameraProviderFuture.get()
                cameraProviderFuture.addListener(
                    CameraListener(this),
                    ContextCompat.getMainExecutor(requireContext())
                )
            } catch (ex: Exception) {
                showMessage(ex.message!!)
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(),
                R.id.fragment_container
            ).navigate(
                CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    /**
     * Evento de inicialização do sintetizador de voz.
     * @param status do sintetizador. Caso seja TextToSpeech.SUCCESS, está tudo certo, caso
     * contrário, não pode acessar o sintetizador.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Escolhe o idioma e a voz padrão para o sintetizador configurados como padrão no sistema.
            val result = textToSpeech.setLanguage(Locale.getDefault())
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech.voice = Voice(
                    textToSpeech.defaultVoice.name,
                    Locale.getDefault(),
                    400,
                    200,
                    false,
                    hashSetOf("male")
                )
            } else {
                isTTSEnabled = false
            }
        } else {
            isTTSEnabled = false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = binding.cameraView.display.rotation
    }

    /**
     * Configurar o detector de objetos de acordo com os parâmetros definidos.
     */
    private fun configObjectDetector() {

        // Threshold e número de objetos a detectar.
        val optionsBuilder = ObjectDetector
            .ObjectDetectorOptions
            .builder()
            .setScoreThreshold(threshold)
            .setMaxResults(1)

        // Número de threads.
        val baseOptionsBuilder = BaseOptions
            .builder()
            .setNumThreads(numThreads)

        // Modo de processamento (CPU, GPU, etc).
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Padrão
            }
            DELEGATE_GPU -> {
                if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                    baseOptionsBuilder.useGpu()
                } else {
                    showMessage("GPU não é suportada neste dispositivo.")
                }
            }
            DELEGATE_NNAPI -> {
                baseOptionsBuilder.useNnapi()
            }
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        // Arquivo de modelo do TensorFlow. Estes arquivos são baixados automáticamente pelo
        // Gradle quando executa pela primeira vez.
        val modelName = when (currentModel) {
            MODEL_MOBILENETV1 -> "mobilenetv1.tflite"
            MODEL_EFFICIENTDETV0 -> "efficientdet-lite0.tflite"
            MODEL_EFFICIENTDETV1 -> "efficientdet-lite1.tflite"
            MODEL_EFFICIENTDETV2 -> "efficientdet-lite2.tflite"
            else -> "mobilenetv1.tflite"
        }

        // Configura o detector de objetos.
        try {
            objectDetector = null
            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                modelName,
                optionsBuilder.build()
            )
        } catch (e: IllegalStateException) {
            showMessage("Falha na inicialização do detector de objetos.")
        }

        // Atualiza os controle na tela.
        binding.bottomSheetLayout.thresholdValue.text = String.format("%.2f", threshold)
        binding.bottomSheetLayout.threadsValue.text = numThreads.toString()
        binding.drawingView.clear()

    }

    /**
     * Realizar a detecção de objetos na imagem obtida da câmera. Caso se passe 1 segundo e continua
     * apontando para o mesmo objeto, indica que o utilizador focou o mesmo e quer a sua descrição.
     * @param image imagem capturada pela câmera que será passada para o detector de objetos
     * para que este localize o objeto que desejamos na cena.
     * @param imageRotation rotação da imagem.
     */
    private fun detectObject(image: Bitmap, imageRotation: Int) {

        // Aqui, vai verificar se o programa está em modo de detecção ou não. No modo de detecção,
        // a sequência de processamento é a seguinte:
        //
        // 1. Normaliza a imagem obtida da câmera e a aplica à detecção.
        //
        // 2. Caso o detector de objetos do TensorFlow tenha encontrado algum objeto nela, primeiramente
        // verifica se já faz 1 segundo ou mais que o algoritmo está voltando o rótulo para o mesmo
        // objeto. Isto indica que o utilizador está com a câmera focada nele.
        //
        // Se faz um segundo ou mais, tira o app do modo de detecção e prepara para a exibição do
        // resultado.
        //
        // A exibição do resultado é em três etapas:
        //
        //     1. Aciona o sintetizador de fala para falar a descrição do objeto detectado.
        //
        //     2. Exibe um Snackbar com o texto da descrição do objeto na parte inferior.
        //
        //     3. Desenha a imagem do personagem no componente de segunda camada.
        //
        // É registrado o tempo que saiu do modo de detecção. Por 4 segundos vai continuar exibindo
        // o personagem na tela, bem como o Snackbar. Passado este tempo, retoma o modo de detecção.
        //
        // Se o app não estiver no modo de detecção, apenas verifica se já se passaram os 4 segundos
        // desde que o app saiu, e retona o modo de detecção.

        if (detectionMode) {

            // O App está no modo de detecção de objetos...

            // Normaliza a imagem recebida e submete à detecção de objetos nela.
            val imageProcessor = ImageProcessor.Builder().add(Rot90Op(-imageRotation / 90)).build()
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))
            val detectionsList = objectDetector?.detect(tensorImage)

            // Como está configurado para detectar somente um objeto, basta verificar se a lista
            // de detecções está vazia ou não e pegar a primeira detecção.
            if (detectionsList!!.isNotEmpty()) {

                // Verifica a mudança ou permanência da label do objeto detectado.
                val detection = detectionsList[0]
                val currentLabel = dictionary.translateToPortuguese(detection.categories[0].label)
                if (currentLabel != controlLabel) {
                    controlLabel = currentLabel
                    lastUpdateLabel = SystemClock.uptimeMillis()
                }

                // Verifica se já faz 1 segundo ou mais que está retornando o mesma label.
                if (SystemClock.uptimeMillis() - lastUpdateLabel >= 1000) {

                    // Já faz 1 segundo ou mais que o detector de objetos está retornando a
                    // mesma label. Isso indica que o utilizador está com a câmera focada no
                    // objeto...

                    // Sai do modo de detecção e registra o momento em que saiu.
                    detectionMode = false
                    lastUpdateLabel = SystemClock.uptimeMillis()
                    controlLabel = ""

                    // Aqui vai executar as duas primeiras etapas da saída da detecção.
                    //
                    // 1. Aciona o sintetizador de fala para falar a descrição do objeto detectado.
                    //
                    // 2. Exibe um Snackbar com o texto da descrição do objeto na parte inferior.
                    activity?.runOnUiThread {
                        binding.cameraView.invalidate()
                        Thread.sleep(1000)
                        try {
                            if (isTTSEnabled) {
                                val bundle = Bundle()
                                bundle.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                                textToSpeech.speak(currentLabel, TextToSpeech.QUEUE_FLUSH, bundle, "")
                            }
                            showMessage(currentLabel)
                        } catch (ex: Exception) {
                            showMessage(ex.message!!)
                        }
                    }

                }

                // Aciona o componente de exibição da segunda camada. Caso esteja em modo de
                // detecção, vai desenhar o retângulo ao redor do objeto detectado pelo
                // detector de objetos. Estas coordenadas são lidas do próprio objeto "detection"
                // obtido do processamento.
                //
                // Caso tenha saído do modo de detecção, vai ficar exibindo a imagem do personagem
                // no canto superior esquerdo da tela até voltar novamente ao modo de detecção.
                activity?.runOnUiThread {
                    binding.drawingView.setDetection(detection)
                    binding.drawingView.setImageBounds(tensorImage.height, tensorImage.width)
                    binding.drawingView.setDetectionMode(detectionMode)
                    binding.drawingView.invalidate()
                }

            }

        } else {

            // Ao passar 4 segundos ou mais após sair do modo de detecção, volta novamente para
            // este modo.
            if (SystemClock.uptimeMillis() - lastUpdateLabel >= 4000) {
                detectionMode = true
            }

        }

    }

    /**
     * Exibe uma Snackbar na tela.
     */
    private fun showMessage(text: String) {
        activity?.runOnUiThread {
            Snackbar.make(
                binding.cameraView,
                text.uppercase(Locale.ROOT),
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Configura o componente de captura de frames da câmera
     */
    private inner class CameraListener(val fragment: CameraFragment): Runnable {

        override fun run() {

            val cameraSelector = CameraSelector
                .Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            preview = Preview
                .Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(binding.cameraView.display.rotation)
                .build()

            imageAnalyzer = ImageAnalysis
                .Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(binding.cameraView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888).build().also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        if (!::bitmapBuffer.isInitialized) {
                            bitmapBuffer = Bitmap.createBitmap(
                                image.width,
                                image.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }
                        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
                        val imageRotation = image.imageInfo.rotationDegrees
                        detectObject(bitmapBuffer, imageRotation)
                    }
                }

            cameraProvider?.unbindAll()

            try {
                camera = cameraProvider?.bindToLifecycle(
                    fragment,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                preview?.setSurfaceProvider(binding.cameraView.surfaceProvider)
            } catch (ex: Exception) {
                showMessage(ex.message!!)
            }

        }

    }

}
