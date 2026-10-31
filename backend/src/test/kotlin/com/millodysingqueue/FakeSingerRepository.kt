package com.millodysingqueue

import com.millodysingqueue.singer.DuplicateSingerEmailException
import com.millodysingqueue.singer.DuplicateSingerNicknameException
import com.millodysingqueue.singer.NewSinger
import com.millodysingqueue.singer.Singer
import com.millodysingqueue.singer.SingerRepository
import com.millodysingqueue.singer.SingerStorageException
import java.time.Instant

/** Used only by unit/API tests. Runtime always uses PostgreSQL. */
class FakeSingerRepository : SingerRepository {
    val saved = mutableListOf<Singer>()
    var unavailable = false

    override suspend fun create(singer: NewSinger): Singer {
        if (unavailable) throw SingerStorageException()
        if (singer.email != null && saved.any { it.email.equals(singer.email, ignoreCase = true) }) {
            throw DuplicateSingerEmailException()
        }
        if (singer.nickName != null && saved.any {
            it.singerName.equals(singer.singerName, ignoreCase = true) && it.nickName.equals(singer.nickName, ignoreCase = true)
        }) {
            throw DuplicateSingerNicknameException()
        }
        return Singer((saved.size + 1).toLong(), singer.singerName, singer.nickName, singer.email,
            Instant.parse("2026-10-31T00:00:00Z")).also { saved.add(it) }
    }

    override suspend fun list(): List<Singer> {
        if (unavailable) throw SingerStorageException()
        return saved.sortedWith(compareBy<Singer> { it.singerName.lowercase() }.thenBy { it.singerId })
    }
}
