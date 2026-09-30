package cn.traintrip.core.waitlist

import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform implementations must validate the attempt binding and never retry a submit internally. */
interface WaitlistGateway {
    suspend fun submit(attempt: WaitlistAttempt): WaitlistSubmissionResult
    suspend fun queryOrder(attempt: WaitlistAttempt): WaitlistOrderResult
}

/** save must be atomic and durable on return. Never store platform credentials in this snapshot. */
interface WaitlistStore {
    suspend fun load(): WaitlistState?
    suspend fun save(state: WaitlistState)
}

class WaitlistPersistenceException : IllegalStateException("候补进度未能保存，已停止发送后续请求")

/**
 * One coordinator owns one store. State changes are serialized independently of network I/O, so
 * stop remains available while submit is in flight. Persisting a pending attempt precedes I/O;
 * recovery only queries it, including when the process died before the actual network call.
 */
class WaitlistCoordinator(
    private val gateway: WaitlistGateway,
    private val store: WaitlistStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val stateMutex = Mutex()
    private val effectMutex = Mutex()
    private val mutable = MutableStateFlow<WaitlistState?>(null)
    val state: StateFlow<WaitlistState?> = mutable.asStateFlow()
    private var persistenceFailed = false

    /** Display saved progress before authentication; never trusts a persisted binding as a login. */
    suspend fun restore() {
        stateMutex.withLock {
            check(!persistenceFailed)
            if (mutable.value != null) return@withLock
            val saved = load() ?: return@withLock
            persist(WaitlistFlow.accept(saved, WaitlistEvent.Pause(WaitlistPause.BACKGROUND), clock.instant()))
        }
    }

    suspend fun start(request: WaitlistRequest, limits: WaitlistLimits) {
        val effect = stateMutex.withLock {
            check(!persistenceFailed) { "请重新加载已保存进度后核对订单" }
            val previous = mutable.value ?: load()
            check(previous == null || previous.phase in setOf(
                WaitlistPhase.ORDER_CREATED, WaitlistPhase.EXHAUSTED,
            )) { "已有候补处理记录，请先核对或继续原任务" }
            if (previous != null) require(previous.request.id != request.id) { "候补任务标识不可重复使用" }
            persist(WaitlistFlow.start(request, limits, clock.instant()))
        }
        execute(effect)
    }

    suspend fun stop(reason: WaitlistPause = WaitlistPause.STOPPED) {
        require(reason == WaitlistPause.STOPPED || reason == WaitlistPause.BACKGROUND)
        dispatch(WaitlistEvent.Pause(reason))
    }

    suspend fun continueRemaining(binding: WaitlistBinding) {
        dispatch(WaitlistEvent.Continue(binding))
    }

    suspend fun recover(binding: WaitlistBinding) {
        val effect = stateMutex.withLock {
            check(!persistenceFailed) { "请重新创建协调器并加载已保存进度" }
            check(mutable.value == null) { "已加载任务，不能覆盖进行中的状态" }
            val saved = load() ?: return@withLock null
            val paused = WaitlistFlow.accept(
                saved, WaitlistEvent.Pause(WaitlistPause.BACKGROUND), clock.instant(),
            )
            if (saved.pending != null) persist(WaitlistFlow.accept(
                paused.state, WaitlistEvent.Continue(binding), clock.instant(),
            )) else persist(paused)
        }
        execute(effect)
    }

    suspend fun checkOrder(binding: WaitlistBinding) {
        val effect = stateMutex.withLock {
            check(!persistenceFailed) { "请重新加载已保存进度后核对订单" }
            val current = mutable.value ?: return@withLock null
            if (current.pending == null) return@withLock null
            persist(WaitlistFlow.accept(current, WaitlistEvent.Continue(binding), clock.instant()))
        }
        execute(effect)
    }

    private suspend fun dispatch(event: WaitlistEvent) {
        val effect = stateMutex.withLock {
            check(!persistenceFailed) { "请重新加载已保存进度后核对订单" }
            val current = mutable.value ?: return@withLock null
            persist(WaitlistFlow.accept(current, event, clock.instant()))
        }
        execute(effect)
    }

    private suspend fun load(): WaitlistState? = try {
        store.load()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        persistenceFailed = true
        throw WaitlistPersistenceException()
    }

    private suspend fun persist(transition: WaitlistTransition): WaitlistEffect? {
        try {
            store.save(transition.state)
        } catch (e: CancellationException) {
            persistenceFailed = true
            throw e
        } catch (_: Exception) {
            persistenceFailed = true
            throw WaitlistPersistenceException()
        }
        mutable.value = transition.state
        return transition.effect
    }

    private suspend fun execute(initial: WaitlistEffect?) {
        if (initial == null) return
        effectMutex.withLock {
            var next: WaitlistEffect? = initial
            while (next != null) {
                val effect = next
                val attempt = when (effect) {
                    is WaitlistEffect.Submit -> effect.attempt
                    is WaitlistEffect.QueryOrder -> effect.attempt
                }
                val claimed = stateMutex.withLock {
                    val current = mutable.value
                    !persistenceFailed && current?.pending == attempt &&
                        (effect !is WaitlistEffect.Submit ||
                            (current.autoContinue && current.phase == WaitlistPhase.SUBMITTING))
                }
                if (!claimed) return@withLock
                // Once claimed, stop treats this request as in flight; it must not imply cancellation.
                val event = when (effect) {
                    is WaitlistEffect.Submit -> WaitlistEvent.Submission(attempt.id, submit(attempt))
                    is WaitlistEffect.QueryOrder -> WaitlistEvent.OrderChecked(attempt.id, query(attempt))
                }
                next = stateMutex.withLock {
                    check(!persistenceFailed) { "请重新加载已保存进度后核对订单" }
                    val current = mutable.value ?: return@withLock null
                    persist(WaitlistFlow.accept(current, event, clock.instant()))
                }
            }
        }
    }

    private suspend fun submit(attempt: WaitlistAttempt): WaitlistSubmissionResult = try {
        gateway.submit(attempt)
    } catch (e: CancellationException) {
        throw e // The durable pending attempt remains unresolved; recovery must query, never replay.
    } catch (_: Exception) {
        WaitlistSubmissionResult.Unknown
    }

    private suspend fun query(attempt: WaitlistAttempt): WaitlistOrderResult = try {
        gateway.queryOrder(attempt)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        WaitlistOrderResult.Unresolved
    }
}
