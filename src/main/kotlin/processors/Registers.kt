package processors

import kotlin.math.pow

abstract class Register(registerSize: Int) {

    protected val binaryRepresentation = Array(registerSize) { false }

    fun encode(): Int {
        var result = 0
        for (i in binaryRepresentation.indices) {
            val num = if (binaryRepresentation[i]) (2.0).pow(i).toInt() else 0
            result = result or num
        }
        // The binary encoding of the registers flags
        return result
    }

    fun decode(value: Int) {
        for (i in binaryRepresentation.indices) {
            // Then use the index to get the corresponding mask and if that but is set than set the field
            val mask = (2.0).pow(i).toInt()
            binaryRepresentation[i] = (value and mask) != 0
        }
    }

    open fun reset() {
        binaryRepresentation.fill(false)
    }
}

class StatusRegisterCPU : Register(8) {

    // When the status register is powered on all flags but the break and interrupt are set to false
    internal var carry: Boolean
        get() = binaryRepresentation[0]
        set(value) {
            binaryRepresentation[0] = value
        }

    internal var zero: Boolean
        get() = binaryRepresentation[1]
        set(value) {
            binaryRepresentation[1] = value
        }

    internal var interruptDisable: Boolean
        get() = binaryRepresentation[2]
        set(value) {
            binaryRepresentation[2] = value
        }

    internal var decimal: Boolean
        get() = binaryRepresentation[3]
        set(value) {
            binaryRepresentation[3] = value
        }

    internal var breakCommand: Boolean
        get() = binaryRepresentation[5]
        set(value) {
            binaryRepresentation[5] = value
        }

    internal var overflow: Boolean
        get() = binaryRepresentation[6]
        set(value) {
            binaryRepresentation[6] = value
        }

    internal var negative: Boolean
        get() = binaryRepresentation[7]
        set(value) {
            binaryRepresentation[7] = value
        }

    override fun reset() {
        super.reset()
        // When the status register is reset all flags but the break and interrupt are set to false
        interruptDisable = true
        breakCommand = true
    }
}

class ControlRegister : Register(8) {

    internal var nameTableLSB: Boolean
        get() = binaryRepresentation[0]
        set(value) {
            binaryRepresentation[0] = value
        }

    internal var nameTableMSB: Boolean
        get() = binaryRepresentation[1]
        set(value) {
            binaryRepresentation[1] = value
        }

    internal var vramIncrement: Boolean
        get() = binaryRepresentation[2]
        set(value) {
            binaryRepresentation[2] = value
        }

    internal var spriteTable: Boolean
        get() = binaryRepresentation[3]
        set(value) {
            binaryRepresentation[3] = value
        }

    internal var backgroundTable: Boolean
        get() = binaryRepresentation[4]
        set(value) {
            binaryRepresentation[4] = value
        }

    internal var spriteSize: Boolean
        get() = binaryRepresentation[5]
        set(value) {
            binaryRepresentation[5] = value
        }

    internal var slaveSelect: Boolean
        get() = binaryRepresentation[6]
        set(value) {
            binaryRepresentation[6] = value
        }

    internal var enableNMI: Boolean
        get() = binaryRepresentation[7]
        set(value) {
            binaryRepresentation[7] = value
        }

    fun baseNameTableAddress(): Int {

        val lsb = if (nameTableLSB) 1 else 0
        val msb = if (nameTableMSB) 2 else 0

        return when (msb + lsb) {
            0 -> 0x2000
            1 -> 0x2400
            2 -> 0x2800
            else -> 0x2c00
        }
    }
}

class MaskRegister : Register(8) {

    internal var greyscale: Boolean
        get() = binaryRepresentation[0]
        set(value) {
            binaryRepresentation[0] = value
        }

    internal var leftBackground: Boolean
        get() = binaryRepresentation[1]
        set(value) {
            binaryRepresentation[1] = value
        }

    internal var leftSprites: Boolean
        get() = binaryRepresentation[2]
        set(value) {
            binaryRepresentation[2] = value
        }

    internal var showBackground: Boolean
        get() = binaryRepresentation[3]
        set(value) {
            binaryRepresentation[3] = value
        }

    internal var showSprites: Boolean
        get() = binaryRepresentation[4]
        set(value) {
            binaryRepresentation[4] = value
        }

    internal var red: Boolean
        get() = binaryRepresentation[5]
        set(value) {
            binaryRepresentation[5] = value
        }

    internal var green: Boolean
        get() = binaryRepresentation[6]
        set(value) {
            binaryRepresentation[6] = value
        }

    internal var blue: Boolean
        get() = binaryRepresentation[7]
        set(value) {
            binaryRepresentation[7] = value
        }
}

class StatusRegisterPPU : Register(8) {

    internal var spriteOverflow: Boolean
        get() = binaryRepresentation[5]
        set(value) {
            binaryRepresentation[5] = value
        }

    internal var spriteZeroHit: Boolean
        get() = binaryRepresentation[6]
        set(value) {
            binaryRepresentation[6] = value
        }

    internal var vBlank: Boolean
        get() = binaryRepresentation[7]
        set(value) {
            binaryRepresentation[7] = value
        }
}

class VRamAddress : Register(16) {

    internal var courseX: Int
        get() = encodeRange(0, 4)
        set(value) = decodeRange(0, 4, value)

    internal var courseY: Int
        get() = encodeRange(5, 9) ushr 5
        set(value) = decodeRange(5, 9, value)

    internal var nameTableX: Int
        get() = encodeRange(10, 10) ushr 10
        set(value) = decodeRange(10, 10, value)

    internal var nameTableY: Int
        get() = encodeRange(11, 11) ushr 11
        set(value) = decodeRange(11, 11, value)

    internal var fineY: Int
        get() = encodeRange(12, 14) ushr 12
        set(value) = decodeRange(12, 14, value)

    private fun encodeRange(start: Int, end: Int): Int {
        var value = 0
        for (i in start..end) {
            val num = if (binaryRepresentation[i]) 2.0.pow(i).toInt() else 0
            value = value or num
        }
        return value
    }

    fun decodeRange(start: Int, end: Int, value: Int) {
        for (i in start..end) {
            val mask = 2.0.pow(i).toInt()
            binaryRepresentation[i] = (value and mask) != 0
        }
    }

    operator fun plusAssign(num: Int) {
        decode(encode() + num)
    }
}