package com.knupdate.backend

import java.sql.DriverManager

class DatabaseMigrator(private val configuredUrl: String) {
    fun migrate() {
        val jdbcUrl = normalizeJdbcUrl(configuredUrl)
        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        CREATE TABLE IF NOT EXISTS schema_migrations (
                            version TEXT PRIMARY KEY,
                            applied_at TIMESTAMPTZ NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                val migrationVersion = "V1__create_core_tables"
                val alreadyApplied = connection.prepareStatement(
                    "SELECT 1 FROM schema_migrations WHERE version = ?"
                ).use { statement ->
                    statement.setString(1, migrationVersion)
                    statement.executeQuery().use { result -> result.next() }
                }

                if (!alreadyApplied) {
                    val migration = requireNotNull(
                        javaClass.getResource("/db/migration/V1__create_core_tables.sql")
                    ) { "The initial database migration is missing." }.readText()
                    connection.createStatement().use { statement -> statement.execute(migration) }
                    connection.prepareStatement(
                        "INSERT INTO schema_migrations(version, applied_at) VALUES (?, CURRENT_TIMESTAMP)"
                    ).use { statement ->
                        statement.setString(1, migrationVersion)
                        statement.executeUpdate()
                    }
                }

                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    }

    private fun normalizeJdbcUrl(url: String): String = when {
        url.startsWith("jdbc:postgresql://") -> url
        url.startsWith("postgresql://") -> "jdbc:$url"
        else -> throw IllegalArgumentException("DATABASE_URL must be a PostgreSQL JDBC URL.")
    }
}
