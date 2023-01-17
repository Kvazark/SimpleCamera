package com.example.cameraapp.fragments

import AutoFitPreviewBuilder
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.*
import androidx.camera.view.TextureViewMeteringPointFactory
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.cameraapp.MainActivity
import com.example.cameraapp.R
import kotlinx.android.synthetic.main.fragment_gallery.*
import kotlinx.android.synthetic.main.fragment_image_capture_fragment.*
import kotlinx.android.synthetic.main.fragment_video_capture_fragment.*
import kotlinx.android.synthetic.main.fragment_video_capture_fragment.view_finder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Main fragment for this app. Реализует все операции с камерой, включая:
 * - Viewfinder
 * - Video taking
 */
private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
private const val VIDEO_EXTENSION = ".mp4"
private const val DEF_TIME_OUT = 1000L

@SuppressLint("RestrictedApi")
class VideoCaptureFragment : Fragment(R.layout.fragment_video_capture_fragment) {

    private lateinit var outputDirectory: File
    private val executor by lazy(LazyThreadSafetyMode.NONE) { Executors.newSingleThreadExecutor() }

    private var lensFacing = CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var videoCapture: VideoCapture? = null
    private var isRecording = false

    private val navController by lazy(LazyThreadSafetyMode.NONE) {
        Navigation.findNavController(
            requireActivity(),
            R.id.fragment_container
        )
    }

    private val videoCaptureLister = object : VideoCapture.OnVideoSavedListener {
        override fun onError(
            videoCaptureError: VideoCapture.VideoCaptureError,
            message: String,
            cause: Throwable?
        ) {
        }

        override fun onVideoSaved(file: File) {

        }
    }

    @SuppressLint("MissingPermission", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //Определение выходного каталога
        outputDirectory = MainActivity.getOutputDirectory(requireContext())
        isRecording = false
        // для корректного отображения вида должным образом
        view_finder.post {
            setUpCameraUI()
            bindCameraUseCases()
        }
    }

    // Объявление и привязывание вариантов использования для предварительного просмотра, захвата и анализа
    private fun bindCameraUseCases() {

        // Настройка использования видоискателя для отображения предварительного просмотра камеры
        val viewFinderConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            //Запрос на соотношение сторон, но не разрешение(камера оптимизирует варианты использования)
            setTargetAspectRatio(AspectRatio.RATIO_4_3)
        }.build()

        // Использ-ие конструктор предварительного просмотра автоматической подгонки для автоматической обработки изменений размера и ориентации
        preview = AutoFitPreviewBuilder.build(viewFinderConfig, view_finder)

        setUpVideoCapture()

        // объявление конфигураций к камере
        CameraX.bindToLifecycle(
            viewLifecycleOwner, preview, videoCapture
        )
    }

    private fun setUpVideoCapture() {
        val videoCaptureConfig = VideoCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
            setTargetAspectRatio(AspectRatio.RATIO_4_3)
            setTargetRotation(view_finder.display.rotation)

        }.build()

        videoCapture = VideoCapture(videoCaptureConfig)
    }

    @SuppressLint("RestrictedApi")
    private fun setUpCameraUI() {

        //переход в галерею
        val navController = Navigation.findNavController(requireActivity(), R.id.fragment_container)
        video_view_button.setOnClickListener {
            val bundle = Bundle().apply {
                putString("root_directory", outputDirectory.absolutePath)
            }
            navController
                .navigate(
                    VideoCaptureFragmentDirections
                        .actionVideoCaptureToGallery(outputDirectory.absolutePath)
                )
        }

        //переход на экран фотографирования
        image_view_button.setOnClickListener {
            navController
                .navigate(
                    VideoCaptureFragmentDirections
                        .actionVideoCaptureFragmentToImageCaptureFragment()
                )
        }
        camera_switch_but.setOnClickListener { switchCamera() }

        isRecording = false
        camera_stop_button.setOnClickListener {
            it.isEnabled = false
            if (!isRecording) {
                it.setBackgroundResource(R.drawable.capture_stop)
                isRecording = true
                startRecordVideo()
            } else {
                it.setBackgroundResource(R.drawable.capture_videostart)
                videoCapture?.apply {
                    if (isRecording)
                        stopRecording()
                }
                isRecording = false
            }
            it.postDelayed({ it.isEnabled = true }, 500)
        }
    }
    private fun switchCamera() {
        lensFacing = if (CameraX.LensFacing.FRONT == lensFacing) {
            CameraX.LensFacing.BACK
        } else {
            CameraX.LensFacing.FRONT
        }
        try {
            CameraX.getCameraWithLensFacing(lensFacing)

            CameraX.unbindAll()
            bindCameraUseCases()
        } catch (exc: Exception) {

        }
    }

    private fun startRecordVideo() {
        videoCapture?.apply {

            // Создание выходного файла для хранения картинки
            val videoFile = createFile(outputDirectory)

            // Настройка прослушивателя захвата изображений, который запускается после того, как картинка была сделана
            startRecording(videoFile, executor, videoCaptureLister)
            isRecording = true

        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

// Вспомогательная функция, используемая для создания файла с временной меткой
    private fun createFile(baseFolder: File) =
        File(
            baseFolder, SimpleDateFormat(FILENAME, Locale.US)
                .format(System.currentTimeMillis()) + VIDEO_EXTENSION
        )
}

