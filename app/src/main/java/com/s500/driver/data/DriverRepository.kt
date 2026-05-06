package com.s500.driver.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /**
     *  Loads the SPX index plus all top constituents in parallel.  Each
     *  symbol is fetched independently — a single failure does not abort
     *  the rest.
     *
     *  windowSessions = rolling window for the analysis (e.g. 1 / 5 / 20 / 60).
     */
    suspend fun load(windowSessions: Int): DriverData = coroutineScope {
        // SPX first — if this fails the whole report can't run.
        val spxJob = async(Dispatchers.IO) {
            runCatching { barchart.fetchEod("\$SPX", historyDays) }
        }

        val constituentJobs = SP500Constituents.TOP.map { c ->
            async(Dispatchers.IO) {
                val r = runCatching { barchart.fetchEod(c.symbol, historyDays) }
                if (r.isSuccess) {
                    FetchOutcome(c.symbol, StockSeries(c.symbol, r.getOrThrow()), null)
                } else {
                    FetchOutcome(c.symbol, null, r.exceptionOrNull()?.message ?: "fetch failed")
                }
            }
        }

        val spxResult = spxJob.await()
        val spxBars = spxResult.getOrNull()
            ?: throw RuntimeException("ดึง SPX จาก Barchart ไม่ได้: ${spxResult.exceptionOrNull()?.message ?: "?"}")

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
