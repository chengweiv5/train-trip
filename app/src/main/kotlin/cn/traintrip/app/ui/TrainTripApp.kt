package cn.traintrip.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.traintrip.app.*
import cn.traintrip.core.DestinationGuides

@Composable fun TrainTripApp(vm:AppViewModel,destinationVm:DestinationViewModel = viewModel(),launcher:((Context)->AppLaunchResult)?=null) {
    val s by vm.state.collectAsStateWithLifecycle()
    val destination by destinationVm.state.collectAsStateWithLifecycle()
    var contentSettings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current
    val screenState=rememberSaveableStateHolder()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle,vm,destinationVm) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP) { vm.pauseForegroundWork();destinationVm.cancel() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(s.page,s.cityId) {
        if(s.page==Page.DESTINATION) s.catalog.byCity[s.cityId]?.let(destinationVm::open)
        else destinationVm.leave()
    }
    fun openSettings() { destinationVm.cancel(); destinationVm.clearSettingsFeedback(); contentSettings=true }
    fun openRailway() { vm.reportAppLaunch(launcher?.invoke(context) ?: launchRailwayApp(context)) }
    BackHandler(!contentSettings && s.page!=Page.FILTERS) { if(s.page==Page.DETAIL || s.page==Page.DESTINATION) vm.backFromCity() else vm.showFilters() }
    Surface(Modifier.fillMaxSize(),color=Cream) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if(!contentSettings) screenState.SaveableStateProvider(if(s.page==Page.DETAIL || s.page==Page.DESTINATION) "${s.page.name}/${s.cityId}/${s.searchSession}" else s.page.name) { when(s.page) {
                Page.FILTERS->FiltersScreen(s,vm::updateFilters,{vm.search()},::openSettings)
                Page.RESULTS->ResultsScreen(s,vm::showFilters,vm::showCity,{vm.search(refresh=true)},vm::stopSearch,{vm.search(resume=true)},{vm.search(retryFailed=true)},vm::sortCities,vm::showDestination,destination.guides)
                Page.DESTINATION->DestinationGuideScreen(s.catalog.byCity[s.cityId]?.name.orEmpty(),destination.guide.takeIf { destination.cityId==s.cityId } ?: destination.guides[s.cityId],vm::backFromCity,vm::showDestinationTrains, onSource = { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
                        .onFailure { Toast.makeText(context,"未找到浏览器",Toast.LENGTH_SHORT).show() }
                }, provinceLabel = s.catalog.byCity[s.cityId]?.provinceLabel.orEmpty(), runtime = destination.takeIf { it.cityId==s.cityId },onRefresh=destinationVm::retry,onSettings=::openSettings)
                Page.DETAIL->DetailScreen(s,vm::backFromCity,vm::select,{vm.refreshCity()},{vm.refreshCity(retryFailed=true)},vm::clearSelection,::openRailway)
            } }
        }
    }
    if(contentSettings) SettingsScreen(destination,{contentSettings=false},destinationVm::saveModelSettings,
        destinationVm::saveSearchSettings,destinationVm::removeKey,destinationVm::removeTavilyKey,destinationVm::clearSettingsFeedback)
}
