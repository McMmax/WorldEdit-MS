package dev.terrorify.worldedit

import dev.terrorify.whatsmyclient.WhatsMyClient
import net.minestom.server.entity.Player

/**
 * Renders the player's current selection via the whatsmyclient client API. On Lunar Client this draws
 * through the built-in WorldEditCUI; other clients ignore it. Re-sent on every corner change.
 */
object SelectionVisualizer {

    /** Re-draws (or clears) a player's selection from their current session state. */
    @JvmStatic
    fun update(player: Player) {
        val session = SessionManager.get(player.uuid)
        val p1 = session.pos1
        val p2 = session.pos2
        when {
            p1 != null && p2 != null ->
                WhatsMyClient.showSelection(
                    player,
                    p1.blockX(), p1.blockY(), p1.blockZ(),
                    p2.blockX(), p2.blockY(), p2.blockZ(),
                )
            // Single corner: a 1-block cuboid so the set corner is visible before the other is set.
            p1 != null ->
                WhatsMyClient.showSelection(player, p1.blockX(), p1.blockY(), p1.blockZ(), p1.blockX(), p1.blockY(), p1.blockZ())
            p2 != null ->
                WhatsMyClient.showSelection(player, p2.blockX(), p2.blockY(), p2.blockZ(), p2.blockX(), p2.blockY(), p2.blockZ())
            else -> WhatsMyClient.clearSelection(player)
        }
    }

    @JvmStatic
    fun clear(player: Player) = WhatsMyClient.clearSelection(player)
}
