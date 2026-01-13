# CustomPlayer 资源相关坑点参考

开发 STS 角色类 mod 时的常见资源加载问题，聚焦 CustomPlayer、Spine 动画、能量球、角色选择界面、TopPanel/tooltip。

---

## 1. 路径大小写敏感

**现象**：Windows 下正常，Linux/Mac 下游戏崩溃或资源加载失败。

**根因**：Linux 文件系统区分大小写，Windows 不区分。代码中的路径与实际文件名大小写不匹配。

**推荐做法**：
```java
// 正确做法：使用工具函数统一路径管理
public class ModPaths {
    public static String makeCharPath(String file) {
        return "ModResources/images/char/" + file;
    }
}
loadAnimation(ModPaths.makeCharPath("player.atlas"), ModPaths.makeCharPath("player.json"), 1.0F);
```

**证据指针**：
- `Downfall/sneckomod/TheSnecko.java:51` - 使用动态路径变量
- `LingMod/lingmod/character/Ling.java:77-80` - 使用 `ModCore.makeCharacterPath()`

**自检命令**：
```bash
# 检查代码中的路径与实际文件名大小写是否匹配
find ../resources/mods -name "*.java" -exec grep -l "\.atlas\|\.json" {} \; | xargs grep -oh "[^\"']*\.[Aa][Tt][Ll][Aa][Ss]"
```

---

## 2. 打包进 jar 后路径变化

**现象**：IDE 运行正常，打包后游戏崩溃。报错信息包含 `classpath` 或 `ClassLoader` 相关。

**根因**：开发时使用 `File API` 访问文件系统路径，打包后资源在 jar 内，必须使用 `ClassLoader.getResource()`。

**推荐做法**：
```java
// 错误示例：使用 File API
File file = new File("images/char/player.png");  // 打包后无法访问

// 正确做法：使用 Gdx.files.internal
Texture tex = new Texture(Gdx.files.internal("ModResources/images/char/player.png"));
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomPlayer.java:212` - 使用 `Gdx.files.internal`
- `AnonMod/characters/char_Anon.java:83` - 直接使用 `new SpriterAnimation(filepath)`

**自检命令**：
```bash
grep -rn "new File\(" ../resources/mods --include="*.java"
```

---

## 3. atlas-json 配对错误

**现象**：角色动画不显示或显示错乱。报 `Spine: Skeleton not found` 或 `Atlas region not found`。

**根因**：`.atlas` 文件和 `.json` 文件不匹配，或使用了不同版本的 Spine 导出。

**推荐做法**：
```java
// 确保同名配对
String basePath = "ModResources/images/char/player";
loadAnimation(basePath + ".atlas", basePath + ".json", 1.0F);

// 验证文件存在性
if (Gdx.files.internal(basePath + ".atlas").exists() &&
    Gdx.files.internal(basePath + ".json").exists()) {
    loadAnimation(...);
}
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomPlayer.java:78` - 构造函数中自动调用 `loadAnimation`
- `Downfall/sneckomod/TheSnecko.java:51` - 使用动态路径确保配对

**自检命令**：
```bash
# 检查 atlas 和 json 文件是否成对存在
find ../resources/mods -name "*.atlas" | while read f; do
  json="${f%.atlas}.json"
  if [ ! -f "$json" ]; then echo "Missing pair: $f"; fi
done
```

---

## 4. 资源未放在 ModTheSpire.json 声明的 resources 目录

**现象**：IDE 运行正常，打包后资源加载失败。报错 `Resource not found: xxxResources/...`。

**根因**：`ModTheSpire.json` 未声明资源目录，或声明路径与实际不符。

**推荐做法**：
```json
// ModTheSpire.json
{
  "resources": ["ModResources/"]
}
```

```java
// 代码中的路径前缀必须与声明一致
public static final String RESOURCE_PATH = "ModResources/";
loadAnimation(RESOURCE_PATH + "images/char/player.atlas", ...);
```

**证据指针**：
- `LingMod/lingmod/ModCore.java:makeCharacterPath()` - 使用统一前缀 `ModResources`
- `Koishi/Koishi/characters/KoishiCharacter.java:51` - 使用 `KoishiResources` 前缀

**自检命令**：
```bash
grep -h "\"resources\"" -A10 ../resources/mods/*/ModTheSpire.json
```

---

## 5. orbTextures/orbVfxPath 传 null 导致复用原版资源

**现象**：自定义角色使用了铁甲师的红色能量球。

**根因**：`CustomEnergyOrb` 构造函数在 `orbTexturePaths == null` 时回退到 `ImageMaster.ENERGY_RED_*`。

**推荐做法**：
```java
// 占位阶段：复用原版能量球
super(name, playerClass, (String[])null, null, (String)null, (String)null);

// 正式版：提供完整资源
private static final String[] ORB_TEXTURES = new String[] {
    "ModResources/images/char/orb/layer1.png",
    // ... 共 11 个文件
};
super(name, playerClass, ORB_TEXTURES, "ModResources/images/char/orb/vfx.png", LAYER_SPEED, ...);
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomEnergyOrb.java:38-52` - null 处理逻辑
- `LingMod/lingmod/character/Ling.java:65` - 传入 `(String)null, (String)null)`

**自检命令**：
```bash
grep -rn "orbTextures.*null\|orbVfxPath.*null" ../resources/mods --include="*.java"
```

---

## 6. layer1..6d 资源缺失导致渲染异常

**现象**：能量球有能量时正常，无能量时显示白色方块或消失。

**根因**：`orbTextures` 数组长度应为奇数（中间是 base，前后对称），缺少无能量状态的层（带 d 后缀的文件）。

**推荐做法**：
```java
// 正确的数组结构：[enabled1..5, base, disabled1..5]
// 总共 11 个元素（5层+base+5层）
private static final String[] ORB_TEXTURES = new String[] {
    "layer1.png", "layer2.png", "layer3.png", "layer4.png", "layer5.png",
    "layer6.png",  // base
    "layer1d.png", "layer2d.png", "layer3d.png", "layer4d.png", "layer5d.png"
};
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomEnergyOrb.java:22-25` - 长度和奇数校验
- `Downfall/sneckomod/TheSnecko.java:35` - 完整的 11 层数组

**自检命令**：
```bash
ls ModResources/images/char/orb/{layer{1..5}.png,layer6.png,layer{1..5}d.png}
```

---

## 7. layerSpeeds 数组长度不匹配

**现象**：能量球动画速度异常。报错 `ArrayIndexOutOfBoundsException`。

**根因**：`layerSpeeds` 长度必须等于 `orbTextures.length / 2`（层数）。

**推荐做法**：
```java
// 5层动画需要5个速度值
private static final float[] LAYER_SPEED = new float[] {
    -20.0F,   // layer1 逆时针
    20.0F,    // layer2 顺时针
    -40.0F,   // layer3 快速逆时针
    40.0F,    // layer4 快速顺时针
    360.0F    // layer5 特效层旋转
};
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomEnergyOrb.java:55-62` - 默认值和校验
- `AnonMod/characters/char_Anon.java:48` - 完整的 10 元素速度数组

**自检命令**：
```bash
grep -A10 "LAYER_SPEED" ../resources/mods --include="*.java" | grep "new float\[\]" -A10
```

---

## 8. ScreenShake 参数过强导致"抖动很久"

**现象**：点击角色后屏幕剧烈震动，持续数秒。

**根因**：`ShakeIntensity.HIGH` 或 `ShakeDur.LONG` 参数过于极端。

**推荐做法**：
```java
@Override
public void doCharSelectScreenSelectEffect() {
    CardCrawlGame.sound.playA("SELECT_SOUND", MathUtils.random(-0.2F, 0.2F));
    CardCrawlGame.screenShake.shake(
        ShakeIntensity.LOW,   // 或 MED，避免 HIGH
        ShakeDur.SHORT,       // 避免 LONG、VERY_LONG
        false
    );
}
```

**证据指针**：
- `Downfall/sneckomod/TheSnecko.java:90` - 使用 `ShakeIntensity.LOW, ShakeDur.SHORT`
- `Koishi/Koishi/characters/KoishiCharacter.java:87` - 同样的 LOW/SHORT 组合

**自检命令**：
```bash
grep -rn "ShakeIntensity.HIGH\|ShakeDur.LONG\|ShakeDur.VERY_LONG" ../resources/mods --include="*.java"
```

---

## 9. 选中效果/头像资源缺失

**现象**：角色选择界面头像显示为黑色方块。

**根因**：`initializeClass` 参数中的 shoulder1/shoulder2/corpse 路径错误或文件不存在。

**推荐做法**：
```java
public static final String SHOULDER1 = ModPaths.makeCharPath("shoulder.png");
public static final String SHOULDER2 = ModPaths.makeCharPath("shoulder2.png");
public static final String CORPSE = ModPaths.makeCharPath("corpse.png");

// 构造时验证
if (Gdx.files.internal(SHOULDER1).exists()) {
    this.initializeClass(null, SHOULDER1, SHOULDER2, CORPSE, getLoadout(), ...);
}
```

**证据指针**：
- `AnonMod/characters/char_Anon.java:42-44` - 定义 SELES_SHOULDER/SELES_CORPSE 常量
- `LingMod/lingmod/character/Ling.java:51-52` - 使用 `ModCore.makeCharacterPath()`

**自检命令**：
```bash
ls ModResources/images/char/{shoulder.png,shoulder2.png,corpse.png}
```

---

## 10. loadAnimation 调用时机错误

**现象**：角色模型不显示或显示默认姿势。

**根因**：`loadAnimation` 未在构造函数中调用，或 `SpineAnimation` 的 atlasUrl/skeletonUrl 设置错误。

**推荐做法**：
```java
// 方式1：通过 CustomPlayer 构造函数自动调用
public MyCharacter(String name, PlayerClass setClass) {
    super(name, setClass, orbTextures, orbVfx, new SpineAnimation(
        "ModResources/images/char/player.atlas",
        "ModResources/images/char/player.json",
        1.0F
    ));
    // CustomPlayer 构造函数会自动调用 loadAnimation
}

// 方式2：手动调用
public void reloadAnimation() {
    this.loadAnimation(atlasUrl, skeletonUrl, scale);
    AnimationState.TrackEntry e = this.state.setAnimation(0, "Idle", true);
    e.setTime(e.getEndTime() * MathUtils.random());
}
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomPlayer.java:76-79` - 自动调用 `loadAnimation`
- `Downfall/sneckomod/TheSnecko.java:50-56` - 手动 `reloadAnimation` 方法

**自检命令**：
```bash
grep -rn "loadAnimation" ../resources/mods --include="*Character.java"
```

---

## 11. tooltip 坐标硬编码导致画到屏幕外

**现象**：鼠标悬停时 tooltip 不显示，或显示位置偏移。

**根因**：`TipHelper.renderGenericTip` 使用了固定坐标，未考虑 `Settings.scale` 缩放因子。

**推荐做法**：
```java
// 错误示例：硬编码坐标
TipHelper.renderGenericTip(1500.0F, 700.0F, title, body);

// 正确做法：使用相对坐标 + Settings.scale
protected void onHover() {
    if (this.hitbox.hovered) {
        float tipX = 1550.0F * Settings.scale;
        float tipY = (Settings.HEIGHT - 120.0F) * Settings.scale;
        TipHelper.renderGenericTip(tipX, tipY, TEXT[0], TEXT[1]);
    }
}
```

**证据指针**：
- `LingMod/lingmod/ui/PoetryTopPanel.java:168` - 使用 `Settings.scale`
- `KeyCuts/test447/keycuts/patches/ProceedButtonPatches.java:73` - 动态计算 x - 140.0F * Settings.scale

**自检命令**：
```bash
grep -rn "renderGenericTip" ../resources/mods --include="*.java" | grep -v "Settings.scale"
```

---

## 12. TipHelper.renderGenericTip 不展开 NL

**现象**：tooltip 文本中的 `\n` 显示为字面量而非换行。

**根因**：`TipHelper.renderGenericTip` 不会自动处理换行符。

**推荐做法**：
```java
// 方式1：手动换行（推荐）
String[] paragraphs = new String[] {
    "第一段内容",
    "第二段内容",
    "第三段内容"
};
TipHelper.renderGenericTip(x, y, paragraphs[0], paragraphs[1] + " " + paragraphs[2]);

// 方式2：预处理 NL
String processed = body.replace(" NL ", "\n");
TipHelper.renderGenericTip(x, y, title, processed);
```

**证据指针**：
- `KeyCuts/test447/keycuts/patches/ProceedButtonPatches.java:70-73` - 使用 UIStrings.TEXT 数组
- `LingMod/lingmod/ui/PoetryTopPanel.java:168` - 使用预定义的 TEXT[0], TEXT[1]

**自检命令**：
```bash
grep -rn "renderGenericTip.*\\\\n" ../resources/mods --include="*.java"
```

---

## 13. updateTips 回调顺序问题

**现象**：自定义 TopPanel 的 tooltip 显示延迟或不显示。

**根因**：`onHover()` 调用时机晚于原版渲染，未正确覆盖 `updateTips()` 回调。

**推荐做法**：
```java
// TopPanelItem 中正确实现 onHover
@Override
protected void onHover() {
    super.onHover();  // 先调用父类
    if (this.hitbox.hovered) {
        TipHelper.renderGenericTip(...);
    }
}
```

**证据指针**：
- `LingMod/lingmod/ui/PoetryTopPanel.java:161-171` - 完整的 `onHover` 实现
- `BaseMod/basemod/TopPanelItem.java` - 基类定义

**自检命令**：
```bash
grep -A5 "protected void onHover()" ../resources/mods --include="*TopPanel*.java"
```

---

## 14. 占位复用原版资源的边界

**安全的行为**（占位阶段可接受）：
- 能量球纹理：`orbTextures=null`, `orbVfxPath=null`（低风险）
- 字体：`FontHelper.energyNumFontRed`（低风险）
- 动画占位：`new SpriterAnimation(path)` 使用原版路径（低风险）

**危险的行为**（可能导致冲突）：
- `CardColor` 枚举：`return CardColor.RED;`（高风险：与铁甲师冲突）
- 原版枚举值：`PlayerClass.IRONCLAD`（高风险：破坏角色系统）

**推荐做法**：
```java
// 错误示例：复用 CardColor 枚举
@Override
public AbstractCard.CardColor getCardColor() {
    return CardColor.RED;  // 危险！会与铁甲师混用卡池
}

// 正确做法：使用 SpireEnum 创建自定义枚举
@SpireEnum
public static AbstractPlayer.PlayerClass MY_CHARACTER;
@SpireEnum
public static AbstractCard.CardColor MY_COLOR;

@Override
public AbstractCard.CardColor getCardColor() {
    return MY_COLOR;  // 安全，独立的卡池
}
```

**证据指针**：
- `Kikuchiyo/kikuchiyo/character/Kikuchiyo.java:108` - **危险示例**：直接返回 `CardColor.RED`
- `BaseMod/basemod/abstracts/CustomPlayer.java:119-130` - 正确使用 `this.getCardColor()`

**自检命令**：
```bash
grep -rn "return CardColor\.RED\|return CardColor\.BLUE\|return CardColor\.GREEN" ../resources/mods --include="*Character.java"
```
