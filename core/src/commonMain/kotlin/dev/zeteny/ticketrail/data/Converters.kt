package dev.zeteny.ticketrail.data

import androidx.room3.ColumnTypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class Converters {
    @ColumnTypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @ColumnTypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @ColumnTypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @ColumnTypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)

    @ColumnTypeConverter
    fun fromStringMap(value: Map<String, String>): String = Json.encodeToString(value)

    @ColumnTypeConverter
    fun toStringMap(value: String): Map<String, String> = Json.decodeFromString(value)

    @ColumnTypeConverter
    fun fromSubtasks(value: List<Subtask>): String = Json.encodeToString(value)

    @ColumnTypeConverter
    fun toSubtasks(value: String): List<Subtask> = Json.decodeFromString(value)

    @ColumnTypeConverter
    fun fromJsonObject(value: JsonObject): String = Json.encodeToString(value)

    @ColumnTypeConverter
    fun toJsonObject(value: String): JsonObject = Json.decodeFromString(value)

}
