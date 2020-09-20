package utils

import memory.Cartridge
import java.io.File
import java.lang.Integer.toHexString
import java.util.logging.FileHandler
import java.util.logging.Formatter
import java.util.logging.LogRecord
import java.util.logging.Logger

class Logger(private val nes: NES) {

    private val logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME)        // Get the default system logger
    private val fileHandler = FileHandler("knes.log")                // Get the file handler for the system logger

    init {
        // Add the file handler, formatter to the logger and dont dont use parent handlers
        logger.addHandler(fileHandler)
        fileHandler.formatter = NESLogFormatter()
        logger.useParentHandlers = false
    }

    fun logSystemState() {
        logger.info {
            "${toHexString(nes.cpu.pc)} ${toHexString(nes.cpu.op)} ${operandsToString(nes.cpu.operands)}    " +
                    "${nes.cpu.opcode.operation.toString().slice(10 until 13)} -> " +
                    "${nes.cpu.opcode.address.toString().slice(10 until 13)}    " +
                    "A:${toHexString(nes.cpu.regA)} X:${toHexString(nes.cpu.regX)} " +
                    "Y:${toHexString(nes.cpu.regY)} P:${toHexString(nes.cpu.status.encode())} " +
                    "SP:${(toHexString(nes.cpu.sp))} CYC:${nes.cpu.totalCycles}"
        }
    }

    fun logCartridgeChange(cartridge: Cartridge) {
        // Get the index of the last back slash to be able to get only the rom name and add a log entry for the change
        val path = cartridge.filename.split(File.separator)
        logger.info {
            "Cartridge Inserted: ${path.last()}. Starting Emulation..."
        }
    }

    private fun operandsToString(operands: Array<Int>): String {
        // Make a new string builder and append the operands to it
        val builder = StringBuilder()
        for (int in operands)
            builder.append(" ${toHexString(int)}")
        // If the operands are shorter then 5 characters ie the opcode has less then 2 operands then add spaces to align columns
        for (spacer in 0..(5 - builder.length))
            builder.append(" ")
        // Then return the new string object without the initial space
        return builder.toString().drop(1)
    }

    class NESLogFormatter : Formatter() {
        // Override the format method for the logger to uppercase the letters and add a double newline after an entry
        override fun format(record: LogRecord): String {
            return record.message.toUpperCase() + "\n\n"
        }
    }
}