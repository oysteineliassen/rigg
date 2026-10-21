package no.oppdragsliste.app.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Konverterere {
    @TypeConverter fun fraDato(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun tilDato(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun fraTid(t: LocalTime?): Int? = t?.toSecondOfDay()
    @TypeConverter fun tilTid(v: Int?): LocalTime? = v?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun fraInstant(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun tilInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    @TypeConverter fun fraPrioritet(p: Prioritet): String = p.name
    @TypeConverter fun tilPrioritet(v: String): Prioritet =
        runCatching { Prioritet.valueOf(v) }.getOrDefault(Prioritet.OMRADE)
}
