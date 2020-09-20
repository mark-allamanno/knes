package gui.debug

import javafx.scene.canvas.Canvas
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import utils.NES

class NameTableDebug(nes: NES) : DebugWindow(nes) {

    override val windowTitle = "NameTable Debug Window"     // The window title for this debug window instance
    override val dimensions = Pair(512.0, 480.0)            // The starting dimensions of this debug window instance
    override val layout = VBox()                            // The preferred layout we will use for this debug instance
    private val canvas = Canvas()                           // A canvas to draw the name tables to

    init {
        // Initialize the stage, and add the canvas to the layout
        configureStage()
        layout.children.add(canvas)
        canvas.graphicsContext2D.isImageSmoothing = false
    }

    override fun updateScreen() {
        super.updateScreen()
        // If the name tables have been updated by the CPU then we need to show that change
        if (nes.system.nameTableChange) {
            updateNameTable()
            nes.system.nameTableChange = false
        }
    }

    override fun resizeChildren() {
        // Scale the canvas with the window and clear it before we redraw the sprites
        canvas.widthProperty().set(width)
        canvas.heightProperty().set(height)
        // Then redraw the graphical data for the name table
        updateNameTable()
    }

    private fun updateNameTable() {
        // Clear the entire screen on a redraw
        canvas.graphicsContext2D.clearRect(0.0, 0.0, canvas.width, canvas.height)
        // Get the dimensions of each individual sprite before we atrt drawing
        val width = canvas.width / 64
        val height = canvas.height / 60
        // Iterate over all 4 name tables and draw them
        for (nameTable in 0 until 4) {
            // Get the start address that corresponds tp the name table we are currently drawing
            val anchorAddress = 0x2000 + (nameTable * 0x400)
            // Iterate over each sprite in the name table, which is 32x32 sprites
            for (y in 0 until 30) {
                for (x in 0 until 32) {
                    // Get the x and y location of each individual sprite before drawing
                    val locationX = if (nameTable % 2 == 0) (x * width) else (32 * width) + (x * width)
                    val locationY = if (nameTable < 2) (y * height) else (30 * height) + (y * height)
                    // Finally get the sprite address from memory, fetch the sprite, and draw it to the screen
                    val spriteAddress = nes.system.ppuReadByte(anchorAddress + (y * 32) + x)
                    val patternTableSelect = if (nes.ppu.control.backgroundTable) 256 else 0
                    val sprite = sprites[patternTableSelect + spriteAddress]
                    canvas.graphicsContext2D.drawImage(sprite, locationX, locationY, width - 1, height - 1)
                }
            }
        }
        // Then draw teo lines to visually split the name tables into their 4 distinct quadrants
        canvas.graphicsContext2D.stroke = Color.YELLOW
        canvas.graphicsContext2D.strokeLine(canvas.width / 2, 0.0, canvas.width / 2, canvas.height)
        canvas.graphicsContext2D.strokeLine(0.0, canvas.height / 2, canvas.width, canvas.height / 2)
    }
}