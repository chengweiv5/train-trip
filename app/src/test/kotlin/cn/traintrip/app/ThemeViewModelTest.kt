package cn.traintrip.app

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {
    private val main = StandardTestDispatcher()
    private class Store(var value: ThemeChoice = ThemeChoice.BLUE) : ThemePreference {
        var readsFail = false
        var writesFail = false
        val writes = mutableListOf<ThemeChoice>()
        override fun read(): ThemeChoice {
            if (readsFail) throw IOException("read failed")
            return value
        }
        override fun save(choice: ThemeChoice) {
            writes += choice
            if (writesFail) throw IOException("write failed")
            value = choice
        }
    }
    @Before fun setUp() { Dispatchers.setMain(main) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun stableIdsDefaultAndExistingPreferenceArePreserved() = runTest(main) {
        assertEquals(ThemeChoice.BLUE, ThemeChoice.fromId(null))
        assertEquals(ThemeChoice.BLUE, ThemeChoice.fromId("unrecognized"))
        ThemeChoice.entries.forEach { assertEquals(it, ThemeChoice.fromId(it.id)) }
        val store = Store(ThemeChoice.PURPLE)
        val vm = ThemeViewModel(store, main)
        vm.select(ThemeChoice.GREEN)
        advanceUntilIdle()
        assertEquals(ThemeChoice.PURPLE, vm.state.value.choice)
        assertTrue(store.writes.isEmpty())
        vm.select(ThemeChoice.PURPLE)
        advanceUntilIdle()
        assertTrue(store.writes.isEmpty())
    }

    @Test fun previewChangesImmediatelyButSuccessWaitsForPersistence() = runTest(main) {
        val store = Store()
        val vm = ThemeViewModel(store, main)
        advanceUntilIdle()
        vm.select(ThemeChoice.GREEN)
        assertEquals(ThemeChoice.GREEN, vm.state.value.choice)
        assertNull(vm.state.value.message)
        assertTrue(vm.state.value.saving)
        advanceUntilIdle()
        assertEquals(ThemeChoice.GREEN, store.value)
        assertEquals("已切换为松林绿", vm.state.value.message)
        assertFalse(vm.state.value.saving)
        val restored = ThemeViewModel(store, main)
        advanceUntilIdle()
        assertEquals(ThemeChoice.GREEN, restored.state.value.choice)
    }

    @Test fun failedWriteRestoresLastSavedChoiceAndCanRetry() = runTest(main) {
        val store = Store(ThemeChoice.GREEN)
        val vm = ThemeViewModel(store, main)
        advanceUntilIdle()
        store.writesFail = true
        vm.select(ThemeChoice.ORANGE)
        advanceUntilIdle()
        assertEquals(ThemeChoice.GREEN, vm.state.value.choice)
        assertEquals(ThemeChoice.GREEN, store.value)
        assertEquals("未能保存主题，请重试", vm.state.value.error)
        assertNull(vm.state.value.message)
        store.writesFail = false
        vm.select(ThemeChoice.ORANGE)
        advanceUntilIdle()
        assertEquals(ThemeChoice.ORANGE, store.value)
        assertNull(vm.state.value.error)
    }

    @Test fun readFailurePreservesStorageAndRetryLoadsOriginalChoice() = runTest(main) {
        val store = Store(ThemeChoice.PURPLE).apply { readsFail = true }
        val vm = ThemeViewModel(store, main)
        advanceUntilIdle()
        assertEquals(ThemeChoice.BLUE, vm.state.value.choice)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.ready)
        vm.select(ThemeChoice.GREEN)
        advanceUntilIdle()
        assertTrue(store.writes.isEmpty())
        store.readsFail = false
        vm.reload()
        advanceUntilIdle()
        assertEquals(ThemeChoice.PURPLE, vm.state.value.choice)
        assertTrue(vm.state.value.ready)
    }

    @Test fun rapidQueuedChoicesOnlySaveLatestAndOldFeedbackCannotClearNewResult() = runTest(main) {
        val store = Store()
        val vm = ThemeViewModel(store, main)
        advanceUntilIdle()
        vm.select(ThemeChoice.GREEN)
        val previous = vm.state.value.event
        vm.select(ThemeChoice.ORANGE)
        vm.select(ThemeChoice.PURPLE)
        advanceUntilIdle()
        assertEquals(listOf(ThemeChoice.PURPLE), store.writes)
        assertEquals(ThemeChoice.PURPLE, store.value)
        vm.clearFeedback(previous)
        assertNotNull(vm.state.value.message)
        vm.clearFeedback(vm.state.value.event)
        assertNull(vm.state.value.message)
    }

    @Test fun inFlightSuccessCannotReplaceNewPreviewAndFailureRestoresThatSuccess() = runTest(main) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        var stored = ThemeChoice.BLUE
        val store = object : ThemePreference {
            override fun read() = stored
            override fun save(choice: ThemeChoice) {
                if (choice == ThemeChoice.GREEN) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    stored = choice
                    finished.countDown()
                } else throw IOException("latest write failed")
            }
        }
        try {
            val vm = ThemeViewModel(store, executor)
            repeat(100) { main.scheduler.runCurrent(); if (!vm.state.value.loading) return@repeat; Thread.sleep(5) }
            assertTrue(vm.state.value.ready)
            vm.select(ThemeChoice.GREEN)
            main.scheduler.runCurrent()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            vm.select(ThemeChoice.PURPLE)
            main.scheduler.runCurrent()
            assertEquals(ThemeChoice.PURPLE, vm.state.value.choice)
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            repeat(100) { main.scheduler.runCurrent(); if (!vm.state.value.saving) return@repeat; Thread.sleep(5) }
            assertEquals(ThemeChoice.GREEN, vm.state.value.choice)
            assertEquals(ThemeChoice.GREEN, stored)
            assertNull(vm.state.value.message)
            assertNotNull(vm.state.value.error)
        } finally { release.countDown(); executor.close() }
    }
}
