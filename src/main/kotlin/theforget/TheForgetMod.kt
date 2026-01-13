package theforget

import basemod.BaseMod
import basemod.interfaces.PostInitializeSubscriber
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer
import org.apache.logging.log4j.LogManager

@SpireInitializer
object TheForgetMod : PostInitializeSubscriber {
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

    override fun receivePostInitialize() {
        logger.info("The Forget loaded successfully.")
    }
}

