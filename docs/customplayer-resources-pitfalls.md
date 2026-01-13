# CustomPlayer 资源相关坑点指南

本文档归纳了开发 STS 角色类 mod 时常见的资源加载问题，聚焦 CustomPlayer、Spine 动画、能量球、角色选择界面、TopPanel/tooltip、渲染相关。

## 1. 资源加载失败/路径问题

### 1.1 路径大小写敏感

**现象**：
- Windows 下正常，Linux/Mac 下游戏崩溃或资源加载失败
- 报错通常为 `File not found` 或 `NullPointerException`

**根因**：
- Linux 文件系统区分大小写，Windows 不区分
- 代码中的路径与实际文件名大小写不匹配

**推荐做法**：
```java
// 错误示例：大小写不一致
loadAnimation("ModResources/images/char/Player.atlas", ...);  // 文件实际是 player.atlas

// 正确做法：使用工具函数统一路径管理
public class ModPaths {
    public static String makeCharPath(String file) {
        return "ModResources/images/char/" + file;
    }
}
// 调用时
loadAnimation(ModPaths.makeCharPath("player.atlas"), ModPaths.makeCharPath("player.json"), 1.0F);
```

**证据指针**：
- `Downfall/sneckomod/TheSnecko.java:51` - 使用动态路径变量
- `LingMod/lingmod/character/Ling.java:77-80` - 使用 `ModCore.makeCharacterPath()` 构建路径

**自检清单**：
```bash
# 1. 检查代码中的路径与实际文件名大小写是否匹配
find ../resources/mods -name "*.java" -exec grep -l "\.atlas\|\.json" {} \; | xargs grep -oh "[^\"']*\.[Aa][Tt][Ll][Aa][Ss]"

# 2. 在 Linux 环境运行 mod，查看日志中的文件路径报错
grep -i "file not found\|nullpointer" ~/Library/Logs/STS/log.txt
```

---

### 1.2 打包进 jar 后路径变化

**现象**：
- IDE 运行正常，打包后游戏崩溃
- 报错信息包含 `classpath` 或 `ClassLoader` 相关

**根因**：
- 开发时使用 `File API` 访问文件系统路径
- 打包后资源在 jar 内，必须使用 `ClassLoader.getResource()`

**推荐做法**：
```java
// 错误示例：使用 File API
File file = new File("images/char/player.png");  // 打包后无法访问

// 正确做法：使用 Gdx.files.internal 或 ClassLoader
Texture tex = new Texture(Gdx.files.internal("ModResources/images/char/player.png"));
// BaseMod 会自动处理资源路径
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomPlayer.java:212` - 使用 `Gdx.files.internal` 加载按钮图标
- `AnonMod/characters/char_Anon.java:83` - 直接使用 `new SpriterAnimation(filepath)` 依赖 BaseMod 路径解析

**自检清单**：
```bash
# 1. 搜索是否有直接 new File() 的用法
grep -rn "new File\(" ../resources/mods --include="*.java"

# 2. 确认 ModTheSpire.json 中声明了 resources 目录
grep -A5 "\"resources\"" ../resources/mods/*/ModTheSpire.json
```

---

### 1.3 atlas-json 配对错误

**现象**：
- 角色动画不显示或显示错乱
- 控制台报 `Spine: Skeleton not found` 或 `Atlas region not found`

**根因**：
- `.atlas` 文件和 `.json` 文件不匹配
- 使用了不同版本的 Spine 导出
- 文件名前缀不一致

**推荐做法**：
```java
// 确保同名配对
String basePath = "ModResources/images/char/player";
loadAnimation(basePath + ".atlas", basePath + ".json", 1.0F);

// 验证文件存在性
if (Gdx.files.internal(basePath + ".atlas").exists() &&
    Gdx.files.internal(basePath + ".json").exists()) {
    loadAnimation(...);
} else {
    logger.error("Spine resources not found: " + basePath);
}
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomPlayer.java:78` - 构造函数中自动调用 `loadAnimation`
- `Downfall/sneckomod/TheSnecko.java:51` - 使用动态路径确保配对

**自检清单**：
```bash
# 1. 检查 atlas 和 json 文件是否成对存在
find ../resources/mods -name "*.atlas" | while read f; do
  json="${f%.atlas}.json"
  if [ ! -f "$json" ]; then echo "Missing pair: $f"; fi
done

# 2. 检查 Spine 版本一致性（注释头应匹配）
head -n1 ../resources/mods/*/images/char/*.atlas
```

---

### 1.4 资源未放在 ModTheSpire.json 声明的 resources 目录

**现象**：
- IDE 运行正常，打包后资源加载失败
- 报错 `Resource not found: xxxResources/...`

**根因**：
- `ModTheSpire.json` 未声明资源目录，或声明路径与实际不符
- 代码中使用的资源前缀与声明不一致

**推荐做法**：
```json
// ModTheSpire.json
{
  "modLoader": "java",
  "launcher": "default",
  "resources": [
    "ModResources/"
  ]
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

**自检清单**：
```bash
# 1. 检查 ModTheSpire.json 中的 resources 声明
grep -h "\"resources\"" -A10 ../resources/mods/*/ModTheSpire.json

# 2. 验证代码中的资源路径前缀是否匹配
grep -rh "Resources/images" ../resources/mods --include="*.java" | grep -h "loadAnimation\|loadImage"
```

---

## 2. 能量球 (Orb) 相关

### 2.1 orbTextures/orbVfxPath 传 null 导致复用原版资源

**现象**：
- 自定义角色使用了铁甲师的红色能量球
- 传入 null 后使用了默认的红色能量球样式

**根因**：
- `CustomEnergyOrb` 构造函数在 `orbTexturePaths == null` 时回退到 `ImageMaster.ENERGY_RED_*`
- 代码第 38-52 行的 null 处理逻辑

**推荐做法**：
```java
// 如果你确实想复用原版能量球（占位阶段）
super(name, playerClass,
    (String[])null,  // orbTextures
    null,            // orbVfxPath
    (String)null, (String)null);

// 正式版应提供完整资源
private static final String[] ORB_TEXTURES = new String[] {
    "ModResources/images/char/orb/layer1.png",
    "ModResources/images/char/orb/layer2.png",
    "ModResources/images/char/orb/layer3.png",
    "ModResources/images/char/orb/layer4.png",
    "ModResources/images/char/orb/layer5.png",
    "ModResources/images/char/orb/layer6.png",
    "ModResources/images/char/orb/layer1d.png",
    "ModResources/images/char/orb/layer2d.png",
    "ModResources/images/char/orb/layer3d.png",
    "ModResources/images/char/orb/layer4d.png",
    "ModResources/images/char/orb/layer5d.png"
};
super(name, playerClass, ORB_TEXTURES, "ModResources/images/char/orb/vfx.png", LAYER_SPEED, ...);
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomEnergyOrb.java:38-52` - null 处理逻辑
- `LingMod/lingmod/character/Ling.java:65` - 传入 `(String)null, (String)null)` 复用原版

**自检清单**：
```bash
# 1. 检查是否传入了 null
grep -rn "orbTextures.*null\|orbVfxPath.*null" ../resources/mods --include="*.java"

# 2. 运行游戏检查能量球颜色
# 角色选择界面，能量球应显示为自定义颜色而非红色
```

---

### 2.2 layer1..6d 资源缺失导致渲染异常

**现象**：
- 能量球有能量时正常，无能量时显示白色方块或消失
- 报错 `Texture not found` 或渲染错乱

**根因**：
- `orbTextures` 数组长度应为奇数（中间是 base，前后对称）
- 缺少无能量状态的层（带 d 后缀的文件）

**推荐做法**：
```java
// 正确的数组结构：[enabled1..5, base, disabled1..5]
// 总共 11 个元素（5层+base+5层）
private static final String[] ORB_TEXTURES = new String[] {
    // 有能量状态（5层）
    "layer1.png", "layer2.png", "layer3.png", "layer4.png", "layer5.png",
    // 基础层（1层，中间）
    "layer6.png",
    // 无能量状态（5层，带 d）
    "layer1d.png", "layer2d.png", "layer3d.png", "layer4d.png", "layer5d.png"
};
```

**证据指针**：
- `BaseMod/basemod/abstracts/CustomEnergyOrb.java:22-25` - 长度和奇数校验
- `Downfall/sneckomod/TheSnecko.java:35` - 完整的 11 层数组

**自检清单**：
```bash
# 1. 检查 orbTextures 数组长度是否为奇数
grep -A1 "orbTextures.*\[\]" ../resources/mods --include="*.java" | grep -c "new String\[\]"

# 2. 验证资源文件是否完整
ls ModResources/images/char/orb/{layer{1..5}.png,layer6.png,layer{1..5}d.png}
```

---

### 2.3 layerSpeeds 数组长度不匹配

**现象**：
- 能量球动画速度异常
- 报错 `ArrayIndexOutOfBoundsException`

**根因**：
- `layerSpeeds` 长度必须等于 `orbTextures.length / 2`（层数）
- 默认 5 层需要 5 个速度值

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

**自检清单**：
```bash
# 1. 检查 LAYER_SPEED 数组长度是否为 (ORB_TEXTURES.length / 2)
grep -A10 "LAYER_SPEED" ../resources/mods --include="*.java" | grep "new float\[\]" -A10

# 2. 观察能量球动画是否各层旋转速度协调
```

---

## 3. 角色选择界面 (Character Select)

### 3.1 ScreenShake 参数过强导致"抖动很久"

**现象**：
- 点击角色后屏幕剧烈震动，持续数秒
- 影响视觉体验，玩家抱怨

**根因**：
- `ShakeIntensity.HIGH` 或 `ShakeDur.LONG` 参数过于极端
- 未考虑屏幕分辨率和玩家舒适度

**推荐做法**：
```java
// 推荐使用轻微震动
@Override
public void doCharSelectScreenSelectEffect() {
    CardCrawlGame.sound.playA("SELECT_SOUND", MathUtils.random(-0.2F, 0.2F));
    CardCrawlGame.screenShake.shake(
        ShakeIntensity.LOW,   // 或 MED，避免 HIGH
        ShakeDur.SHORT,       // 避免 LONG、VERY_LONG
        false                 // forceReset 通常为 false
    );
}
```

**证据指针**：
- `Downfall/sneckomod/TheSnecko.java:90` - 使用 `ShakeIntensity.LOW, ShakeDur.SHORT`
- `Koishi/Koishi/characters/KoishiCharacter.java:87` - 同样的 LOW/SHORT 组合

**自检清单**：
```bash
# 1. 检查是否使用了过激参数
grep -rn "ShakeIntensity.HIGH\|ShakeDur.LONG\|ShakeDur.VERY_LONG" ../resources/mods --include="*.java"

# 2. 实际点击角色观察震动效果是否自然
```

---

### 3.2 选中效果/头像资源缺失

**现象**：
- 角色选择界面头像显示为黑色方块
- 选中效果无响应或报错

**根因**：
- `initializeClass` 参数中的 shoulder1/shoulder2/corpse 路径错误
- 资源文件不存在或路径大小写不匹配

**推荐做法**：
```java
// 确保所有路径资源存在
public static final String SHOULDER1 = ModPaths.makeCharPath("shoulder.png");
public static final String SHOULDER2 = ModPaths.makeCharPath("shoulder2.png");
public static final String CORPSE = ModPaths.makeCharPath("corpse.png");

// 构造时验证
if (Gdx.files.internal(SHOULDER1).exists()) {
    this.initializeClass(null, SHOULDER1, SHOULDER2, CORPSE, getLoadout(), ...);
} else {
    logger.error("Character select resources missing!");
}
```

**证据指针**：
- `AnonMod/characters/char_Anon.java:42-44` - 定义 SELES_SHOULDER/SELES_CORPSE 常量
- `LingMod/lingmod/character/Ling.java:51-52` - 使用 `ModCore.makeCharacterPath()`

**自检清单**：
```bash
# 1. 检查 initializeClass 参数中的路径是否真实存在
grep -h "initializeClass" ../resources/mods --include="*.java" -A1 | grep -o "\"[^\"]*shoulder[^\"]*\""

# 2. 验证文件存在
ls ModResources/images/char/{shoulder.png,shoulder2.png,corpse.png}
```

---

### 3.3 loadAnimation 调用时机错误

**现象**：
- 角色模型不显示或显示默认姿势
- 动画状态未初始化

**根因**：
- `loadAnimation` 未在构造函数中调用
- `SpineAnimation` 的 atlasUrl/skeletonUrl 设置错误

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

**自检清单**：
```bash
# 1. 检查是否调用了 loadAnimation
grep -rn "loadAnimation" ../resources/mods --include="*Character.java"

# 2. 验证动画是否显示
# 运行游戏，观察角色模型是否有待机动画
```

---

## 4. TopPanel / Tooltip

### 4.1 tooltip 坐标硬编码导致画到屏幕外

**现象**：
- 鼠标悬停时 tooltip 不显示
- 或显示位置偏移，部分在屏幕外

**根因**：
- `TipHelper.renderGenericTip` 使用了固定坐标
- 未考虑 `Settings.scale` 缩放因子
- 不同分辨率下坐标偏移

**推荐做法**：
```java
// 错误示例：硬编码坐标
TipHelper.renderGenericTip(1500.0F, 700.0F, title, body);  // 1080p 可能超出

// 正确做法：使用相对坐标 + Settings.scale
protected void onHover() {
    if (this.hitbox.hovered) {
        float tipX = 1550.0F * Settings.scale;  // 右侧固定位置
        float tipY = (Settings.HEIGHT - 120.0F) * Settings.scale;  // 顶部偏移
        TipHelper.renderGenericTip(tipX, tipY, TEXT[0], TEXT[1]);
    }
}
```

**证据指针**：
- `LingMod/lingmod/ui/PoetryTopPanel.java:168` - 使用 `Settings.scale` 缩放坐标
- `KeyCuts/test447/keycuts/patches/ProceedButtonPatches.java:73` - 动态计算 x - 140.0F * Settings.scale

**自检清单**：
```bash
# 1. 检查是否使用了 Settings.scale
grep -rn "renderGenericTip" ../resources/mods --include="*.java" | grep -v "Settings.scale"

# 2. 在不同分辨率测试（1920x1080, 2560x1440, 3840x2160）
```

---

### 4.2 TipHelper.renderGenericTip 不展开 NL

**现象**：
- tooltip 文本中的 `\n` 显示为字面量而非换行
- 文本显示为单行，超出屏幕

**根因**：
- `TipHelper.renderGenericTip` 不会自动处理换行符
- 需要手动分段或使用其他 API

**推荐做法**：
```java
// 方式1：使用 TipHelper.renderTopPanelTip（支持自动换行）
// 需要通过 patch 访问

// 方式2：手动换行（推荐）
String[] paragraphs = new String[] {
    "第一段内容",
    "第二段内容",
    "第三段内容"
};
TipHelper.renderGenericTip(x, y, paragraphs[0], paragraphs[1] + " " + paragraphs[2]);

// 方式3：使用 SmartTextHelper 或 BaseMod 的扩展 API
```

**证据指针**：
- `KeyCuts/test447/keycuts/patches/ProceedButtonPatches.java:70-73` - 使用 UIStrings.TEXT 数组避免硬编码
- `LingMod/lingmod/ui/PoetryTopPanel.java:168` - 使用预定义的 TEXT[0], TEXT[1]

**自检清单**：
```bash
# 1. 检查 tooltip 内容中是否包含 \n 字面量
grep -rn "renderGenericTip.*\\\\n" ../resources/mods --include="*.java"

# 2. 验证 tooltip 文本是否正确换行显示
```

---

### 4.3 updateTips 回调顺序问题

**现象**：
- 自定义 TopPanel 的 tooltip 显示延迟或不显示
- 与原版 TopPanel 交互异常

**根因**：
- `onHover()` 调用时机晚于原版渲染
- 未正确覆盖 `updateTips()` 回调

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

// 确保 updateTips 在正确的渲染阶段调用
// BaseMod 会自动处理 TopPanelGroup 的渲染顺序
```

**证据指针**：
- `LingMod/lingmod/ui/PoetryTopPanel.java:161-171` - 完整的 `onHover` 实现
- `BaseMod/basemod/TopPanelItem.java` - 基类定义

**自检清单**：
```bash
# 1. 检查是否调用了 super.onHover()
grep -A5 "protected void onHover()" ../resources/mods --include="*TopPanel*.java"

# 2. 观察自定义 TopPanel 与原版 TopPanel 的交互是否正常
```

---

## 5. 占位复用原版资源的边界

### 5.1 安全的行为（视觉层面）

以下复用原版资源的行为在占位阶段是**可接受**的：

| 复用内容 | 示例 | 风险评估 |
|---------|------|---------|
| 能量球纹理 | `orbTextures=null`, `orbVfxPath=null` | 低：仅视觉效果 |
| 字体 | `FontHelper.energyNumFontRed` | 低：仅视觉效果 |
| 动画占位 | `new SpriterAnimation(path)` 使用原版路径 | 低：临时占位 |

**推荐做法**：
```java
// 占位阶段明确标注 TODO
public MyCharacter(String name, PlayerClass setClass) {
    // TODO: 替换为自定义能量球资源
    super(name, setClass, (String[])null, null, ...);
}

// 提供回退配置
private static final boolean USE_PLACEHOLDER = true;
public BitmapFont getEnergyNumFont() {
    return USE_PLACEHOLDER ? FontHelper.energyNumFontRed : customFont;
}
```

**证据指针**：
- `LingMod/lingmod/character/Ling.java:239` - 复用 `energyNumFontRed`
- `ArknightsTheSpire/.../CharacterW.java:124` - 同样复用原版字体

**自检清单**：
```bash
# 1. 检查是否有 TODO 标记占位代码
grep -rn "TODO.*能量球\|TODO.*orb\|TODO.*font" ../resources/mods --include="*.java"
```

---

### 5.2 危险的行为（影响未来重构）

以下复用原版资源的行为是**危险**的，可能导致 mod 冲突或原版更新后崩溃：

| 复用内容 | 示例 | 风险 | 影响 |
|---------|------|------|------|
| CardColor 枚举 | `return CardColor.RED;` | 高：与铁甲师冲突 | 无法区分卡牌 |
| 原版枚举值 | `PlayerClass.IRONCLAD` | 高：破坏角色系统 | 崩溃/数据损坏 |
| 资源测试断言 | 依赖原版资源路径存在 | 中：原版更新后失效 | 加载失败 |

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
- `BaseMod/basemod/abstracts/CustomPlayer.java:119-130` - 正确使用 `this.getCardColor()` 获取卡池

**自检清单**：
```bash
# 1. 检查是否直接返回原版 CardColor
grep -rn "return CardColor\.RED\|return CardColor\.BLUE\|return CardColor\.GREEN" ../resources/mods --include="*Character.java"

# 2. 检查是否使用了 @SpireEnum 创建自定义枚举
grep -rn "@SpireEnum" ../resources/mods --include="*.java" | grep -i "enum\|class\|color"
```

---

### 5.3 复用资源的迁移路径

当准备从占位迁移到自定义资源时：

```java
// 阶段1：占位（开发初期）
public MyCharacter(String name, PlayerClass setClass) {
    super(name, setClass, (String[])null, null, ...);
}

// 阶段2：混合（测试期间）
public MyCharacter(String name, PlayerClass setClass) {
    super(name, setClass,
        "ModResources/images/char/orb/vfx.png",  // 仅提供 vfx
        null,  // 其他纹理仍占位
        ...
    );
}

// 阶段3：完整（正式发布）
public MyCharacter(String name, PlayerClass setClass) {
    super(name, setClass, ORB_TEXTURES, ORB_VFX, LAYER_SPEED, ...);
}

// 清理检查清单：
// [ ] 移除所有 (String)null 占位参数
// [ ] 删除 USE_PLACEHOLDER 等开关变量
// [ ] 删除 TODO 注释
// [ ] 验证所有自定义资源文件存在
// [ ] 测试不同分辨率下的显示效果
```

---

## 附录：快速诊断命令

```bash
# 1. 检查所有 CustomPlayer 子类的资源路径
grep -rn "extends CustomPlayer" ../resources/mods --include="*.java" | \
  cut -d: -f1 | sort -u | \
  xargs grep -h "loadAnimation\|ORB_TEXTURES\|SHOULDER"

# 2. 查找所有潜在的硬编码坐标
grep -rn "renderGenericTip.*[0-9]\+\.[0-9]\+F" ../resources/mods --include="*.java" | \
  grep -v "Settings.scale"

# 3. 检查 ScreenShake 参数使用
grep -rn "screenShake.shake" ../resources/mods --include="*.java" | \
  grep -E "HIGH|LONG|VERY_LONG"

# 4. 查找复用原版 CardColor 的危险行为
grep -rn "return CardColor\." ../resources/mods --include="*.java" | \
  grep -v "getCardColor()" | grep "return"

# 5. 验证 atlas/json 配对
find ../resources/mods -name "*.atlas" | while read f; do
  json="${f%.atlas}.json"
  [ ! -f "$json" ] && echo "Unpaired: $f"
done
```

---

## 相关文档

- [patching-pitfalls.md](./patching-pitfalls.md) - 修补相关坑点
- [localization-ids-prefs-pitfalls.md](./localization-ids-prefs-pitfalls.md) - 本地化和配置坑点
