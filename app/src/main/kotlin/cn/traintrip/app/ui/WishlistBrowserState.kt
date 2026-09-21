package cn.traintrip.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import cn.traintrip.core.WishProvince

/** Browsing state only; selecting a province never changes the saved cities. */
class WishlistBrowserState {
    var provinceName by mutableStateOf("")
        private set
    private var provinceIndex by mutableIntStateOf(0)
    var navigation = LazyListState()
        private set
    private val lists = mutableMapOf<String, LazyListState>()

    fun list(province: String) = lists.getOrPut(province) { LazyListState() }

    fun current(groups: List<WishProvince>): WishProvince? =
        groups.firstOrNull { it.name == provinceName }
            ?: groups.getOrNull(provinceIndex.coerceAtMost(groups.lastIndex))

    fun select(group: WishProvince, groups: List<WishProvince>) {
        provinceName = group.name
        provinceIndex = groups.indexOf(group).coerceAtLeast(0)
    }

    companion object {
        val Saver = listSaver<WishlistBrowserState, Any>(
            save = { state ->
                mutableListOf<Any>(state.provinceName, state.provinceIndex,
                    state.navigation.firstVisibleItemIndex, state.navigation.firstVisibleItemScrollOffset).apply {
                    state.lists.forEach { (name, list) ->
                        add(name); add(list.firstVisibleItemIndex); add(list.firstVisibleItemScrollOffset)
                    }
                }
            },
            restore = { values ->
                WishlistBrowserState().apply {
                    provinceName = values[0] as String
                    provinceIndex = values[1] as Int
                    navigation = LazyListState(values[2] as Int, values[3] as Int)
                    values.drop(4).chunked(3).forEach { (name, index, offset) ->
                        lists[name as String] = LazyListState(index as Int, offset as Int)
                    }
                }
            }
        )
    }
}
