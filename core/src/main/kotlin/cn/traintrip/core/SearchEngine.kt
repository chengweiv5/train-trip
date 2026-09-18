package cn.traintrip.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface TicketSource {
    suspend fun initialize(): SourceInfo
    suspend fun query(unit: QueryUnit): QueryResult
}
class SearchEngine(private val source: TicketSource, private val spacingMillis: Long = 1100) {
    fun search(plan: List<QueryUnit>, existing: Map<String,QueryResult> = emptyMap()): Flow<SearchProgress> = flow {
        val results = existing.filterKeys { key -> plan.any { it.key == key } }.toMutableMap()
        var consecutiveFailures = 0
        emit(SearchProgress(plan,results.toMap(),running=true))
        for(unit in plan.filterNot { it.key in results }) {
            currentCoroutineContext().ensureActive()
            delay(spacingMillis)
            val result = source.query(unit)
            currentCoroutineContext().ensureActive()
            results[unit.key] = result
            consecutiveFailures = if(result is QueryResult.Failure) consecutiveFailures+1 else 0
            val stop = (result is QueryResult.Failure && result.stopSearch) || consecutiveFailures >= 3
            emit(SearchProgress(plan,results.toMap(),running=!stop,stopped=stop))
            if(stop) return@flow
        }
        emit(SearchProgress(plan,results.toMap()))
    }
}
