package cn.traintrip.app

import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.traintrip.app.ui.*
import cn.traintrip.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsMenuTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun back() = compose.onNodeWithTag("settings-back").performClick()
    @Test fun navigationSeparatesServicesDiscardsDraftsAndRestoresSecureFlag() {
        var closed=false;var saved:Pair<String,String>?=null;var searches=0
        compose.setContent { TrainTripTheme {
            SettingsScreen(DestinationState(configured=true,tavilyConfigured=true),{closed=true},
                {m,k,done->saved=m to k;done()},{_,done->searches++;done()},{},{})
        } }
        compose.onNodeWithTag("settings-model").performClick()
        compose.onNodeWithTag("settings-model-name").performTextReplacement("deepseek-test")
        compose.onNodeWithTag("deepseek-key").performTextInput("sk-draft-never-saved")
        compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        back()
        compose.runOnIdle { assertFalse(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
        compose.onNodeWithTag("settings-model").performClick()
        compose.onNodeWithTag("settings-model-name").assertTextContains(DeepSeekGuideGenerator.MODEL)
        compose.onNodeWithTag("deepseek-key").assertTextContains("")
        compose.onNodeWithTag("settings-model-name").performTextReplacement("deepseek-other")
        compose.onNodeWithTag("save-settings").performScrollTo().performClick()
        assertEquals("deepseek-other" to "",saved)
        compose.onNodeWithTag("settings-search").performClick()
        compose.onNodeWithTag("settings-model-name").assertDoesNotExist()
        compose.onNodeWithTag("tavily-key").performTextInput("tvly-draft")
        back();assertEquals(0,searches)
        back();assertTrue(closed)
    }
    @Test fun largeFontKeepsSaveAndRemoveReachableAndBusyBlocksBack() {
        var busy by mutableStateOf(false);var closed=false
        compose.setContent { TrainTripTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                SettingsScreen(DestinationState(configured=true,settingsBusy=busy),{closed=true},{_,_,_->},{_,_->},{},{})
            }
        } }
        compose.onNodeWithTag("settings-model").performClick()
        compose.onNodeWithTag("save-settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("remove-settings-key").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { busy=true }
        compose.onNodeWithTag("settings-back").assertIsNotEnabled()
        compose.onNodeWithTag("save-settings").assertIsNotEnabled()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        assertFalse(closed)
        compose.onNodeWithTag("deepseek-key").assertExists()
    }
    @Test fun modelPreferenceKeepsOldKeyAndCachedContentAndDoesNotGenerateOnSave() {
        class Credentials(var value:String?):GuideCredentials {
            override fun read()=value
            override fun save(value:String){this.value=value}
            override fun remove(){value=null}
        }
        class Model:GuideModelPreference {
            var value=DeepSeekGuideGenerator.MODEL
            override fun read()=value
            override fun save(value:String){this.value=value}
        }
        val model=Model();val deep=Credentials("sk-existing-test-123456");val search=Credentials("tvly-existing-test-123456")
        var calls=0
        val source=object:GuideMaterialSource {override suspend fun fetch(city:City,stage:(String)->Unit):GuideMaterial {calls++;error("unexpected search")}}
        val vm=DestinationViewModel(compose.activity.application as Application,deep,source=source,searchCredentials=search,modelPreference=model,photoSource=GuidePhotoSource { _,_,_ -> PhotoCandidates(emptyList()) })
        var saved=false
        compose.runOnIdle { vm.saveModelSettings("deepseek-new"," "){saved=true} }
        compose.waitUntil(5000){saved}
        assertEquals("deepseek-new",model.value);assertEquals("sk-existing-test-123456",deep.value);assertEquals(0,calls)
        saved=false
        compose.runOnIdle {vm.saveModelSettings("invalid model","sk-new-test-value-123456"){saved=true}}
        compose.waitUntil(5000){vm.state.value.settingsError!=null}
        assertFalse(saved);assertEquals("sk-existing-test-123456",deep.value);assertEquals("deepseek-new",model.value)
        var removed=false
        compose.runOnIdle {vm.removeKey{removed=true}}
        compose.waitUntil(5000){removed}
        assertNull(deep.value);assertNotNull(search.value);assertEquals("deepseek-new",model.value);assertEquals(0,calls)
        val settings=DeepSeekSettings(compose.activity);settings.save("sk-legacy-storage-test-123456")
        val preference=DeepSeekModelSettings(compose.activity);preference.save("deepseek-storage-test")
        assertEquals("deepseek-storage-test",DeepSeekModelSettings(compose.activity).read())
        assertEquals("sk-legacy-storage-test-123456",DeepSeekSettings(compose.activity).read())
        settings.remove();preference.save(DeepSeekGuideGenerator.MODEL)
    }
}
