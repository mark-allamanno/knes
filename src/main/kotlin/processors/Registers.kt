package processors

import kotlin.math.pow

abstract class Register(registerSize: Int) {

    protected val binaryRepresentation = Array(registerSize) { false }

    fun encode(): Int {
        // Iterate over all of the boolean values and use the formula 2 ^ (bool index) to convert the bool to an int and
        // merge it with the resulting value until we are done
        var result = 0
        for (i in binaryRepresentation.indices) {
            val num = if (binaryRepresentation[i]) (2.0).pow(i).toInt() else 0
            result = result or num
        }
        return result
    }

    fun decode(value: Int) {
        // To decode a value we just use a mask of 2 ^ (bool index) to check if the bit is a 1 or a zero
        for (i in binaryRepresentation.indices) {
            val mask = (2.0).pow(i).toInt()
            binaryRepresentation[i] = (value and mask) != 0
        }
    }

    open fun reset() {
        binaryRepresentation.fill(false)    // On reset default is just setting all values to zero
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

class VRamAddress : Register(15) {

    internal var tileX: Int
        get() = encodeRange(0, 4)
        set(value) = decodeRange(5, 0, value)

    internal var tileY: Int
        get() = encodeRange(5, 9)
        set(value) = decodeRange(5, 5, value)

    internal var nameTableX: Int
        get() = encodeRange(10, 10)
        set(value) = decodeRange(1, 10, value)

    internal var nameTableY: Int
        get() = encodeRange(11, 11)
        set(value) = decodeRange(1, 11, value)

    internal var tileScanline: Int
        get() = encodeRange(12, 14)
        set(value) = decodeRange(3, 12, value)

    private fun encodeRange(start: Int, end: Int): Int {
        // WHen we encode a value we iterate over the range specified and convert the boolean values to 2 ^ (bool index)
        // to convert it to a number! We then or this with the result until we finish
        var value = 0
        for (i in start..end) {
            val num = if (binaryRepresentation[i]) 2.0.pow(i).toInt() else 0
            value = value or num
        }
        return (value ushr start)
    }

    fun decodeRange(range: Int, offset: Int, value: Int) {
        // When we decode a range then simply iterate over the first n bits in a byte and check if they are one or zero
        // However since this register is more complicated than the others if we say nameTableY = 1 we actually need to
        // shift that '1' to the 11th bit in the number! So for that we use our offset
        for (i in 0 until range) {
            val mask = 2.0.pow(i).toInt()
            binaryRepresentation[i + offset] = (value and mask) != 0
        }
    }

    operator fun plusAssign(num: Int) {
        decode(encode() + num)      // Define plus assign for easier syntax later
    }
}

class ShiftRegister {

    var patternTableID = 0
    var attributeTableByte = 0
    var patternTableLSB = 0
    var patternTableMSB = 0

    private var patternLSBShift = 0
    private var patternMSBShift = 0
    private var attributeByte = Array(2) { 0 }

    fun shiftRegisterBits() {
        // Just shift all of the registers left by 1 and make sure there is no overflow!
        patternLSBShift = (patternLSBShift shl 1) and 0xffff
        patternMSBShift = (patternMSBShift shl 1) and 0xffff
    }

    fun loadNextTile() {
        // We then shift the attribute bytes forward and merge the lower bits of the shift registers with the new bytes
        attributeByte[0] = attributeByte[1]
        attributeByte[1] = attributeTableByte
        patternLSBShift = (patternLSBShift and 0xff00) or (patternTableLSB and 0xff)
        patternMSBShift = (patternMSBShift and 0xff00) or (patternTableMSB and 0xff)
    }

    fun getCurrentPalette(): Int {
        return attributeByte[0]     // Return the current attribute byte
    }

    fun getCurrentBitPlane(fineX: Int): Int {
        val mask = 0x8000 ushr fineX
        // Using a right shift we use a mask to get the msb and lsb of the but plane from the shift registers
        val lsb = if ((patternLSBShift and mask) != 0) 1 else 0
        val msb = if ((patternMSBShift and mask) != 0) 2 else 0
        return msb or lsb
    }
}