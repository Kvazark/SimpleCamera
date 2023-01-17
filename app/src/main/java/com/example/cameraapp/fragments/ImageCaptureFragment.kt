package com.example.cameraapp.fragments

import ANIMATION_FAST_MILLIS
import ANIMATION_SLOW_MILLIS
import AutoFitPreviewBuilder
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CaptureMode
import androidx.camera.view.TextureViewMeteringPointFactory
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.cameraapp.MainActivity
import com.example.cameraapp.R
import kotlinx.android.synthetic.main.fragment_image_capture_fragment.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 */
private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
private const val PHOTO_EXTENSION = ".jpg"


@SuppressLint("RestrictedApi")
class ImageCaptureFragment : Fragment(R.layout.fragment_image_capture_fragment) {

    private lateinit var outputDirectory: File
    private val executor by lazy(LazyThreadSafetyMode.NONE) { Executors.newSingleThreadExecutor() }

    private var lensFacing = CameraX.LensFacing.BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private val navController by lazy(LazyThreadSafetyMode.NONE) {
        Navigation.findNavController(
            requireActivity(),
            R.id.fragment_container
        )
    }


    override fun onResume() {
        super.onResume()
//        // Make sure that all permissions are still present, since user could have removed them
//        //  while the app was on paused state
//        if (!PermissionsFragment.hasPermissions(requireContext())) {
//            navController.navigate(
//                ImageCaptureFragmentDirections.actionCameraToPermissions()
//            )
//
//        }
    }

    //Определение обратного вызова, который будет запущен после того, как фотография будет сделана и сохранена на диск
    private val imageSavedListener = object : ImageCapture.OnImageSavedListener {
        override fun onError(
            error: ImageCapture.ImageCaptureError, message: String, exc: Throwable?
        ) {
            exc?.printStackTrace()
        }

        override fun onImageSaved(photoFile: File) {
            setGalleryThumbnail(photoFile)
        }
    }

    @SuppressLint("MissingPermission", "ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Определение выходной каталог
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        view_finder.post {

            // Создание элементов управления пользовательского интерфейса и связка вариантов использования камеры
            updateCameraUi()
            bindCameraUseCases()

        }
        setUpTapToFocus()
    }

    private fun setUpTapToFocus() {
        view_finder.setOnTouchListener { _, motionEvent ->
            val point = TextureViewMeteringPointFactory(view_finder)
                .createPoint(motionEvent.x, motionEvent.y)
            val action = FocusMeteringAction.Builder
                .from(point)
                .build()
            CameraX.getCameraControl(CameraX.LensFacing.BACK).startFocusAndMetering(action)
            false
        }
    }

    // Предварительный просмотр объявления и привязки, захвата и анализа вариантов использования
    private fun bindCameraUseCases() {

        // Настройка viewfinder для отображения предварительного просмотра камеры
        val viewFinderConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            //запрос на соотношение сторон
            setTargetAspectRatio(AspectRatio.RATIO_4_3)

            setTargetRotation(view_finder.display.rotation)
        }.build()

        // Использование конструктора  preview для автоматической обработки изменений размера и ориентации
        preview = AutoFitPreviewBuilder.build(viewFinderConfig, view_finder)

        setUpImageCapture()

        CameraX.bindToLifecycle(
            viewLifecycleOwner, preview, imageCapture
        )
    }

    private fun setUpImageCapture() {
        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(lensFacing)
            setCaptureMode(CaptureMode.MAX_QUALITY)
            // запрос на размер
            setTargetAspectRatio(AspectRatio.RATIO_4_3)

            setTargetRotation(view_finder.display.rotation)
        }.build()

        imageCapture = ImageCapture(imageCaptureConfig)
    }

    @SuppressLint("RestrictedApi")
    private fun updateCameraUi() {
        camera_capture_button.setOnClickListener { takeCapture() }

        camera_switch_button.setOnClickListener { switchCamera() }

        //переход в галерею
        val navController = Navigation.findNavController(requireActivity(), R.id.fragment_container)
        photo_view_button.setOnClickListener {
            val bundle = Bundle().apply {
                putString("root_directory", outputDirectory.absolutePath)
            }
            navController
                .navigate(
                    ImageCaptureFragmentDirections
                        .actionCameraToGallery(outputDirectory.absolutePath)
                )
        }
    //переход для записи видео
        camera_record_button.setOnClickListener {
            navController
                .navigate(
                    ImageCaptureFragmentDirections
                        .actionCameraFragmentToVideoCaptureFragment()
                )
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

    private fun takeCapture() {
        // Получение стабильной ссылки на вариант использования изменяемого захвата изображения
        imageCapture?.apply {

            // Создайте выходного файла для хранения изображения
            val photoFile = createFile(outputDirectory)

                // Настройка метаданных захвата изображения
            val metadata = ImageCapture.Metadata().apply {
                // Зеркальное отображение при использовании фронтальной камеры
                isReversedHorizontal = lensFacing == CameraX.LensFacing.FRONT
            }

            // Настройка прослушивателя захвата изображений, который запускается после того, как фотография была сделана
            takePicture(photoFile, metadata, executor, imageSavedListener)


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Отображение флэш-анимации, указывающей на то, что фотография была сделана
                camera_container.postDelayed({
                    camera_container.foreground = ColorDrawable(Color.WHITE)
                    camera_container.postDelayed(
                        { camera_container.foreground = null }, ANIMATION_FAST_MILLIS
                    )
                }, ANIMATION_SLOW_MILLIS)
            }
        }
    }

    private fun setGalleryThumbnail(file: File) {
        // Запуск операции в потоке представления
        view
            ?.findViewById<ImageButton>(R.id.photo_view_button)
            ?.post {
                photo_view_button.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

                // Загрузите миниатюру в круглую кнопку с помощью скольжения
                Glide.with(photo_view_button)
                    .load(file)
                    .apply(RequestOptions.circleCropTransform())
                    .into(photo_view_button)
            }
    }

    private fun createFile(baseFolder: File) =
        File(
            baseFolder, SimpleDateFormat(FILENAME, Locale.US)
                .format(System.currentTimeMillis()) + PHOTO_EXTENSION
        )
}
