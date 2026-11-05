package com.millodysingqueue.song

import com.millodysingqueue.workspace.WorkspaceContext
import java.sql.SQLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory

private object Songs : Table("app.songs") {
    val workspaceId = long("workspace_id")
    val id = long("song_id").autoIncrement()
    val title = varchar("title", 200)
    val artist = varchar("artist", 200)
    val videoId = varchar("youtube_video_id", 11)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)
}

class ExposedSongRepository(private val database: Database, private val workspace: WorkspaceContext) : SongRepository {
    override suspend fun create(song: NewSong): Song = query {
        Songs.insertReturning {
            it[workspaceId] = workspace.workspaceId
            it[title] = song.title
            it[artist] = song.artist
            it[videoId] = song.youtubeVideoId
        }.single().toSong()
    }

    override suspend fun list(): List<Song> = query {
        Songs.selectAll().where { Songs.workspaceId eq workspace.workspaceId }.orderBy(Songs.title.lowerCase() to SortOrder.ASC,
            Songs.artist.lowerCase() to SortOrder.ASC, Songs.id to SortOrder.ASC).map { it.toSong() }
    }

    private suspend fun <T> query(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            transaction(database) {
                maxAttempts = 1
                queryTimeout = 5
                block()
            }
        } catch (exception: SQLException) {
            val duplicateVideo = exception.sqlState == "23505" && generateSequence<Throwable>(exception) { it.cause }
                .filterIsInstance<PSQLException>().any { it.serverErrorMessage?.constraint == "songs_youtube_video_id_unique" }
            if (duplicateVideo) throw DuplicateSongVideoException()
            logger.warn("Song database operation failed (SQLState={})", exception.sqlState)
            throw SongStorageException()
        }
    }

    private fun ResultRow.toSong() = Song(this[Songs.id], this[Songs.title], this[Songs.artist],
        this[Songs.videoId], this[Songs.createdAt].toInstant())

    private companion object {
        val logger = LoggerFactory.getLogger(ExposedSongRepository::class.java)
    }
}
