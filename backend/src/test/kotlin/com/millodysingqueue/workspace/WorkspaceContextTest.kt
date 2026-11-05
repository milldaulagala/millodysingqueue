package com.millodysingqueue.workspace

import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceContextTest {
    @Test
    fun `accepts positive BIGINT workspace configuration`() {
        assertEquals(Long.MAX_VALUE, WorkspaceContext.fromConfig(
            MapApplicationConfig("workspace.id" to Long.MAX_VALUE.toString())).workspaceId)
    }

    @Test
    fun `missing malformed nonpositive and overflowing IDs fail closed`() {
        assertFailsWith<IllegalArgumentException> { WorkspaceContext.fromConfig(MapApplicationConfig()) }
        for (value in listOf("", " ", "0", "-1", "abc", "1.5", "9223372036854775808")) {
            assertFailsWith<IllegalArgumentException> {
                WorkspaceContext.fromConfig(MapApplicationConfig("workspace.id" to value))
            }
        }
        assertFailsWith<IllegalArgumentException> { WorkspaceContext(0) }
    }
}
