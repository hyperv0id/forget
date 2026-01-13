# STS Mod 开发踩坑调研报告

> 生成时间：2026-01-13
> 数据来源：`../resources/mods/`（119 个解包 mod）、`docs/pitfalls.md`

---

## 1) 缺口总结（对照 `docs/pitfalls.md`）

| 现有 9 条 | 本次可补充的新维度 |
|-----------|-------------------|
| 资源目录命名、ScreenShake、Orb 资源复用、卡色复用、gh PR 坑、TopPanel tooltip、TipHelper NL、Javassist `$0` | **CustomPlayer 骨架模式**、**@SpireEnum 自定义枚举**、**SpireConfig 跨存档持久化**、**Localization 多语言热加载**、**Patching 兼容性**、**ModTheSpire.json 依赖声明** |

**结论**：`docs/pitfalls.md` 主要是"开发/构建/调试"层面的坑。**系统级功能**（卡色/角色/存档/多语言）几乎是空白。

---

## 2) 调查维度表

| 维度 | 目的 | 关键问题 |
|------|------|----------|
| **语言 & 构建** | 判断模组技术栈 | 用 Java 还是 Kotlin？Gradle 还是 Maven？依赖 BaseMod/StSLib？ |
| **CustomPlayer** | 角色实现骨架 | 怎么注册？能量球/卡色怎么处理？`doCharSelectScreenSelectEffect`？ |
| **@SpirePatch** | 代码注入策略 | patch 哪些类？用 `cls` 还是 `expr`？patch 顺序冲突怎么处理？ |
| **@SpireEnum** | 自定义枚举扩展 | 自定义 `CardColor` / `RelicTier` / `LibraryType`？如何配合 `BaseMod.addColor`？ |
| **Localization** | 多语言文案 | 用哪种语言文件格式？`CardStrings`/`RelicStrings`？热更新还是重启生效？ |
| **SpireConfig** | 用户设置/存档 | 怎么注册？`saveSpecial`/`saveString` 在哪里写？怎么避免存档膨胀？ |
| **Resources & Packaging** | 资源打包 | 贴图/Spine/音效放哪？`ModTheSpire.json` 的 `resources` 字段怎么配？ |
| **Dependencies** | 依赖管理 | 是否依赖 BaseMod/StSLib/Downfall？版本声明怎么写？ |

---

## 3) 代表 Mod 列表（30 个，覆盖全维度）

| Mod 名 | 推荐理由 |
|--------|----------|
| **LingMod** | 本地参考标杆，Java + Maven，角色/卡牌/遗物/UI 全覆盖 |
| **BaseMod** | 基础设施级，所有 Patch/Enum/Config 最佳实践源头 |
| **StSLib** | Patch-heavy 代表，`@SpirePatch` 复杂用法（MultiUpgrade、BranchingUpgrades） |
| **Downfall** | 超大型模组，跨模组兼容性、资源打包、多语言、存档管理参考 |
| **DuskMod** | 中型 Java 模组，角色实现简洁 |
| **ReimuMod** | 多角色/多卡色，@SpireEnum 完整示例（ClassEnum、LibraryTypeEnum、CardTagEnum） |
| **Lobotomy** | 大量自定义 Enum（CharacterEnum、RelicTierEnum），大量 Patch |
| **YogSothothMod** | 多角色分目录，存档管理（`saveSpecial`）参考 |
| **hsr-mod** | Kotlin + Gradle，现代模组配置 |
| **FrierenMod** | 多角色 + 多卡色，SpireConfig 典型用法 |
| **KaltsitMod** | 大型二次元模组，资源打包、Spine 集成参考 |
| **ArknightsTheSpire** | 多角色 + 多职业，UI 扩展、Config 参考 |
| **ThePackmaster** | 卡牌包模组，CardColor 扩展 + 自定义 Enum |
| **LogosMod** | 大型模组，Patch 冲突处理、兼容性写法参考 |
| **AcaciaTheSpire** | 中型模组，Java 风格规范，ModTheSpire.json 完整示例 |
| **Tenshi** | 角色实现 + 简单 UI，入门参考 |
| **Mizuki** | 角色 + 遗物，SpireConfig 简单用法 |
| **AetherMod** | 早期模组，对比新式模组看演进 |
| **MuseDashReskin** | Patch-heavy，特定系统注入 |
| **intentgraph** | UI 扩展、屏幕自定义，Resources 打包参考 |
| **DeckTracker** | UI/功能模组，SpireConfig + Patch 组合 |
| **TheColorful** | 角色实现，代码结构简洁 |
| **Koishi** | 多角色 + 卡牌，Enum + Config 参考 |
| **RemiliaScarlet** | Enum 完整示例（多个 Enum 文件） |
| **Keinemod** | 角色 + 卡牌，基础用法典范 |
| **BlackRuseMod** | 角色实现 + Patch 参考 |
| **SovietMod** | ModTheSpire.json 示例（dependencies） |
| **Reed-1.0-SHALL** | UI 扩展、皮肤选择屏幕 |
| **Elaina** | 二次元模组，角色/卡牌/UI 综合示例 |
| **IbukiSuika** | 角色 + 简单功能，入门参考 |

---

## 4) 证据采集命令清单

### 4.1 CustomPlayer 骨架
```bash
# 查找所有 CustomPlayer 实现
rg -g "*.java" -g "*.kt" "extends CustomPlayer|: CustomPlayer\(" ../resources/mods -l

# 典型写法对照（看 ScreenShake 参数）
rg "doCharSelectScreenSelectEffect" ../resources/mods/LingMod -A 5

# Orb 资源传参
rg "orbTextures|orbVfxPath" ../resources/mods/LingMod -A 2
```

### 4.2 @SpirePatch 模式
```bash
# Patch-heavy 模组列表
rg -g "*.java" -g "*.kt" "@SpirePatch" ../resources/mods -l | head -20

# Patch 类写法（cls vs expr）
rg "@SpirePatch\(cls" ../resources/mods/BaseMod -l | head -5
rg "@SpirePatch\(expr" ../resources/mods/StSLib -l | head -5

# Patch 冲突/顺序处理
rg "SpireInsert\(|SpireReplace\(" ../resources/mods/StSLib -l
```

### 4.3 @SpireEnum + 自定义卡色
```bash
# 自定义 CardColor 的 Enum
rg "@SpireEnum" ../resources/mods/ReimuMod -l
rg "BaseMod.addColor" ../resources/mods/ReimuMod -B 2 -A 5

# LibraryTypeEnum 对应
rg "LibraryTypeEnum" ../resources/mods/Lobotomy -l
```

### 4.4 Localization 多语言
```bash
# 有 localization 目录的模组
rg -g "*.json" "\"localization\"" ../resources/mods -l

# 多语言目录结构
ls -la ../resources/mods/Downfall/hexamodResources/localization/

# CardStrings/RelicStrings 写法
rg "\"DESCRIPTION\"" ../resources/mods/Downfall/hexamodResources/localization/eng/CardStrings.json | head -5
```

### 4.5 SpireConfig & Save
```bash
# SpireConfig 注册
rg "new SpireConfig" ../resources/mods -l | head -10

# saveString / saveSpecial 用法
rg "saveString|saveSpecial" ../resources/mods/StSLib -l
rg "abstractSaveString|abstractSaveSpecial" ../resources/mods/BaseMod -l

# Config UI 绑定
rg "setShowPopup\(|setPopupText\(" ../resources/mods/hsr-mod -l
```

### 4.6 ModTheSpire.json & 依赖
```bash
# 依赖声明示例
cat ../resources/mods/AcaciaTheSpire/ModTheSpire.json
cat ../resources/mods/hsr-mod/ModTheSpire.json

# resources 字段配置
rg "\"resources\"" ../resources/mods/Downfall/ModTheSpire.json -A 5
```

### 4.7 Resources & 打包
```bash
# 资源目录结构
ls -la ../resources/mods/intentgraph/io/chaofan/sts/intentgraph/

# Spire 资源路径引用
rg "CardCrawlFiles\\.resource" ../resources/mods/LingMod -l
```

---

## 5) Heatmap & 深挖优先级

| 主题 | 频次 | 踩坑风险 | 深挖理由 |
|------|------|---------|----------|
| **@SpirePatch** | ★★★★★ (115/30/25) | 高 | Patch 冲突/顺序是最常见崩溃原因 |
| **CustomPlayer** | ★★★★☆ (~90) | 中 | 角色入口，能量球/卡色/UI 都在此汇聚 |
| **@SpireEnum + CardColor** | ★★★★☆ (~50) | 高 | 卡色独立是"完全自洽模组"的分水岭 |
| **SpireConfig** | ★★★☆☆ (~103) | 中 | 存档持久化、回档兼容、用户设置 |
| **Localization** | ★★☆☆☆ (~10 有目录) | 中 | 多语言热更新、占位符替换 |
| **Resources & Packaging** | ★★☆☆☆ (差异大) | 低~中 | 路径错误只会导致资源不显示 |

### 优先深挖主题（TOP 3）

1. **@SpirePatch** — 证据最多，Patch 冲突是社区高频问题
2. **@SpireEnum + CardColor** — "完全独立角色"必经之路
3. **SpireConfig & Save** — 存档兼容性影响长期体验

---

## 附录：关键 Mod 文件路径速查

| Mod | 关键文件路径 |
|-----|-------------|
| LingMod | `lingmod/ModCore.java`、`lingmod/character/Ling.java` |
| BaseMod | `basemod/patches/...`（大量 Patch 示例） |
| StSLib | `com/evacipated/cardcrawl/mod/stslib/patches/...` |
| ReimuMod | `ReimuMod/patches/ReimuClassEnum.java`、`characters/ReiMu.java` |
| Lobotomy | `lobotomyMod/patch/CharacterEnum.java`、`patch/AbstractCardEnum.java` |
| hsr-mod | `ModTheSpire.json`（dependencies 示例） |
| AcaciaTheSpire | `ModTheSpire.json`、`characters/Acacia.java` |
| Downfall | `hexamodResources/localization/`（多语言示例） |
