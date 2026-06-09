package `fun`.nightshift.launcher.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Lifecycle owner for the JDBC pool, Flyway migrator and the Exposed `Database`
 * handle. Initialised once during application start-up via [init].
 */
object DatabaseFactory {

    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    @Volatile
    private var dataSource: HikariDataSource? = null

    @Volatile
    private var database: Database? = null

    /**
     * Builds the JDBC pool, runs Flyway migrations and connects Exposed to it.
     *
     * The expected HOCON layout matches `application.conf`:
     * ```
     * database {
     *   host = "..."
     *   port = 5432
     *   name = "..."
     *   user = "..."
     *   password = "..."
     *   poolSize = 10
     * }
     * ```
     */
    fun init(config: ApplicationConfig) {
        if (database != null) {
            log.warn("DatabaseFactory.init() called twice; ignoring second invocation")
            return
        }

        val driver = config.propertyOrNull("database.driver")?.getString()?.lowercase() ?: "h2"
        val poolSize = config.propertyOrNull("database.poolSize")?.getString()?.toIntOrNull() ?: 10

        val ds = when (driver) {
            "h2" -> buildH2DataSource(poolSize)
            "postgres", "postgresql" -> buildPostgresDataSource(config, poolSize)
            else -> error("Unsupported database.driver '$driver'; expected 'h2' or 'postgres'")
        }

        // Connect Exposed first so we can use it to create the schema
        // when running on H2 (Flyway SQL migrations are PostgreSQL-only).
        database = Database.connect(ds)
        dataSource = ds

        when (driver) {
            "h2" -> initSchemaH2()
            else -> runMigrations(ds)
        }

        log.info("Database initialised (driver={})", driver)
    }

    private fun buildH2DataSource(poolSize: Int): HikariDataSource {
        // File-backed H2 keeps the data between restarts but is still
        // dependency-free — no docker, no postgres install. Useful for
        // local development and demos.
        val dataDir = System.getProperty("user.home") + "/.nightshift-backend"
        java.io.File(dataDir).mkdirs()
        val jdbcUrl = "jdbc:h2:file:$dataDir/db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
        log.info("Using embedded H2 database at {}", jdbcUrl)
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = "sa"
                this.password = ""
                this.driverClassName = "org.h2.Driver"
                this.maximumPoolSize = poolSize
                this.minimumIdle = 1
                this.isAutoCommit = false
                this.poolName = "nightshift-h2-pool"
                validate()
            }
        )
    }

    private fun buildPostgresDataSource(config: ApplicationConfig, poolSize: Int): HikariDataSource {
        val host = config.property("database.host").getString()
        val port = config.property("database.port").getString().toInt()
        val name = config.property("database.name").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        val jdbcUrl = "jdbc:postgresql://$host:$port/$name"
        log.info("Connecting to PostgreSQL at {} as {}", jdbcUrl, user)
        return HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = user
                this.password = password
                this.driverClassName = "org.postgresql.Driver"
                this.maximumPoolSize = poolSize
                this.minimumIdle = (poolSize / 2).coerceAtLeast(1)
                this.isAutoCommit = false
                this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                this.poolName = "nightshift-pool"
                validate()
            }
        )
    }

    /**
     * H2 schema creation via Exposed's `SchemaUtils.createMissingTablesAndColumns`.
     * Flyway is bypassed because the V1 migration uses PostgreSQL-specific
     * features (pgcrypto, INET, JSONB) that wouldn't translate cleanly.
     */
    private fun initSchemaH2() {
        log.info("Creating H2 schema via Exposed")
        org.jetbrains.exposed.sql.transactions.transaction(database) {
            org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns(
                Accounts,
                Sessions,
                ActivationKeys,
                LoginAttempts,
                PasswordResetRequests,
                OnlineHeartbeats,
                TelemetryEvents,
                CrashReports,
            )
        }
    }

    private fun runMigrations(ds: DataSource) {
        log.info("Running Flyway migrations")
        val result = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()
        log.info(
            "Flyway migration finished: success={} migrationsExecuted={} initialSchemaVersion={} targetSchemaVersion={}",
            result.success,
            result.migrationsExecuted,
            result.initialSchemaVersion,
            result.targetSchemaVersion,
        )
    }

    /** Closes the connection pool. Mainly used by tests / graceful shutdown. */
    fun close() {
        dataSource?.close()
        dataSource = null
        database = null
    }

    /**
     * Convenience wrapper around [newSuspendedTransaction] that uses the IO
     * dispatcher and the application-wide Exposed database handle.
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db = database) { block() }
}

/**
 * Top-level extension mirroring `DatabaseFactory.dbQuery` so call sites can
 * write `dbQuery { ... }` without referencing the singleton each time.
 */
suspend fun <T> dbQuery(block: suspend () -> T): T = DatabaseFactory.dbQuery(block)
