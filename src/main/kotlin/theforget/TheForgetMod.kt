package theforget

import basemod.BaseMod
import basemod.interfaces.EditStringsSubscriber
import basemod.interfaces.EditCharactersSubscriber
import basemod.interfaces.PostDungeonInitializeSubscriber
import basemod.interfaces.PostInitializeSubscriber
import basemod.interfaces.PostUpdateSubscriber
import basemod.interfaces.StartGameSubscriber
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer
import com.badlogic.gdx.Gdx
import com.megacrit.cardcrawl.core.CardCrawlGame
import com.megacrit.cardcrawl.dungeons.AbstractDungeon
import org.apache.logging.log4j.LogManager
import theforget.characters.TheForgetCharacter
import theforget.core.TheForgetAssets
import theforget.core.TheForgetLocalization
import theforget.enums.TheForgetEnums
import theforget.reputation.ReputationSaveField
import theforget.reputation.ReputationState
import theforget.reputation.ReputationTopPanelItem
import theforget.reputation.vfx.ReputationCombatVfxController

@SpireInitializer
object TheForgetMod :
    PostInitializeSubscriber,
    PostDungeonInitializeSubscriber,
    PostUpdateSubscriber,
    StartGameSubscriber,
    EditCharactersSubscriber,
    EditStringsSubscriber {
    private val logger = LogManager.getLogger(TheForgetMod::class.java)

    private const val REPUTATION_SAVE_FIELD_ID: String = "theforget:Reputation"

    private var reputationTopPanelItem: ReputationTopPanelItem? = null
    private val reputationSaveField: ReputationSaveField = ReputationSaveField()
    private var reputationTopPanelAdded: Boolean = false

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
        val playerClass = requireNotNull(TheForgetEnums.THE_FORGET) { "TheForgetEnums.THE_FORGET was not initialized (SpireEnum failed?)" }
        BaseMod.addCharacter(
            TheForgetCharacter(CardCrawlGame.playerName),
            TheForgetAssets.CHAR_SELECT_BUTTON,
            TheForgetAssets.CHAR_SELECT_PORTRAIT,
            playerClass,
        )
    }

    override fun receiveEditStrings() {
        logger.info("Loading The Forget localization strings...")
        TheForgetLocalization.loadUiStrings()
    }

    override fun receivePostInitialize() {
        // Save fields are registered once at mod load.
        BaseMod.addSaveField<Int?>(REPUTATION_SAVE_FIELD_ID, reputationSaveField)
        logger.info("Registered save field: $REPUTATION_SAVE_FIELD_ID")

        logger.info("The Forget loaded successfully.")
    }

    override fun receiveStartGame() {
        // For new runs, reset to default. When loading a save, BaseMod will call our save field's onLoad.
        if (!CardCrawlGame.loadingSave) {
            ReputationState.reset()
        }
    }

    override fun receivePostDungeonInitialize() {
        val isTheForget = AbstractDungeon.player?.chosenClass == TheForgetEnums.THE_FORGET

        // Avoid reserving top panel space for other characters: add/remove dynamically per run.
        if (isTheForget && !reputationTopPanelAdded) {
            val item = reputationTopPanelItem ?: ReputationTopPanelItem().also { reputationTopPanelItem = it }
            BaseMod.addTopPanelItem(item)
            reputationTopPanelAdded = true
            logger.info("Reputation top panel item added")
        } else if (!isTheForget && reputationTopPanelAdded) {
            reputationTopPanelItem?.let { BaseMod.removeTopPanelItem(it) }
            reputationTopPanelAdded = false
            logger.info("Reputation top panel item removed")
        }
    }

    override fun receivePostUpdate() {
        ReputationCombatVfxController.update(Gdx.graphics.deltaTime)
    }
}
