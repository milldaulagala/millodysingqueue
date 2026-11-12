package com.millodysingqueue.performance

import com.millodysingqueue.workspace.WorkspaceContext
import java.sql.SQLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

private object Events : Table("app.events") {
    val id = long("event_id").autoIncrement()
    val workspace = long("workspace_id")
    val name = varchar("event_name", 200)
    val date = date("event_date")
    val organiser = varchar("organiser_name", 200)
    val version = long("queue_version").default(0)
    val created = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)
}
private object Performances : Table("app.performances") {
    val id = long("performance_id").autoIncrement()
    val workspace = long("workspace_id")
    val event = long("event_id")
    val singer = long("singer_id")
    val song = long("song_id")
    val position = long("queue_position")
    val status = varchar("status", 20).default("WAITING")
    val created = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)
}
private object QueueSingers : Table("app.singers") {
    val id = long("singer_id")
    val workspace = long("workspace_id")
    val name = text("singer_name")
    val nickname = text("nick_name").nullable()
}
private object QueueSongs : Table("app.songs") {
    val id = long("song_id")
    val workspace = long("workspace_id")
    val title = varchar("title", 200)
    val artist = varchar("artist", 200)
}

/** All order mutations lock the workspace-owned event first, then compare its version. */
class ExposedPerformanceRepository(private val database: Database, private val workspace: WorkspaceContext) : PerformanceRepository {
    private val scope = workspace.workspaceId

    override suspend fun events(): List<Event> = query {
        Events.selectAll().where { Events.workspace eq scope }
            .orderBy(Events.date to SortOrder.DESC, Events.id to SortOrder.DESC).map { it.toEvent() }
    }

    override suspend fun createEvent(event: NewEvent): Event = query {
        Events.insertReturning {
            it[workspace] = scope
            it[name] = event.eventName
            it[date] = event.eventDate
            it[organiser] = event.organiserName
        }.single().toEvent()
    }

    override suspend fun queue(eventId: Long): PerformanceQueue = query {
        // Locking also makes the version and rows a consistent snapshot at READ COMMITTED.
        snapshot(lockEvent(eventId))
    }

    override suspend fun add(eventId: Long, singerId: Long, songId: Long, version: Long): PerformanceQueue = query {
        val event = lockEvent(eventId, version)
        val errors = linkedMapOf<String, String>()
        if (!QueueSingers.selectAll().where { (QueueSingers.workspace eq scope) and (QueueSingers.id eq singerId) }.any()) {
            errors["singerId"] = "Select a singer from this workspace."
        }
        if (!QueueSongs.selectAll().where { (QueueSongs.workspace eq scope) and (QueueSongs.id eq songId) }.any()) {
            errors["songId"] = "Select a song from this workspace."
        }
        if (errors.isNotEmpty()) throw PerformanceValidationException(errors)
        val maximum = Performances.position.max()
        val next = (Performances.select(maximum).where {
            (Performances.workspace eq scope) and (Performances.event eq eventId)
        }.single()[maximum] ?: 0) + 1
        Performances.insert {
            it[workspace] = scope
            it[this.event] = eventId
            it[singer] = singerId
            it[song] = songId
            it[position] = next
        }
        snapshot(bump(event))
    }

    override suspend fun move(eventId: Long, performanceId: Long, direction: String, version: Long): PerformanceQueue = query {
        val event = lockEvent(eventId, version)
        val waiting = waiting(eventId)
        val index = waiting.indexOfFirst { it.performanceId == performanceId }
        if (index < 0) throw PerformanceNotFoundException()
        val neighbour = index + if (direction == "UP") -1 else 1
        if (neighbour !in waiting.indices) {
            throw PerformanceValidationException(mapOf("direction" to "This performance cannot move further in that direction."))
        }
        fun position(id: Long, value: Long) {
            Performances.update({ (Performances.workspace eq scope) and (Performances.event eq eventId) and (Performances.id eq id) }) {
                it[Performances.position] = value
            }
        }
        // The deferred unique constraint permits an atomic swap without temporary positions.
        position(performanceId, waiting[neighbour].queuePosition)
        position(waiting[neighbour].performanceId, waiting[index].queuePosition)
        snapshot(bump(event))
    }

    override suspend fun cancel(eventId: Long, performanceId: Long, version: Long): PerformanceQueue = query {
        val event = lockEvent(eventId, version)
        val changed = Performances.update({ (Performances.workspace eq scope) and (Performances.event eq eventId) and
            (Performances.id eq performanceId) and (Performances.status eq "WAITING") }) { it[status] = "CANCELLED" }
        if (changed != 1) throw PerformanceNotFoundException()
        snapshot(bump(event))
    }

    private fun lockEvent(id: Long, version: Long? = null): Event {
        val event = Events.selectAll().where { (Events.workspace eq scope) and (Events.id eq id) }
            .forUpdate().singleOrNull()?.toEvent() ?: throw PerformanceNotFoundException()
        if (version != null && version != event.queueVersion) throw StaleQueueException()
        return event
    }

    private fun bump(event: Event): Event {
        val next = Math.addExact(event.queueVersion, 1)
        Events.update({ (Events.workspace eq scope) and (Events.id eq event.eventId) }) { it[version] = next }
        return event.copy(queueVersion = next)
    }

    private fun snapshot(event: Event) = PerformanceQueue(event.eventId, event.queueVersion, waiting(event.eventId))

    private fun waiting(eventId: Long): List<Performance> = Performances
        .join(QueueSingers, org.jetbrains.exposed.v1.core.JoinType.INNER, Performances.singer, QueueSingers.id)
        .join(QueueSongs, org.jetbrains.exposed.v1.core.JoinType.INNER, Performances.song, QueueSongs.id)
        .selectAll().where { (Performances.workspace eq scope) and (Performances.event eq eventId) and
            (Performances.status eq "WAITING") and (QueueSingers.workspace eq scope) and (QueueSongs.workspace eq scope) }
        .orderBy(Performances.position to SortOrder.ASC).map {
            Performance(it[Performances.id], it[QueueSingers.id], it[QueueSingers.name], it[QueueSingers.nickname],
                it[QueueSongs.id], it[QueueSongs.title], it[QueueSongs.artist], it[Performances.position],
                it[Performances.status], it[Performances.created].toInstant())
        }

    private fun ResultRow.toEvent() = Event(this[Events.id], this[Events.name], this[Events.date], this[Events.organiser],
        this[Events.version], this[Events.created].toInstant())

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            transaction(database) {
                maxAttempts = 1
                queryTimeout = 5
                block()
            }
        } catch (exception: SQLException) {
            logger.warn("Performance database operation failed (SQLState={})", exception.sqlState)
            throw PerformanceStorageException()
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ExposedPerformanceRepository::class.java)
    }
}
