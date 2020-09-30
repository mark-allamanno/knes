package processors

import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import memory.SystemBus
import utils.NES
import kotlin.math.pow


class PPU(private val graphics: SystemBus) {

    private val mask = MaskRegister()
    private val status = StatusRegisterPPU()
    val control = ControlRegister()

    private var vRamAddress = VRamAddress()
    private var tRamAddress = VRamAddress()
    private var shiftRegister = ShiftRegister()
    private var oamAddress = 0

    private var tilePixel = 0
    private var writeLatch = true
    private var readBuffer = 0

    private var scanline = 261
    private var scanlinePixel = 1

    var emitNMI = false
    var frame = WritableImage(256, 240)

    fun emulateCycle() {
        if (scanline in 0 until 240 || scanline == 261) {
            // On every visible scanline and the pre-render scanline load the new name
            // table info and check the loopy registers
            fetchNameTableInfo()
            updateLoopyRegisters()
        }
        // Then draw the current pixel of the shift register and progress the scanline counters
        drawFramePixel()
        progressScanline()
    }

    // All of the bitwise stuff in this section is lifted from the wiki: https://wiki.nesdev.com/w/index.php/PPU_scrolling
    private fun fetchNameTableInfo() {
        // Make sure we are wither in a visible scanline or fetching the first two tiles for the next frame
        if (scanlinePixel in 1 until 256 || scanlinePixel in 321 until 337) {
            // Always shift the registers when we do this
            shiftRegister.shiftRegisterBits()
            // A tile is 8 pixels wide and we are using NTSC timings for rendering so we need to check what pixel
            // we are on in that tile
            when (scanlinePixel % 8) {

                0 -> {
                    // If we are on a multiple of 8 then we just started a new tile so increment the tile pointer
                    if (scanlinePixel != 0 && (mask.showBackground || mask.showSprites))
                        vRamAddress.tileX++
                    // If the tile pointer has crossed the name table boundary then let us know and reset the pointer
                    if (31 < vRamAddress.tileX) {
                        vRamAddress.tileX = 0
                        vRamAddress.nameTableX = if (vRamAddress.nameTableX == 0) 1 else 0
                    }
                }

                1 -> {
                    // If we are on pixel 1 of the tile then we need to get the location of the pattern table we are rendering
                    shiftRegister.patternTableID = graphics.ppuReadByte(0x2000 + (vRamAddress.encode() and 0xfff))
                }

                // Attribute tables explained in greater detail here: https://wiki.nesdev.com/w/index.php/PPU_attribute_tables
                3 -> {
                    val vRam = vRamAddress.encode()
                    val attribute = 0x23c0 or (vRam and 0xc00) or ((vRam ushr 4) and 0x38) or ((vRam ushr 2) and 0x7)
                    // We are rendering the upper left tile, so we use bits 0-1 to get the palette
                    if (vRamAddress.tileX % 4 < 2 && vRamAddress.tileY % 4 < 2)
                            shiftRegister.attributeTableByte = graphics.ppuReadByte(attribute) and 0x3
                    // We are rendering the upper right tile, so we use bits 2-3 to get the palette
                    else if (vRamAddress.tileX % 4 >= 2 && vRamAddress.tileY % 4 < 2)
                        shiftRegister.attributeTableByte = (graphics.ppuReadByte(attribute) and 0xc) ushr 2
                    // We are rendering the lower left tile, so we use bits 4-5 to get the palette
                    else if (vRamAddress.tileX % 4 < 2 && vRamAddress.tileY % 4 >= 2)
                        shiftRegister.attributeTableByte = (graphics.ppuReadByte(attribute) and 0x30) ushr 4
                    // We are rendering the lower right tile, so we use bits 6-7 to get the palette
                    else if(vRamAddress.tileX % 4 >= 2 && vRamAddress.tileY % 4 >= 2)
                        shiftRegister.attributeTableByte = (graphics.ppuReadByte(attribute) and 0xc0) ushr 6
                }

                5 -> {
                    // If we are on pixel 5 then we need to get the least significant byte from the pattern table scanline
                    val backgroundOffset = if (control.backgroundTable) 4096 else 0
                    shiftRegister.patternTableLSB = graphics.ppuReadByte(backgroundOffset +
                            (shiftRegister.patternTableID * 16) + vRamAddress.tileScanline)
                }

                7 -> {
                    // If we are on pixel 7 then we need to get the most significant byte from the pattern table scanline
                    val backgroundOffset = if (control.backgroundTable) 4096 else 0
                    shiftRegister.patternTableMSB = graphics.ppuReadByte(backgroundOffset +
                            (shiftRegister.patternTableID * 16) + vRamAddress.tileScanline + 8)
                    // Then we are done loading this tile's scanline and can load it into the register
                    shiftRegister.loadNextTile()
                }
            }
        }
    }

    private fun updateLoopyRegisters() {
        // If we are on pixel 256 of a scanline then NTSC tells us we need to increment the vertical component of the
        // loopy register
        if (scanlinePixel == 256 && (mask.showBackground || mask.showSprites)) {
            // If we are on the 7th scanline of a tile then we have finished that tile
            if (7 == vRamAddress.tileScanline) {
                // So we move to the next tile in the name table and reset the scanline
                vRamAddress.tileY++
                vRamAddress.tileScanline = 0
                // However if we are moving to another name table then reset the tileY and flip the name table
                if (vRamAddress.tileY == 30) {
                    vRamAddress.tileY = 0
                    vRamAddress.nameTableY = if (vRamAddress.nameTableY == 0) 1 else 0
                }
                // If we are in the attribute table reset tileY but do not switch name table bit
                else if (vRamAddress.tileY == 32) {
                    vRamAddress.tileY = 0
                }
            }
            // Otherwise we are still using this tile on the next scanline
            else {
                vRamAddress.tileScanline++
            }
        }
        // If we are on scanline 257 then we need to 'scroll' the current address pointer to the temp one that has
        // changed but only in the x direction
        if (scanlinePixel == 257) {
            if (mask.showBackground || mask.showSprites) {
                vRamAddress.nameTableX = tRamAddress.nameTableX
                vRamAddress.tileX = tRamAddress.tileX
            }
        }
        // If we are on the pre-render scanline then we need to 'scroll' the current address pointer to the temp one
        // that has changed, now in the y direction
        if (scanline == 261 && scanlinePixel in 280 until 305 && (mask.showBackground || mask.showSprites)) {
            vRamAddress.nameTableY = tRamAddress.nameTableY
            vRamAddress.tileY = tRamAddress.tileY
            vRamAddress.tileScanline = tRamAddress.tileScanline
        }
    }

    private fun drawFramePixel() {
        if (scanlinePixel in 0 until 256 && scanline in 0 until 240) {
            // If we are on the visible part of the frame and rendering background then draw the background pixel
            if (mask.showBackground) {
                // Do this by first getting the palette and pixel from the shift register
                val palette = shiftRegister.getCurrentPalette()
                val bitPlane = shiftRegister.getCurrentBitPlane(tilePixel)
                // We then plug those into the nes palette to get our color and set the pixel!
                val (r, g, b) = NES.palette[graphics.ppuReadPalette(palette, bitPlane)] ?: Triple(0, 0, 0)
                frame.pixelWriter.setColor(scanlinePixel, scanline, Color.rgb(r, g, b))
            }
        }
    }

    private fun progressScanline() {
        // We have entered the vertical blank, so let the CPU know. Optionally we can emit an NMI if it is enabled
        if (scanline == 241 && scanlinePixel == 1) {
            status.vBlank = true
            emitNMI = control.enableNMI
        }
        // We have exited the vertical blank so let the CPU know
        else if (scanline == 261 && scanlinePixel == 1) {
            status.vBlank = false
        }
        // If we are at pixel 341 in a row then we are done with that scanline
        if (scanlinePixel == 341) {
            // Increment the scanline and reset the pixel counter
            scanline++
            scanlinePixel = -1
            // If we are at scanline 262 then we are at the prerender scanline
            if (scanline == 262)
                scanline = 0
        }
        // Always increment the scanline pixel
        scanlinePixel++
    }

    fun writeRegister(address: Int, value: Int) {
        // When we write to an address $2000-$2007 then we are actually writing to the PPU registers
        when (address % 8) {

            0 -> {
                // We first decode the value into the control register and then set the name table bits in the loopy
                // t register
                control.decode(value)
                tRamAddress.nameTableX = (value and 0x1)
                tRamAddress.nameTableY = (value and 0x2) ushr 1
            }

            1 -> mask.decode(value)
            3 ->  oamAddress = value and 0xff
            4 -> graphics.ppuWriteOAM(oamAddress, value)

            5 -> {
                if (writeLatch) {
                    // If we are on the first write of $2005 then we set the x location of the tile and the pixel in
                    // that tile to start with
                    tRamAddress.tileX = (value and 0xf8) ushr 3
                    tilePixel = (value and 0x7)
                }
                else {
                    // On the second write we set the y location of the tile and the scanline we are at on that tile
                    tRamAddress.tileY = (value and 0xf8) ushr 3
                    tRamAddress.tileScanline = (value and 0x7)
                }
                // Always invert the write latch after an operation
                writeLatch = !writeLatch
            }

            6 -> {
                if (writeLatch) {
                    // On the first write to $2006 we just decode the high 6 bits of the value into the loopy t register
                    tRamAddress.decodeRange(6, 8, value and 0x3f)
                }
                else {
                    // On the second write though we decode the lower 8 bits into the register and then load the loopy
                    // v register with the loopy t register
                    tRamAddress.decodeRange(8, 0, value and 0xff)
                    vRamAddress.decode(tRamAddress.encode())
                }
                // Always invert the write latch after an operation
                writeLatch = !writeLatch
            }

            7 -> {
                // When the CPU writes to $2007 then we actually just write to the graphics RAM the value at the v
                // ram address and increment the v register by an amount decoded by the control register
                graphics.ppuWriteByte(vRamAddress.encode(), value)
                vRamAddress += if (control.vramIncrement) 32 else 1
            }
        }
    }

    fun readRegister(address: Int): Int {
        // We are reading from $2000-$2007 so we need ot return the values of the registers that can be read from
        return when (address and 0x7) {

            2 -> {
                // When reading from #2002 we bit the high bits in and fill the low 5 with noise (apparently factual)
                val state = (status.encode() and 0xe0) or (readBuffer and 0x1f)
                // Reading also resets from flags
                status.vBlank = false
                writeLatch = true
                return state
            }

            4 -> return graphics.ppuReadOAM(oamAddress)

            7 -> {
                // A read from the PPU actually tasks 2 CPU cycles so we need to buffer then in a variable and then
                // increment the loopy v register
                val value = readBuffer
                readBuffer = graphics.ppuReadByte(vRamAddress.encode())
                vRamAddress += if (control.vramIncrement) 32 else 1
                return value
            }
            // Otherwise just return 0 as it is an illegal access
            else -> 0
        }
    }

    // How pattern tables are stored in memory -> https://wiki.nesdev.com/w/index.php/PPU_pattern_tables
    fun readPatternTable(address: Int): Array<Array<Int>> {
        // Create the arrays for the msb, lsb, and resulting bit planes
        val bitPlaneLSB = Array(8) { graphics.ppuReadByte(address + it) }
        val bitPlaneMSB = Array(8) { graphics.ppuReadByte(address + it + 8) }
        val result = Array(8) { Array(8) { 0 } }
        // Then iterate over every bit of every byte to combine the lsb and msb
        for (byte in 0 until 8) {
            for (bit in 0 until 8) {
                // Use a power of 2 to get each bit in the byte
                val mask = 2.0.pow(bit).toInt()
                // Then grab the lsb and msb from the byte and shift them over to the 1's place
                val lsb = (bitPlaneLSB[byte] and mask) ushr bit
                val msb = (bitPlaneMSB[byte] and mask) ushr bit
                // Finally combine the lsb and msb together and place then in the resulting array
                result[byte][7 - bit] = (msb shl 1) or lsb
            }
        }
        return result       // Then return the resulting 8x8 array
    }

    fun reset() {
        // Reset all of the registers for the PPU
        control.reset()
        mask.reset()
        status.reset()
        // Reset the vram address pointers
        vRamAddress = VRamAddress()
        tRamAddress = VRamAddress()
        // Reset the read buffer and the write latch
        readBuffer = 0
        writeLatch = true
        // Finally reset the scanline and pixel counters
        scanline = 261
        scanlinePixel = -1
    }

    fun invertHorizontal(invertRow: Int): Int {

        var result = 0
        for (bit in 0 until 4) {

            val lowBitInvert = (invertRow and 2.0.pow(bit).toInt()) shl (7 - (bit * 2))
            val highBitInvert = (invertRow and 2.0.pow(7-bit).toInt()) ushr (7 - (bit * 2))
            
            result = result or highBitInvert or lowBitInvert
        }
        return result
    }


}