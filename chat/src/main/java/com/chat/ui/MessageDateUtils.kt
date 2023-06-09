package com.chat.ui

import android.text.format.DateUtils
import org.joda.time.format.DateTimeFormat

internal object MessageDateUtils {
    private val DATE_FORMAT = DateTimeFormat.forPattern("dd.MM.yyy")
    private val TODAY_DATE_FORMAT = DateTimeFormat.forPattern("hh:mm a")

    fun getDateText(message: Message): String {
//        val date = Date(message.timestamp)
//        val localDate = LocalDate.fromDateFields(date)
        return getDateText(message.timestamp)
    }

    fun getDateText(timestamp: Long): String {
        return if (DateUtils.isToday(timestamp)) {
            TODAY_DATE_FORMAT.print(timestamp)
        } else {
            DATE_FORMAT.print(timestamp)
        }
    }
}