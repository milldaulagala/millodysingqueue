package com.millodysingqueue.database

import liquibase.Scope
import liquibase.command.CommandScope
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import java.io.OutputStream
import java.sql.DriverManager

/** Explicit migration entry point; Ktor never calls this during application startup. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Supply one command: validate, update-sql, update, or status." }
    DatabaseMigrations.execute(
        command = args.single(),
        url = requiredEnvironment("MIGRATION_DB_URL"),
        username = requiredEnvironment("MIGRATION_DB_USER"),
        password = requiredEnvironment("MIGRATION_DB_PASSWORD"),
    )
}

private fun requiredEnvironment(name: String): String =
    requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "$name must be configured." }

object DatabaseMigrations {
    private const val CHANGELOG = "db/changelog/db.changelog-master.xml"
    private val commands = mapOf(
        "validate" to "validate",
        "update-sql" to "updateSql",
        "update" to "update",
        "status" to "status",
    )

    fun execute(
        command: String,
        url: String,
        username: String,
        password: String,
        output: OutputStream = System.out,
    ) {
        val liquibaseCommand = requireNotNull(commands[command]) { "Unsupported migration command." }
        DriverManager.getConnection(url, username, password).use { connection ->
            val database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(connection))
            database.defaultSchemaName = "app"
            database.liquibaseSchemaName = "liquibase"

            ClassLoaderResourceAccessor().use { resources ->
                Scope.child(mapOf(Scope.Attr.resourceAccessor.name to resources)) {
                    CommandScope(liquibaseCommand)
                        .addArgumentValue("database", database)
                        .addArgumentValue("changelogFile", CHANGELOG)
                        .setOutput(output)
                        .execute()
                }
            }
        }
    }
}
