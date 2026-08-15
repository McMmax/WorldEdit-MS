package dev.terrorify.worldedit

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Owns per-player [LocalSession] state, keyed by player UUID. */
object SessionManager {
    private val sessions = ConcurrentHashMap<UUID, LocalSession>()

    fun get(uuid: UUID): LocalSession = sessions.computeIfAbsent(uuid) { LocalSession() }

    fun remove(uuid: UUID) {
        sessions.remove(uuid)
    }
}
