package com.millodysingqueue.singer

import java.sql.SQLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory

private object Singers : Table("app.singers") {
    val id = long("singer_id").autoIncrement()
    val name = text("singer_name")
    val nickname = text("nick_name").nullable()
    val email = text("email").nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)
}

class ExposedSingerRepository(private val database: Database) : SingerRepository {
    override suspend fun create(singer: NewSinger): Singer = query {
        Singers.insertReturning {
            it[name] = singer.singerName
            it[nickname] = singer.nickName
            it[email] = singer.email
        }.single().toSinger()
    }

    override suspend fun list(): List<Singer> = query {
        Singers.selectAll()
            .orderBy(Singers.name.lowerCase() to SortOrder.ASC, Singers.id to SortOrder.ASC)
            .map { it.toSinger() }
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            transaction(database) {
                maxAttempts = 1
                queryTimeout = 5
                block()
            }
        } catch (exception: SQLException) {
            // Use structured constraint metadata: never parse SQL/error text or guess which field conflicted.
            if (exception.sqlState == "23505") {
                val constraint = generateSequence<Throwable>(exception) { it.cause }
                    .filterIsInstance<PSQLException>()
                    .mapNotNull { it.serverErrorMessage?.constraint }
                    .firstOrNull()
                when (constraint) {
                    "singers_name_nickname_case_insensitive_unique" -> throw DuplicateSingerNicknameException()
                    "singers_email_case_insensitive_unique" -> throw DuplicateSingerEmailException()
                }
            }
            logger.warn("Singer database operation failed (SQLState={})", exception.sqlState)
            throw SingerStorageException()
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ExposedSingerRepository::class.java)
    }

    private fun ResultRow.toSinger() = Singer(
        this[Singers.id], this[Singers.name], this[Singers.nickname], this[Singers.email],
        this[Singers.createdAt].toInstant(),
    )
}
