package dev.zeteny.ticketrail.data

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverters
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class TicketKind {
    @SerialName("line") LINE,
    @SerialName("note") NOTE,
}

@Serializable
data class Subtask(
    val text: String,
    val done: Boolean,
)
@Serializable
enum class Status{
    @SerialName("inbox") INBOX,
    @SerialName("ready") READY,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("blocked") BLOCKED,
    @SerialName("done") DONE,
    @SerialName("dropped") DROPPED,


}

@Serializable
@Entity(tableName = "tickets_db")
@ColumnTypeConverters(Converters::class)
data class TicketEntity(
    @ColumnInfo(name = "id") @PrimaryKey val id: String,
    @ColumnInfo(name = "kind") val kind: TicketKind,
    @SerialName("note_type") @ColumnInfo(name = "note_type") val noteType: String? = null,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "status") val status: Status,
    @ColumnInfo(name = "queue") val queue: String? = null,
    @ColumnInfo(name = "priority") val priority: Int,
    @SerialName("do_date") @ColumnInfo(name = "do_date") val doDate: LocalDate? = null,
    @SerialName("due_date") @ColumnInfo(name = "due_date") val dueDate: LocalDate? = null,
    @SerialName("start_date") @ColumnInfo(name = "start_date") val startDate: LocalDate? = null,
    @SerialName("created_date") @ColumnInfo(name = "created_date") val createdDate: LocalDate? = null,
    @SerialName("done_date") @ColumnInfo(name = "done_date") val doneDate: LocalDate? = null,
    @SerialName("cancelled_date") @ColumnInfo(name = "cancelled_date") val cancelledDate: LocalDate? = null,
    @ColumnInfo(name = "recurrence") val recurrence: String? = null,
    @ColumnInfo(name = "tags") val tags: List<String> = emptyList(),
    @ColumnInfo(name = "links") val links: Map<String, String> = emptyMap(),
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "line") val line: Int? = null,
    @ColumnInfo(name = "heading") val heading: String? = null,
    @ColumnInfo(name = "subtasks") val subtasks: List<Subtask> = emptyList(),
    @ColumnInfo(name = "hash") val hash: String,
    @ColumnInfo(name = "raw") val raw: String,
    @ColumnInfo(name = "extra") val extra: JsonObject = JsonObject(emptyMap()),
)
