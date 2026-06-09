package `fun`.nightshift.launcher.backend.db

import org.jetbrains.exposed.sql.insert
import java.util.UUID

object TelemetryRepository {

    fun recordEvent(sessionId: UUID?, eventType: String, payloadJson: String?) {
        TelemetryEvents.insert {
            it[TelemetryEvents.sessionId] = sessionId
            it[TelemetryEvents.eventType] = eventType
            it[TelemetryEvents.payload] = payloadJson
        }
    }

    fun recordCrash(sessionId: UUID?, stackTrace: String, clientVersion: String?) {
        CrashReports.insert {
            it[CrashReports.sessionId] = sessionId
            it[CrashReports.stackTrace] = stackTrace
            it[CrashReports.clientVersion] = clientVersion
        }
    }
}
