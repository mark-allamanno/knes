package processors

import memory.SystemBus
import utils.IllegalOpcode

class CPU(private val system: SystemBus) {

    // Data class to store functions pointers and info about an opcode
    data class Opcode(val operation: () -> Unit, val address: () -> Int?, val cycles: Int)

    var regA = 0                                // The accumulator register
    var regX = 0                                // The special x register
    var regY = 0                                // The special y register

    var sp = 0xfd                               // The stack pointer
    var pc = 0                                  // The program counter
    var opcodeKey = 0                           // The raw opcode data, an unsigned byte

    var stall = 0                               // The amount of cycle to do no action to sync to PPU
    var totalCycles = 0                         // The total cycles we have emulated

    var address: Int? = 0                       // The current address given by the opcodes addressing mode
    var opcodeLength = 0                        // The length of the opcode anywhere from 1-3
    var pageCrossed = false                     // Lets us know if a page was crossed during the operation

    var operands = Array(0) { 0 }           // The operands of the current opcode
    lateinit var opcode: Opcode                 // The opcode data class that holds the addressing mode, function pointer, and cycles

    val status = StatusRegisterCPU()            // The status register that sets flags about the CPU operation

    // A short array of functions that require an extra cycle if a page is crossed
    private val extraCycles = arrayOf(this::adc, this::and, this::cmp, this::eor, this::lda, this::ldx,
            this::ldy, this::ora, this::sbc
    )

    // A map that links all opcode bytes to their data class counter parts
    private val opcodeMap = mapOf(
            0x69 to Opcode(this::adc, this::imm, 2),
            0x65 to Opcode(this::adc, this::zpi, 3),
            0x75 to Opcode(this::adc, this::zpx, 4),
            0x6d to Opcode(this::adc, this::abs, 4),
            0x7d to Opcode(this::adc, this::abx, 4),
            0x79 to Opcode(this::adc, this::aby, 4),
            0x61 to Opcode(this::adc, this::idx, 6),
            0x71 to Opcode(this::adc, this::idy, 5),

            0x29 to Opcode(this::and, this::imm, 2),
            0x25 to Opcode(this::and, this::zpi, 3),
            0x35 to Opcode(this::and, this::zpx, 4),
            0x2d to Opcode(this::and, this::abs, 4),
            0x3d to Opcode(this::and, this::abx, 4),
            0x39 to Opcode(this::and, this::aby, 4),
            0x21 to Opcode(this::and, this::idx, 6),
            0x31 to Opcode(this::and, this::idy, 5),

            0x0a to Opcode(this::asl, this::acc, 2),
            0x06 to Opcode(this::asl, this::zpi, 5),
            0x16 to Opcode(this::asl, this::zpx, 6),
            0x0e to Opcode(this::asl, this::abs, 6),
            0x1e to Opcode(this::asl, this::abx, 7),

            0x90 to Opcode(this::bcc, this::rel, 2),
            0xb0 to Opcode(this::bcs, this::rel, 2),
            0xf0 to Opcode(this::beq, this::rel, 2),

            0x24 to Opcode(this::bit, this::zpi, 3),
            0x2c to Opcode(this::bit, this::abs, 4),

            0x30 to Opcode(this::bmi, this::rel, 2),
            0xd0 to Opcode(this::bne, this::rel, 2),
            0x10 to Opcode(this::bpl, this::rel, 2),

            0x00 to Opcode(this::brk, this::imp, 7),

            0x50 to Opcode(this::bvc, this::rel, 2),
            0x70 to Opcode(this::bvs, this::rel, 2),

            0x18 to Opcode(this::clc, this::imp, 2),
            0xd8 to Opcode(this::cld, this::imp, 2),
            0x58 to Opcode(this::cli, this::imp, 2),
            0xb8 to Opcode(this::clv, this::imp, 2),

            0xc9 to Opcode(this::cmp, this::imm, 2),
            0xc5 to Opcode(this::cmp, this::zpi, 3),
            0xd5 to Opcode(this::cmp, this::zpx, 4),
            0xcd to Opcode(this::cmp, this::abs, 4),
            0xdd to Opcode(this::cmp, this::abx, 4),
            0xd9 to Opcode(this::cmp, this::aby, 4),
            0xc1 to Opcode(this::cmp, this::idx, 6),
            0xd1 to Opcode(this::cmp, this::idy, 5),

            0xe0 to Opcode(this::cpx, this::imm, 2),
            0xe4 to Opcode(this::cpx, this::zpi, 3),
            0xec to Opcode(this::cpx, this::abs, 4),

            0xc0 to Opcode(this::cpy, this::imm, 2),
            0xc4 to Opcode(this::cpy, this::zpi, 3),
            0xcc to Opcode(this::cpy, this::abs, 4),

            0xc6 to Opcode(this::dec, this::zpi, 5),
            0xd6 to Opcode(this::dec, this::zpx, 6),
            0xce to Opcode(this::dec, this::abs, 6),
            0xde to Opcode(this::dec, this::abx, 7),

            0xca to Opcode(this::dex, this::imp, 2),
            0x88 to Opcode(this::dey, this::imp, 2),

            0x49 to Opcode(this::eor, this::imm, 2),
            0x45 to Opcode(this::eor, this::zpi, 3),
            0x55 to Opcode(this::eor, this::zpx, 4),
            0x4d to Opcode(this::eor, this::abs, 4),
            0x5d to Opcode(this::eor, this::abx, 4),
            0x59 to Opcode(this::eor, this::aby, 4),
            0x41 to Opcode(this::eor, this::idx, 6),
            0x51 to Opcode(this::eor, this::idy, 5),

            0xe6 to Opcode(this::inc, this::zpi, 5),
            0xf6 to Opcode(this::inc, this::zpx, 6),
            0xee to Opcode(this::inc, this::abs, 6),
            0xfe to Opcode(this::inc, this::abx, 7),

            0xe8 to Opcode(this::inx, this::imp, 2),
            0xc8 to Opcode(this::iny, this::imp, 2),

            0x4c to Opcode(this::jmp, this::abs, 3),
            0x6c to Opcode(this::jmp, this::ind, 5),

            0x20 to Opcode(this::jsr, this::abs, 6),

            0xa9 to Opcode(this::lda, this::imm, 2),
            0xa5 to Opcode(this::lda, this::zpi, 3),
            0xb5 to Opcode(this::lda, this::zpx, 4),
            0xad to Opcode(this::lda, this::abs, 4),
            0xbd to Opcode(this::lda, this::abx, 4),
            0xb9 to Opcode(this::lda, this::aby, 4),
            0xa1 to Opcode(this::lda, this::idx, 6),
            0xb1 to Opcode(this::lda, this::idy, 5),

            0xa2 to Opcode(this::ldx, this::imm, 2),
            0xa6 to Opcode(this::ldx, this::zpi, 3),
            0xb6 to Opcode(this::ldx, this::zpy, 4),
            0xae to Opcode(this::ldx, this::abs, 4),
            0xbe to Opcode(this::ldx, this::aby, 4),

            0xa0 to Opcode(this::ldy, this::imm, 2),
            0xa4 to Opcode(this::ldy, this::zpi, 3),
            0xb4 to Opcode(this::ldy, this::zpx, 4),
            0xac to Opcode(this::ldy, this::abs, 4),
            0xbc to Opcode(this::ldy, this::abx, 4),

            0x4a to Opcode(this::lsr, this::acc, 2),
            0x46 to Opcode(this::lsr, this::zpi, 5),
            0x56 to Opcode(this::lsr, this::zpx, 6),
            0x4e to Opcode(this::lsr, this::abs, 6),
            0x5e to Opcode(this::lsr, this::abx, 7),

            0xea to Opcode(this::nop, this::imp, 2),

            0x09 to Opcode(this::ora, this::imm, 2),
            0x05 to Opcode(this::ora, this::zpi, 3),
            0x15 to Opcode(this::ora, this::zpx, 4),
            0x0d to Opcode(this::ora, this::abs, 4),
            0x1d to Opcode(this::ora, this::abx, 4),
            0x19 to Opcode(this::ora, this::aby, 4),
            0x01 to Opcode(this::ora, this::idx, 6),
            0x11 to Opcode(this::ora, this::idy, 5),

            0x48 to Opcode(this::pha, this::imp, 3),
            0x08 to Opcode(this::php, this::imp, 3),
            0x68 to Opcode(this::pla, this::imp, 4),
            0x28 to Opcode(this::plp, this::imp, 4),

            0x40 to Opcode(this::rti, this::imp, 6),
            0x60 to Opcode(this::rts, this::imp, 6),

            0x2a to Opcode(this::rol, this::acc, 2),
            0x26 to Opcode(this::rol, this::zpi, 5),
            0x36 to Opcode(this::rol, this::zpx, 6),
            0x2e to Opcode(this::rol, this::abs, 6),
            0x3e to Opcode(this::rol, this::abx, 7),

            0x6a to Opcode(this::ror, this::acc, 2),
            0x66 to Opcode(this::ror, this::zpi, 5),
            0x76 to Opcode(this::ror, this::zpx, 6),
            0x6e to Opcode(this::ror, this::abs, 6),
            0x7e to Opcode(this::ror, this::abx, 7),

            0xe9 to Opcode(this::sbc, this::imm, 2),
            0xe5 to Opcode(this::sbc, this::zpi, 3),
            0xf5 to Opcode(this::sbc, this::zpx, 4),
            0xed to Opcode(this::sbc, this::abs, 4),
            0xfd to Opcode(this::sbc, this::abx, 4),
            0xf9 to Opcode(this::sbc, this::aby, 4),
            0xe1 to Opcode(this::sbc, this::idx, 6),
            0xf1 to Opcode(this::sbc, this::idy, 5),

            0x38 to Opcode(this::sec, this::imp, 2),
            0xf8 to Opcode(this::sed, this::imp, 2),
            0x78 to Opcode(this::sei, this::imp, 2),

            0x85 to Opcode(this::sta, this::zpi, 3),
            0x95 to Opcode(this::sta, this::zpx, 4),
            0x8d to Opcode(this::sta, this::abs, 4),
            0x9d to Opcode(this::sta, this::abx, 5),
            0x99 to Opcode(this::sta, this::aby, 5),
            0x81 to Opcode(this::sta, this::idx, 6),
            0x91 to Opcode(this::sta, this::idy, 6),

            0x86 to Opcode(this::stx, this::zpi, 3),
            0x96 to Opcode(this::stx, this::zpy, 4),
            0x8e to Opcode(this::stx, this::abs, 4),

            0x84 to Opcode(this::sty, this::zpi, 3),
            0x94 to Opcode(this::sty, this::zpx, 4),
            0x8c to Opcode(this::sty, this::abs, 4),

            0xaa to Opcode(this::tax, this::imp, 2),
            0xa8 to Opcode(this::tay, this::imp, 2),
            0xba to Opcode(this::tsx, this::imp, 2),
            0x8a to Opcode(this::txa, this::imp, 2),
            0x9a to Opcode(this::txs, this::imp, 2),
            0x98 to Opcode(this::tya, this::imp, 2)
    )

    fun reset() {
        // Reset all of the registers back to 0
        regA = 0
        regX = 0
        regY = 0
        // Reset all of the pointers back to their default values
        sp = 0xfd
        opcodeKey = 0
        pc = system.cpuReadHalfWord(0xfffc)
        // Reset the status registers
        status.reset()
        // Reset the cycle counters as well
        totalCycles = 0
        stall = 7
    }

    fun nmi() {
        system.cpuWriteHalfWord(0x100 + sp, pc)
        sp = (sp - 2) and 0xff

        system.cpuWriteByte(0x100 + sp, status.encode())
        sp = (sp - 1) and 0xff

        pc = system.cpuReadHalfWord(0xfffa)

        stall = 8
    }

    fun irq() {
        system.cpuWriteHalfWord(0x100 + sp, pc)
        sp = (sp - 2) and 0xff

        status.interruptDisable = true
        system.cpuWriteByte(0x100 + sp, status.encode())
        sp = (sp - 1) and 0xff

        pc = system.cpuReadHalfWord(0xfffe)

        stall = 7
    }

    fun emulateCycle() {
        if (stall <= 0) {
            // Get the current opcode from the system memory
            opcodeKey = system.cpuReadByte(pc)
            // Get the corresponding opcode class from the map
            opcode = opcodeMap[opcodeKey] ?: throw IllegalOpcode(opcodeKey)
            // Then get the address from that class and log the CPU state before the instruction is executed
            address = opcode.address()
            // Execute the instruction
            opcode.operation()
        }
        // Update the total cycles and stall cycles of the CPU
        totalCycles++
        stall--
    }

    private fun postprocess() {
        if (pageCrossed && extraCycles.contains(opcode.operation))
            stall++

        stall += opcode.cycles
        pc += opcodeLength
    }

    // Addressing

    private fun imp(): Int? {
        opcodeLength = 1
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return null
    }

    private fun acc(): Int? {
        opcodeLength = 1
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return null
    }

    private fun imm(): Int {
        opcodeLength = 2
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return pc + 1
    }

    private fun zpi(): Int {
        opcodeLength = 2
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return system.cpuReadByte(pc + 1)
    }

    private fun zpx(): Int {
        opcodeLength = 2
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return (system.cpuReadByte(pc + 1) + regX) and 0xff
    }

    private fun zpy(): Int {
        opcodeLength = 2
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return (system.cpuReadByte(pc + 1) + regY) and 0xff
    }

    private fun rel(): Int {
        opcodeLength = 2
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return pc + 2 + system.cpuReadByte(pc + 1).toByte().toInt()
    }

    private fun abs(): Int {
        opcodeLength = 3
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }
        return system.cpuReadHalfWord(pc + 1)
    }

    private fun abx(): Int {
        opcodeLength = 3
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }

        val address = system.cpuReadHalfWord(pc + 1)
        pageCrossed = ((address + regX) and 0xff00) != (address and 0xff00)

        return (address + regX) and 0xffff
    }

    private fun aby(): Int {
        opcodeLength = 3
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }

        val address = system.cpuReadHalfWord(pc + 1)
        pageCrossed = (address + regY and 0xff00) != (address and 0xff00)

        return (address + regY) and 0xffff
    }

    private fun ind(): Int {
        opcodeLength = 2
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }

        val address = system.cpuReadHalfWord(pc + 1)

        return if (address and 0xff == 0xff)
            (system.cpuReadByte(address and 0xff00) shl 8) or system.cpuReadByte(address)
        else
            system.cpuReadHalfWord(address)
    }

    private fun idx(): Int {
        opcodeLength = 2
        pageCrossed = false
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }

        val address = (system.cpuReadByte(pc + 1) + regX) and 0xff

        val low = system.cpuReadByte(address and 0xff)
        val high = system.cpuReadByte((address + 1) and 0xff)

        return (high shl 8) or low
    }

    private fun idy(): Int {
        opcodeLength = 2
        operands = Array(opcodeLength - 1) { system.cpuReadByte(it + pc + 1) }

        val address = system.cpuReadByte(pc + 1)

        val low = system.cpuReadByte(address and 0xff)
        val high = system.cpuReadByte((address + 1) and 0xff)
        val result = (((high shl 8) or low) + regY) and 0xffff

        pageCrossed = (high shl 8) != (result and 0xff00)

        return result
    }

    // Instructions

    private fun nop() {
        postprocess()
    }

    private fun clc() {
        status.carry = false
        postprocess()
    }

    private fun sec() {
        status.carry = true
        postprocess()
    }

    private fun cld() {
        status.decimal = false
        postprocess()
    }

    private fun sed() {
        status.decimal = true
        postprocess()
    }

    private fun cli() {
        status.interruptDisable = false
        postprocess()
    }

    private fun sei() {
        status.interruptDisable = true
        postprocess()
    }

    private fun clv() {
        status.overflow = false
        postprocess()
    }

    private fun bcc() {

        if (!status.carry) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun bcs() {

        if (status.carry) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun bmi() {

        if (status.negative) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun bpl() {

        if (!status.negative) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun bvc() {

        if (!status.overflow) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun bvs() {

        if (status.overflow) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun bne() {

        if (!status.zero) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun beq() {

        if (status.zero) {
            pc = address!!
            stall += if (pc and 0xff00 != address!! and 0xff00) 4 else 3
        } else
            postprocess()
    }

    private fun jmp() {
        pc = address!!
        stall += opcodeMap[opcodeKey]!!.cycles
    }

    private fun brk() {
        system.cpuWriteHalfWord(0x100 + sp, pc)
        sp = (sp - 2) and 0xff

        system.cpuWriteByte(0x100 + sp, status.encode())
        sp = (sp - 1) and 0xff

        status.breakCommand = true

        pc = system.cpuReadHalfWord(0xfffc)
        stall = 6
    }

    private fun rti() {
        val state = system.cpuReadByte(0x100 + sp + 1)
        status.decode(state)
        sp = (sp + 1) and 0xff

        pc = system.cpuReadHalfWord(0x100 + sp + 1)
        sp = (sp + 2) and 0xff

        stall = 6
    }

    private fun jsr() {

        system.cpuWriteHalfWord(0x100 + sp, pc + 2)
        sp = (sp - 2) and 0xff

        pc = address!!
        stall = 6
    }

    private fun rts() {
        pc = system.cpuReadHalfWord(0x100 + sp + 1) + 1
        sp = (sp + 2) and 0xff

        stall = 6
    }

    private fun lsr() {
        val shifted: Int
        val operand: Int

        if (address == null) {
            operand = regA and 0xff
            shifted = (operand ushr 1) and 0xff
            regA = shifted
        } else {
            operand = system.cpuReadByte(address!!)
            shifted = (operand ushr 1) and 0xff
            system.cpuWriteByte(address!!, shifted)
        }

        status.carry = (operand and 0x1) == 1
        status.zero = shifted == 0
        status.negative = (shifted ushr 7) == 1

        postprocess()
    }

    private fun rol() {
        val carry = if (status.carry) 0x1 else 0x00
        val shifted: Int
        val operand: Int

        if (address == null) {
            operand = regA and 0xff
            shifted = ((operand shl 1) or carry) and 0xff
            regA = shifted
        } else {
            operand = system.cpuReadByte(address!!)
            shifted = ((operand shl 1) or carry) and 0xff
            system.cpuWriteByte(address!!, shifted)
        }

        status.carry = (operand and 0x80) != 0
        status.zero = shifted == 0
        status.negative = (shifted ushr 7) == 1

        postprocess()
    }

    private fun ror() {
        val carry = if (status.carry) 0x80 else 0x0
        val shifted: Int
        val operand: Int

        if (address == null) {
            operand = regA and 0xff
            shifted = (operand ushr 1) or carry
            regA = shifted
        } else {
            operand = system.cpuReadByte(address!!)
            shifted = (operand ushr 1) or carry
            system.cpuWriteByte(address!!, shifted)
        }

        status.carry = (operand and 0x1) == 1
        status.zero = shifted == 0
        status.negative = (shifted ushr 7) == 1

        postprocess()
    }

    private fun eor() {
        val operand = system.cpuReadByte(address!!)

        regA = (regA xor operand)

        status.zero = regA == 0
        status.negative = (regA ushr 7) == 1

        postprocess()
    }

    private fun ora() {
        val operand = system.cpuReadByte(address!!)

        regA = (regA or operand)

        status.zero = regA == 0
        status.negative = (regA ushr 7) == 1

        postprocess()
    }

    private fun adc() {
        val operand = system.cpuReadByte(address!!)
        val carry = if (status.carry) 1 else 0

        val tmp = regA + operand + carry

        status.carry = tmp > 255
        status.zero = (tmp and 0xff) == 0
        status.overflow = ((regA xor operand) and 0x80) == 0 && ((tmp xor regA) and 0x80) != 0
        status.negative = (tmp ushr 7) == 1

        regA = tmp and 0xff
        postprocess()
    }

    private fun and() {
        val operand = system.cpuReadByte(address!!)

        regA = (regA and operand)

        status.zero = regA == 0
        status.negative = (regA ushr 7) == 1

        postprocess()
    }

    private fun asl() {
        val shifted: Int
        val operand: Int

        if (address == null) {
            operand = regA and 0xff
            shifted = (operand shl 1) and 0xff
            regA = shifted
        } else {
            operand = system.cpuReadByte(address!!)
            shifted = (operand shl 1) and 0xff
            system.cpuWriteByte(address!!, shifted)
        }

        status.carry = (operand ushr 7) == 1
        status.zero = (shifted == 0)
        status.negative = (shifted ushr 7) == 1

        postprocess()
    }

    private fun bit() {
        val operand = system.cpuReadByte(address!!)

        status.zero = (operand and regA) == 0
        status.overflow = (operand and 0x40) != 0
        status.negative = (operand and 0x80) != 0

        postprocess()
    }

    private fun cmp() {
        val operand = system.cpuReadByte(address!!)
        val tmp = (regA - operand) and 0xff

        status.carry = regA >= operand
        status.zero = regA == operand
        status.negative = (tmp ushr 7) == 1

        postprocess()
    }

    private fun cpx() {
        val operand = system.cpuReadByte(address!!)
        val tmp = (regX - operand) and 0xff

        status.carry = regX >= operand
        status.zero = regX == operand
        status.negative = (tmp ushr 7) == 1

        postprocess()
    }

    private fun cpy() {
        val operand = system.cpuReadByte(address!!)
        val tmp = (regY - operand) and 0xff

        status.carry = regY >= operand
        status.zero = regY == operand
        status.negative = (tmp ushr 7) == 1

        postprocess()
    }

    private fun dec() {
        val operand = (system.cpuReadByte(address!!) - 1) and 0xff

        system.cpuWriteByte(address!!, operand)

        status.zero = operand == 0
        status.negative = (operand ushr 7) == 1

        postprocess()
    }

    private fun dex() {
        regX = (regX - 1) and 0xff

        status.zero = regX == 0
        status.negative = (regX ushr 7) == 1

        postprocess()
    }

    private fun dey() {
        regY = (regY - 1) and 0xff

        status.zero = regY == 0
        status.negative = (regY ushr 7) == 1

        postprocess()
    }

    private fun lda() {
        regA = system.cpuReadByte(address!!)

        status.zero = regA == 0
        status.negative = (regA ushr 7) == 1

        postprocess()
    }

    private fun ldx() {
        regX = system.cpuReadByte(address!!)

        status.zero = regX == 0
        status.negative = (regX ushr 7) == 1

        postprocess()
    }

    private fun ldy() {
        regY = system.cpuReadByte(address!!)

        status.zero = regY == 0
        status.negative = (regY ushr 7) == 1

        postprocess()
    }

    private fun inc() {
        val operand = (system.cpuReadByte(address!!) + 1) and 0xff

        system.cpuWriteByte(address!!, operand)

        status.zero = operand == 0
        status.negative = (operand ushr 7) == 1

        postprocess()
    }

    private fun inx() {
        regX = (regX + 1) and 0xff

        status.zero = regX == 0
        status.negative = (regX ushr 7) == 1

        postprocess()
    }

    private fun iny() {
        regY = (regY + 1) and 0xff

        status.zero = regY == 0
        status.negative = (regY ushr 7) == 1

        postprocess()
    }

    private fun pha() {
        system.cpuWriteByte(0x100 + sp, regA)
        sp = (sp - 1) and 0xff

        postprocess()
    }

    private fun php() {
        system.cpuWriteByte(0x100 + sp, status.encode())
        sp = (sp - 1) and 0xff

        postprocess()
    }

    private fun pla() {
        regA = system.cpuReadByte(0x100 + sp + 1)
        sp = (sp + 1) and 0xff

        status.zero = regA == 0
        status.negative = (regA ushr 7) == 1

        postprocess()
    }

    private fun plp() {
        val state = system.cpuReadByte(0x100 + sp + 1)
        sp = (sp + 1) and 0xff

        status.decode(state)

        postprocess()
    }

    private fun sta() {

        system.cpuWriteByte(address!!, regA)

        postprocess()
    }

    private fun stx() {

        system.cpuWriteByte(address!!, regX)

        postprocess()
    }

    private fun sty() {

        system.cpuWriteByte(address!!, regY)

        postprocess()
    }

    private fun tax() {
        regX = regA

        status.zero = regX == 0
        status.negative = (regX ushr 7) == 1

        postprocess()
    }

    private fun txa() {
        regA = regX

        status.zero = regA == 0
        status.negative = (regA ushr 7) == 1

        postprocess()
    }

    private fun tay() {
        regY = regA

        status.zero = regY == 0
        status.negative = (regY ushr 7) == 1

        postprocess()
    }

    private fun tsx() {
        regX = sp

        status.zero = regX == 0
        status.negative = (regX ushr 7) == 1

        postprocess()
    }

    private fun txs() {
        sp = regX
        postprocess()
    }

    private fun tya() {
        regA = regY

        status.zero = regA == 0
        status.negative = (regA ushr 7) == 1

        postprocess()
    }

    private fun sbc() {
        val operand = system.cpuReadByte(address!!) xor 0xff
        val carry = if (status.carry) 1 else 0
        val tmp = regA + operand + carry

        status.carry = (tmp and 0xff00) != 0
        status.zero = (tmp and 0xff) == 0
        status.overflow = ((regA xor operand) and 0x80) == 0 && ((tmp xor regA) and 0x80) != 0
        status.negative = (tmp ushr 7) == 1

        regA = tmp and 0xff
        postprocess()
    }
}