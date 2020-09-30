package memory

import mappers.Mapper
import mappers.NRom
import utils.InvalidRomFormat
import utils.UnsupportedMapper
import java.io.File

class Cartridge(val filename: String, private val system: SystemBus) {

    private val romData: ByteArray                  // The byte array of the raw rom data
    private val header: IntArray                    // The first 16 bytes of the rom data that hold important info about the rom
    val programRomBanks: Int                        // The number of program rom banks specified in the header
    val characterRomBanks: Int                      // The number of character rom banks specified in the header
    val trainerPresent: Boolean                     // Boolean value to tell if a trainer is present in the rom
    val mirrorMode: Mirroring                       // The mirroring mode that the cartridge uses for its name tables
    val mapper: Mapper                              // The mapper used to interpret the actual addresses given to the CPU

    init {
        // Get the un-parsed rom date and the header (first 16 bytes) for the file
        romData = File(filename).inputStream().readAllBytes()
        header = if (romData.isNotEmpty()) IntArray(16) { romData[it].toInt() } else IntArray(16) { 0 }
        // Then make sure that the file we have loaded is a valid one
        if ("${header[0].toChar()}${header[1].toChar()}${header[2].toChar()}" != "NES" || header[3] != 0x1a)
            throw InvalidRomFormat(filename)
        // After we get the header then get the program and character rom size from the header
        programRomBanks = (header[9] and 0xf shl 4) or header[4]
        characterRomBanks = (header[9] and 0xf0 shl 4) or header[5]
        trainerPresent = (header[6] and 0x4) == 1
        mirrorMode = if ((header[6] and 0x1) == 0) Mirroring.HORIZONTAL else Mirroring.VERTICAL
        // Then we instanciate the mapper for the given game before loading the rom
        mapper = readMapper()
    }

    companion object {
        enum class Mirroring {
            HORIZONTAL, VERTICAL
        }
    }

    fun loadToBuses() {
        // Load the trainer into memory if it is present in the ROM file
        val offsetTrainer = if (trainerPresent) 16 else 512
        for (i in offsetTrainer until 512)
            system.cpuWriteByte(0x7000 + i, romData[offsetTrainer + i].toInt())
        // Load the program rom into memory, initial offset in the raw data is dependent on if a trainer is present
        val offsetProgram = if (trainerPresent) 512 else 16
        for (i in 0 until programRomBanks * 16384)
            system.cpuWriteByte(i + 0x8000, romData[offsetProgram + i].toInt())
        // Load the character rom into memory, initial offset is given by the number of program rom banks and trainer
        val offsetCharacter = (programRomBanks * 16384) + offsetProgram
        for (i in 0 until 8192)
            system.ppuWriteByte(i, romData[offsetCharacter + i].toInt())
    }

    private fun readMapper(): Mapper {
        // Get the ID number of the mapper for the ROM and attempt to find a working mapper to match to
        val mapperID = (header[6] and 0xff00) or (header[7] and 0xff00 shl 4) or (header[8] and 0xff shl 8)
        return when (mapperID) {
            0 -> NRom(this)
            else -> throw UnsupportedMapper()
        }
    }
}