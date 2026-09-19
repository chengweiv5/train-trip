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
    fun search(plan: List<QueryUnit>, existing: Map<String,QueryResult> = emptyMap()): Flow<SearchProgress> = flow {
        val results = existing.filterKeys { key -> plan.any { it.key == key } }.toMutableMap()
        emit(SearchProgress(plan,results.toMap(),running=true))
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
            emit(SearchProgress(plan,results.toMap(),running=true))
        }
        emit(SearchProgress(plan,results.toMap()))
    }
}
