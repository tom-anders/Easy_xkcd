package de.tap.easy_xkcd.comicBrowsing

import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.tap.xkcd_reader.R
import timber.log.Timber

class ComicBrowserFragment : ComicBrowserBaseFragment() {
    val model: ComicsViewModel by activityViewModels()

    private lateinit var progress: ProgressDialog

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        progress = ProgressDialog(activity)
        progress.setTitle(activity?.resources?.getString(R.string.update_database))
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progress.isIndeterminate = false

        model.progress.observe(viewLifecycleOwner) {
            if (it != null) {
                progress.progress = it
                progress.max = model.progressMax
                progress.show()
            } else {
                progress.dismiss()
            }
        }

        return view
    }
}