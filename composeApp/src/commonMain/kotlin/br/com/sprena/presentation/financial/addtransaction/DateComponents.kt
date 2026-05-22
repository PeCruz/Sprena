package br.com.sprena.presentation.financial.addtransaction

/**
 * Platform-agnostic date components extracted from epoch millis.
 */
data class DateComponents(
    val day: Int,
    val month: Int,
    val year: Int,
)

/**
 * Converts epoch millis (UTC) to day/month/year components.
 */
expect fun dateComponentsFromMillis(millis: Long): DateComponents
