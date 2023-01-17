package com.example.cameraapp.fragments

import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.cameraapp.BuildConfig
import com.example.cameraapp.R
import kotlinx.android.synthetic.main.fragment_gallery.*
import java.io.File


val EXTENSION_WHITELIST = arrayOf("JPG", "MP4")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    // AndroidX navigation arguments
    private val args: GalleryFragmentArgs by navArgs()

    private lateinit var mediaList: MutableList<File>

    // Класс адаптера, используемый для представления фрагмента, содержащего одну фотографию или видео, в виде страницы */
    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment {
            val currentItem = mediaList[position]
            return if (currentItem.extension.toLowerCase() == "jpg") {
                PhotoFragment.create(currentItem)
            } else {
                VideoFragment.create(currentItem)
            }
        }

        override fun getItemPosition(obj: Any): Int = POSITION_NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // а-ля фрагмент сохранения, чтобы жизненный цикл не перезапускался при изменении конфигурации
        retainInstance = true

        // Получить корневой каталог носителя из аргументов навигации
        val rootDirectory = File(args.rootDirectory)

        //Проходимся по всем файлам в корневом каталоге
        // Меняем порядок списка на обратный, чтобы сначала представить последние фото/виедо
        mediaList = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
        }.sorted().reversed().toMutableList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_gallery, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Функция области видимости. Заполняет ViewPager и устанавливает возвращение
        // 2-х страниц за пределами экрана, которые кэшируются в памяти.
        photo_view_pager.apply {
            offscreenPageLimit = 2
            adapter = MediaPagerAdapter(childFragmentManager)
        }
        backVideo.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                R.id.action_gallery_fragment_to_videoCaptureFragment)
        }
        backPhoto.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                R.id.action_gallery_fragment_to_imageCaptureFragment)
        }

    }

}