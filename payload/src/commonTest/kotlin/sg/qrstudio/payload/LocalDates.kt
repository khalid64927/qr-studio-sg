package sg.qrstudio.payload

import kotlinx.datetime.LocalDate

/** Fixed dates so tests never depend on the wall clock. */
object LocalDates {
    val TODAY = LocalDate(2026, 9, 9)
    val YESTERDAY = LocalDate(2026, 9, 8)
    val FAR_FUTURE = LocalDate(2030, 12, 31)
}
