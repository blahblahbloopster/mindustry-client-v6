package mindustry.client.ui

import arc.*
import arc.graphics.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import mindustry.*
import mindustry.Vars.player
import mindustry.Vars.tilesize
import mindustry.client.*
import mindustry.client.utils.*
import mindustry.entities.*
import mindustry.ui.dialogs.*
import mindustry.world.*

object FindDialog : BaseDialog("@find") {
    private val imageTable = Table()
    private val images = List(10) { Image() }
    private val inputField = TextField()
    private var guesses: List<Block> = emptyList()

    private fun updateGuesses() {
        guesses = Vars.content.blocks().copy().toMutableList().sortedBy { BiasedLevenshtein.biasedLevenshteinInsensitive(it.localizedName, inputField.text) }
    }

    init {
        var first = true
        for (img in images) {
            imageTable.add(img).size(64f).padBottom(if (first) 30f else 10f)
            imageTable.row()
            first = false
        }
        cont.add(inputField)
        cont.row()
        cont.add(imageTable)

        inputField.typed {
            updateGuesses()
            if (guesses.size >= images.size) {  // You can never be too careful
                for (i in images.indices) {
                    val guess = guesses[i]
                    images[i].setDrawable(guess.uiIcon)
                }
            }
        }

        keyDown {
            if (it == KeyCode.enter) {
                val block = guesses[0]
                val closest = Units.findAllyTile(player.team(), player.x, player.y, Float.MAX_VALUE / 2) {t -> t.block == block}
                if (closest == null) {
                    Vars.ui.chatfrag.addMessage("No ${block.localizedName} was found", "client", Color.coral.cpy().mul(0.75f))
                } else {
                    ClientVars.lastSentPos.set(closest.x / tilesize, closest.y / tilesize)
                    // FINISHME: Make the line below use toasts similar to UnitPicker.java
                    Vars.ui.chatfrag.addMessage("Found ${block.localizedName} at ${closest.x},${closest.y} (!go to go there)", "client", Color.coral.cpy().mul(0.75f))
                }
                Core.app.post(this::hide)
            }
        }

        setup()
        shown(this::setup)
        addCloseListener()
    }

    private fun setup() {
        inputField.clearText()
        Core.app.post {
            Core.app.post {
                inputField.requestKeyboard()
            }
        }

        images.forEach(Image::clear)
    }
}