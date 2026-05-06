package com.s500.driver.data

/**
 *  Top S&P 500 constituents with approximate shares-outstanding (in billions).
 *
 *  Index contribution is computed as
 *      contribution_i  =  ΔPrice_i × shares_i
 *  Then we normalise by Σ contributions to get a relative weight share.
 *
 *  These figures are public, mid-2025, and only need to be in the right
 *  ballpark — the analysis is **relative**, so an exact float-share count
 *  is not required.  Update from time to time if you want better precision.
 *
 *  Sectors: Tech / Comm / Cons-Disc / Cons-Stap / Health / Fin / Energy /
 *           Indus / Util / Mat / RE.
 */
data class Constituent(
    val symbol: String,
    val name: String,
    val sector: String,
    val sharesB: Double            // shares outstanding, billions
)

object SP500Constituents {

    val TOP: List<Constituent> = listOf(
        Constituent("AAPL",  "Apple",                 "Tech",       15.20),
        Constituent("MSFT",  "Microsoft",             "Tech",        7.43),
        Constituent("NVDA",  "NVIDIA",                "Tech",       24.50),
        Constituent("AMZN",  "Amazon",                "Cons-Disc",  10.50),
        Constituent("META",  "Meta Platforms",        "Comm",        2.52),
        Constituent("GOOGL", "Alphabet A",            "Comm",        5.80),
        Constituent("GOOG",  "Alphabet C",            "Comm",        5.50),
        Constituent("BRK.B", "Berkshire Hathaway B",  "Fin",         1.32),
        Constituent("AVGO",  "Broadcom",              "Tech",        4.66),
        Constituent("TSLA",  "Tesla",                 "Cons-Disc",   3.21),
        Constituent("LLY",   "Eli Lilly",             "Health",      0.95),
        Constituent("JPM",   "JPMorgan Chase",        "Fin",         2.81),
        Constituent("WMT",   "Walmart",               "Cons-Stap",   8.05),
        Constituent("V",     "Visa",                  "Fin",         1.94),
        Constituent("MA",    "Mastercard",            "Fin",         0.93),
        Constituent("UNH",   "UnitedHealth",          "Health",      0.92),
        Constituent("XOM",   "ExxonMobil",            "Energy",      4.40),
        Constituent("ORCL",  "Oracle",                "Tech",        2.78),
        Constituent("COST",  "Costco",                "Cons-Stap",   0.44),
        Constituent("HD",    "Home Depot",            "Cons-Disc",   0.99),
        Constituent("PG",    "Procter & Gamble",      "Cons-Stap",   2.36),
        Constituent("JNJ",   "Johnson & Johnson",     "Health",      2.41),
        Constituent("ABBV",  "AbbVie",                "Health",      1.77),
        Constituent("BAC",   "Bank of America",       "Fin",         7.83),
        Constituent("NFLX",  "Netflix",               "Comm",        0.43),
        Constituent("CVX",   "Chevron",               "Energy",      1.83),
        Constituent("KO",    "Coca-Cola",             "Cons-Stap",   4.31),
        Constituent("MRK",   "Merck",                 "Health",      2.53),
        Constituent("CRM",   "Salesforce",            "Tech",        0.96),
        Constituent("PEP",   "PepsiCo",               "Cons-Stap",   1.37),
        Constituent("ADBE",  "Adobe",                 "Tech",        0.43),
        Constituent("AMD",   "AMD",                   "Tech",        1.62),
        Constituent("LIN",   "Linde",                 "Mat",         0.47),
        Constituent("ACN",   "Accenture",             "Tech",        0.62),
        Constituent("MCD",   "McDonald's",            "Cons-Disc",   0.72),
        Constituent("TMO",   "Thermo Fisher",         "Health",      0.38),
        Constituent("WFC",   "Wells Fargo",           "Fin",         3.36),
        Constituent("CSCO",  "Cisco",                 "Tech",        4.00),
        Constituent("DIS",   "Disney",                "Comm",        1.81),
        Constituent("ABT",   "Abbott Laboratories",   "Health",      1.74),
        Constituent("IBM",   "IBM",                   "Tech",        0.92),
        Constituent("PM",    "Philip Morris",         "Cons-Stap",   1.55),
        Constituent("GE",    "GE Aerospace",          "Indus",       1.08),
        Constituent("AXP",   "American Express",      "Fin",         0.71),
        Constituent("INTU",  "Intuit",                "Tech",        0.28),
        Constituent("CAT",   "Caterpillar",           "Indus",       0.49),
        Constituent("VZ",    "Verizon",               "Comm",        4.21),
        Constituent("T",     "AT&T",                  "Comm",        7.18),
        Constituent("NOW",   "ServiceNow",            "Tech",        0.21),
        Constituent("BX",    "Blackstone",            "Fin",         1.21),
    )

    val BY_SYMBOL: Map<String, Constituent> = TOP.associateBy { it.symbol }
}
