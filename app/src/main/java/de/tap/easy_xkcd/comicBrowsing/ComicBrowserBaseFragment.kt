package de.tap.easy_xkcd.comicBrowsing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tap.xkcd_reader.databinding.PagerLayoutBinding
import de.tap.easy_xkcd.utils.PrefHelper
import de.tap.easy_xkcd.utils.ThemePrefs

abstract class ComicBrowserBaseFragment: Fragment() {
    private var _binding: PagerLayoutBinding? = null
    protected val binding get() = _binding!!

    protected lateinit var prefHelper: PrefHelper
    protected lateinit var themePrefs: ThemePrefs

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = PagerLayoutBinding.inflate(inflater, container, false)

        setHasOptionsMenu(true)

        prefHelper = PrefHelper(activity)
        themePrefs = ThemePrefs(activity)

        return binding.root
    }

    protected fun inflateLayout(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) {
    }
}