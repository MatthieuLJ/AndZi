package com.matthieu.zi_app.ui.home

enum class TimeRange(val label: String, val durationMillis: Long) {
    DAYS_7("7D", 7L * 24 * 60 * 60 * 1000L),
    DAYS_30("30D", 30L * 24 * 60 * 60 * 1000L),
    DAYS_90("90D", 90L * 24 * 60 * 60 * 1000L),
    ALL("All", Long.MAX_VALUE)
}
