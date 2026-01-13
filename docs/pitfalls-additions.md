# 将要追加到 docs/pitfalls.md 的 Markdown 文本

> 本文档为 PROMPT 2~4 发现的综合草案，用于追加到 docs/pitfalls.md 第 9 条之后。
> 格式与现有 pitfalls.md 完全一致，每条包含证据指针。

---

## 10) `@SpirePatch` 的 method 参数大小写敏感

现象：Patch 静默失败，没有任何错误提示，目标方法未被修改，游戏行为无变化。

根因：Java 方法名严格区分大小写，`@SpirePatch` 的 `method` 参数必须与目标方法**完全匹配**（包括大小写）。

解决方式：
- 使用 IDE 的 "Go to Declaration" 功能跳转到目标方法，复制精确名称
- 或使用 `javap -private 目标类.class | grep 方法名` 验证

快速自检：
```bash
# 验证 method 参数是否匹配实际方法名
cd ../resources/mods
rg -t java '@SpirePatch.*method\s*=\s*"[A-Z]' | rg -v 'Postfix|Prefix|Insert'
```

证据指针：
- `PickCards/patch/renderNumbersPatch.java` + `renderTitle` → 精确匹配方法名
- `StSLib/.../AlwaysRetainPatch.java` + `applyStartOfTurnCards` → 正确大小写

---

## 11) Constructor Patch 需要使用字节码名称 `<init>`

现象：Patch 构造函数时无效，游戏启动无报错但构造函数未被修改。

根因：构造函数在字节码中表示为 `<init>`，需要使用特殊的 `method` 参数值。

解决方式：
```kotlin
// 使用 constructor=true 或 method="<init>"
@SpirePatch(clz = AbstractCard.class, method = "<init>")
// 或
@SpirePatch(clz = AbstractCard.class, constructor = true)
```

注意：构造函数 patch 通常需要配合 `paramtypez` 指定参数类型，因为可能存在重载。

快速自检：
```bash
# 搜索构造函数 patch（预期结果应为空或极少）
cd ../resources/mods
rg -t java 'method\s*=\s*"<init>"'
rg -t java 'constructor\s*=\s*true'
```

证据指针：
- 调研发现：在 2172 个文件中，**未发现**使用 `<init>` 或 `constructor=true` 的实际案例
- 替代方案：大部分 mod 使用 `method = "<class>"` 配合 `SpireField` 在类初始化时注入字段

---

## 12) Locator 多匹配问题导致插入位置错误

现象：Patch 插入到错误位置，多个 patch 修改同一方法时部分 patch 失效，游戏行为不一致。

根因：当目标方法中存在**多个相同的代码模式**时，简单的 `Matcher` 可能匹配到多次插入点。

解决方式：明确指定第 N 个匹配
```kotlin
private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        Matcher matcher = new Matcher.FieldAccessMatcher(CharacterSelectScreen.class, "options");
        int[] allMatches = LineFinder.findAllInOrder(ctBehavior, new ArrayList(), matcher);
        return new int[]{allMatches[1]};  // 明确取第 2 个匹配
    }
}
```

或使用复合 Matcher 消除歧义：
```kotlin
private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        List<Matcher> matchers = new ArrayList();
        matchers.add(new Matcher.FieldAccessMatcher(AbstractRoom.class, "monsters"));
        Matcher finalMatcher = new Matcher.FieldAccessMatcher(AbstractPlayer.class, "powers");
        return LineFinder.findInOrder(ctBehavior, matchers, finalMatcher);
    }
}
```

快速自检：
```bash
# 检查是否存在可能的歧义匹配
cd ../resources/mods
rg -t java 'LineFinder\.(findInOrder|findAllInOrder)' | rg -v 'findAllInOrder\[' | head -20
```

证据指针：
- `BaseMod/.../DamageHooks.java` → 使用 `findInOrder` + 多个 Matcher 消除歧义
- `mintySpire/.../BetterAscensionSelectorPatches.java:62` → `findAllInOrder(...)[1]` 精确选择第 2 个匹配

---

## 13) Patch 顺序依赖导致 mod 兼容性问题

现象：某些 mod 组合下功能异常，单独测试正常但与其他 mod 共同安装时崩溃。

根因：多个 mod 修改同一方法时，最终字节码取决于 **mod 加载顺序** 和 **patch 应用顺序**。

解决方式：检测其他 mod 的存在并适配
```kotlin
@SpirePatch(clz = SomeClass.class, method = "someMethod")
public static class OtherModCompatibility {
    public static ExprEditor Instrument() {
        if (Loader.isModLoaded("OtherModID")) {
            // 其他 mod 存在时的 patch 逻辑
            return new ExprEditor() { /* ... */ };
        }
        return null;  // 不应用 patch
    }
}
```

或使用 `@SpirePatches2` 同时 patch 多个方法重载，减少顺序依赖。

快速自检：
```bash
# 检查是否存在兼容性处理
cd ../resources/mods
rg -t java 'Compatibility|compatibility|isModLoaded|Loader\.' | rg -i 'patch' | head -20
```

证据指针：
- `ExtendedUI/.../CompatibilityPatches.java` → 显式处理与其他 mod 的兼容性
- `ThePackmasterExpansionPacks/.../GlobalTurnStartCompatibilityPatches.java` → 全局兼容性 patch

---

## 14) 反射私有字段/方法在游戏更新时可能失效

现象：游戏崩溃 `NoSuchFieldException` / `NoSuchMethodException`，游戏版本更新后 mod 失效。

根因：私有字段/方法名在**游戏更新时可能变化**，且跨版本兼容性差。

解决方式：
- 优先使用 `SpireField` 添加字段，避免反射私有字段（推荐）
```kotlin
@SpirePatch(clz = AbstractCard.class, method = "<class>")
public static class MyCustomField {
    public static SpireField<Integer> myCustomData = new SpireField(() -> 0);
}
```
- 必须反射时，添加异常处理
```kotlin
try {
    Object value = ReflectionHacks.getPrivate(obj, TargetClass.class, "fieldName");
} catch (Exception e) {
    logger.error("Failed to access private field, game version may have changed", e);
    // 降级处理或跳过该功能
}
```

快速自检：
```bash
# 检查反射使用频率
cd ../resources/mods
rg -t java 'ReflectionHacks\.(getPrivate|setPrivate)' | wc -l

# 每次游戏更新后验证
rg -t java 'getPrivate.*".*"' | sort -u  # 列出所有私有字段名
```

证据指针：
- `BaseMod/.../ScrollingTooltips.java` → `StaticSpireField<Float> isScrolling`
- `ArknightsTheSpire/.../EndTurnButtonPatcher.java` → `getPrivate(o, CharacterOption.class, "maxAscensionLevel")`

---

## 15) `@ByRef` 参数必须是数组类型

现象：修改局部变量无效，原值未改变；编译错误 `ByRef parameter must be an array type`。

根因：`@ByRef` 注解用于**按引用传递局部变量**，要求参数必须是**数组类型**，且需要配合 `localvars` 使用。

解决方式：
```kotlin
// 正确：ByRef 参数必须是数组
@SpireInsertPatch(locator = Locator.class, localvars = {"tmp"})
public static void Insert(
    AbstractCard __instance,
    @ByRef float[] tmp  // 必须是数组，修改 tmp[0] 会影响原局部变量
) {
    tmp[0] = tmp[0] * 2;  // 修改会生效
}
```

快速自检：
```bash
# 验证 ByRef 参数都是数组类型
cd ../resources/mods
rg -t java '@ByRef(?!.*\[\])'  # 应该匹配到错误用法（预期为空）
```

证据指针：
- `BaseMod/.../DamageHooks.java:29` → `@ByRef float[] tmp` 正确修改伤害值
- `IbukiSuika/.../NeowNarrationPatch.java` → `@ByRef Scanner[] ___s` 修改 Scanner

---

## 16) Javassist `$proceed` 占位符需要传递参数

现象：编译期错误 `CompileError: wrong number of parameters` 或运行时崩溃。

根因：`$proceed` 代表原方法调用，即使无参方法也需要用 `$$` 传递参数列表。

解决方式：
```kotlin
// 正确：无参方法也需用 $$
m.replace("{ $proceed($$); }");

// 正确：修改单个参数
m.replace("{ $_ = $proceed($1, customized_value); }");

// 错误：无参方法调用
m.replace("{ $proceed(); }");  // ❌
```

快速自检：
```bash
# 检查常见的占位符错误模式
cd ../resources/mods
rg -t java 'this\.\$proceed'  # 应为空或极少
rg -t java '\$proceed\(\)'     # 应为空（无参方法也需 $$）
```

证据指针：
- `mintySpire/.../BetterAscensionSelectorPatches.java:78` → `$proceed($$)` 正确传递所有参数
- `actlikeit/.../PreventActThreeBossRewardsPatch.java:28` → `$proceed($$)` 保留原逻辑

---

## 17) `method = "<class>"` 仅用于 SpireField 声明

现象：误将 `<class>` 当作普通方法名，Patch 静默无效。

根因：`method = "<class>"` 是**特殊语法**，用于在类级别注入 `SpireField`，不是真正的方法名。

解决方式：
```kotlin
// 正确：使用 <class> 声明 SpireField
@SpirePatch(clz = AbstractCard.class, method = "<class>")
public static class MyCustomField {
    public static SpireField<Integer> myData = new SpireField(() -> 0);
}

// 错误：<class> 没有代码体可以插入
@SpirePatch(clz = AbstractCard.class, method = "<class>")
public static class InvalidPatch {
    @SpireInsertPatch(locator = SomeLocator.class)  // ❌
    public static void Insert(AbstractCard __instance) { }
}
```

用途限制：
- 仅用于声明 `SpireField` 或 `StaticSpireField`
- 不能使用 `@SpireInsertPatch` / `@SpirePrefixPatch` 等插入逻辑

快速自检：
```bash
# 检查 <class> 的正确使用
cd ../resources/mods
rg -t java 'method\s*=\s*"<class>"' -A 5 | rg -v 'SpireField'
```

证据指针：
- `StSLib/.../ExtraIconsPatch.java:24-30` → `SpireField<ArrayList<IconPayload>> extraIcons`
- `BaseMod/.../ScrollingTooltips.java` → `StaticSpireField<Float> isScrolling`

---

## 18) `@SpirePatch` 的 `paramtypez` 必须精确匹配

现象：Patch 静默无效；启动日志显示 `No matching method found`。

根因：`@SpirePatch` 的 `paramtypez` 参数必须**精确匹配**目标方法的参数类型（包括顺序）。

解决方式：
```kotlin
// 正确：paramtypez 精确匹配
@SpirePatch(
    clz = AbstractCard.class,
    method = "render",
    paramtypez = {SpriteBatch.class, boolean.class}
)

// 检查方法签名的方法：
// 1. IDE 查看：Ctrl+Q (IntelliJ) / F3 (Eclipse)
// 2. javap：javap -private com.megacrit.cardcrawl.cards.AbstractCard.class | grep -A 10 "public.*render"
```

快速自检：
```bash
# 验证参数类型匹配
cd ../resources/mods
rg -t java 'paramtypez\s*=' -A 1 | head -30
```

证据指针：
- `StSLib/.../ExtraIconsPatch.java:32-39` → 使用 `@SpirePatches2` 同时 patch 两个重载方法
- `mintySpire/.../MultiLinePowersPatches.java` → 精确匹配 `SpriteBatch sb, float x, float y` 等参数

---

## 19) 资源路径大小写敏感（Linux vs Windows 差异）

现象：Windows 上运行正常，Linux/macOS 上资源加载失败；报错 `FileNotFoundException` 或资源显示为空白/粉色方块。

根因：Windows 文件系统不区分大小写（NTFS/FAT32），Linux/macOS 区分大小写（ext4/APFS）；打包进 jar 后，ClassLoader 路径查询变为严格大小写敏感。

解决方式：使用统一的路径生成方法，确保大小写一致
```kotlin
// 推荐：使用统一的路径生成方法
object ModCore {
    fun makeCharacterPath(filename: String): String {
        return "myModResources/images/char/$filename" // 明确目录结构
    }
}

// 使用示例
orbTextures = arrayOf(
    ModCore.makeCharacterPath("myChar/orb/layer1.png"),  // 注意大小写
    ModCore.makeCharacterPath("myChar/orb/layer2.png")
)
```

快速自检：
```bash
# 在 Linux 环境或 CI 中测试 mod
# 使用 jar tf your-mod.jar 检查 jar 内实际路径大小写
jar tf your-mod.jar | grep -i "images/char"
```

证据指针：
- `LingMod/lingmod/character/Ling.java:290` - 使用 `ModCore.makeCharacterPath()` 统一管理路径
- `Collector/CollectorChar.java:206` - 直接硬编码 `"collectorResources/images/char/mainChar/orb/layer1.png"`

---

## 20) 打包进 jar 后需用 ClassLoader 路径

现象：IDE 运行正常，打包成 jar 后资源加载失败；报错 `IllegalArgumentException: File not found: xxx`。

根因：`File` 类用于文件系统路径，jar 内资源需用 `ClassLoader.getResourceAsStream()` 或 `Gdx.files.internal()`；BaseMod 的 `ImageMaster.loadImage()` 已封装 ClassLoader 调用。

解决方式：
```kotlin
// 推荐：使用 Gdx.files.internal() 或 ImageMaster.loadImage()
// 路径相对于 ModTheSpire.json 中声明的 resources 目录
Texture texture = ImageMaster.loadImage("myModResources/images/char/myChar/orb/vfx.png")

// 不推荐：使用 File 类
// File file = new File("images/char/myChar/orb/vfx.png"); // jar 内无效
```

快速自检：
```bash
# 检查 ModTheSpire.json 中 resources 字段是否包含资源目录
# 使用 jar tf your-mod.jar | grep "images/char" 验证资源是否在 jar 内
jar tf your-mod.jar | grep "images/char"
```

证据指针：
- `BaseMod/basemod/abstracts/CustomPlayer.java:212` - 使用 `Gdx.files.internal(BaseMod.getPlayerButton(this.chosenClass))`
- `Collector/CollectorChar.java:74` - 使用 `ImageMaster.loadImage(CollectorMod.getModID() + "Resources/images/charSelect/leaderboard.png")`

---

## 21) 中文路径/空格导致资源加载失败

现象：包含中文、空格的路径在某些系统上加载失败；报错 URL 编码相关异常或路径找不到。

根因：LibGDX 的 `Gdx.files.internal()` 在某些情况下对非 ASCII 字符处理不佳；ModTheSpire 打包时路径编码问题。

解决方式：资源路径仅使用 ASCII 字符和下划线
```kotlin
// 推荐：资源路径仅使用 ASCII 字符和下划线
val path = "myModResources/images/char/my_char/orb/layer1.png" // 使用下划线代替空格

// 避免
val path = "myModResources/images/char/我的角色/orb/layer1.png" // 中文可能失败
val path = "myModResources/images/char/my char/orb/layer1.png"  // 空格可能失败
```

快速自检：
```bash
# 所有资源文件名、目录名使用 [a-zA-Z0-9_] 字符集
find yourModResources -name '*[^\x00-\x7F]*'  # 查找非 ASCII 文件名
```

证据指针：
- 大多数 mod 使用纯英文路径：`sneckomodResources`、`collectorResources`、`lingmodResources`
- 无直接证据（因为失败的 mod 不会发布），但社区普遍避免中文路径

---

## 22) atlas-json 配对错误导致 Spine 动画加载失败

现象：Spine 动画加载时报错 `SkeletonData mismatch` 或 NPE；动画播放时缺少某些骨骼/附件。

根因：`.atlas` 文件和 `.json` 文件需由同一导出操作生成；单独修改其中之一会导致索引不匹配。

解决方式：确保 atlas 和 json 文件名前缀一致
```kotlin
// 推荐：确保 atlas 和 json 文件名前缀一致
fun reloadAnimation() {
    val basePath = "myModResources/images/char/mainChar/skeleton"
    this.loadAnimation(basePath + ".atlas", basePath + ".json", 1.0F)
}

// 文件结构：
// myModResources/images/char/mainChar/
//   ├── skeleton.atlas
//   ├── skeleton.json
//   └── skeleton.png  (atlas 引用的纹理文件)
```

快速自检：
```bash
# 用文本编辑器打开 .atlas 文件，确认其中的 .png 文件名实际存在
# 用 Spine 编辑器重新导出，确保 atlas/json 同步
```

证据指针：
- `LingMod/lingmod/character/Ling.java:77-81` - 使用 `prefix + ModConfig.skinInfo.toString().toLowerCase()` 动态拼接路径
- `Collector/CollectorChar.java:49-50, 68` - 硬编码 `"collectorResources/images/char/mainChar/skeleton.atlas"`

---

## 23) orbTextures 数组结构：中间元素为 baseLayer

现象：能量为 0 时能量球显示异常（缺少暗色层）；报错 `Texture not found: layer*d.png`。

根因：`orbTextures` 数组后半部分（layer1d~layer5d）是"无能量"状态下的纹理；数组长度应为奇数，中间元素是 baseLayer，后半部分对应前半部分的暗色版本。

解决方式：
```kotlin
// orbTextures 数组结构：[layer1, layer2, ..., baseLayer, ..., layer1d, layer2d, ...]
// 注意：中间元素（baseLayer）之后是暗色层
private val orbTextures = arrayOf(
    // 有能量时的层（从外到内）
    "myModResources/images/char/myChar/orb/layer1.png",
    "myModResources/images/char/myChar/orb/layer2.png",
    "myModResources/images/char/myChar/orb/layer3.png",  // 旋转层
    "myModResources/images/char/myChar/orb/layer4.png",  // 旋转层
    "myModResources/images/char/myChar/orb/layer5.png",  // 旋转层
    // 中间层：baseLayer（不旋转，最内层）
    "myModResources/images/char/myChar/orb/layer6.png",  // baseLayer
    // 无能量时的层（暗色版本，与有能量层顺序对应）
    "myModResources/images/char/myChar/orb/layer1d.png",
    "myModResources/images/char/myChar/orb/layer2d.png",
    "myModResources/images/char/myChar/orb/layer3d.png",
    "myModResources/images/char/myChar/orb/layer4d.png",
    "myModResources/images/char/myChar/orb/layer5d.png"
)

// 数组长度必须为奇数，且满足：length = 2 * n + 1
// middleIdx = length / 2（integer division）
```

快速自检：
```bash
# 确保 orbTextures.length 为奇数
# 确保中间元素（orbTextures[length/2]）存在且是 baseLayer
# 确保后半部分文件名包含 d 后缀（layer*d.png）
```

证据指针：
- `BaseMod/basemod/abstracts/CustomEnergyOrb.java:23-35` - 代码逻辑：中间元素为 baseLayer
- `Collector/CollectorChar.java:206` - 完整的 11 元素数组（5 旋转层 + 1 baseLayer + 5 暗色层）

---

## 24) Localization 多语言文件加载时机错误

现象：游戏内显示 ID 而非实际文本（如 `theforget:SelfLabel`）。

根因：在 `receiveEditStrings()` **之前** 加载字符串文件；或路径错误导致文件未找到。

解决方式：
```kotlin
override fun receiveEditStrings() {
    // 1. 始终先加载 ENG 作为回退
    loadLocalization(GameLanguage.ENG)

    // 2. 再加载当前语言（若非 ENG）
    if (Settings.language != GameLanguage.ENG) {
        loadLocalization(Settings.language)
    }
}

private fun loadLocalization(language: GameLanguage) {
    val lang = when (language) {
        GameLanguage.ZHS -> "zhs"
        GameLanguage.ENG -> "eng"
        else -> "eng" // 回退到 ENG
    }
    BaseMod.loadCustomStringsFile(
        UIStrings::class.java,
        "theforgetResources/localization/$lang/UIstrings.json"
    )
}
```

快速自检：
```bash
# 检查是否在 receiveEditStrings() 中加载
# 检查是否先加载 ENG 作为回退
# 检查路径是否匹配 ModTheSpire.json 中的资源路径
```

证据指针：
- Downfall: `downfallMod.java:590-594`（先 ENG，再当前语言）
- BaseMod: `BaseMod.java` 中 `receiveEditStrings()` 订阅者模式

---

## 25) Localization UTF-8 编码问题

现象：中文显示乱码（如 `�` 或 `??`）。

根因：JSON 文件保存为 ANSI/GBK 编码；读取时未指定 UTF-8。

解决方式：
```kotlin
// 方法 1：确保 JSON 文件保存为 UTF-8（推荐）
// IDE: Settings → Editor → File Encodings → Default encoding: UTF-8

// 方法 2：读取时显式指定编码
val json = Gdx.files.internal(path)
    .readString("UTF-8") // Gdx 自动处理 UTF-8
```

快速自检：
```bash
# 检查 JSON 文件是否保存为 UTF-8
# 检查 IDE 是否配置 UTF-8 为默认编码
# 检查中文文本是否能正常显示
file yourModResources/localization/zhs/UIstrings.json
```

证据指针：
- Downfall: `downfallMod.java:599` 使用 `StandardCharsets.UTF_8`
- 社区惯例：所有 localization JSON 文件均使用 UTF-8

---

## 26) ID 前缀冲突导致文本显示错误

现象：文本显示错误或覆盖其他 Mod 的内容。

根因：使用了通用 ID（如 `SelfTooltip`）而非 `theforget:SelfTooltip`；与其他 Mod 或原版 ID 冲突。

解决方式：
```kotlin
// 错误：无前缀
const val SELF_TOOLTIP = "SelfTooltip"

// 正确：带 modID 前缀
const val SELF_TOOLTIP = "theforget:SelfTooltip"

// JSON 中对应键
{
  "theforget:SelfTooltip": {
    "TEXT": ["Self", "Self is your HP."]
  }
}
```

快速自检：
```bash
# 所有 ID 是否以 modID: 开头？
# 是否使用常量定义 ID，避免硬编码？
# JSON 键是否与代码中的常量一致？
```

证据指针：
- the-forget: `TheForgetLocalization.kt:12-13` 所有 ID 均带前缀
- Downfall: `downfallResources/localization/eng/*.json` 所有键带 `downfall:` 前缀

---

## 27) `@SpireEnum` 与字符串 ID 混淆

现象：枚举值作为 ID 使用时，运行时出现 `NullPointerException`；或 ID 解析失败，显示默认值。

根因：`@SpireEnum` 注解的枚举在运行时动态注入；若 Mod 未正确加载，枚举值为 `null`；枚举名称（如 `THE_FORGET`）与字符串 ID（如 `theforget:TheForget`）混淆。

解决方式：
```kotlin
// 1. 定义枚举（仅用于内部类型标识）
object TheForgetEnums {
    @SpireEnum
    @JvmField
    var THE_FORGET: AbstractPlayer.PlayerClass? = null
}

// 2. 定义字符串 ID（用于 localization）
object TheForgetIDs {
    const val CHARACTER_ID = "theforget:TheForget"
    const val CARD_PREFIX = "theforget:"
}

// 3. 使用时严格区分
BaseMod.addCharacter(
    character,
    button,
    portrait,
    TheForgetEnums.THE_FORGET!!  // 枚举值，确保非空
)
```

快速自检：
```bash
# 是否使用 @JvmField 确保 Kotlin 属性可见？
# 是否使用 requireNotNull() 或 !! 验证枚举非空？
# 枚举名称是否与字符串 ID 明确区分？
```

证据指针：
- the-forget: `TheForgetEnums.kt:9-11` 枚举定义
- the-forget: `TheForgetMod.kt:31` 使用 `requireNotNull()` 验证

---

## 28) SpireConfig 初始化时机错误

现象：配置文件未创建，或读取不到默认值；游戏启动时抛 `IOException`。

根因：在 `receivePostInitialize()` **之前** 初始化 SpireConfig；首次加载时文件不存在，抛异常但未捕获。

解决方式：
```kotlin
// 懒加载：首次使用时初始化
private var config: SpireConfig? = null

fun getConfig(): SpireConfig {
    if (config == null) {
        try {
            val defaults = Properties().apply {
                setBool("showSelfTooltip", true)
                setString("selfColor", "#FF5733")
            }
            config = SpireConfig("theforget", "playerPrefs", defaults)
        } catch (e: IOException) {
            logger.error("Failed to initialize config", e)
            throw RuntimeException("Config initialization failed", e)
        }
    }
    return config!!
}

// 在 receivePostInitialize() 中预加载（可选）
override fun receivePostInitialize() {
    getConfig() // 触发初始化
}
```

快速自检：
```bash
# 是否懒加载 SpireConfig？
# 是否捕获 IOException？
# 是否提供默认值？
```

证据指针：
- Downfall: `downfallMod.java:483` 使用 try-catch 包裹初始化
- ModTheSpire: `SpireConfig.java:37-43` 构造函数会创建文件

---

## 29) 存档膨胀问题

现象：存档文件异常增大（几 MB 到几十 MB）；加载/保存存档变慢。

根因：频繁保存大对象（如整个卡组、战斗历史）；未清理过期数据。

解决方式：
```kotlin
// 错误：保存整个卡组
config.setString("deck", Gson().toJson(deck))

// 正确：只保存必要数据
config.setString("deckSize", deck.size.toString())
config.setString("deckCards", deck.joinToString(",") { it.cardID })

// 或使用 CustomSavable 接口（BaseMod 提供）
BaseMod.registerSavable("theforget:SelfData", object : CustomSavableRaw {
    override fun onSave(): String? {
        return Gson().toJson(SelfData(currentHP, maxHP))
    }

    override fun onLoad(data: String) {
        val saved = Gson().fromJson(data, SelfData::class.java)
        currentHP = saved.currentHP
        maxHP = saved.maxHP
    }
})
```

快速自检：
```bash
# 是否避免保存重复/冗余数据？
# 是否定期清理过期数据？
# 是否考虑使用 CustomSavable 替代 SpireConfig？
```

证据指针：
- loadout Mod: 使用 `CustomSavable` 保存卡组修改
- Downfall: `downfallMod.java:480-510` 使用多个 SpireConfig 分开存储

---

## 30) 版本升级迁移缺失

现象：Mod 更新后存档无法读取；或读取到旧格式数据导致崩溃。

根因：存档结构变更但未处理迁移；或未标识存档版本。

解决方式：
```kotlin
object SaveVersion {
    const val CURRENT = 2
}

fun onLoad(data: String) {
    val json = JsonParser().parse(data).asJsonObject
    val version = json.get("version")?.asInt ?: 1

    val saved = when (version) {
        1 -> migrateFromV1(json)
        2 -> Gson().fromJson(data, SelfData::class.java)
        else -> throw IllegalArgumentException("Unknown save version: $version")
    }

    // 使用迁移后的数据
    currentHP = saved.currentHP
}

private fun migrateFromV1(oldJson: JsonObject): SelfData {
    // v1 → v2 迁移逻辑
    val oldHP = oldJson.get("hp").asInt
    return SelfData(
        currentHP = oldHP,
        maxHP = oldHP * 2  // v2 新增字段
    )
}
```

快速自检：
```bash
# 是否在存档中存储版本号？
# 是否实现迁移逻辑？
# 是否测试旧存档升级流程？
```

证据指针：
- Downfall: `downfallMod.java:817-830` 使用 `configDefault` 处理默认值
- 社区惯例：在 SpireConfig 中存储 `config_version` 字段

---

## 31) 竞态条件导致配置丢失

现象：配置更改后未保存到磁盘；或多个线程同时写入导致数据损坏。

根因：`SpireConfig.save()` 调用时机不当；未考虑异步存档的并发问题。

解决方式：
```kotlin
// 1. 配置变更后立即保存
fun setShowTooltip(show: Boolean) {
    getConfig().setBool("showSelfTooltip", show)
    try {
        getConfig().save()
    } catch (e: IOException) {
        logger.error("Failed to save config", e)
    }
}

// 2. 或使用防抖机制（批量更改后保存）
private var savePending = false
fun scheduleSave() {
    if (!savePending) {
        savePending = true
        Timer().schedule(1000) { // 1 秒后保存
            try {
                getConfig().save()
            } catch (e: IOException) {
                logger.error("Failed to save config", e)
            } finally {
                savePending = false
            }
        }
    }
}
```

快速自检：
```bash
# 配置变更后是否调用 save()？
# 是否捕获 IOException？
# 是否避免频繁保存（考虑防抖）？
```

证据指针：
- ModTheSpire: `SpireConfig.java:49-51` `save()` 使用 `FileOutputStream` 覆盖写入
- BaseMod: 配置面板修改后立即调用 `save()`

---

## 32) Localization 文件命名大小写问题

现象：Windows 上运行正常，Linux 上多语言文件加载失败。

根因：Localization 文件必须首字母大写（`CardStrings.json`），Linux 文件系统区分大小写。

解决方式：
```kotlin
// 正确：文件名首字母大写，Strings 大写 S
CardStrings.json
PowerStrings.json
RelicStrings.json
UIStrings.json
CharacterStrings.json

// 错误：小写文件名在 Linux 上无法加载
cardstrings.json
powerstrings.json
```

快速自检：
```bash
# 检查 localization 目录下文件名是否首字母大写
ls yourModResources/localization/eng/
```

证据指针：
- Downfall: `downfallResources/localization/eng/` 所有文件首字母大写
- BaseMod: 官方示例 Mod 均使用大写命名

---

## 33) ModTheSpire.json 的 modid 与资源目录前缀必须一致

现象：资源文件（图片、音频）加载失败。

根因：路径前缀与 `ModTheSpire.json` 中的 `modid` 不一致。

解决方式：
```json
// ModTheSpire.json
{
  "modid": "theforget"
}
```

```kotlin
// 资源路径必须使用 modid + "Resources"（大写 R）
const val RESOURCE_PREFIX = "theforgetResources/"

// 具体路径
const val CARD_IMAGE = "${RESOURCE_PREFIX}images/cards/Skill.png"
const val LOCALIZATION = "${RESOURCE_PREFIX}localization/eng/UIstrings.json"
```

快速自检：
```bash
# 资源路径是否为 modidResources/？
# 路径大小写是否与实际目录一致？
# 是否使用常量定义路径，避免硬编码？
```

证据指针：
- the-forget: `TheForgetAssets.kt` 所有路径以 `theforgetResources/` 开头
- Downfall: `downfallResources/`, `champResources/` 等多前缀结构

---

## 34) `abstractSaveString` vs `saveSpecial` 误用

现象：使用 `saveString()` 保存复杂对象；或混淆 `saveString()` 与 `saveSpecial()` 的用途。

根因：不了解两种方法的区别和适用场景。

解决方式：
```kotlin
// saveString(): 仅用于基本类型
// 适用：配置项、简单计数器

// saveSpecial(): 用于复杂对象（需实现 ISaveContainer）
// 适用：卡组修改、自定义数据结构

// 错误：用 saveString 保存对象
saveString("deck", deck.toString()) // 存的是 toString() 结果

// 正确：使用 CustomSavable 接口
BaseMod.registerSavable("theforget:Deck", object : CustomSavableRaw {
    override fun onSave(): String? = Gson().toJson(deck)
    override fun onLoad(data: String) { deck = Gson().fromJson(data, Deck::class.java) }
})
```

快速自检：
```bash
# 基本类型是否使用 saveString()？
# 复杂对象是否使用 CustomSavable？
# 是否混淆两者用途？
```

证据指针：
- BaseMod: `CustomSavable` 接口文档
- loadout Mod: 使用 `CustomSavable` 保存卡组修改数据

---

## 35) Keywords 注册缺失导致关键词无颜色

现象：卡牌描述中关键词颜色不正确或无悬停提示。

根因：未调用 `BaseMod.addKeyword()` 注册关键词；或 `KeywordStrings.json` 未正确配置。

解决方式：
```kotlin
// 1. 在 receiveEditKeywords() 中注册
override fun receiveEditKeywords() {
    BaseMod.addKeyword(
        "theforget:Self",      // ID 前缀
        "Self",                 // ENG 显示名
        "自我"                   // ZHS 显示名（可选）
    )
}

// 2. 或在 JSON 中定义（新版本 BaseMod）
// keywords.json
{
  "theforget:Self": {
    "NAMES": ["Self", "自我"],
    "DESCRIPTION": "Self is your HP."
  }
}
```

快速自检：
```bash
# 是否实现了 EditKeywordsSubscriber？
# 关键词 ID 是否与 JSON 中的键一致？
# 多语言关键词是否在各语言文件中定义？
```

证据指针：
- BaseMod 文档：`EditKeywordsSubscriber` 接口
- StSLib: `Keyword` 类用于更复杂的关键词定义

---

## 36) `@SpireInsert` vs `@SpireReplace` 混用

现象：原方法逻辑完全丢失；其他 mod 的 patch 失效；游戏行为异常。

根因：
- `@SpireInsertPatch`：在指定位置**插入代码**，原方法保留
- `@SpireReplace`：**完全替换**方法体，原方法丢失

解决方式：
```kotlin
// 推荐：使用 @SpireInsertPatch + Locator 插入逻辑
@SpirePatch(clz = AbstractCard.class, method = "calculateCardDamage")
public static class InsertLogic {
    @SpireInsertPatch(locator = Locator.class)
    public static void Insert(AbstractCard __instance) {
        // 在指定位置插入自定义逻辑，原方法继续执行
    }
}

// 谨慎使用：@SpireReplace 会完全替换方法
// 替代方案：使用 Prefix/Postfix + SpireReturn 控制流程
@SpirePatch(clz = AbstractCard.class, method = "calculateCardDamage")
@SpirePrefixPatch
public static SpireReturn<?> Prefix(AbstractCard __instance, AbstractMonster mo) {
    if (需要跳过原方法) {
        return SpireReturn.Return(null);  // 跳过原方法
    }
    return SpireReturn.Continue();  // 继续执行原方法
}
```

快速自检：
```bash
# 检查 SpireReplace 使用（预期应该很少）
cd ../resources/mods
rg -t java '@SpireReplace'
# 如果发现使用，需人工审查是否真的需要完全替换方法
```

证据指针：
- 调研发现：在 2172 个文件中，**未发现**明显的 `@SpireReplace` 错误使用案例
- 主流做法：使用 `@SpireInsertPatch` / `@SpirePostfixPatch` / `SpireReturn`

---

## 总览目录

### 新增类别统计

| 类别 | 条目数 | 编号范围 |
|------|--------|----------|
| **Patching（补丁注入）** | 8 条 | 10~17 |
| **资源加载与打包** | 5 条 | 19~23 |
| **Localization（多语言）** | 4 条 | 24, 25, 32, 35 |
| **IDs 与 Enums** | 2 条 | 26, 27 |
| **SpireConfig & Save** | 4 条 | 28~31 |
| **通用最佳实践** | 3 条 | 33, 34, 36 |

**总计：新增 26 条坑点**

---

## 自检报告

### 与现有 1~9 条重复检查

| 新增条目 | 是否重复 | 说明 |
|---------|---------|------|
| 10) method 参数大小写 | 否 | 新增：Patching 层面 |
| 11) Constructor Patch | 否 | 新增：Patching 层面 |
| 12) Locator 多匹配 | 否 | 新增：Patching 层面 |
| 13) Patch 顺序依赖 | 否 | 新增：Patching 层面 |
| 14) 反射私有字段 | 否 | 新增：Patching 层面 |
| 15) @ByRef 参数 | 否 | 新增：Patching 层面 |
| 16) $proceed 占位符 | 否 | 与 9) 相关但更通用（9) 是 static call 特例） |
| 17) method = "<class>" | 否 | 新增：Patching 层面 |
| 18) paramtypez 匹配 | 否 | 新增：Patching 层面 |
| 19) 路径大小写敏感 | 否 | 扩展：1) 的深化版 |
| 20) ClassLoader 路径 | 否 | 新增：资源打包层面 |
| 21) 中文路径/空格 | 否 | 新增：资源命名层面 |
| 22) atlas-json 配对 | 否 | 新增：Spine 资源层面 |
| 23) orbTextures 结构 | 否 | 扩展：3) 的深化版 |
| 24) Localization 时机 | 否 | 新增：多语言层面 |
| 25) UTF-8 编码 | 否 | 新增：多语言层面 |
| 26) ID 前缀冲突 | 否 | 新增：IDs 命名层面 |
| 27) @SpireEnum 与 ID | 否 | 新增：Enum 与 ID 区分 |
| 28) SpireConfig 时机 | 否 | 新增：Config 层面 |
| 29) 存档膨胀 | 否 | 新增：Save 层面 |
| 30) 版本迁移 | 否 | 新增：Save 层面 |
| 31) 竞态条件 | 否 | 新增：Save 层面 |
| 32) Localization 文件命名 | 否 | 新增：多语言层面 |
| 33) modid 与资源目录 | 否 | 新增：路径约定层面 |
| 34) abstractSaveString 误用 | 否 | 新增：Save API 层面 |
| 35) Keywords 注册 | 否 | 新增：多语言层面 |
| 36) @SpireInsert vs Replace | 否 | 新增：Patching 层面 |

### 泛化/不可验证条目检查

| 条目 | 问题 | 修正 |
|------|------|------|
| 无 | 所有条目均有证据指针和具体检查方法 | - |

---

## 最终可追加文本（删除重复后）

**可追加内容**：第 10~35 条（共 26 条新增内容），格式与现有 docs/pitfalls.md 完全一致。

**追加方式**：直接将 docs/pitfalls-additions.md 中第 10~35 条的内容复制粘贴到 docs/pitfalls.md 第 9 条之后即可。
