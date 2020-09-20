package gui.debug

import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.Text
import utils.NES
import kotlin.math.min

class CPUDebug(nes: NES) : DebugWindow(nes) {

    override val windowTitle = "CPU Debug Window"       // The window title for this debug window instance
    override val dimensions = Pair(500.0, 600.0)        // The starting dimensions of this debug window instance
    override val layout = BorderPane()                  // A new layout for the stage to organize the components
    private lateinit var font: Font                     // The current font size based on the window size
    private val labelContainer = VBox()                 // A sub layout for the main layout to manage the labels
    private val cpuLabels = arrayOf(
            Label("Program Counter: ${Integer.toHexString(nes.cpu.pc)}"),
            Label("Opcode: ${Integer.toHexString(nes.cpu.op)}"),
            Label("Accumulator: ${Integer.toHexString(nes.cpu.regA)}"),
            Label("Register X: ${Integer.toHexString(nes.cpu.regX)}"),
            Label("Register Y: ${Integer.toHexString(nes.cpu.regY)}"),
            Label("Status: ${Integer.toHexString(nes.cpu.status.encode())}"),
            Label("Stack Pointer: ${Integer.toHexString(nes.cpu.sp)}"),
            Label("Total Cycles: ${nes.cpu.totalCycles}"),
            Label("Stall Cycles: ${nes.cpu.stall}")
    )

    init {
        // Configure the stage and initialize the labels for the window
        configureStage()
        initLabels()
        resizeChildren()
    }

    override fun updateScreen() {
        // Always update the CPU labels as they are constantly changing
        updateLabels()
    }

    override fun resizeChildren() {
        // Make a text field and set its font to 1 pixel to get a reference scale
        val text = Text(cpuLabels[0].text)
        text.font = Font("Serif", 1.0)
        // Get the appropriate font sizes according to the width and height bounds
        val width = (width) / text.boundsInLocal.width
        val height = (height / 11) / text.boundsInLocal.height
        // Return the minimum of the two font sizes
        font = Font("Serif", min(width, height))
        // Then update the labels with this new font
        updateLabels()
    }

    private fun initLabels() {
        // Add the label container to the layout and set the labels alignment to the left
        layout.center = labelContainer
        labelContainer.alignment = Pos.CENTER
        // Set the initiate font size for each of the labels and add them to the window
        for (label in cpuLabels)
            labelContainer.children.add(label)
    }

    private fun updateLabels() {
        // Make a new array of strings that we will use to update the labels
        val strings = arrayOf(
                "Program Counter: ${Integer.toHexString(nes.cpu.pc)}",
                "Opcode: ${Integer.toHexString(nes.cpu.op)}",
                "Accumulator: ${Integer.toHexString(nes.cpu.regA)}",
                "Register X: ${Integer.toHexString(nes.cpu.regX)}",
                "Register Y: ${Integer.toHexString(nes.cpu.regY)}",
                "Status: ${Integer.toHexString(nes.cpu.status.encode())}",
                "Stack Pointer: ${Integer.toHexString(nes.cpu.sp)}",
                "Total Cycles: ${nes.cpu.totalCycles}",
                "Stall Cycles: ${nes.cpu.stall}"
        )
        // Iterate over each label and update its font and label text
        for (i in cpuLabels.indices) {
            cpuLabels[i].font = font
            cpuLabels[i].text = strings[i]
        }
    }
}