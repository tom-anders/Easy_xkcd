package de.tap.easy_xkcd.utils

import androidx.lifecycle.*
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
