package com.feerlessleadr.sightline.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** One line of Blue Iris's system log. [level] is Blue Iris's number (0 info, 1 warning, 2 error, 3 alert, 4 OK, 5 question, 6 signal, 10 user). */
data class LogEntry(val level: Int, val source: String, val message: String, val dateSec: Long, val count: Int)

/** Which lines of the log to show. */
enum class LogFilter(val label: String) {
    All("All"),
    Problems("Warnings & errors"),
    Info("Info"),
}

/** What asking for the log came to. */
sealed interface LogResult {
    class Ok(val entries: List<LogEntry>) : LogResult

    /** Blue Iris only shows its log to admin users. */
    class Denied(val reason: String) : LogResult

    class Failed(val message: String) : LogResult
}

object SystemLog {
    /** Warnings, errors, alerts and questions count as problems. */
    private val problemLevels = setOf(1, 2, 3, 5)

    fun isProblem(level: Int) = level in problemLevels

    fun levelName(level: Int) = when (level) {
        0 -> "Info"
        1 -> "Warning"
        2 -> "Error"
        3 -> "Alert"
        4 -> "OK"
        5 -> "Question"
        6 -> "Signal"
        10 -> "User"
        else -> "Level $level"
    }

    /** The entries of a `log` answer, newest first. */
    fun parse(data: JsonElement?): List<LogEntry> =
        (data as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = o[key]?.jsonPrimitive?.contentOrNull.orEmpty()
            fun number(key: String) = text(key).toDoubleOrNull()?.toLong() ?: 0L
            LogEntry(number("level").toInt(), text("obj"), text("msg"), number("date"), maxOf(1, number("count").toInt()))
        }.sortedByDescending { it.dateSec }

    fun matches(entry: LogEntry, filter: LogFilter, search: String): Boolean {
        val levelOk = when (filter) {
            LogFilter.All -> true
            LogFilter.Problems -> isProblem(entry.level)
            LogFilter.Info -> !isProblem(entry.level)
        }
        val s = search.trim().lowercase()
        return levelOk && (s.isEmpty() || entry.source.lowercase().contains(s) || entry.message.lowercase().contains(s))
    }

    fun count(entries: List<LogEntry>, filter: LogFilter) = entries.count { matches(it, filter, "") }
}
