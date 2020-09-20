package utils

import java.io.PrintWriter
import java.io.StringWriter

/*
	We define a master NES Exception class to be able to display exceptions graphically to the end user
	in case something goes wrong. It's children will override the errorMessage variable and then call the
	show message function to be able to be able to show their respective error messages to screen.
*/
abstract class NESException : Exception() {

    abstract val errorMessage: String
    abstract val errorHeader: String

    fun stackTraceString(): String {
        // Create a new string writer and print writer to convert the stack trace to a string
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        // Print the stack trace into the print writer that contains the string writer
        this.printStackTrace(printWriter)
        // Finally return the string conversion of the given stack trace
        return stringWriter.toString()
    }
}

/*
	We then subsequently define the individual exception classes, which are very concise. Only really overriding the
	errorMessage string and then calling the show message function from the super class in their init block. These
	are then throw in places in the code when errors such as these might occur ie in processors.CPU throwing an invalid opcode
	due to it not being found in the opcode table.
*/
class InvalidRomFormat(private val filename: String) : NESException() {

    override val errorMessage: String
        get() = "Cartridge Emulation Error! The file, $filename, lacks the correct NES 2.0 header. Are you sure this is a valid ROM?"

    override val errorHeader: String
        get() = "The specified ROM file does not appear to be in the correct format for the emulation"
}

class IllegalOpcode(private val opcode: Int) : NESException() {

    override val errorMessage: String
        get() = "CPU Emulation Error! The given opcode, ${Integer.toHexString(opcode)}, is undefined for the 6502 processors.CPU"

    override val errorHeader: String
        get() = "The program was given an illegal opcode to execute"
}

class UnsupportedMapper : NESException() {

    override val errorMessage: String
        get() = "The specified ROM file requires an unimplemented mapper to work properly"

    override val errorHeader: String
        get() = "The ROM files requires unimplemented features to be correctly emulated. Check back at a later date"
}