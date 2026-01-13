package theforget.core

/**
 * Centralized resource paths used by the mod.
 *
 * For the Issue #2 skeleton we intentionally reference assets that already exist in
 * Slay the Spire's `desktop-1.0.jar` so we can be "no-assets" and still be selectable.
 */
object TheForgetAssets {
    // Character select (use Ironclad assets as placeholders)
    const val CHAR_SELECT_BUTTON = "images/ui/charSelect/ironcladButton.png"
    const val CHAR_SELECT_PORTRAIT = "images/ui/charSelect/ironcladPortrait.jpg"

    // Animation + textures (use Ironclad assets as placeholders)
    const val SKELETON_ATLAS = "images/characters/ironclad/idle/skeleton.atlas"
    const val SKELETON_JSON = "images/characters/ironclad/idle/skeleton.json"

    const val SHOULDER_1 = "images/characters/ironclad/shoulder.png"
    const val SHOULDER_2 = "images/characters/ironclad/shoulder2.png"
    const val CORPSE = "images/characters/ironclad/corpse.png"

    // Energy orb (use built-in red orb assets so CustomPlayer doesn't crash on null textures).
    // Source of truth: ImageMaster ENERGY_RED_LAYER* loads these paths from desktop-1.0.jar.
    val ENERGY_ORB_TEXTURES: Array<String> = arrayOf(
        "images/ui/topPanel/red/layer1.png",
        "images/ui/topPanel/red/layer2.png",
        "images/ui/topPanel/red/layer3.png",
        "images/ui/topPanel/red/layer4.png",
        "images/ui/topPanel/red/layer5.png",
        "images/ui/topPanel/red/layer6.png",
        "images/ui/topPanel/red/layer1d.png",
        "images/ui/topPanel/red/layer2d.png",
        "images/ui/topPanel/red/layer3d.png",
        "images/ui/topPanel/red/layer4d.png",
        "images/ui/topPanel/red/layer5d.png",
    )

    const val ENERGY_ORB_VFX = "images/ui/topPanel/energyRedVFX.png"

    // Same default speeds used by common CustomEnergyOrb implementations.
    val ENERGY_ORB_LAYER_SPEEDS: FloatArray = floatArrayOf(
        -40.0f,
        -32.0f,
        20.0f,
        -20.0f,
        0.0f,
        -10.0f,
        -8.0f,
        5.0f,
        -5.0f,
        0.0f,
    )
}
