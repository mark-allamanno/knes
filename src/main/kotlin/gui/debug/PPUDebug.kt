package gui.debug

import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import utils.NES

class PPUDebug(nes: NES) : DebugWindow(nes) {

    override val windowTitle = "PPU Debug Window"       // The window title for this debug window instance
    override val dimensions = Pair(1000.0, 400.0)       // The starting dimensions of this debug window instance
    override val layout = HBox()                        // The preferred layout we will use for this debug instance
    private val spriteDisplay = Canvas()                // A canvas to draw the sprite information to
    private val paletteContainer = VBox()               // A layout to hold all of the canvases for the palettes to be drawn to

    init {
        // Initialize the palettes and sprite display
        initPalettes()
        initSprites()
        updateSpriteData()
        configureStage()
    }

    private fun initSprites() {
        // Create a new horizontal box for the sprite display to reside in and sets its padding
        val hbox = HBox()
        // Then add the sprite display to the HBox and the HBox to the window layout
        hbox.children.add(spriteDisplay)
        layout.children.add(hbox)
        // Finally set image smoothing to false because we want to keep sprites crisp and then show the sprite information
        spriteDisplay.graphicsContext2D.isImageSmoothing = false
    }

    private fun initPalettes() {
        // Set the alignment and padding of the palette container
        paletteContainer.alignment = Pos.CENTER_RIGHT
        paletteContainer.padding = Insets(0.0, 20.0, 0.0, 20.0)
        // Then create very palette's canvas
        for (i in 0 until 8) {
            // Canvas on which we will draw the 4 palette colors
            val canvas = Canvas()
            // When we curse over this canvas put a yellow rect around it to show it has been 'selected'
            canvas.onMouseMoved = EventHandler {
                canvas.graphicsContext2D.stroke = Color.YELLOW
                canvas.graphicsContext2D.strokeRect(0.0, 0.0, canvas.width + 1, canvas.height + 1)
            }
            // When we click on this canvas then set the palette equal to its palette number and update the sprites
            canvas.onMouseClicked = EventHandler {
                activePalette = i
                updateSpriteData()
                updateSpriteDisplay()
            }
            // Then we stop cursing over this canvas then redraw the palette as it was before
            canvas.onMouseExited = EventHandler {
                drawPalette(canvas, i)
            }
            // Then finally add this canvas to the palette container layout
            paletteContainer.children.add(canvas)
        }
        // After we have created all the palettes canvas' then add the container to the layout and draw the colors on them
        layout.children.add(paletteContainer)
    }

    override fun updateScreen() {
        super.updateScreen()

        if (nes.system.graphicsChange) {
            // If the graphics information has changed then update the palettes and sprite display accordingly
            updatePalettes()
            updateSpriteDisplay()
            nes.system.graphicsChange = false
        }
    }

    override fun resizeChildren() {
        // Scale the canvas with the window
        spriteDisplay.widthProperty().set(width * .8)
        spriteDisplay.heightProperty().set(height * .99)
        // Recalculate the palettes spacing between each pother in case we have been resized
        paletteContainer.spacing = ((height) - ((height / 15) * 8)) / 10
        // Then redraw their graphical data
        updatePalettes()
        updateSpriteDisplay()
    }

    private fun updateSpriteDisplay() {
        spriteDisplay.graphicsContext2D.clearRect(0.0, 0.0, spriteDisplay.width, spriteDisplay.height)
        // Get the sprite width and height of the sprite
        val width = spriteDisplay.width / 32
        val height = spriteDisplay.height / 16
        // Iterate over the entire program memory and draw it to the debug canvas
        for (y in 0 until 32) {
            for (x in 0 until 16) {
                val sprite = sprites[(y * 16) + x]
                // Get the x and y location of the sprite, we do this because we draw the first 4kb and then the last
                // 4kb side by side
                val locationX = if (y < 16) x else x + 16
                val locationY = if (y < 16) y else y - 16
                // Then draw the sprite to the canvas at the appropriate location
                spriteDisplay.graphicsContext2D.drawImage(sprite, locationX * width,
                        locationY * height, width - 1, height - 1)
            }
        }
    }

    private fun updatePalettes() {
        // Then draw every color from every palette
        for (i in 0 until 8) {
            // Get and type cast the canvas from the palette container
            val canvas = paletteContainer.children[i] as Canvas
            // Resize the canvas to be the correct size in case we have been resized
            canvas.widthProperty().set(width * .15)
            canvas.heightProperty().set(height / 15)
            // Finally draw the current palette
            drawPalette(canvas, i)
        }
    }

    private fun drawPalette(canvas: Canvas, palette: Int) {
        // Before doing anything clear the canvas
        canvas.graphicsContext2D.clearRect(0.0, 0.0, canvas.width, canvas.height)
        // Then draw the 4 colors in the specified palette
        for (color in 0 until 4) {
            // Get the color in hex from the standard NES palette and then convert that into RGB values from the map
            val hexColor = nes.ppu.readPalette(palette, color)
            val (r, g, b) = NES.palette[hexColor] ?: Triple(0, 0, 0)
            // Set the fill and stroke to the correct colors
            canvas.graphicsContext2D.fill = Color.rgb(r, g, b)
            canvas.graphicsContext2D.stroke = Color.BLACK
            // Then draw the border of each palette, fill it with the correct color
            canvas.graphicsContext2D.strokeRect((canvas.width / 4) * color, 0.0, (canvas.width / 4), canvas.height + 1)
            canvas.graphicsContext2D.fillRect((canvas.width / 4) * color, 0.0, (canvas.width / 4), canvas.height + 1)
        }
        // Finally draw a border to encapsulate the 4 colors in the palette
        canvas.graphicsContext2D.strokeRect(0.0, 0.0, canvas.width, canvas.height)
    }
}