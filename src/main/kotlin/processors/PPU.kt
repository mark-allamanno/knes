package processors

import javafx.scene.image.WritableImage
import memory.SystemBus
import utils.NES
import kotlin.math.pow


class PPU(private val graphics: SystemBus, private val nes: NES) {

    val control = ControlRegister()
    private val mask = MaskRegister()
    private val status = StatusRegisterPPU()
    private var vRamAddress = VRamAddress()
    private var tRamAddress = VRamAddress()
    private var fineXScroll = 0
    private var writeLatch = true
    private var readBuffer = 0
    private var scanline = -1
    private var scanlinePixel = 1
    var emitNMI = false
    var frame = WritableImage(256, 240)

    fun emulateCycle() {
        // We have entered the vertical blank, so let the CPU know. Optionally we can emit an NMI if it is enabled
        if (scanline == 241 && scanlinePixel == 1) {
            status.vBlank = true
            emitNMI = control.enableNMI
        }
        // We have exited the vertical blank so let the CPU know
        else if (scanline == 261 && scanlinePixel == 1)
            status.vBlank = false

        if (scanlinePixel == 341) {

            scanline++
            scanlinePixel = -1

            if (scanline == 262)
                scanline = -1
        }

        scanlinePixel++
    }

    fun writeRegister(address: Int, value: Int) {
        when (address and 0x7) {

            0 -> {
                control.decode(value)
                tRamAddress.nameTableX = (value and 0x1)
                tRamAddress.nameTableY = (value and 0x2) ushr 1
            }

            1 -> mask.decode(value)

            5 -> {
                if (writeLatch) {
                    tRamAddress.courseX = (value ushr 3) and 0xff
                    fineXScroll = (value and 0x7)
                } else {
                    tRamAddress.fineY = (value and 0x7)
                    tRamAddress.courseY = (value and 0xf8) ushr 3
                }

                writeLatch = !writeLatch
            }

            6 -> {
                if (writeLatch) {
                    tRamAddress.decodeRange(8, 15, (value and 0x3f) shl 8)
                } else {
                    tRamAddress.decodeRange(0, 7, value and 0xff)
                    vRamAddress.decode(tRamAddress.encode())
                }

                writeLatch = !writeLatch
            }

            7 -> {
                graphics.ppuWriteByte(vRamAddress.encode(), value)
                vRamAddress += if (control.vramIncrement) 32 else 1
            }
        }
    }

    fun readRegister(address: Int): Int {
        return when (address and 0x7) {

            2 -> {
                // When reading from #2002 we
                val state = (status.encode() and 0xe0) or (readBuffer and 0x1f)
                status.vBlank = false
                writeLatch = true
                return state
            }

            7 -> {
                val value = readBuffer
                readBuffer = graphics.ppuReadByte(vRamAddress.encode())
                vRamAddress += if (control.vramIncrement) 32 else 1
                return value
            }

            else -> 0
        }
    }

    fun readPatternTable(address: Int): Array<Array<Int>> {
        // Create the arrays for the msb, lsb, and resulting bit planes
        val bitPlaneLSB = Array(8) { graphics.ppuReadByte(address + it) }
        val bitPlaneMSB = Array(8) { graphics.ppuReadByte(address + it + 8) }
        val result = Array(8) { Array(8) { 0 } }
        // Then iterate over every bit of every byte to combine the lsb and msb
        for (i in 0 until 8) {
            for (b in 0 until 8) {
                // Use a power of 2 to get each bit in the byte
                val mask = 2.0.pow(b).toInt()
                // Then grab the lsb and msb from the byte and shift them over to the 1's place
                val lsb = (bitPlaneLSB[i] and mask) ushr b
                val msb = (bitPlaneMSB[i] and mask) ushr b
                // Finally combine the lsb and msb together and place then in the resulting array
                result[i][7 - b] = (msb shl 1) or lsb
            }
        }
        return result       // Then return the resulting 8x8 array
    }

    fun readPalette(palette: Int, pixel: Int): Int {
        return graphics.ppuReadByte(0x3f00 + (4 * palette) + pixel)
    }

    fun reset() {

        control.reset()
        mask.reset()
        status.reset()

        vRamAddress = VRamAddress()
        tRamAddress = VRamAddress()
        readBuffer = 0
        writeLatch = true

        scanline = -1
        scanlinePixel = 1
    }
}