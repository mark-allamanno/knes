package gui.debug

import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import javafx.stage.Stage
import utils.NES
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class DebugWindow(protected val nes: NES) : Stage() {

    protected abstract val windowTitle: String
    protected abstract val layout: Parent
    protected abstract val dimensions: Pair<Double, Double>
    private var timer = Executors.newSingleThreadScheduledExecutor()

    companion object {
        var activePalette = 0
        val sprites = Array(512) { WritableImage(8, 8) }    // All debug windows should use the same sprite array
    }

    protected fun configureStage() {
        scene = Scene(layout, dimensions.first, dimensions.second)
        // Set the window to automatically update the labels and sprites on a resize
        widthProperty().addListener { _, _, _ -> resizeChildren() }
        heightProperty().addListener { _, _, _ -> resizeChildren() }
        // Then resize the children when we are created
        resizeChildren()
        // Set the title and icon to the debug window
        this.title = windowTitle
        this.icons.add(Image("nes.png"))
        this.scene.stylesheets.add("css/main.css")
    }

    override fun hide() {
        super.hide()
        timer.shutdownNow()
    }

    fun startRendering() {
        // When we start rendering shutdown the timer and create a new one, in case we have one already running
        timer.shutdownNow()
        timer = Executors.newSingleThreadScheduledExecutor()
        // Then create a new updater runnable to update the screen at a rate of 60 hz
        timer.scheduleAtFixedRate({ Platform.runLater { updateScreen() } },
                0, 10, TimeUnit.MILLISECONDS)
    }

    protected fun updateSpriteData() {
        for (i in sprites.indices) {
            // Make a new writable image, aka a sprite, and get the bit plane for that sprite
            val bitPlane = nes.ppu.readPatternTable(i * 16)
            // Then iterate over the image and write the pixel colors according to the palette and return the sprite
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    // Get the hex color form the palette and pixel. Then get the RGB values from this hex color and write it
                    val hexColor = nes.ppu.readPalette(activePalette, bitPlane[y][x])
                    val (r, g, b) = NES.palette[hexColor] ?: Triple(0, 0, 0)
                    sprites[i].pixelWriter.setColor(x, y, Color.rgb(r, g, b))
                }
            }
        }
    }

    protected open fun updateScreen() {
        // If the CPu has written new sprite data to the PPU then we need to update the sprite array
        if (nes.system.graphicsChange)
            updateSpriteData()
    }

    protected abstract fun resizeChildren()
}