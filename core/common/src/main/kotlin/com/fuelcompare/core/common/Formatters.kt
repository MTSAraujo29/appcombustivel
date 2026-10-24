package com.fuelcompare.core.common

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatação pt-BR centralizada, sem dependência de recursos do Android. */
object Formatters {
    const val CURRENCY_SYMBOL = "R$"
    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBr)
    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", ptBr)
    private val shortDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", ptBr)

    fun currency(value: Double): String {
        val format = NumberFormat.getCurrencyInstance(ptBr)
        return format.format(value)
    }

    fun price(value: Double, unitLabel: String): String = "${currency(value)}/$unitLabel"

    fun date(value: LocalDate): String = dateFormatter.format(value)

    fun dateTime(value: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormatter.format(value.atZone(zone))

    fun shortDateTime(value: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        shortDateTimeFormatter.format(value.atZone(zone))

    fun distance(km: Double?): String = when {
        km == null -> "—"
        km < 1.0 -> "${(km * 1000).toInt()} m"
        km < 10.0 -> String.format(ptBr, "%.1f km", km)
        else -> String.format(ptBr, "%.0f km", km)
    }

    fun lastUpdated(value: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        "Atualizado em ${dateTime(value, zone)}"
}
