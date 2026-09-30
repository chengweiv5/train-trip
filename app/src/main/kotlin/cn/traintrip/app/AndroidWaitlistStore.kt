package cn.traintrip.app

import android.content.Context
import android.util.AtomicFile
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.traintrip.core.OfficialTicketSource
import cn.traintrip.core.waitlist.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** AtomicFile fsyncs before replacement. Kept in noBackupFilesDir; no session secrets are stored. */
internal class WaitlistAtomicFile(file: File) {
    private val atomic = AtomicFile(file)
    private val mutex = Mutex()
    suspend fun read(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { atomic.openRead().bufferedReader().use { it.readText() } }
            catch (e: FileNotFoundException) { if (atomic.baseFile.exists()) throw e else null }
        }
    }
    suspend fun write(value: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stream = atomic.startWrite()
            try {
                stream.write(value.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                atomic.failWrite(stream)
                throw e
            }
            // AtomicFile logs some rename failures instead of throwing. A durable gateway
            // boundary must detect them before allowing the coordinator to issue a request.
            val saved = atomic.openRead().bufferedReader().use { it.readText() }
            if (saved != value) throw IOException("候补进度落盘回读不一致")
        }
    }
}

class AndroidWaitlistStore(context: Context, name: String = "waitlist-operation-v1.json") : WaitlistStore {
    private val file = WaitlistAtomicFile(File(context.noBackupFilesDir, name))
    override suspend fun load() = file.read()?.let(WaitlistSnapshotCodec::decode)
    override suspend fun save(state: WaitlistState) = file.write(WaitlistSnapshotCodec.encode(state))
}

class AndroidWaitlistDraftStore(context: Context, name: String = "waitlist-filters-v1.json") : WaitlistDraftStore {
    private val file = WaitlistAtomicFile(File(context.noBackupFilesDir, name))
    override suspend fun load() = file.read()?.let(WaitlistFiltersCodec::decode)
    override suspend fun save(filters: WaitlistFilters) = file.write(WaitlistFiltersCodec.encode(filters))
}

/** Fail closed until authenticated session continuity and attempt reconciliation are verified. */
private object UnavailableWaitlistGateway : WaitlistGateway {
    override suspend fun submit(attempt: WaitlistAttempt) = WaitlistSubmissionResult.AuthenticationRequired
    override suspend fun queryOrder(attempt: WaitlistAttempt) = WaitlistOrderResult.Unresolved
}

fun waitlistViewModelFactory(context: Context): ViewModelProvider.Factory {
    val app = context.applicationContext
    return viewModelFactory {
        initializer {
            val session = RailwaySession()
            val references = RailwayLocalReferences()
            val debug = app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            // Clean up the discontinued QR experiment; never export credentials or login images.
            if (debug) runCatching { File(app.filesDir, "railway-qr-preview.png").delete() }
            WaitlistViewModel(OfficialTicketSource(), AndroidWaitlistDraftStore(app),
                WaitlistCoordinator(UnavailableWaitlistGateway, AndroidWaitlistStore(app)),
                RailwayAccountService(session, references::reference),
                accountDiagnostic = { status, count ->
                    if (debug) File(app.filesDir, "railway-diagnostic.txt")
                        .writeText("status=$status\npassengers=$count\n")
                },
                passwordLogin = RailwayPasswordLogin(session) { android.os.SystemClock.elapsedRealtime() })
        }
    }
}
