package com.s500.driver.data

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/**
 *  Rolling-window driver analysis for the S&P 500.
 *
 *  For every trading session t and every constituent i we compute
 *      contribution_i(t) = sharesOut_i × (close_i(t) − close_i(t−1))
 *  This is proportional to "how many index points stock i added this day"
 *  — index providers use the same formula, modulo a constant float-share
 *  divisor.  Because we only need *relative* drivers, the divisor cancels.
 *
 *  Then for window W we sum  contribution_i  over the last W sessions and
 *  divide by  Σ_i |contribution_i|  to get a **share** of the index move
 *  (the percentage of the total index move that this stock is responsible
 *  for, with sign).
 *
 *  Streak: how many sessions in a row the stock had the same-sign
 *  contribution as the SPX move that day (i.e. has been pushing in the
 *  market's direction).
 *
 *  Flip: a stock that was a bottom-3 dragger over the previous 5 sessions
 *  but is a top-3 driver today — likely a sentiment turn.
 *
 *  News-likely flag: today's |return| > 2.5 × σ_20  (an abnormal move that
 *  in practice almost always coincides with a fresh story).  We don't
 *  fetch headlines (Barchart's news API is auth-walled), but the flag
 *  reliably points the user to "go read what just happened".
 */

data class StockSeries(
    val symbol: String,
    val bars: List<DayBar>
)

data class DriverEntry(
    val symbol: String,
    val name: String,
    val sector: String,
    val priceLatest: Double,
    val pctChangeWindow: Double,        // stock's own pct change over the window
    val cumContribution: Double,        // index points (relative) added in window
    val sharePct: Double,               // % of total |contribution| sum (signed)
    val streakSessions: Int,            // sessions in a row pushing in SPX direction
    val streakStartDate: LocalDate?,    // first day of the current streak
    val flip: Boolean,                  // bottom-3 dragger 5d ago → top-3 driver today
    val newsLikely: Boolean,            // |today r| > 2.5σ20
    val todayContribution: Double,      // today's contribution alone
    val todayPctChange: Double          // today's pct change
)

data class DriverReport(
    val asOf: LocalDate,
    val spxLast: Double,
    val spxPctWindow: Double,
    val spxPctToday: Double,
    val windowSessions: Int,
    val drivers: List<DriverEntry>,     // sorted by sharePct desc (positive top)
    val draggers: List<DriverEntry>,    // sorted by sharePct asc (negative top)
    val flips: List<DriverEntry>,       // currently flipping
    val streakLeaders: List<DriverEntry>, // longest active streaks
    val newsLikely: List<DriverEntry>,  // abnormal-move flags today
    val headline: String                // human-readable summary in Thai
)

object DriverEngine {

    fun analyze(
        spx: List<DayBar>,
        constituents: List<StockSeries>,
        windowSessions: Int
    ): DriverReport? {
        if (spx.size < windowSessions + 1) return null
        val spxAsc = spx.sortedBy { it.date }
        val asOf = spxAsc.last().date
        val spxLast = spxAsc.last().close
        val spxStart = spxAsc[spxAsc.size - 1 - windowSessions].close
        val spxPctWindow = (spxLast / spxStart - 1.0) * 100.0
        val spxPctToday = if (spxAsc.size >= 2)
            (spxAsc[spxAsc.size - 1].close / spxAsc[spxAsc.size - 2].close - 1.0) * 100.0
        else 0.0
        val spxTodaySign = if (spxPctToday >= 0) 1 else -1

        // Pre-compute per-stock metrics
        val entries = ArrayList<DriverEntry>()
        var totalAbsContribution = 0.0
        val perStockCum = HashMap<String, Double>()
        val perStockToday = HashMap<String, Double>()

        for (s in constituents) {
            val info = SP500Constituents.BY_SYMBOL[s.symbol] ?: continue
            val bars = s.bars.sortedBy { it.date }
            if (bars.size < windowSessions + 1) continue
            val sharesB = info.sharesB
            // Cumulative contribution (index-point analogue) over window
            val n = bars.size
            val end = bars[n - 1].close
            val start = bars[n - 1 - windowSessions].close
            val cumContribution = (end - start) * sharesB
            perStockCum[s.symbol] = cumContribution
            totalAbsContribution += abs(cumContribution)

            val todayContribution = (bars[n - 1].close - bars[n - 2].close) * sharesB
            perStockToday[s.symbol] = todayContribution
        }

        // Now build entries with relative sharePct
        for (s in constituents) {
            val info = SP500Constituents.BY_SYMBOL[s.symbol] ?: continue
            val bars = s.bars.sortedBy { it.date }
            if (bars.size < windowSessions + 1) continue
            val n = bars.size
            val end = bars[n - 1].close
            val start = bars[n - 1 - windowSessions].close
            val pctWindow = (end / start - 1.0) * 100.0
            val pctToday = (bars[n - 1].close / bars[n - 2].close - 1.0) * 100.0

            val cum = perStockCum[s.symbol] ?: 0.0
            val today = perStockToday[s.symbol] ?: 0.0
            val sharePct = if (totalAbsContribution > 0)
                cum / totalAbsContribution * 100.0
            else 0.0

            // Streak: walk back from today; count sessions while sign(stock_i return)
            // == sign(SPX return).  Stop on first miss.
            val (streak, streakStart) = computeStreak(bars, spxAsc)

            // News-likely: today's |return| > 2.5 * sigma(last 20 returns)
            val newsLikely = isAbnormal(bars, lookback = 20, k = 2.5)

            entries.add(
                DriverEntry(
                    symbol = s.symbol,
                    name = info.name,
                    sector = info.sector,
                    priceLatest = end,
                    pctChangeWindow = pctWindow,
                    cumContribution = cum,
                    sharePct = sharePct,
                    streakSessions = streak,
                    streakStartDate = streakStart,
                    flip = false,                 // filled in next pass
                    newsLikely = newsLikely,
                    todayContribution = today,
                    todayPctChange = pctToday
                )
            )
        }

        // Flip detection: rank by 5-day cumulative contribution ENDING 1 session ago,
        // and rank by today's contribution.  A "flip" = was bottom-3 (most negative)
        // 5d-prev → now top-3 today, or vice versa.
        val flipFlags = computeFlips(constituents)

        // Apply flip flags
        val flagged = entries.map { e ->
            e.copy(flip = flipFlags.contains(e.symbol))
        }

        val drivers = flagged.sortedByDescending { it.sharePct }.take(10)
        val draggers = flagged.sortedBy { it.sharePct }.take(10)
        val flips = flagged.filter { it.flip }
            .sortedByDescending { abs(it.todayContribution) }
        // Streak leaders that align with today's SPX direction
        val streakLeaders = flagged.filter { it.streakSessions >= 3 }
            .sortedByDescending { it.streakSessions }
            .take(8)
        val news = flagged.filter { it.newsLikely }
            .sortedByDescending { abs(it.todayPctChange) }

        val headline = buildHeadline(
            asOf = asOf,
            spxPctToday = spxPctToday,
            spxPctWindow = spxPctWindow,
            windowSessions = windowSessions,
            top = drivers.firstOrNull(),
            bottom = draggers.firstOrNull(),
            firstFlip = flips.firstOrNull(),
            longestStreak = streakLeaders.firstOrNull()
        )

        return DriverReport(
            asOf = asOf,
            spxLast = spxLast,
            spxPctWindow = spxPctWindow,
            spxPctToday = spxPctToday,
            windowSessions = windowSessions,
            drivers = drivers,
            draggers = draggers,
            flips = flips,
            streakLeaders = streakLeaders,
            newsLikely = news,
            headline = headline
        )
    }

    private fun computeStreak(
        stockBars: List<DayBar>,
        spxBars: List<DayBar>
    ): Pair<Int, LocalDate?> {
        val spxByDate = spxBars.associateBy { it.date }
        var streak = 0
        var streakStart: LocalDate? = null
        for (i in stockBars.size - 1 downTo 1) {
            val today = stockBars[i]; val prev = stockBars[i - 1]
            val stockRet = today.close - prev.close
            val spxToday = spxByDate[today.date] ?: break
            val spxPrev = spxByDate[prev.date] ?: break
            val spxRet = spxToday.close - spxPrev.close
            if (sign(stockRet) == 0 || sign(spxRet) == 0) break
            if (sign(stockRet) != sign(spxRet)) break
            streak += 1
            streakStart = today.date
        }
        return streak to streakStart
    }

    private fun isAbnormal(bars: List<DayBar>, lookback: Int, k: Double): Boolean {
        if (bars.size < lookback + 2) return false
        val n = bars.size
        val rets = ArrayList<Double>(lookback)
        for (i in n - 1 - lookback until n - 1) {
            if (bars[i].close > 0)
                rets.add(bars[i + 1].close / bars[i].close - 1.0)
        }
        if (rets.size < 5) return false
        val mean = rets.average()
        val variance = rets.sumOf { (it - mean) * (it - mean) } / rets.size
        val sigma = sqrt(variance)
        if (sigma <= 0) return false
        val today = bars[n - 1].close / bars[n - 2].close - 1.0
        return abs(today - mean) > k * sigma
    }

    private fun computeFlips(constituents: List<StockSeries>): Set<String> {
        // 5-day ago to 1-day ago (inclusive of t-5..t-1) cumulative contribution
        // vs today's contribution.
        val prev5: List<Pair<String, Double>> = constituents.mapNotNull { s ->
            val info = SP500Constituents.BY_SYMBOL[s.symbol] ?: return@mapNotNull null
            val b = s.bars.sortedBy { it.date }
            if (b.size < 7) return@mapNotNull null
            val n = b.size
            val cum = (b[n - 2].close - b[n - 7].close) * info.sharesB
            s.symbol to cum
        }
        val today: List<Pair<String, Double>> = constituents.mapNotNull { s ->
            val info = SP500Constituents.BY_SYMBOL[s.symbol] ?: return@mapNotNull null
            val b = s.bars.sortedBy { it.date }
            if (b.size < 2) return@mapNotNull null
            val n = b.size
            val c = (b[n - 1].close - b[n - 2].close) * info.sharesB
            s.symbol to c
        }
        val bottom5 = prev5.sortedBy { it.second }.take(3).map { it.first }.toSet()
        val top5 = prev5.sortedByDescending { it.second }.take(3).map { it.first }.toSet()
        val topToday = today.sortedByDescending { it.second }.take(3).map { it.first }.toSet()
        val bottomToday = today.sortedBy { it.second }.take(3).map { it.first }.toSet()
        // Flip up: was bottom 5d, now top today.
        // Flip down: was top 5d, now bottom today.
        return (bottom5 intersect topToday) + (top5 intersect bottomToday)
    }

    private fun sign(x: Double): Int = when {
        x > 0 -> 1
        x < 0 -> -1
        else -> 0
    }

    private fun buildHeadline(
        asOf: LocalDate,
        spxPctToday: Double,
        spxPctWindow: Double,
        windowSessions: Int,
        top: DriverEntry?,
        bottom: DriverEntry?,
        firstFlip: DriverEntry?,
        longestStreak: DriverEntry?
    ): String {
        val sb = StringBuilder()
        val dir = if (spxPctToday >= 0) "ขึ้น" else "ลง"
        sb.append("S&P 500 วันที่ %s %s %+.2f%% (%dD: %+.2f%%)".format(
            asOf, dir, spxPctToday, windowSessions, spxPctWindow))
        if (top != null && top.sharePct > 0) {
            sb.append("  •  ตัวผลักดันหลัก: ${top.symbol} ${"%+.1f".format(top.sharePct)}%")
        }
        if (bottom != null && bottom.sharePct < 0) {
            sb.append("  •  ตัวฉุดหลัก: ${bottom.symbol} ${"%+.1f".format(bottom.sharePct)}%")
        }
        if (firstFlip != null) {
            val arrow = if (firstFlip.todayContribution >= 0) "พลิกมาผลักดัน" else "พลิกมาฉุด"
            sb.append("  •  ${firstFlip.symbol} $arrow (น่าจะมีข่าว)")
        }
        if (longestStreak != null && longestStreak.streakSessions >= 4) {
            sb.append("  •  ${longestStreak.symbol} ผลักดันต่อเนื่อง ${longestStreak.streakSessions} วันแล้ว")
        }
        return sb.toString()
    }
}
