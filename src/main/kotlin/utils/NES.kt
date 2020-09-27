package utils

import gui.MainWindow
import javafx.application.Platform
import javafx.scene.canvas.Canvas
import memory.Cartridge
import memory.SystemBus
import processors.CPU
import processors.PPU
import java.util.concurrent.Executors.newSingleThreadScheduledExecutor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class NES(private val mainWindow: MainWindow) : Runnable {

    val system = SystemBus(this)                            // The system memory for the NES
    val cpu = CPU(system)                                       // The system CPU for the NES
    val ppu = PPU(system)                                       // The system PPU for the NES
    var controllerState = 0
    lateinit var cartridge: Cartridge                           // The currently inserted cartridge for the NES system
    private val logger = Logger(this)                       // The logger class that enables us to write to a log file
    private lateinit var timer: ScheduledExecutorService        // The timer that will continuously execute our system cycle code

    companion object {
        val palette = mapOf(
                0x00 to Triple(84, 84, 84),
                0x01 to Triple(0, 30, 116),
                0x02 to Triple(8, 16, 144),
                0x03 to Triple(48, 0, 136),
                0x04 to Triple(68, 0, 100),
                0x05 to Triple(92, 0, 48),
                0x06 to Triple(84, 4, 0),
                0x07 to Triple(60, 24, 0),
                0x08 to Triple(32, 42, 0),
                0x09 to Triple(8, 58, 0),
                0x0a to Triple(0, 64, 0),
                0x0b to Triple(0, 60, 0),
                0x0c to Triple(0, 50, 60),

                0x10 to Triple(152, 150, 152),
                0x11 to Triple(8, 76, 196),
                0x12 to Triple(48, 50, 236),
                0x13 to Triple(92, 30, 228),
                0x14 to Triple(136, 20, 176),
                0x15 to Triple(160, 20, 100),
                0x16 to Triple(152, 34, 32),
                0x17 to Triple(120, 60, 0),
                0x18 to Triple(84, 90, 0),
                0x19 to Triple(40, 114, 0),
                0x1a to Triple(8, 124, 0),
                0x1b to Triple(0, 118, 40),
                0x1c to Triple(0, 102, 120),

                0x20 to Triple(236, 238, 236),
                0x21 to Triple(76, 154, 236),
                0x22 to Triple(120, 124, 236),
                0x23 to Triple(176, 98, 236),
                0x24 to Triple(228, 84, 236),
                0x25 to Triple(236, 88, 180),
                0x26 to Triple(236, 106, 100),
                0x27 to Triple(212, 136, 32),
                0x28 to Triple(160, 170, 0),
                0x29 to Triple(116, 196, 0),
                0x2a to Triple(76, 208, 32),
                0x2b to Triple(56, 204, 108),
                0x2c to Triple(56, 180, 204),
                0x2d to Triple(60, 60, 60),

                0x30 to Triple(236, 238, 236),
                0x31 to Triple(168, 204, 236),
                0x32 to Triple(188, 188, 236),
                0x33 to Triple(212, 178, 236),
                0x34 to Triple(236, 174, 236),
                0x35 to Triple(236, 174, 212),
                0x36 to Triple(236, 180, 176),
                0x37 to Triple(228, 196, 144),
                0x38 to Triple(204, 210, 120),
                0x39 to Triple(180, 222, 120),
                0x3a to Triple(168, 226, 144),
                0x3b to Triple(152, 226, 180),
                0x3c to Triple(160, 214, 228),
                0x3d to Triple(160, 162, 160)
        )
    }

    override fun run() {
        try {
            // The PPU runs 3x as fast as the CPU so emulate 3 cycles for every 1 CPU cycle
            for (cyc in 0 until 3)
                ppu.emulateCycle()
            // Then emulate one CPU cycle
            cpu.emulateCycle()
            // Then we need to check if the PPU has emitted an NMI request abd execute one if it did
            if (ppu.emitNMI) {
                cpu.nmi()
                ppu.emitNMI = false
            }
        } catch (e: NESException) {
            // If we encounter an error during the emulation then show an error message and stop execution
            Platform.runLater { mainWindow.errorMessage(e) }
            stop()
        }
    }

    fun isCartridgeActive(): Boolean {
        return ::cartridge.isInitialized
    }

    fun insertCartridge(filename: String) {
        try {
            val cartridge = Cartridge(filename, system)
            // Then insert, load, and assign the cartridge to the class
            system.insertCartridge(cartridge)
            cartridge.loadToBuses()
            this.cartridge = cartridge
            // Reset the ppu and cpu
            cpu.reset()
            ppu.reset()
            // Log the cartridge change before we start the emulation
            logger.logCartridgeChange(cartridge)
        } catch (e: NESException) {
            // If we encounter an error then show an error message and stop execution
            Platform.runLater { mainWindow.errorMessage(e) }
            stop()
        }
    }

    fun start() {
        if (::cartridge.isInitialized) {
            // Create a new scheduler instance whose thread is set to high priority to get the correct performance
            timer = newSingleThreadScheduledExecutor { runnable ->
                val thread = Thread(runnable, "NES Backend Thread")
                thread.priority = Thread.MAX_PRIORITY
                thread  // Returning the thread implicitly
            }
            timer.scheduleAtFixedRate(this, 0, 562, TimeUnit.NANOSECONDS)
        }
    }

    fun stop() {
        if (::timer.isInitialized)
            timer.shutdownNow()     // If the timer is not shutdown then shut it down
    }
}