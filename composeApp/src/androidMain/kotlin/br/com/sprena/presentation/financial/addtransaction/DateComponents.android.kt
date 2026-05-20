package br.com.sprena.presentation.financial.addtransaction

import java.util.Calendar
import java.util.TimeZone

actual fun dateComponentsFromMillis(millis: Long): DateComponents {
    val cal =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = millis
        }
    return DateComponents(
        day = cal.get(Calendar.DAY_OF_MONTH),
        month = cal.get(Calendar.MONTH) + 1,
        year = cal.get(Calendar.YEAR),
    )
}
