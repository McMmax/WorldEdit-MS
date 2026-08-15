package dev.terrorify.worldedit

import dev.terrorify.worldedit.core.WorldEditCore
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import java.nio.file.Files
import java.nio.file.Path

/**
 * `//schem load <name>` — load a schematic from the `schematics/` directory and paste it at the player.
 *
 * The name may be given with or without an extension; without one, the directory is searched across the
 * supported formats and loaded only if a single match exists (otherwise it reports the ambiguity).
 */
object SchematicCommands {

    private const val DIR = "schematics"
    private val EXTENSIONS = listOf("schem", "schematic", "nbt", "litematic", "bp")

    fun register() {
        val command = Command("/schem")
        command.setCondition { sender, commandString ->
            if (commandString != null) return@setCondition true
            if (sender is Player) WorldEdit.hasPermission(sender) else true
        }
        val load = ArgumentType.Literal("load")
        val name = ArgumentType.String("name")
        command.addSyntax({ sender, ctx ->
            if (sender is Player) {
                if (!WorldEdit.checkPermission(sender)) return@addSyntax
                load(sender, ctx.get(name))
            }
        }, load, name)
        command.addSyntax({ sender, _ ->
            if (sender is Player) {
                if (!WorldEdit.checkPermission(sender)) return@addSyntax
                list(sender)
            }
        }, ArgumentType.Literal("list"))
        command.setDefaultExecutor { sender, _ ->
            if (sender is Player) {
                if (!WorldEdit.checkPermission(sender)) return@setDefaultExecutor
                sender.sendMessage(Component.text("Usage: //schem load <name> | //schem list"))
            }
        }
        MinecraftServer.getCommandManager().register(command)
    }

    private fun list(player: Player) {
        val dir = Path.of(DIR)
        if (!Files.isDirectory(dir)) {
            player.sendMessage(Component.text("No schematics/ directory."))
            return
        }
        val names = ArrayList<String>()
        Files.newDirectoryStream(dir).use { stream ->
            for (p in stream) {
                if (!Files.isRegularFile(p)) continue
                val fileName = p.fileName.toString()
                val dot = fileName.lastIndexOf('.')
                if (dot > 0 && fileName.substring(dot + 1).lowercase() in EXTENSIONS) names.add(fileName)
            }
        }
        names.sort()
        if (names.isEmpty()) {
            player.sendMessage(Component.text("No schematics in $DIR/"))
        } else {
            player.sendMessage(Component.text("Schematics (${names.size}): ${names.joinToString(", ")}"))
        }
    }

    private fun load(player: Player, name: String) {
        val path = resolve(player, name) ?: return
        try {
            val clipboard = WorldEditCore.load(path)
            val instance = player.instance ?: return
            SessionManager.get(player.uuid).clipboard = clipboard
            WorldEditCore.paste(instance, clipboard, player.position)
            player.sendMessage(Component.text(
                "Loaded ${path.fileName} (${clipboard.sizeX}x${clipboard.sizeY}x${clipboard.sizeZ})"))
        } catch (e: Exception) {
            player.sendMessage(Component.text("Failed to load ${path.fileName}: ${e.message}"))
        }
    }

    /** Resolves a schematic name to a file, messaging the player on miss/ambiguity. */
    private fun resolve(player: Player, name: String): Path? {
        val arg = Path.of(name)
        if (arg.isAbsolute) {
            if (Files.isRegularFile(arg)) return arg
            player.sendMessage(Component.text("Not found: $name"))
            return null
        }
        val dir = Path.of(DIR)
        if (!Files.isDirectory(dir)) {
            player.sendMessage(Component.text("No schematics/ directory."))
            return null
        }
        // Explicit extension -> exact file.
        if (name.contains('.')) {
            val exact = dir.resolve(name)
            if (Files.isRegularFile(exact)) return exact
            player.sendMessage(Component.text("Not found: $DIR/$name"))
            return null
        }
        // No extension -> match by base name across supported formats.
        val matches = ArrayList<Path>()
        Files.newDirectoryStream(dir).use { stream ->
            for (p in stream) {
                if (!Files.isRegularFile(p)) continue
                val fileName = p.fileName.toString()
                val dot = fileName.lastIndexOf('.')
                if (dot <= 0) continue
                val base = fileName.substring(0, dot)
                val ext = fileName.substring(dot + 1).lowercase()
                if (ext in EXTENSIONS && base.equals(name, ignoreCase = true)) matches.add(p)
            }
        }
        return when {
            matches.isEmpty() -> {
                player.sendMessage(Component.text("No schematic named '$name' in $DIR/"))
                null
            }
            matches.size > 1 -> {
                val list = matches.joinToString(", ") { it.fileName.toString() }
                player.sendMessage(Component.text("Multiple matches for '$name': $list — include the extension."))
                null
            }
            else -> matches[0]
        }
    }
}
