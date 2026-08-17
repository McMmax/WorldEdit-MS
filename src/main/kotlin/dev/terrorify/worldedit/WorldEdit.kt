package dev.terrorify.worldedit

import dev.terrorify.worldedit.core.capture.ClipboardCapture
import dev.terrorify.worldedit.core.clipboard.BoundingBox
import dev.terrorify.worldedit.core.clipboard.Clipboard
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerStartDiggingEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import dev.terrorify.worldedit.core.WorldEditCore
import dev.terrorify.worldedit.core.clipboard.Int3
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.spacetime.SpaceIntegration
import net.minestom.server.spacetime.SpacetimeProfileService

/**
 * Interactive WorldEdit selection layer. Provides the wand + position commands and a Java-friendly API
 * ([selection], [captureSelection]) that Janitor uses for `/janitor create`.
 *
 * Selecting is implicit: left-click a block with the wand sets pos1, right-click sets pos2 (each click
 * is a selection, so there's no separate "show selection" command). Left-click is handled via the
 * block-break event so it works in creative (the break is cancelled).
 */
object WorldEdit {

    /** The selection wand material; left-click a block sets pos1, right-click sets pos2. */
    private val WAND_MATERIAL = Material.WOODEN_AXE

    @JvmStatic
    fun bootstrap() {
        registerCommands()
        registerWandListeners()
    }

    /** The active cuboid selection for a player, or null if both corners aren't set. */
    @JvmStatic
    fun selection(player: Player): BoundingBox? = SessionManager.get(player.uuid).selection()

    /** Captures the player's selection from their current instance into a [Clipboard], or null. */
    @JvmStatic
    fun captureSelection(player: Player): Clipboard? {
        val box = selection(player) ?: return null
        val instance = player.instance ?: return null
        val clip = ClipboardCapture.capture(instance, box)
        return Clipboard(
            sizeX = clip.sizeX, sizeY = clip.sizeY, sizeZ = clip.sizeZ,
            offsetX = box.minX - player.position.blockX(),
            offsetY = box.minY - player.position.blockY(),
            offsetZ = box.minZ - player.position.blockZ(),
            blocks = clip.blocks,
            blockEntities = clip.blockEntities,
            entities = clip.entities
        )
    }

    @JvmStatic
    fun hasPermission(player: Player): Boolean {
        val serverId = SpaceIntegration.INSTANCE.serverId() ?: return false
        val profile = SpacetimeProfileService.getProfile(player.uuid, serverId)
        return profile.hasPermission("command.worldedit")
    }

    @JvmStatic
    fun checkPermission(player: Player): Boolean {
        if (!hasPermission(player)) {
            player.sendMessage(Component.text("You don't have permission to use WorldEdit.", net.kyori.adventure.text.format.NamedTextColor.RED))
            return false
        }
        return true
    }

    private fun registerCommands() {
        val cm = MinecraftServer.getCommandManager()

        // //wand
        val wand = Command("/wand", "wand")
        wand.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        wand.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                sender.inventory.addItemStack(ItemStack.of(WAND_MATERIAL))
                sender.sendMessage(Component.text("Left-click a block for pos1, right-click for pos2."))
            }
        }
        cm.register(wand)

        // //pos1
        val pos1 = Command("/pos1", "pos1")
        pos1.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        pos1.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                setPos1(sender, aimedBlock(sender))
            }
        }
        cm.register(pos1)

        // //pos2
        val pos2 = Command("/pos2", "pos2")
        pos2.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        pos2.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                setPos2(sender, aimedBlock(sender))
            }
        }
        cm.register(pos2)

        // //copy
        val copy = Command("/copy", "copy")
        copy.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        copy.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                val clipboard = captureSelection(sender)
                if (clipboard != null) {
                    val session = SessionManager.get(sender.uuid)
                    session.clipboard = clipboard
                    sender.sendMessage(Component.text("Selection copied to clipboard (${clipboard.sizeX}x${clipboard.sizeY}x${clipboard.sizeZ})"))
                } else {
                    sender.sendMessage(Component.text("Make a selection first!"))
                }
            }
        }
        cm.register(copy)

        // //paste
        val paste = Command("/paste", "paste")
        paste.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        paste.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                val session = SessionManager.get(sender.uuid)
                val clipboard = session.clipboard
                if (clipboard == null) {
                    sender.sendMessage(Component.text("Clipboard is empty! Copy something first."))
                    return@setDefaultExecutor
                }
                if (clipboard.volume > 500000) {
                    sender.sendMessage(Component.text("Clipboard is too large! Maximum block limit is 500,000 blocks.", net.kyori.adventure.text.format.NamedTextColor.RED))
                    return@setDefaultExecutor
                }
                val instance = sender.instance ?: return@setDefaultExecutor

                val anchor = sender.position
                val undoClipboard = session.captureRegion(instance, anchor, clipboard)
                session.pushUndo(undoClipboard, anchor)

                WorldEditCore.paste(instance, clipboard, anchor)
                sender.sendMessage(Component.text("Pasted clipboard at player position."))
            }
        }
        cm.register(paste)

        // //set
        cm.register(setCommand())

        // //replace
        cm.register(replaceCommand())

        // //undo
        val undo = Command("/undo", "undo")
        undo.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        undo.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                val instance = sender.instance ?: return@setDefaultExecutor
                val ok = SessionManager.get(sender.uuid).undo(instance, sender)
                if (ok) {
                    sender.sendMessage(Component.text("Undo applied."))
                } else {
                    sender.sendMessage(Component.text("Nothing to undo!"))
                }
            }
        }
        cm.register(undo)

        // //redo
        val redo = Command("/redo", "redo")
        redo.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        redo.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                val instance = sender.instance ?: return@setDefaultExecutor
                val ok = SessionManager.get(sender.uuid).redo(instance, sender)
                if (ok) {
                    sender.sendMessage(Component.text("Redo applied."))
                } else {
                    sender.sendMessage(Component.text("Nothing to redo!"))
                }
            }
        }
        cm.register(redo)

        // //up
        cm.register(upCommand())

        // //schem
        SchematicCommands.register()
    }

    private fun setCommand(): Command {
        val command = Command("/set", "set")
        command.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        val blockArg = ArgumentType.String("block")
        command.addSyntax({ sender, ctx ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@addSyntax
                val blockName = ctx.get(blockArg)
                val block = resolveBlock(blockName)
                if (block == null) {
                    sender.sendMessage(Component.text("Unknown block: $blockName"))
                    return@addSyntax
                }
                val box = selection(sender)
                if (box == null) {
                    sender.sendMessage(Component.text("Make a selection first!"))
                    return@addSyntax
                }
                if (box.volume > 500000) {
                    sender.sendMessage(Component.text("Selection too large! Maximum block limit is 500,000 blocks.", net.kyori.adventure.text.format.NamedTextColor.RED))
                    return@addSyntax
                }
                val instance = sender.instance ?: return@addSyntax

                // Capture undo state BEFORE modifying blocks!
                val undoClipboard = captureSelection(sender)
                if (undoClipboard != null) {
                    SessionManager.get(sender.uuid).pushUndo(undoClipboard, sender.position)
                }

                var count = 0
                val minX = box.minX
                val minY = box.minY
                val minZ = box.minZ

                var ly = 0
                while (ly < box.sizeY) {
                    var lz = 0
                    while (lz < box.sizeZ) {
                        var lx = 0
                        while (lx < box.sizeX) {
                            val x = minX + lx
                            val y = minY + ly
                            val z = minZ + lz
                            instance.setBlock(x, y, z, block)
                            count++
                            lx++
                        }
                        lz++
                    }
                    ly++
                }

                sender.sendMessage(Component.text("Set $count blocks to ${block.name()}."))
            }
        }, blockArg)
        command.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                sender.sendMessage(Component.text("Usage: //set <block>"))
            }
        }
        return command
    }

    private fun replaceCommand(): Command {
        val command = Command("/replace", "replace")
        command.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        val arg1 = ArgumentType.String("arg1")
        val arg2 = ArgumentType.String("arg2")

        command.addSyntax({ sender, ctx ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@addSyntax
                replace(sender, null, ctx.get(arg1))
            }
        }, arg1)

        command.addSyntax({ sender, ctx ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@addSyntax
                replace(sender, ctx.get(arg1), ctx.get(arg2))
            }
        }, arg1, arg2)

        command.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                sender.sendMessage(Component.text("Usage: //replace [from] <to>"))
            }
        }
        return command
    }

    private fun replace(player: Player, fromName: String?, toName: String) {
        val toBlock = resolveBlock(toName)
        if (toBlock == null) {
            player.sendMessage(Component.text("Unknown block: $toName"))
            return
        }
        val fromBlock = if (fromName != null) {
            val b = resolveBlock(fromName)
            if (b == null) {
                player.sendMessage(Component.text("Unknown block: $fromName"))
                return
            }
            b
        } else null

        val box = selection(player)
        if (box == null) {
            player.sendMessage(Component.text("Make a selection first!"))
            return
        }
        if (box.volume > 500000) {
            player.sendMessage(Component.text("Selection too large! Maximum block limit is 500,000 blocks.", net.kyori.adventure.text.format.NamedTextColor.RED))
            return
        }
        val instance = player.instance ?: return

        // Capture undo state BEFORE modifying blocks!
        val undoClipboard = captureSelection(player)
        if (undoClipboard != null) {
            SessionManager.get(player.uuid).pushUndo(undoClipboard, player.position)
        }

        val minX = box.minX
        val minY = box.minY
        val minZ = box.minZ

        var count = 0
        var ly = 0
        while (ly < box.sizeY) {
            var lz = 0
            while (lz < box.sizeZ) {
                var lx = 0
                while (lx < box.sizeX) {
                    val x = minX + lx
                    val y = minY + ly
                    val z = minZ + lz
                    val oldBlock = instance.getBlock(x, y, z)

                    val match = if (fromBlock != null) {
                        oldBlock.id() == fromBlock.id()
                    } else {
                        !oldBlock.isAir
                    }

                    if (match) {
                        instance.setBlock(x, y, z, toBlock)
                        count++
                    }
                    lx++
                }
                lz++
            }
            ly++
        }

        player.sendMessage(Component.text("Replaced $count blocks."))
    }

    private fun registerWandListeners() {
        val handler = MinecraftServer.getGlobalEventHandler()
        // Left-click a block with the wand -> pos1. Handled via the dig-start event (cancelled) so it
        // fires in creative without instantly breaking the block.
        handler.addListener(PlayerStartDiggingEvent::class.java) { event ->
            if (holdingWand(event.player)) {
                event.isCancelled = true
                setPos1(event.player, event.blockPosition)
            }
        }
        // Right-click a block with the wand -> pos2.
        handler.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (holdingWand(event.player)) setPos2(event.player, event.blockPosition)
        }
    }

    // ── //up <n> [block] ──────────────────────────────────────────────────────────────────────
    private fun upCommand(): Command {
        val command = Command("/up", "up")
        command.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) hasPermission(sender) else true
        }
        val count = ArgumentType.Integer("count")
        val block = ArgumentType.String("block")
        command.addSyntax({ sender, ctx ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@addSyntax
                up(sender, ctx.get(count), Block.GLASS)
            }
        }, count)
        command.addSyntax({ sender, ctx ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@addSyntax
                up(sender, ctx.get(count), resolveBlock(ctx.get(block)) ?: Block.GLASS)
            }
        }, count, block)
        command.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!checkPermission(sender)) return@setDefaultExecutor
                sender.sendMessage(Component.text("Usage: //up <count> [block]"))
            }
        }
        return command
    }

    /** Rise [n] blocks, placing [block] beneath the new feet so the player stands on it. */
    private fun up(player: Player, n: Int, block: Block) {
        val instance = player.instance ?: return
        val pos = player.position
        val bx = pos.blockX()
        val bz = pos.blockZ()
        val newFeetY = pos.blockY() + maxOf(1, n)
        val oldBlock = instance.getBlock(bx, newFeetY - 1, bz)
        val undoBlocks = shortArrayOf(oldBlock.stateId().toShort())
        val undoEntities = if (oldBlock.nbt() != null) mapOf(Int3(0, 0, 0) to oldBlock.nbt()!!) else emptyMap()

        val undoClipboard = Clipboard(
            sizeX = 1, sizeY = 1, sizeZ = 1,
            offsetX = bx - player.position.blockX(),
            offsetY = (newFeetY - 1) - player.position.blockY(),
            offsetZ = bz - player.position.blockZ(),
            blocks = undoBlocks,
            blockEntities = undoEntities
        )
        SessionManager.get(player.uuid).pushUndo(undoClipboard, player.position)

        instance.setBlock(bx, newFeetY - 1, bz, block)
        player.teleport(Pos(bx + 0.5, newFeetY.toDouble(), bz + 0.5, pos.yaw, pos.pitch))
    }

    private fun resolveBlock(name: String): Block? {
        val key = if (name.contains(':')) name else "minecraft:$name"
        return Block.fromState(key) ?: Block.fromState(name)
    }

    /** The first non-air block the player is looking at (raycast), or their feet if looking at sky. */
    private fun aimedBlock(player: Player): Point {
        val instance = player.instance
        if (instance != null) {
            for (point in player.getLineOfSight(64)) {
                if (!instance.getBlock(point).isAir) return point
            }
        }
        return player.position
    }

    private fun holdingWand(player: Player): Boolean =
        player.itemInMainHand.material() == WAND_MATERIAL

    private fun setPos1(player: Player, point: Point) {
        val session = SessionManager.get(player.uuid)
        session.pos1 = point
        player.sendMessage(Component.text(message(1, point, session.pos2)))
        SelectionVisualizer.update(player)
    }

    private fun setPos2(player: Player, point: Point) {
        val session = SessionManager.get(player.uuid)
        session.pos2 = point
        player.sendMessage(Component.text(message(2, point, session.pos1)))
        SelectionVisualizer.update(player)
    }

    /** Builds the selection feedback line for the just-set position [justSet] (1 or 2). */
    private fun message(justSet: Int, set: Point, other: Point?): String {
        val otherName = if (justSet == 1) "Position 2" else "Position 1"
        val head = "Position $justSet Selected: ${coord(set)} | "
        if (other == null) return head + "$otherName Not Set"
        return head + "$otherName: ${coord(other)}" + dims(set, other)
    }

    private fun coord(p: Point): String = "x${p.blockX()}, y${p.blockY()}, z${p.blockZ()}"

    private fun dims(a: Point, b: Point): String {
        val box = BoundingBox(
            minOf(a.blockX(), b.blockX()), minOf(a.blockY(), b.blockY()), minOf(a.blockZ(), b.blockZ()),
            maxOf(a.blockX(), b.blockX()), maxOf(a.blockY(), b.blockY()), maxOf(a.blockZ(), b.blockZ()),
        )
        return " (${box.sizeX}W x ${box.sizeY}H x ${box.sizeZ}L - ${box.volume} blocks)"
    }

    private inline fun simpleCommand(name: String, crossinline action: (Player) -> Unit): Command {
        val command = Command(name)
        command.setDefaultExecutor { sender, _ -> if (sender is Player) action(sender) }
        return command
    }
}
