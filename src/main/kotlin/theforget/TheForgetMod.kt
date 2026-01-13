package theforget

import basemod.BaseMod
import basemod.interfaces.EditCharactersSubscriber
import basemod.interfaces.PostInitializeSubscriber
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer
import com.megacrit.cardcrawl.core.CardCrawlGame
import org.apache.logging.log4j.LogManager
import theforget.characters.TheForgetCharacter
import theforget.core.TheForgetAssets
import theforget.enums.TheForgetEnums

@SpireInitializer
object TheForgetMod : PostInitializeSubscriber, EditCharactersSubscriber {
    private val logger = LogManager.getLogger(TheForgetMod::class.java)

    @JvmStatic
    fun initialize() {
        runCatching {
            logger.info("Initializing The Forget mod...")
            BaseMod.subscribe(this)
            logger.info("TheForgetMod: BaseMod subscribed")
        }.onFailure { t ->
            logger.error("Failed to initialize The Forget mod", t)
            throw t
        }
    }

    override fun receiveEditCharacters() {
        logger.info("Registering The Forget character...")
        BaseMod.addCharacter(
            TheForgetCharacter(CardCrawlGame.playerName),
            TheForgetAssets.CHAR_SELECT_BUTTON,
            TheForgetAssets.CHAR_SELECT_PORTRAIT,
            TheForgetEnums.THE_FORGET,
        )
    }

    override fun receivePostInitialize() {
        logger.info("The Forget loaded successfully.")
    }
}
