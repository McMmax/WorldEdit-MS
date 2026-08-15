package dev.terrorify.worldedit

import dev.terrorify.worldedit.core.clipboard.BoundingBox
import dev.terrorify.worldedit.core.clipboard.Clipboard
import net.minestom.server.coordinate.Point
import dev.terrorify.worldedit.core.capture.ClipboardCapture
import dev.terrorify.worldedit.core.WorldEditCore
import dev.terrorify.worldedit.core.paste.PasteOptions
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

/** Per-player WorldEdit selection + clipboard state. */
class LocalSession {
    @Volatile var pos1: Point? = null
    @Volatile var pos2: Point? = null

    /** Last loaded/copied schematic, for future //paste. */
    @Volatile var clipboard: Clipboard? = null

    private val undoStack = java.util.ArrayDeque<HistoryEntry>()
    private val redoStack = java.util.ArrayDeque<HistoryEntry>()

    data class HistoryEntry(val clipboard: Clipboard, val anchor: Point)

    fun pushUndo(clipboard: Clipboard, anchor: Point) {
        undoStack.push(HistoryEntry(clipboard, anchor))
        redoStack.clear()
        if (undoStack.size > 20) {
            undoStack.removeLast()
        }
    }

    internal fun captureRegion(instance: Instance, anchor: Point, clipboard: Clipboard): Clipboard {
        val originX = anchor.blockX() + clipboard.offsetX
        val originY = anchor.blockY() + clipboard.offsetY
        val originZ = anchor.blockZ() + clipboard.offsetZ

        val box = BoundingBox(
            minX = originX,
            minY = originY,
            minZ = originZ,
            maxX = originX + clipboard.sizeX - 1,
            maxY = originY + clipboard.sizeY - 1,
            maxZ = originZ + clipboard.sizeZ - 1
        )
        val clip = ClipboardCapture.capture(instance, box)
        return Clipboard(
            sizeX = clipboard.sizeX, sizeY = clipboard.sizeY, sizeZ = clipboard.sizeZ,
            offsetX = clipboard.offsetX, offsetY = clipboard.offsetY, offsetZ = clipboard.offsetZ,
            blocks = clip.blocks,
            blockEntities = clip.blockEntities,
            entities = clip.entities
        )
    }

    fun undo(instance: Instance, player: Player): Boolean {
        val entry = if (undoStack.isNotEmpty()) undoStack.pop() else return false
        val clip = entry.clipboard
        val anchor = entry.anchor

        val redoClipboard = captureRegion(instance, anchor, clip)
        redoStack.push(HistoryEntry(redoClipboard, anchor))

        WorldEditCore.paste(instance, clip, anchor, PasteOptions(pasteEntities = false))
        return true
    }

    fun redo(instance: Instance, player: Player): Boolean {
        val entry = if (redoStack.isNotEmpty()) redoStack.pop() else return false
        val clip = entry.clipboard
        val anchor = entry.anchor

        val undoClipboard = captureRegion(instance, anchor, clip)
        undoStack.push(HistoryEntry(undoClipboard, anchor))

        WorldEditCore.paste(instance, clip, anchor, PasteOptions(pasteEntities = false))
        return true
    }

    /** The inclusive cuboid box spanning both corners, or null if either is unset. */
    fun selection(): BoundingBox? {
        val a = pos1 ?: return null
        val b = pos2 ?: return null
        return BoundingBox(
            minX = minOf(a.blockX(), b.blockX()),
            minY = minOf(a.blockY(), b.blockY()),
            minZ = minOf(a.blockZ(), b.blockZ()),
            maxX = maxOf(a.blockX(), b.blockX()),
            maxY = maxOf(a.blockY(), b.blockY()),
            maxZ = maxOf(a.blockZ(), b.blockZ()),
        )
    }
}
