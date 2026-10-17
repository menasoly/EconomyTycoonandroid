package app.many.economytycoon.net

import app.many.economytycoon.core.ByteReader
import app.many.economytycoon.core.ByteWriter
import app.many.economytycoon.persistence.CommandCodec
import app.many.economytycoon.simulation.Command

/** A command read back from the bytes a client sent. */
sealed class ParsedCommand {
    class Ok(val command: Command) : ParsedCommand()
    data class Bad(val reason: String) : ParsedCommand()
}

/**
 * Commands travel as the save format's own encoding, so the wire never needs a second description of every
 * command. Parsing is strict: the bytes must be exactly what the encoder would have written, which rules out
 * trailing data and alternative spellings of the same command.
 */
object CommandPayload {
    fun encode(command: Command): ByteArray {
        val w = ByteWriter()
        CommandCodec.writeCommand(command, w)
        return w.toByteArray()
    }

    fun parse(bytes: ByteArray): ParsedCommand {
        if (bytes.isEmpty()) return ParsedCommand.Bad("empty command")
        if (bytes.size > NetLimits.MAX_COMMAND_BYTES) return ParsedCommand.Bad("command is ${bytes.size} bytes")
        val command = try {
            val r = ByteReader(bytes)
            val c = CommandCodec.readCommand(r)
            if (r.remaining != 0) return ParsedCommand.Bad("${r.remaining} bytes after the command")
            c
        } catch (e: ByteReader.CorruptDataException) {
            return ParsedCommand.Bad(e.message ?: "unreadable command")
        } catch (e: IllegalArgumentException) {
            return ParsedCommand.Bad(e.message ?: "invalid command")
        } catch (e: ArithmeticException) {
            return ParsedCommand.Bad("number out of range")
        }
        if (!encode(command).contentEquals(bytes)) return ParsedCommand.Bad("non-canonical command bytes")
        return ParsedCommand.Ok(command)
    }
}
