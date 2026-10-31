package com.millodysingqueue.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/** Owns runtime connections only. Liquibase runs through its separate migration entry point. */
class ApplicationDatabase(config: ApplicationConfig) : AutoCloseable {
    private val pool = HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.required("database.url")
        username = config.required("database.user")
        password = config.required("database.password")
        maximumPoolSize = 5
        minimumIdle = 1
        connectionTimeout = 5_000
        validationTimeout = 2_000
        initializationFailTimeout = 5_000
        poolName = "singer-database"
        addDataSourceProperty("connectTimeout", "5")
        addDataSourceProperty("socketTimeout", "10")
        addDataSourceProperty("tcpKeepAlive", "true")
        addDataSourceProperty("ApplicationName", "MillodySingQueue")
    })
    val database: Database = try {
        Database.connect(pool)
    } catch (exception: Exception) {
        pool.close()
        throw exception
    }

    override fun close() {
        try {
            TransactionManager.closeAndUnregister(database)
        } finally {
            pool.close()
        }
    }
}

private fun ApplicationConfig.required(path: String): String =
    requireNotNull(propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }) {
        "$path must be configured. For local development, use ./scripts/run.sh."
    }
