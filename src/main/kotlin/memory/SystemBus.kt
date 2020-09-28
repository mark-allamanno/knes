package memory

import memory.Cartridge.Companion.Mirroring
import utils.NES

class SystemBus(private val nes: NES) {

    private var system = ByteArray(0x10000) { 0 }                   // The kb of memory used by the CPU of the NES
    private var graphics = ByteArray(0x4000) { 0 }                  // The 4kb of memory used by the PPU of the NES
    private lateinit var cartridge: Cartridge                           // The cartridge currently inserted into the system
    private val oamData = Array(64) { ByteArray(4) { 0 } }
    private var dmaAddress = 0
    private var dmaLatch = true
    private var dmaReadBuffer = 0
    var spriteDMA = false
    var graphicsChange = false                                          // Lets us know if the sprite memory has been written to
    var nameTableChange = false                                         // Lets us know if the name table memory has been written to

    fun cpuReadHalfWord(address: Int): Int {
        // If no cartridge has been inserted then just return 0
        if (!(::cartridge.isInitialized))
            return 0
        // To get the correct address to write to we first need to make sure it isn't in a static mirror area or mapper area
        val mapped = when (address) {
            in 0x800..0x1fff -> address and 0x7ff
            in 0x8000..0x10000 -> cartridge.mapper.adjustProgramAddress(address)
            else -> address
        }
        // Then we get the low and high bytes of the half word
        val low = system[mapped].toInt() and 0xff
        val high = system[mapped + 1].toInt() and 0xff
        // And then we shift them into place and return the half word
        return (high shl 8) or low
    }

    fun cpuWriteHalfWord(address: Int, value: Int) {
        if (::cartridge.isInitialized) {
            // To get the correct address to write to we first need to make sure it isn't in a static mirror area or mapper area
            val mapped = when (address) {
                in 0x800..0x1fff -> address and 0x7ff
                in 0x8000..0x10000 -> cartridge.mapper.adjustProgramAddress(address)
                else -> address
            }
            // Then we write the half word to that updated address
            system[mapped] = (value ushr 8).toByte()
            system[mapped - 1] = value.toByte()
        }
    }

    fun cpuReadByte(address: Int): Int {
        // To get the correct address to write to we first need to make sure it isn't in a static mirror area or mapper area
        return when (address) {
            in 0x800..0x1fff -> system[address and 0x7ff].toInt() and 0xff
            in 0x2000..0x4000 -> nes.ppu.readRegister(address)
            in 0x4016..0x4017 -> {
                val msb = if (system[address].toInt() and 0x80 != 0) 1 else 0
                system[address] = ((system[address].toInt() shl 1) and 0xff).toByte()
                return msb
            }
            in 0x8000..0x10000 -> {
                val mapped = cartridge.mapper.adjustProgramAddress(address)
                system[mapped].toInt() and 0xff
            }
            else -> system[address].toInt() and 0xff
        }
    }

    fun cpuWriteByte(address: Int, value: Int) {
        when (address) {
            in 0x8000..0x10000 -> {
                val mapped = cartridge.mapper.adjustProgramAddress(address)
                system[mapped] = value.toByte()
            }
            in 0x4014..0x4014 -> {
                dmaAddress = value * 0x100
                spriteDMA = true
            }
            in 0x4016..0x4017 -> system[address] = nes.controllerState.toByte()
            in 0x800..0x1fff -> system[address and 0x7ff] = value.toByte()
            in 0x2000..0x3fff -> nes.ppu.writeRegister(address, value)
            else -> system[address] = value.toByte()
        }
    }

    fun ppuReadByte(address: Int): Int {
        return if (::cartridge.isInitialized) {
            val mapped = when (address) {
                in 0..0x1fff -> cartridge.mapper.adjustCharacterAddress(address)
                in 0x2000..0x3eff -> nameTableAddress(address)
                else -> if ((address and 0xf) % 4 == 0) 0x3f00 else address and 0x3f1f
            }
            graphics[mapped].toInt() and 0xff
        } else 0
    }

    fun ppuWriteByte(address: Int, value: Int) {
        if (::cartridge.isInitialized) {
            when (address and 0x3fff) {
                in 0..0x1fff -> {
                    graphicsChange = true
                    val mapped = cartridge.mapper.adjustCharacterAddress(address)
                    graphics[mapped] = value.toByte()
                }
                in 0x3f00..0x4000 -> {
                    graphicsChange = true
                    val mapped = if (address == 0x3f10) 0x3f00 else 0x3f00 + (address and 0x1f)
                    graphics[mapped] = value.toByte()
                }
                in 0x2000..0x3eff -> {
                    nameTableChange = true
                    val mapped = nameTableAddress(address)
                    graphics[mapped] = value.toByte()
                }
            }
        }
    }

    fun performDMA() {
        // On the read cycle of the dma latch we just read the data pointed to byte the dmaAddress into the buffer and increment it
        if (dmaLatch) {
            dmaReadBuffer = system[dmaAddress].toInt() and 0xff
            dmaAddress++
        }
        // On the write cycle we need to first reduce the actual address to just 0xff which we do with the modulo 0x100
        // We then integer divide by 4 to get which tile we are reading into and then use modulo 4 to get which attrubute
        // of the tile we are reading!
        else {
            val dmaProgress = dmaAddress % 0x100
            oamData[dmaProgress / 4][dmaProgress % 4] = dmaReadBuffer.toByte()
        }
        // We always flip the latch after a read/write and the process is over when we have written all 256 bytes
        // which means we are on 0xff of a page and are on the write stage (false) of the latch
        dmaLatch = !dmaLatch
        spriteDMA = (dmaAddress and 0xff) < 0xff || !dmaLatch
    }

    // How backgrounds are mirrored on the NES -> https://wiki.nesdev.com/w/index.php/PPU_nametables
    private fun nameTableAddress(address: Int): Int {
        // If the mirror mode is horizontal then we need to mirror the name tables regions horizontally
        return if (cartridge.mirrorMode == Mirroring.HORIZONTAL) {
            if (address in 0x2000..0x27ff)
                0x2000 + (address and 0x3ff)
            else
                0x2800 + (address and 0x3ff)
        }
        // If the mirror mode is vertical then we need to mirror the name table regions vertically
        else {
            if (address in 0x2000..0x23ff || address in 0x2800..0x2bff)
                0x2000 + (address and 0x3ff)
            else
                0x2400 + (address and 0x3ff)
        }
    }

    fun insertCartridge(cartridge: Cartridge) {
        // Then assign the new cartridge to the current one
        this.cartridge = cartridge
        // Fill both of the NES' RAM buses with 0's
        system.fill(0)
        graphics.fill(0)
    }
}
