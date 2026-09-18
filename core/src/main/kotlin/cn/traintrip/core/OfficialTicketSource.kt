package cn.traintrip.core

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OfficialTicketSource : TicketSource {
    private val jar = object: CookieJar {
        private val cookies = mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, incoming: List<Cookie>) {
            for(cookie in incoming) { cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }; cookies.add(cookie) }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> { cookies.removeAll { it.expiresAt < System.currentTimeMillis() }; return cookies.filter { it.matches(url) } }
    }
    private val client = OkHttpClient.Builder().cookieJar(jar).connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .callTimeout(30,TimeUnit.SECONDS).followRedirects(false).retryOnConnectionFailure(false).build()
    @Volatile private var info: SourceInfo? = null
    override suspend fun initialize(): SourceInfo {
        val html = get(INIT).second
        val route = Regex("var\\s+CLeftTicketUrl\\s*=\\s*['\"](leftTicket/query[A-Za-z]*)['\"]").find(html)?.groupValues?.get(1) ?: throw IOException("官方查询入口已变化")
        val dates = Regex("var\\s+other_buy_date\\s*=\\s*['\"](\\d{4}-\\d{2}-\\d{2})&(\\d{4}-\\d{2}-\\d{2})['\"]").find(html) ?: throw IOException("未取得当前开售日期范围")
        val stationPath = Regex("src=['\"](/otn/resources/js/framework/station_name\\.js[^'\"]*)['\"]").find(html)?.groupValues?.get(1) ?: throw IOException("未取得车站字典地址")
        val catalog = StationCatalog.parse(get("https://kyfw.12306.cn$stationPath").second)
        return SourceInfo(route,LocalDate.parse(dates.groupValues[1]),LocalDate.parse(dates.groupValues[2]),catalog,Instant.now()).also { info = it }
    }
    override suspend fun query(unit: QueryUnit): QueryResult {
        val current = info ?: return QueryResult.Failure("查询会话尚未初始化",true)
        if(unit.date > current.saleEnd) return QueryResult.NotOnSale("${unit.date} 尚未开售；当前可查至 ${current.saleEnd}")
        if(unit.date < current.saleStart) return QueryResult.Failure("${unit.date} 已不在当前可查询范围")
        val url = ("https://kyfw.12306.cn/otn/"+current.queryPath).toHttpUrl().newBuilder()
            .addQueryParameter("leftTicketDTO.train_date",unit.date.toString()).addQueryParameter("leftTicketDTO.from_station",unit.origin.code)
            .addQueryParameter("leftTicketDTO.to_station",unit.destination.code).addQueryParameter("purpose_codes","ADULT").build()
        return try {
            val (at,body) = get(url.toString())
            TicketParser.parse(body,unit,current.catalog,at)
        } catch(e: IOException) { QueryResult.Failure(e.message ?: "网络查询失败",true) }
    }
    private suspend fun get(url: String): Pair<Instant,String> = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(url).header("User-Agent","Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
            .header("Referer",INIT).header("Cache-Control","no-cache").get().build()
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) { if(cont.isActive) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if(!it.isSuccessful) throw IOException(when(it.code) { 302,301,403,429 -> "12306 暂未接受查询（HTTP ${it.code}），请稍后手动重试"; else -> "查询服务错误（HTTP ${it.code}）" })
                        val body = it.body?.string() ?: throw IOException("查询响应为空")
                        if(body.length > 10_000_000) throw IOException("查询响应超出预期")
                        if(cont.isActive) cont.resume(Instant.now() to body)
                    } catch(e: IOException) { if(cont.isActive) cont.resumeWithException(e) }
                }
            }
        })
    }
    companion object { const val INIT = "https://kyfw.12306.cn/otn/leftTicket/init" }
}
