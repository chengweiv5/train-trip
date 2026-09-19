package cn.traintrip.core

import kotlinx.coroutines.CancellationException
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
    fun search(plan: List<QueryUnit>, existing: Map<String,QueryResult> = emptyMap(), retainedSuccesses: Map<String,QueryResult.Success> = emptyMap()): Flow<SearchProgress> = flow {
        val results = existing.filterKeys { key -> plan.any { it.key == key } }.toMutableMap()
        val retained = retainedSuccesses.filterKeys { key -> plan.any { it.key == key } && results[key] !is QueryResult.Success }.toMutableMap()
        emit(SearchProgress(plan,results.toMap(),running=true,retainedSuccesses=retained.toMap()))
        for(unit in plan.filterNot { it.key in results }) {
            currentCoroutineContext().ensureActive()
            delay(spacingMillis)
            val result = try {
                source.query(unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                QueryResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "查询失败，请稍后重试")
            }
            currentCoroutineContext().ensureActive()
            results[unit.key] = result
            if(result is QueryResult.Success) retained.remove(unit.key)
            emit(SearchProgress(plan,results.toMap(),running=true,retainedSuccesses=retained.toMap()))
        }
        emit(SearchProgress(plan,results.toMap(),retainedSuccesses=retained.toMap()))
    }
}
