package dev.zeteny.ticketrail.data.local.database

import dev.zeteny.ticketrail.data.model.Status
import dev.zeteny.ticketrail.data.model.Subtask
import dev.zeteny.ticketrail.data.model.TicketEntity
import dev.zeteny.ticketrail.data.model.TicketKind
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A ticket with every converted column filled in, so a round trip exercises all of [TicketConverters]. */
fun sampleTicket(id: String = "t142") = TicketEntity(
    id = id,
    kind = TicketKind.LINE,
    title = "Clean the kitchen",
    status = Status.READY,
    queue = "home",
    priority = 2,
    doDate = LocalDate(2026, 10, 22),
    dueDate = LocalDate(2026, 10, 31),
    tags = listOf("home", "chores"),
    links = mapOf("project" to "Projects/House.md"),
    path = "Daily/2026-10-21.md",
    line = 18,
    heading = "Tickets",
    subtasks = listOf(Subtask("buy sponges", done = false), Subtask("empty bin", done = true)),
    hash = "9f2c",
    raw = "- [ ] Clean the kitchen #home 🔼 ⏳ 2026-10-22 ^t142",
    extra = buildJsonObject { put("source", JsonPrimitive("test")) },
)
