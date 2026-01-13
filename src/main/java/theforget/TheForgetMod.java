package theforget;

import basemod.BaseMod;
import basemod.interfaces.PostInitializeSubscriber;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SpireInitializer
public class TheForgetMod implements PostInitializeSubscriber {
    private static final Logger logger = LogManager.getLogger(TheForgetMod.class.getName());

    public TheForgetMod() {
        BaseMod.subscribe(this);
        logger.info("{}: BaseMod subscribed", getClass().getSimpleName());
    }

    public static void initialize() {
        logger.info("Initializing The Forget mod...");
        new TheForgetMod();
    }

    @Override
    public void receivePostInitialize() {
        logger.info("The Forget loaded successfully.");
    }
}

