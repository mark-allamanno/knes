package processors

import memory.SystemBus
import utils.IllegalOpcode
import utils.Logger

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
    private val extraCycles = arrayOf(::adc, ::and, ::cmp, ::eor, ::lda, ::ldx, ::ldy, ::ora, ::sbc)

    // A map that links all opcode bytes to their data class counter parts
    private val opcodeMap = mapOf(
            0x69 to Opcode(::adc, ::imm, 2),
            0x65 to Opcode(::adc, ::zpi, 3),
            0x75 to Opcode(::adc, ::zpx, 4),
            0x6d to Opcode(::adc, ::abs, 4),
            0x7d to Opcode(::adc, ::abx, 4),
            0x79 to Opcode(::adc, ::aby, 4),
            0x61 to Opcode(::adc, ::idx, 6),
            0x71 to Opcode(::adc, ::idy, 5),

            0x29 to Opcode(::and, ::imm, 2),
            0x25 to Opcode(::and, ::zpi, 3),
            0x35 to Opcode(::and, ::zpx, 4),
            0x2d to Opcode(::and, ::abs, 4),
            0x3d to Opcode(::and, ::abx, 4),
            0x39 to Opcode(::and, ::aby, 4),
            0x21 to Opcode(::and, ::idx, 6),
            0x31 to Opcode(::and, ::idy, 5),

            0x0a to Opcode(::asl, ::acc, 2),
            0x06 to Opcode(::asl, ::zpi, 5),
            0x16 to Opcode(::asl, ::zpx, 6),
            0x0e to Opcode(::asl, ::abs, 6),
            0x1e to Opcode(::asl, ::abx, 7),

            0x90 to Opcode(::bcc, ::rel, 2),
            0xb0 to Opcode(::bcs, ::rel, 2),
            0xf0 to Opcode(::beq, ::rel, 2),

            0x24 to Opcode(::bit, ::zpi, 3),
            0x2c to Opcode(::bit, ::abs, 4),

            0x30 to Opcode(::bmi, ::rel, 2),
            0xd0 to Opcode(::bne, ::rel, 2),
            0x10 to Opcode(::bpl, ::rel, 2),

            0x00 to Opcode(::brk, ::imp, 7),

            0x50 to Opcode(::bvc, ::rel, 2),
            0x70 to Opcode(::bvs, ::rel, 2),

            0x18 to Opcode(::clc, ::imp, 2),
            0xd8 to Opcode(::cld, ::imp, 2),
            0x58 to Opcode(::cli, ::imp, 2),
            0xb8 to Opcode(::clv, ::imp, 2),

            0xc9 to Opcode(::cmp, ::imm, 2),
            0xc5 to Opcode(::cmp, ::zpi, 3),
            0xd5 to Opcode(::cmp, ::zpx, 4),
            0xcd to Opcode(::cmp, ::abs, 4),
            0xdd to Opcode(::cmp, ::abx, 4),
            0xd9 to Opcode(::cmp, ::aby, 4),
            0xc1 to Opcode(::cmp, ::idx, 6),
            0xd1 to Opcode(::cmp, ::idy, 5),

            0xe0 to Opcode(::cpx, ::imm, 2),
            0xe4 to Opcode(::cpx, ::zpi, 3),
            0xec to Opcode(::cpx, ::abs, 4),

            0xc0 to Opcode(::cpy, ::imm, 2),
            0xc4 to Opcode(::cpy, ::zpi, 3),
            0xcc to Opcode(::cpy, ::abs, 4),

            0xc6 to Opcode(::dec, ::zpi, 5),
            0xd6 to Opcode(::dec, ::zpx, 6),
            0xce to Opcode(::dec, ::abs, 6),
            0xde to Opcode(::dec, ::abx, 7),

            0xca to Opcode(::dex, ::imp, 2),
            0x88 to Opcode(::dey, ::imp, 2),

            0x49 to Opcode(::eor, ::imm, 2),
            0x45 to Opcode(::eor, ::zpi, 3),
            0x55 to Opcode(::eor, ::zpx, 4),
            0x4d to Opcode(::eor, ::abs, 4),
            0x5d to Opcode(::eor, ::abx, 4),
            0x59 to Opcode(::eor, ::aby, 4),
            0x41 to Opcode(::eor, ::idx, 6),
            0x51 to Opcode(::eor, ::idy, 5),

            0xe6 to Opcode(::inc, ::zpi, 5),
            0xf6 to Opcode(::inc, ::zpx, 6),
            0xee to Opcode(::inc, ::abs, 6),
            0xfe to Opcode(::inc, ::abx, 7),

            0xe8 to Opcode(::inx, ::imp, 2),
            0xc8 to Opcode(::iny, ::imp, 2),

            0x4c to Opcode(::jmp, ::abs, 3),
            0x6c to Opcode(::jmp, ::ind, 5),

            0x20 to Opcode(::jsr, ::abs, 6),

            0xa9 to Opcode(::lda, ::imm, 2),
            0xa5 to Opcode(::lda, ::zpi, 3),
            0xb5 to Opcode(::lda, ::zpx, 4),
            0xad to Opcode(::lda, ::abs, 4),
            0xbd to Opcode(::lda, ::abx, 4),
            0xb9 to Opcode(::lda, ::aby, 4),
            0xa1 to Opcode(::lda, ::idx, 6),
            0xb1 to Opcode(::lda, ::idy, 5),

            0xa2 to Opcode(::ldx, ::imm, 2),
            0xa6 to Opcode(::ldx, ::zpi, 3),
            0xb6 to Opcode(::ldx, ::zpy, 4),
            0xae to Opcode(::ldx, ::abs, 4),
            0xbe to Opcode(::ldx, ::aby, 4),

            0xa0 to Opcode(::ldy, ::imm, 2),
            0xa4 to Opcode(::ldy, ::zpi, 3),
            0xb4 to Opcode(::ldy, ::zpx, 4),
            0xac to Opcode(::ldy, ::abs, 4),
            0xbc to Opcode(::ldy, ::abx, 4),

            0x4a to Opcode(::lsr, ::acc, 2),
            0x46 to Opcode(::lsr, ::zpi, 5),
            0x56 to Opcode(::lsr, ::zpx, 6),
            0x4e to Opcode(::lsr, ::abs, 6),
            0x5e to Opcode(::lsr, ::abx, 7),

            0xea to Opcode(::nop, ::imp, 2),

            0x09 to Opcode(::ora, ::imm, 2),
            0x05 to Opcode(::ora, ::zpi, 3),
            0x15 to Opcode(::ora, ::zpx, 4),
            0x0d to Opcode(::ora, ::abs, 4),
            0x1d to Opcode(::ora, ::abx, 4),
            0x19 to Opcode(::ora, ::aby, 4),
            0x01 to Opcode(::ora, ::idx, 6),
            0x11 to Opcode(::ora, ::idy, 5),

            0x48 to Opcode(::pha, ::imp, 3),
            0x08 to Opcode(::php, ::imp, 3),
            0x68 to Opcode(::pla, ::imp, 4),
            0x28 to Opcode(::plp, ::imp, 4),

            0x40 to Opcode(::rti, ::imp, 6),
            0x60 to Opcode(::rts, ::imp, 6),

            0x2a to Opcode(::rol, ::acc, 2),
            0x26 to Opcode(::rol, ::zpi, 5),
            0x36 to Opcode(::rol, ::zpx, 6),
            0x2e to Opcode(::rol, ::abs, 6),
            0x3e to Opcode(::rol, ::abx, 7),

            0x6a to Opcode(::ror, ::acc, 2),
            0x66 to Opcode(::ror, ::zpi, 5),
            0x76 to Opcode(::ror, ::zpx, 6),
            0x6e to Opcode(::ror, ::abs, 6),
            0x7e to Opcode(::ror, ::abx, 7),

            0xe9 to Opcode(::sbc, ::imm, 2),
            0xe5 to Opcode(::sbc, ::zpi, 3),
            0xf5 to Opcode(::sbc, ::zpx, 4),
            0xed to Opcode(::sbc, ::abs, 4),
            0xfd to Opcode(::sbc, ::abx, 4),
            0xf9 to Opcode(::sbc, ::aby, 4),
            0xe1 to Opcode(::sbc, ::idx, 6),
            0xf1 to Opcode(::sbc, ::idy, 5),

            0x38 to Opcode(::sec, ::imp, 2),
            0xf8 to Opcode(::sed, ::imp, 2),
            0x78 to Opcode(::sei, ::imp, 2),

            0x85 to Opcode(::sta, ::zpi, 3),
            0x95 to Opcode(::sta, ::zpx, 4),
            0x8d to Opcode(::sta, ::abs, 4),
            0x9d to Opcode(::sta, ::abx, 5),
            0x99 to Opcode(::sta, ::aby, 5),
            0x81 to Opcode(::sta, ::idx, 6),
            0x91 to Opcode(::sta, ::idy, 6),

            0x86 to Opcode(::stx, ::zpi, 3),
            0x96 to Opcode(::stx, ::zpy, 4),
            0x8e to Opcode(::stx, ::abs, 4),

            0x84 to Opcode(::sty, ::zpi, 3),
            0x94 to Opcode(::sty, ::zpx, 4),
            0x8c to Opcode(::sty, ::abs, 4),

            0xaa to Opcode(::tax, ::imp, 2),
            0xa8 to Opcode(::tay, ::imp, 2),
            0xba to Opcode(::tsx, ::imp, 2),
            0x8a to Opcode(::txa, ::imp, 2),
            0x9a to Opcode(::txs, ::imp, 2),
            0x98 to Opcode(::tya, ::imp, 2)
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