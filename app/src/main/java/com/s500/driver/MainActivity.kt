package com.s500.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.s500.driver.data.DriverEntry
import com.s500.driver.data.DriverReport
import com.s500.driver.ui.ChartSeries
import com.s500.driver.ui.MainViewModel
import com.s500.driver.ui.PricePoint
import com.s500.driver.ui.UiState
import com.s500.driver.ui.ZoomLineChart
import java.time.ZoneId
import kotlin.math.abs

private val Bg = Color(0xFF0B0F19)
private val Card = Color(0xFF111827)
private val Accent = Color(0xFF60A5FA)
private val UpGreen = Color(0xFF22C55E)
private val DownRed = Color(0xFFEF4444)
private val Yellow = Color(0xFFFFC857)
private val Muted = Color(0xFF94A3B8)
private val Grid = Color(0xFF1F2937)
private val TooltipBg = Color(0xCC1F2937)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
                Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val window by vm.window.collectAsState()

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "S&P 500 Driver Analyzer",
                            color = Color.White, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "ใครกำลังดัน / ฉุด ดัชนี — rolling window",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            WindowSelector(window) { vm.setWindow(it) }
            Spacer(Modifier.height(12.dp))
            when (val s = state) {
                is UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(s.message) { vm.refresh() }
                is UiState.Ready -> ReadyView(s)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindowSelector(current: Int, onPick: (Int) -> Unit) {
    val options = listOf(
        1 to "1d", 5 to "1W", 10 to "2W", 20 to "1M",
        60 to "3M", 120 to "6M"
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (sessions, label) ->
            val selected = sessions == current
            Button(
                onClick = { onPick(sessions) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) Accent else Card,
                    contentColor = if (selected) Color.Black else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(label, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxWidth().height(360.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(12.dp))
            Text("กำลังโหลดราคา SPX + 50 หุ้นจาก Barchart...", color = Muted)
            Spacer(Modifier.height(4.dp))
            Text("(ใช้เวลา ~10–25 วิ ขึ้นกับเครือข่าย)", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("โหลดข้อมูลไม่สำเร็จ", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("ลองใหม่") }
        }
    }
}

@Composable
private fun ReadyView(state: UiState.Ready) {
    val data = state.data
    val report = data.report
    Column(modifier = Modifier.fillMaxWidth()) {
        if (report == null) {
            Text("ข้อมูลไม่พอจะวิเคราะห์ rolling window นี้", color = Color.White)
            return@Column
        }
        HeadlineCard(report)
        Spacer(Modifier.height(12.dp))
        SpxChartCard(spxBars = data.spxBars, windowSessions = report.windowSessions)
        Spacer(Modifier.height(12.dp))
        DriverListCard(
            title = "ตัวผลักดันหลัก (Drivers)",
            subtitle = "% ของขนาดการเคลื่อนไหวของดัชนีในหน้าต่างนี้",
            entries = report.drivers,
            positiveColor = UpGreen
        )
        Spacer(Modifier.height(12.dp))
        DriverListCard(
            title = "ตัวฉุด (Draggers)",
            subtitle = "หุ้นที่กดดันดัชนีมากที่สุด",
            entries = report.draggers,
            positiveColor = DownRed
        )
        Spacer(Modifier.height(12.dp))
        if (report.flips.isNotEmpty()) {
            FlipCard(report.flips)
            Spacer(Modifier.height(12.dp))
        }
        if (report.streakLeaders.isNotEmpty()) {
            StreakCard(report.streakLeaders)
            Spacer(Modifier.height(12.dp))
        }
        if (report.newsLikely.isNotEmpty()) {
            NewsCard(report.newsLikely)
            Spacer(Modifier.height(12.dp))
        }
        if (data.partial) {
            ErrorsCard(data.perSymbolErrors)
        }
        Text(
            "ข้อมูลทั้งหมดดึงจาก Barchart (EOD daily) • " +
                "การถ่วงน้ำหนัก = shares-outstanding × ΔPrice",
            color = Muted, fontSize = 11.sp
        )
    }
}

@Composable
private fun HeadlineCard(r: DriverReport) {
    val (bg, fg) = when {
        r.spxPctToday > 0.5 -> UpGreen to Color.Black
        r.spxPctToday > 0 -> UpGreen.copy(alpha = 0.85f) to Color.Black
        r.spxPctToday < -0.5 -> DownRed to Color.White
        r.spxPctToday < 0 -> DownRed.copy(alpha = 0.85f) to Color.White
        else -> Card to Color.White
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("S&P 500", color = fg.copy(alpha = 0.8f),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("%,.2f".format(r.spxLast),
                    color = fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("วันนี้ %+.2f%%".format(r.spxPctToday),
                    color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("%dD: %+.2f%%".format(r.windowSessions, r.spxPctWindow),
                    color = fg.copy(alpha = 0.85f), fontSize = 12.sp)
                Text(r.asOf.toString(),
                    color = fg.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(r.headline, color = fg.copy(alpha = 0.95f), fontSize = 12.sp)
    }
}

@Composable
private fun SpxChartCard(
    spxBars: List<com.s500.driver.data.DayBar>,
    windowSessions: Int
) {
    val nyZone = ZoneId.of("America/New_York")
    val cutoffDate = if (spxBars.isNotEmpty())
        spxBars.last().date.minusDays(((windowSessions * 1.6).toLong() + 5))
    else null
    val recent = if (cutoffDate != null) spxBars.filter { !it.date.isBefore(cutoffDate) } else spxBars
    val series = ChartSeries(
        label = "SPX",
        color = Accent,
        points = recent.map {
            PricePoint(
                it.date.atTime(16, 0).atZone(nyZone).toEpochSecond(),
                it.close
            )
        },
        fill = true
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .padding(12.dp)
    ) {
        Text("SPX index — last ${recent.size} sessions",
            color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text("Pinch zoom • drag pan • tap = crosshair • double-tap = reset",
            color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            ZoomLineChart(
                series = listOf(series),
                gridColor = Grid, axisColor = Muted,
                tooltipBg = TooltipBg, tooltipText = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DriverListCard(
    title: String,
    subtitle: String,
    entries: List<DriverEntry>,
    positiveColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(subtitle, color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        val maxAbs = (entries.maxOfOrNull { abs(it.sharePct) } ?: 1.0).coerceAtLeast(1.0)
        entries.forEachIndexed { idx, e ->
            if (idx > 0) Spacer(Modifier.height(6.dp))
            DriverRow(e, maxAbsShare = maxAbs, positiveColor = positiveColor)
        }
    }
}

@Composable
private fun DriverRow(
    e: DriverEntry,
    maxAbsShare: Double,
    positiveColor: Color
) {
    val color = if (e.sharePct >= 0) positiveColor else DownRed
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.symbol, color = Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(6.dp))
                    Text(e.sector, color = Muted, fontSize = 10.sp)
                    if (e.flip) {
                        Spacer(Modifier.size(6.dp))
                        Badge("FLIP", Yellow, Color.Black)
                    }
                    if (e.newsLikely) {
                        Spacer(Modifier.size(4.dp))
                        Badge("NEWS?", Color(0xFFFF8AB0), Color.Black)
                    }
                    if (e.streakSessions >= 3) {
                        Spacer(Modifier.size(4.dp))
                        Badge("⏵${e.streakSessions}d", Accent.copy(alpha = 0.4f), Color.White)
                    }
                }
                Text(e.name, color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%+.1f%%".format(e.sharePct),
                    color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("vs SPX move", color = Muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShareBar(value = e.sharePct, maxAbs = maxAbsShare, color = color,
                modifier = Modifier.weight(1f))
        }
        Row {
            Text("ราคา ${"%,.2f".format(e.priceLatest)}", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("วันนี้ ${"%+.2f%%".format(e.todayPctChange)} • " +
                "หน้าต่าง ${"%+.2f%%".format(e.pctChangeWindow)}",
                color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ShareBar(value: Double, maxAbs: Double, color: Color, modifier: Modifier = Modifier) {
    val frac = (abs(value) / maxAbs).coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(frac)
                .height(6.dp)
                .background(color.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) { Text(text, color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun FlipCard(flips: List<DriverEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        Text("⚡ พลิกบทบาท (Flips)", color = Yellow,
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("เคยฉุดดัชนีในรอบสัปดาห์ที่แล้ว → กลับมาผลักดันวันนี้ (หรือกลับกัน) — มักมีข่าว",
            color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        flips.forEach { e ->
            val arrow = if (e.todayContribution >= 0) "↑ ผลักดัน" else "↓ ฉุด"
            val col = if (e.todayContribution >= 0) UpGreen else DownRed
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(e.symbol, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                Text(e.name, color = Muted, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                Text(arrow, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(8.dp))
                Text("%+.2f%%".format(e.todayPctChange), color = col, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StreakCard(leaders: List<DriverEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        Text("🔥 ผลักดันต่อเนื่อง (Streak Leaders)", color = Accent,
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("จำนวนวันติดต่อกันที่หุ้นเคลื่อนตามทิศทางของ SPX",
            color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        leaders.forEach { e ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(e.symbol, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                Text(e.name, color = Muted, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                val startStr = e.streakStartDate?.toString() ?: ""
                Text("${e.streakSessions} วัน",
                    color = Yellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(6.dp))
                Text("ตั้งแต่ $startStr", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun NewsCard(news: List<DriverEntry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        Text("📰 น่าจะมีข่าววันนี้ (>2.5σ moves)", color = Color(0xFFFF8AB0),
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("หุ้นที่ราคาขยับเกิน 2.5 เท่าของ σ20 — เกือบทุกครั้งหมายถึงมีข่าวสด",
            color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        news.forEach { e ->
            val col = if (e.todayPctChange >= 0) UpGreen else DownRed
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(e.symbol, color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                Text(e.name, color = Muted, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                Text("%+.2f%%".format(e.todayPctChange), color = col,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorsCard(errors: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .padding(14.dp)
    ) {
        Text("⚠ ${errors.size} หุ้นโหลดไม่สำเร็จ", color = Yellow,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text("วิเคราะห์ดำเนินต่อด้วยตัวที่โหลดสำเร็จ",
            color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        errors.take(8).forEach { (sym, msg) ->
            Text("• $sym: ${msg.take(70)}",
                color = Muted, fontSize = 11.sp)
        }
        if (errors.size > 8) {
            Text("...และอีก ${errors.size - 8} ตัว", color = Muted, fontSize = 11.sp)
        }
    }
}
