package gui

import gui.debug.CPUDebug
import gui.debug.DebugWindow
import gui.debug.NameTableDebug
import gui.debug.PPUDebug
import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import javafx.stage.Stage
import utils.NES
import utils.NESException
import kotlin.math.pow
import kotlin.system.exitProcess


class MainWindow : Application() {

    private val browser = FileChooser()                     // File Browser to select ROMs through
    private val canvas = Canvas()                           // Canvas to render the main scene to
    private val nes = NES(this)                  // NES instance that holds all components to the backend
    private var cpuDebug: CPUDebug? = null                  // A debug window that can optionally show CPU state information
    private var nameTableDebug: NameTableDebug? = null      // A debug window that can optionally show name table information
    private var ppuDebug: PPUDebug? = null                  // A debug window that can optionally show PPU state information
    private val render = RenderFrame(canvas, nes)           // A helper class to render images to the screen

    init {
        // Set the extension filters and title of the browser window so we dont have to do so later
        browser.extensionFilters.addAll(
                FileChooser.ExtensionFilter("NES Roms (*.nes)", "*.nes"),
                FileChooser.ExtensionFilter("All Files", "*")
        )
        browser.title = "Please Choose the Rom to Emulate"
        canvas.graphicsContext2D.isImageSmoothing = false
        // Start rendering to the main canvas
        render.start()
    }

    companion object {
        private val inputMappings = arrayOf(KeyCode.RIGHT, KeyCode.LEFT, KeyCode.DOWN, KeyCode.UP,  KeyCode.R, KeyCode.E,
                KeyCode.W, KeyCode.Q)
        // A simple helper class to allow us to render the current ppu frame to the screen efficiently
        class RenderFrame(private val canvas: Canvas, private val nes: NES) : AnimationTimer() {
            override fun handle(now: Long) {
                canvas.graphicsContext2D.drawImage(nes.ppu.frame, 0.0, 0.0, canvas.width, canvas.height)
            }
        }
    }

    override fun start(primaryStage: Stage) {
        primaryStage.widthProperty().addListener { _, _, _ -> canvas.widthProperty().set(primaryStage.width) }
        primaryStage.heightProperty().addListener { _, _, _ -> canvas.heightProperty().set(primaryStage.height) }
        // Create a new menubar for the application and initialize its sub menus
        val menuBar = MenuBar()
        initFileMenus(menuBar, primaryStage)
        initDebugMenus(menuBar)
        // Create a new layout to add the menubar to
        val layout = BorderPane()
        layout.top = menuBar
        layout.center = canvas
        canvas.graphicsContext2D.fill = Color.BLACK
        canvas.graphicsContext2D.fillRect(0.0, 0.0, 100.0, 100.0)
        // Create and set a new scene for the primary stage of the app
        val scene = Scene(layout, 1000.0, 700.0)
        primaryStage.scene = scene
        scene.addEventHandler(KeyEvent.KEY_PRESSED) { key ->
            var state = 0
            for (i in inputMappings.indices) {
                val mask = if (key.code == inputMappings[i]) 2.0.pow(i).toInt() else 0
                state = (state or mask)
            }
            println(nes.controllerState)
            nes.controllerState = state


        }
        // Set the cosmetics of the primary stage ie title, icon, etc and how it
        primaryStage.title = "NES Emulator"
        primaryStage.icons.add(Image("nes.png"))
        primaryStage.scene.stylesheets.add("css/main.css")
        primaryStage.onCloseRequest = EventHandler { Platform.exit() }
        primaryStage.show()
        // Invoke the garbage collector to clean up the startup drama of javafx. Releases ~30mb to system
        System.gc()
    }

    override fun stop() {
        // When we stop the application we need to stop the background thread as well or it will keep running
        super.stop()
        render.stop()
        nes.stop()
    }

    private fun initFileMenus(menuBar: MenuBar, primaryStage: Stage) {
        // Create the new sub menus for the menubar
        val fileMenu = Menu("File")
        // Add a menu item for opening roms and set its action to open the file chooser
        val open = MenuItem("Open Rom")
        open.onAction = EventHandler {
            nes.stop()
            // Get the filepath to the selected file and attempt tp load it to the NES and start rendering to the debug
            val file = browser.showOpenDialog(primaryStage)
            if (file != null) {
                nes.insertCartridge(file.absolutePath)
                cpuDebug?.startRendering()
                ppuDebug?.startRendering()
                nameTableDebug?.startRendering()
            }
            nes.start()
        }
        // Add a menu item for closing the application and make its action to end the program
        val exit = MenuItem("Exit")
        exit.onAction = EventHandler {
            // Stop the backend and call kotlin exit method
            nes.stop()
            exitProcess(0)
        }
        // Then add these items to the file sub menu
        fileMenu.items.addAll(open, exit)
        menuBar.menus.add(fileMenu)
    }

    private fun initDebugMenus(menuBar: MenuBar) {
        val debugMenu = Menu("Debug")
        // Another sub menu for showing the debug window and set its action to open a debug window
        val showCPU = MenuItem("CPU Debug")
        showCPU.onAction = EventHandler {
            val tmp = CPUDebug(nes)
            createDebugWindow(cpuDebug, tmp)
            cpuDebug = tmp
        }
        // Add the item to the debug sub menu and add all sub menus to the menubar
        val showPPU = MenuItem("PPU Debug")
        showPPU.onAction = EventHandler {
            val tmp = PPUDebug(nes)
            createDebugWindow(ppuDebug, tmp)
            ppuDebug = tmp
        }
        // Add the item to the debug sub menu and add all sub menus to the menubar
        val showNameTable = MenuItem("NameTable Debug")
        showNameTable.onAction = EventHandler {
            val tmp = NameTableDebug(nes)
            createDebugWindow(nameTableDebug, tmp)
            nameTableDebug = tmp
        }
        // Add the item to the debug sub menu and add all sub menus to the menubar
        debugMenu.items.addAll(showCPU, showPPU, showNameTable)
        menuBar.menus.addAll(debugMenu)
    }

    private fun createDebugWindow(oldWindow: DebugWindow?, newWindow: DebugWindow) {
        // If the cartridge is non null then start rendering its contents to the debug window
        if (nes.isCartridgeActive())
            newWindow.startRendering()
        // If the debug is non null close it after opening another
        newWindow.show()
        oldWindow?.close()
    }

    fun errorMessage(e: NESException) {
        // Create a new error alert message and set its message and header to the exception
        val error = Alert(Alert.AlertType.ERROR)
        error.headerText = e.errorHeader
        error.contentText = e.errorMessage
        // Then create a new label and text area for the stack trace
        val label = Label("Exceptions Stack Trace")
        val textArea = TextArea()
        // Set important traits to the text area ie the stack trace text and text wrap
        textArea.isWrapText = true
        textArea.text = e.stackTraceString()
        textArea.maxHeight = Double.MAX_VALUE
        textArea.maxWidth = Double.MAX_VALUE
        VBox.setVgrow(textArea, Priority.ALWAYS)
        // Make a new vbox with the label and stack trace text
        error.dialogPane.expandableContent = VBox(label, textArea)
        // Then add the application icon to the new stage for coherence and show the stage
        val stage = error.dialogPane.scene.window as Stage
        stage.icons.add(Image("nes.png"))
        stage.scene.stylesheets.add("css/error.css")
        error.showAndWait()
    }
}

fun main(args: Array<String>) {
    Application.launch(MainWindow::class.java, *args)   // App entry point
}