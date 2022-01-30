package de.tap.easy_xkcd.utils

import android.app.ProgressDialog
import androidx.lifecycle.*
import com.tap.xkcd_reader.R
import de.tap.easy_xkcd.Activities.BaseActivity
import de.tap.easy_xkcd.database.ProgressStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * For state flows we need some boilerplate for collecting it in a fragment
 * This extension function allows us to observe the flow like we would observe a LiveData
 * @see https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bd
 */
inline fun <T> StateFlow<T>.observe(viewLifecycleOwner: LifecycleOwner, crossinline action: suspend (value: T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect {
                action(it)
            }
        }
    }
}

inline fun BaseActivity.collectProgress(progressId: Int, progressFlow: Flow<ProgressStatus>,
                                        crossinline actionAfterCollect: suspend () -> Unit) {
    val progress = ProgressDialog(this)
    progress.setTitle(resources?.getString(progressId))
    progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
    progress.isIndeterminate = false
    progress.setCancelable(false)

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            progressFlow.collect {
                when (it) {
                    is ProgressStatus.Finished -> {
                        progress.dismiss()
                        unlockRotation()
                    }
                    is ProgressStatus.Max -> {
                        lockRotation()
                        progress.max = it.max
                        progress.show()
                    }
                    is ProgressStatus.IncrementProgress -> {
                        progress.progress++
                    }
                    is ProgressStatus.SetProgress -> {
                        progress.progress = it.value
                    }
                    is ProgressStatus.ResetProgress -> {
                        progress.progress = 0
                    }
                }
            }

            actionAfterCollect()
        }
    }
}

inline fun <T> Flow<T>.observe(viewLifecycleOwner: LifecycleOwner, crossinline action: suspend (value: T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect {
                action(it)
            }
        }
    }
}

abstract class ViewModelWithFlowHelper : ViewModel() {
    protected fun <T> Flow<T>.asLazyStateFlow(initialValue: T)
            = this.stateIn(viewModelScope, SharingStarted.Lazily, initialValue)

    protected fun <T> Flow<T>.asEagerStateFlow(initialValue: T)
            = this.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue)
}
