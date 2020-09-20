package memory

import memory.Cartridge.Companion.Mirroring
import utils.NES

class SystemBus(private val nes: NES) {

    private var system = ByteArray(0x10000) { 0 }           // The kb of memory used by the CPU of the NES
    private var graphics = ByteArray(0x4000) { 0 }          // The 4kb of memory used by the PPU of the NES
    private lateinit var cartridge: Cartridge                   // The cartridge currently inserted into the system
    var graphicsChange = false                                  // Lets us know if the sprite memory has been written to
    var nameTableChange = false                                 // Lets us know if the name table memory has been written to

    fun cpuReadHalfWord(address: Int): Int {
        // If no cartridge has been inserted then just return 0
        if (!(::cartridge.isInitialized))
            return 0
        // To get the correct address to write to we first need to make sure it isn't in a static mirror area or mapper area
        val mapped = when (address) {
            in 0x8000..0x10000 -> cartridge.mapper.adjustProgramAddress(address)
            in 0x800..0x1fff -> address and 0x7ff
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
                in 0x8000..0x10000 -> cartridge.mapper.adjustProgramAddress(address)
                in 0x800..0x1fff -> address and 0x7ff
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
            in 0x8000..0x10000 -> {
                val mapped = cartridge.mapper.adjustProgramAddress(address)
                system[mapped].toInt() and 0xff
            }
            in 0x800..0x1fff -> system[address and 0x7ff].toInt() and 0xff
            in 0x2000..0x4000 -> nes.ppu.readRegister(address)
            else -> system[address].toInt() and 0xff
        }
    }

    fun cpuWriteByte(address: Int, value: Int) {
        // To get the correct address to write to we first need to make sure it isn't in a static mirror area or mapper area
        when (address) {
            in 0x8000..0x10000 -> {
                val mapped = cartridge.mapper.adjustProgramAddress(address)
                system[mapped] = value.toByte()
            }
            in 0x800..0x1fff -> system[address and 0x7ff] = value.toByte()
            in 0x2000..0x4000 -> nes.ppu.writeRegister(address, value)
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
                    val mapped = 0x3f00 + (address and 0x1f)
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

    private fun nameTableAddress(address: Int): Int {
        // If the mirror mode is horizontal then we need to mirror the name tables regions horizontally
        return if (cartridge.mirrorMode == Mirroring.HORIZONTAL) {
            if (address in 0x2000..0x27ff)
                0x2000 + (address and 0x7ff)
            else
                0x2800 + (address and 0x7ff)
        }
        // If the mirror mode is vertical then we need to mirror the name table regions vertically
        else {
            if (address in 0x2000..0x23ff || address in 0x2800..0x2bff)
                0x2000 + (address and 0x7ff)
            else
                0x2400 + (address and 0x7ff)
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
