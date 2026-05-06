# S&P 500 Driver Analyzer (Android)

แอป Android ที่ดึงราคา S&P 500 + 50 หุ้น market-cap-สูงสุด จาก **Barchart.com**
แล้ววิเคราะห์ rolling window ว่า:

- หุ้นตัวไหน **กำลังผลักดัน** ดัชนี (driver)
- หุ้นตัวไหน **ฉุด** ดัชนี (dragger)
- ผลักดันมา **ต่อเนื่องกี่วันแล้ว** (streak)
- ตัวไหน **พลิกบทบาท** (flip — เคยฉุด → มาผลักดัน, หรือกลับกัน)
- ตัวไหน **น่าจะมีข่าววันนี้** (กรอง |return| > 2.5σ20)

## วิธีดึงข้อมูล (Barchart)
1. **prime**: GET `/stocks/quotes/$SPX/price-history/historical` → ตอบกลับมาพร้อม `XSRF-TOKEN` cookie
2. **fetch**: GET `/proxies/timeseries/queryeod.ashx?symbol=...&data=daily&...` พร้อม header `X-XSRF-TOKEN: <decoded token>` + `Referer`
3. CSV: `SYMBOL, YYYY-MM-DD, open, high, low, close, volume`

โค้ดอยู่ที่ `app/src/main/java/com/s500/driver/data/BarchartClient.kt` (อ้างอิงรูปแบบเดียวกับโปรเจค `Mstr`)

## การคำนวณ
- **contribution_i(t) = sharesOut_i × (close_i(t) − close_i(t-1))**
- **rolling**: รวม contribution ตามจำนวน sessions ที่เลือก (1 / 5 / 20 / 60 / 120)
- **share %** = contribution_i / Σ |contribution_i|  (sign คงไว้)

## Build
- GitHub Actions จะ build APK อัตโนมัติเมื่อ push  → release tag `v1.0.<run>`
- Local: `./gradlew :app:assembleDebug`

## Stack
Kotlin · Jetpack Compose Material3 · OkHttp · Coroutines · Custom Canvas chart (pinch-zoom)
