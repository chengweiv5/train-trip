package cn.traintrip.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.traintrip.app.*
import kotlinx.coroutines.delay

@Composable fun TrainTripApp(vm:AppViewModel,destinationVm:DestinationViewModel = viewModel(),
    wishlistVm:WishlistViewModel=viewModel(),updateVm:UpdateViewModel=viewModel(factory=UpdateViewModel.factory(LocalContext.current)),launcher:((Context)->AppLaunchResult)?=null,
    themeState:ThemeUiState=ThemeUiState(),onThemeSelect:(ThemeChoice)->Unit={},onThemeRetry:()->Unit={},onThemeDismiss:(Long)->Unit={}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val destination by destinationVm.state.collectAsStateWithLifecycle()
    val wish by wishlistVm.state.collectAsStateWithLifecycle()
    val updates by updateVm.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val screenState=rememberSaveableStateHolder()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val accessibility=LocalAccessibilityManager.current
    DisposableEffect(lifecycle,vm,destinationVm,wishlistVm,updateVm) {
        val observer=LifecycleEventObserver { _,event->when(event) {
            Lifecycle.Event.ON_RESUME -> wishlistVm.reload()
            Lifecycle.Event.ON_STOP -> { vm.pauseForegroundWork();destinationVm.cancel();updateVm.leave() }
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(s.page,s.cityId) {
        if(s.page==Page.DESTINATION) s.catalog.byCity[s.cityId]?.let(destinationVm::open)
        else destinationVm.leave()
        if(s.page==Page.OFFLINE || s.page==Page.WISHLIST || s.page==Page.SETTINGS)destinationVm.refreshOffline()
        if(s.page==Page.WISHLIST || s.page==Page.ADD_CITY)wishlistVm.reload()
        if(s.page!=Page.ABOUT)updateVm.leave()
    }
    LaunchedEffect(wish.event,wish.notice) {
        if(wish.notice!=null) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(5000,containsIcons=false,containsText=true,containsControls=wish.undo!=null) ?: 5000)
            wishlistVm.dismiss(wish.event)
        }
    }
    fun openSettings() { destinationVm.cancel();destinationVm.clearSettingsFeedback();vm.navigate(Page.SETTINGS) }
    fun openRailway() { vm.reportAppLaunch(launcher?.invoke(context) ?: launchRailwayApp(context)) }
    BackHandler(s.page!=Page.SETTINGS && s.page!=Page.FILTERS && s.page!=Page.WISHLIST) { vm.back() }
    Surface(Modifier.fillMaxSize(),color=androidx.compose.ui.graphics.Color.White) {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.safeDrawing).background(HeaderTop))
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                val stateKey=when(s.page) {
                    Page.DESTINATION,Page.DETAIL -> "${s.page}/${s.cityId}/${s.searchSession}"
                    Page.ADD_CITY,Page.CITY_QUERY,Page.SETTINGS -> "${s.page}/${s.navigationEntry}"
                    else -> s.page.name
                }
                screenState.SaveableStateProvider(stateKey) { when(s.page) {
                    Page.FILTERS->FiltersScreen(s,vm::updateFilters,{vm.search()},::openSettings,wishlist=wish,onReloadWishlist=wishlistVm::reload)
                    Page.CITY_QUERY->FiltersScreen(s.copy(filters=s.cityQueryFilters ?: s.filters),vm::updateFilters,{vm.search()},onBack=vm::back,wishlist=wish,onReloadWishlist=wishlistVm::reload)
                    Page.WISHLIST->WishlistScreen(wish,s.catalog,destination.guides,destination.offline,{vm.navigate(Page.ADD_CITY)},wishlistVm::toggle,vm::showDestination,vm::openCityQuery,wishlistVm::reload)
                    Page.ADD_CITY->AddCityScreen(s.catalog,wish,vm::back,wishlistVm::add)
                    Page.RESULTS->ResultsScreen(s,vm::showFilters,vm::showCity,{vm.search(refresh=true)},vm::stopSearch,{vm.search(resume=true)},{vm.search(retryFailed=true)},vm::sortCities,vm::showDestination,destination.guides,wish.items.map { it.cityId }.toSet(),wishlistVm::toggle)
                    Page.DESTINATION->DestinationGuideScreen(s.catalog.byCity[s.cityId]?.name.orEmpty(),destination.guide.takeIf { destination.cityId==s.cityId } ?: destination.guides[s.cityId],vm::backFromCity,vm::showDestinationTrains,onSource={url->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { Toast.makeText(context,"未找到浏览器",Toast.LENGTH_SHORT).show() }
                    },provinceLabel=s.catalog.byCity[s.cityId]?.provinceLabel.orEmpty(),runtime=destination.takeIf { destination.cityId==s.cityId },onRefresh=destinationVm::retry,onSettings=::openSettings,
                        city=s.catalog.byCity[s.cityId],favorite=wish.items.any { it.cityId==s.cityId },onFavorite={s.catalog.byCity[s.cityId]?.let(wishlistVm::toggle)},queryAction=!s.guideFromResults)
                    Page.DETAIL->DetailScreen(s,vm::backFromCity,vm::select,{vm.refreshCity()},{vm.refreshCity(retryFailed=true)},vm::clearSelection,::openRailway)
                    Page.SETTINGS->SettingsScreen(destination,vm::back,destinationVm::saveModelSettings,destinationVm::saveSearchSettings,destinationVm::removeKey,destinationVm::removeSearchKey,destinationVm::clearSettingsFeedback,
                        onOffline={vm.navigate(Page.OFFLINE)},onAbout={vm.navigate(Page.ABOUT)},offlineBytes=destination.offline.sumOf { it.bytes },offlineCount=destination.offline.size,applySafeInsets=false,
                        themeState=themeState,onThemeSelect=onThemeSelect,onThemeRetry=onThemeRetry,onThemeDismiss=onThemeDismiss)
                    Page.OFFLINE->OfflineContentScreen(destination,s.catalog,vm::back,vm::showDestination,destinationVm::deleteOffline,destinationVm::refreshOffline)
                    Page.ABOUT->AboutScreen(updates,vm::back,updateVm::check)
                } }
            }
            wish.notice?.let { notice -> Snackbar(action={wish.undo?.let { record->TextButton({wishlistVm.undo(record)},enabled=!wish.busy){Text("撤销")} }}) { Text(notice) } }
            if(s.page==Page.FILTERS || s.page==Page.WISHLIST)RootNavigation(s.page,vm::selectRoot)
        }
      }
    }
}
