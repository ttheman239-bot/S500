package com.s500.driver.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class FetchOutcome(
    val symbol: String,
    val series: StockSeries?,
    val error: String?
)

data class DriverData(
    val report: DriverReport?,
    val spxBars: List<DayBar>,
    val asOf: LocalDate,
    val perSymbolErrors: List<Pair<String, String>>,   // symbol → error msg
    val partial: Boolean
)

class DriverRepository {

    private val cookieJar = SimpleCookieJar()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val barchart = BarchartClient(client, cookieJar)

    /** Sessions to fetch (covers the longest rolling window we expose, +buffer). */
    private val historyDays = 200

    private companion object {
        /**
         *  Tuned empirically.  Barchart accepts ~6 concurrent timeseries
         *  calls comfortably; ~12 starts triggering 403 cool-downs.
         */
        const val MAX_CONCURRENT = 6
    }

    /**
     *  Loads the SPY (S&P 500 proxy) plus all top constituents.  We
     *  throttle concurrency to MAX_CONCURRENT to avoid tripping the
     *  Cloudflare rate-limit Barchart sits behind — past that limit
     *  the proxy starts returning 403/HTML for the timeseries endpoint
     *  and every subsequent request fails until the cooldown.
     *
     *  Each symbol is fetched independently — a single failure does not
     *  abort the rest.
     *
     *  windowSessions = rolling window for the analysis (e.g. 1 / 5 / 20 / 60).
     */
    suspend fun load(windowSessions: Int): DriverData = coroutineScope {
        val gate = Semaphore(MAX_CONCURRENT)

        // SPY first — if this fails the whole report can't run.
        // See BarchartClient.fetchEod doc for why SPY rather than $SPX.
        val spxJob = async(Dispatchers.IO) {
            gate.withPermit { runCatching { barchart.fetchEod("SPY", historyDays) } }
        }

        val constituentJobs = SP500Constituents.TOP.map { c ->
            async(Dispatchers.IO) {
                val r = gate.withPermit {
                    runCatching { barchart.fetchEod(c.symbol, historyDays) }
                }
                if (r.isSuccess) {
                    FetchOutcome(c.symbol, StockSeries(c.symbol, r.getOrThrow()), null)
                } else {
                    FetchOutcome(c.symbol, null, r.exceptionOrNull()?.message ?: "fetch failed")
                }
            }
        }

        val spxResult = spxJob.await()
        val spxBars = spxResult.getOrNull()
            ?: throw RuntimeException("ดึง SPY (S&P 500 proxy) จาก Barchart ไม่ได้: ${spxResult.exceptionOrNull()?.message ?: "?"}")

        val outcomes = constituentJobs.map { it.await() }
        val good = outcomes.mapNotNull { it.series }
        val errs = outcomes.filter { it.error != null }.map { it.symbol to (it.error ?: "?") }

        val report = DriverEngine.analyze(spxBars, good, windowSessions)

        DriverData(
            report = report,
            spxBars = spxBars,
            asOf = spxBars.last().date,
            perSymbolErrors = errs,
            partial = errs.isNotEmpty()
        )
    }
}
