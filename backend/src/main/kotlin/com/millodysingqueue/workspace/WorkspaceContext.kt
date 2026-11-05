package com.millodysingqueue.workspace

import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private object Workspaces : Table("app.workspaces") {
    val id = long("workspace_id")
}

/** Server-selected ownership scope. This is not authentication or membership authorisation. */
data class WorkspaceContext(val workspaceId: Long) {
    init {
        require(workspaceId > 0) { "Workspace ID must be a positive integer." }
    }

    companion object {
        fun fromConfig(config: ApplicationConfig): WorkspaceContext {
            val id = config.propertyOrNull("workspace.id")?.getString()?.toLongOrNull()
            require(id != null && id > 0) { "APPLICATION_WORKSPACE_ID must be configured as a positive integer." }
            return WorkspaceContext(id)
        }
    }

    fun requireExists(database: Database) {
        val exists = transaction(database) {
            maxAttempts = 1
            queryTimeout = 5
            Workspaces.selectAll().where { Workspaces.id eq workspaceId }.any()
        }
        require(exists) { "Configured workspace does not exist. Apply migrations and check APPLICATION_WORKSPACE_ID." }
    }
}
