package mappers

import memory.Cartridge

class NRom(cartridge: Cartridge) : Mapper {

    private val oneBankRom = cartridge.programRomBanks == 1     // Lets use know if we need to mirror addresses down

    // For NRom there is nothing special to do with the character rom space, so let it be
    override fun adjustCharacterAddress(address: Int): Int {
        return address and 0x1fff
    }

    // For NRom if there is only one bank of rom active then we need to mirror the addresses down to 0x8000-0xc000
    override fun adjustProgramAddress(address: Int): Int {
        return if (oneBankRom) 0x8000 + (address and 0x3fff) else address
    }
}