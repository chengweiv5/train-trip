package cn.traintrip.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ThemeUiState(
    val choice: ThemeChoice = ThemeChoice.BLUE,
    val loading: Boolean = false,
    val ready: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val event: Long = 0,
)

class ThemeViewModel(
    private val preference: ThemePreference,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ThemeUiState(loading = true, ready = false))
    val state = mutableState.asStateFlow()
    private var confirmed = ThemeChoice.BLUE
    private var generation = 0L
    private val writes = Mutex()

    init { load() }

    fun reload() {
        if (!state.value.loading && !state.value.saving) load()
    }

    private fun load() {
        mutableState.value = state.value.copy(loading = true, ready = false, message = null, error = null)
        viewModelScope.launch {
            try {
                confirmed = withContext(ioDispatcher) { preference.read() }
                mutableState.value = ThemeUiState(choice = confirmed, event = ++generation)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.value = state.value.copy(loading = false, ready = false,
                    error = "未能读取主题，暂用晴空蓝，请重试", event = ++generation)
            }
        }
    }

    fun select(choice: ThemeChoice) {
        if (!state.value.ready || choice == state.value.choice) return
        val request = ++generation
        mutableState.value = state.value.copy(choice = choice, saving = true, message = null, error = null, event = request)
        viewModelScope.launch {
            writes.withLock {
                // Requests not yet started may be superseded. In-flight writes finish in order.
                if (request != generation) return@withLock
                try {
                    withContext(ioDispatcher) { preference.save(choice) }
                    confirmed = choice
                    if (request == generation) mutableState.value = state.value.copy(saving = false, message = "已切换为${choice.label}")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (request == generation) mutableState.value = state.value.copy(choice = confirmed, saving = false,
                        error = "未能保存主题，请重试")
                }
            }
        }
    }

    fun clearFeedback(event: Long) {
        if (event == state.value.event) mutableState.value = state.value.copy(message = null)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { ThemeViewModel(AndroidThemePreference(context.applicationContext)) }
        }
    }
}
