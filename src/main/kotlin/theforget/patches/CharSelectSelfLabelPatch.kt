package theforget.patches

import com.evacipated.cardcrawl.modthespire.lib.SpireInstrumentPatch
import com.evacipated.cardcrawl.modthespire.lib.SpirePatch2
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

@SpirePatch2(
    clz = com.megacrit.cardcrawl.screens.charSelect.CharacterOption::class,
    method = "renderInfo",
)
object CharSelectSelfLabelPatch {
    @JvmStatic
    @SpireInstrumentPatch
    fun instrument(): ExprEditor {
        return object : ExprEditor() {
            override fun edit(m: MethodCall) {
                if (m.className != "com.megacrit.cardcrawl.helpers.FontHelper") return
                if (m.methodName != "renderSmartText") return

                m.replace(
                    """
                    {
                      try {
                        if (${ '$' }0.c != null && ${ '$' }0.c.chosenClass == theforget.enums.TheForgetEnums.THE_FORGET) {
                          String hpPrefix = com.megacrit.cardcrawl.screens.charSelect.CharacterOption.TEXT[4];
                          if (${ '$' }3 != null && ${ '$' }3.startsWith(hpPrefix)) {
                            com.megacrit.cardcrawl.localization.UIStrings ui =
                              com.megacrit.cardcrawl.core.CardCrawlGame.languagePack.getUIString(theforget.core.TheForgetLocalization.SELF_LABEL_KEY);
                            if (ui != null && ui.TEXT != null && ui.TEXT.length > 0) {
                              ${ '$' }3 = ui.TEXT[0] + ${ '$' }3.substring(hpPrefix.length());
                            }
                          }
                        }
                      } catch (Exception ignored) { }
                      ${ '$' }proceed(${ '$' }${ '$' });
                    }
                    """.trimIndent(),
                )
            }
        }
    }
}
