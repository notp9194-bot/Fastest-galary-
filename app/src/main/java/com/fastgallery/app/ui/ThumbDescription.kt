package com.fastgallery.app.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Thumbnail ki date (ms): dateTaken ho to wahi, warna dateAdded (seconds -> ms). */
internal fun thumbDateMillis(dateTaken: Long, dateAdded: Long): Long =
    if (dateTaken > 0L) dateTaken else dateAdded * 1000L

/**
 * Ek hi [DateFormat] (aur ek hi [Date]) baar-baar use karta hai, jab tak locale na badle.
 * Pehle har grid cell apna naya DateFormat banata tha (locale data lookup, allocation) - scroll me hazaron baar.
 * DateFormat thread-safe nahi hota: ye sirf main thread (Compose semantics) se use karo.
 */
internal class CachedDateFormatter {
    private var locale: Locale? = null
    private var format: DateFormat? = null
    private val date = Date()

    /** Kitni baar naya DateFormat bana (test ke liye). */
    internal var builds = 0
        private set

    fun format(millis: Long, locale: Locale = Locale.getDefault()): String {
        var f = format
        if (f == null || this.locale != locale) {
            f = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            format = f
            this.locale = locale
            builds++
        }
        date.time = millis
        return f.format(date)
    }
}

/** Saare grid cells ka shared date formatter (main thread only). */
internal val ThumbDateFormatter = CachedDateFormatter()
