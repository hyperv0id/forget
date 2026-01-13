# [System] 【自我】系统实现（HP 叙事化）Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 把原版 HP 叙事化为【自我】——对玩家 UI 显示“自我”，并在顶栏血量 hover tooltip 解释“自我”的含义（仅对本 Mod 角色生效）。

**Architecture:** 不改动 HP 的数值/结算（仍是 `currentHealth/maxHealth`），只做 UI 层的“命名 + 解释”替换：  
1) `TopPanel.updateTips()`：当 `hpHb.hovered` 时渲染自定义 tooltip，并阻止原版 HP tooltip。  
2) `CharacterOption.renderInfo()`：在角色选择界面把 HP 文案 label 替换为“自我”。  
3) 文案全部走 `UIStrings` 本地化，路径放在 `theforgetResources/localization/<lang>/UIstrings.json`。

**Tech Stack:** Kotlin + BaseMod + ModTheSpire patch（SpirePatch/SpireReturn/Javassist），Gradle `devWatchInstall` 用于持续安装（不使用启动命令）。

---

## Acceptance Criteria (Issue #4)

- [ ] 顶栏血量区域 hover 时，tooltip 标题为“自我”，正文解释“自我=生命值/归零死亡/叙事含义”
- [ ] 角色选择界面信息面板中，“HP”文字对本角色显示为“自我”
- [ ] 不影响原版角色与其他 mod（仅当 `AbstractDungeon.player.chosenClass == THE_FORGET` 生效）
- [ ] `./gradlew clean test --no-daemon` 通过（至少对本地化资源做 smoke test）

---

## Scope Notes（避免无意义膨胀）

- 本 Issue 不做“全局替换所有出现 HP 的文本”（那会波及原版事件/遗物/卡牌描述，范围不可控）。
- 本 Issue 不新增任何“第二条自我血条”作为正式 UI（HP 本身已经是自我）；额外血条方案仅作为后续【名望/好感度】UI 的参考与技术储备。
- 可选增强项（非 P0）：自定义本角色的红色血条颜色（更符合“自我”叙事），见后文 Optional。

---

## Task 1: 补齐 UIStrings 本地化文件（zhs + eng）

**Files:**
- Create: `src/main/resources/theforgetResources/localization/zhs/UIstrings.json`
- Create: `src/main/resources/theforgetResources/localization/eng/UIstrings.json`

**Step 1: Write the files**

Key 约定（与现有 `modId=theforget` 一致）：
- `theforget:SelfTooltip`
- `theforget:SelfLabel`

`zhs/UIstrings.json` 示例（可按需要微调文案）：

```json
{
  "theforget:SelfTooltip": {
    "TEXT": [
      "自我",
      "【自我】即你的生命值。NL 受伤 = 自我流失。NL 自我归零则本局结束。"
    ]
  },
  "theforget:SelfLabel": {
    "TEXT": ["自我"]
  }
}
```

`eng/UIstrings.json` 示例（保证 fallback 不空）：

```json
{
  "theforget:SelfTooltip": {
    "TEXT": [
      "Self",
      "Self is your HP. NL Taking damage reduces Self. NL Reaching 0 ends the run."
    ]
  },
  "theforget:SelfLabel": {
    "TEXT": ["Self"]
  }
}
```

**Step 2: Run build to ensure resources included**

Run: `./gradlew clean build --no-daemon`  
Expected: PASS（并生成 `build/libs/TheForget.jar`）

**Step 3: Commit**

```bash
git add src/main/resources/theforgetResources/localization/zhs/UIstrings.json
git add src/main/resources/theforgetResources/localization/eng/UIstrings.json
git commit -m "feat: add UIStrings for Self (HP narrative rename)"
```

---

## Task 2: 加载 UIStrings（BaseMod.loadCustomStringsFile）

**Files:**
- Modify: `src/main/kotlin/theforget/TheForgetMod.kt`
- Create: `src/main/kotlin/theforget/core/TheForgetLocalization.kt`

**Step 1: Write a minimal localization helper**

`TheForgetLocalization.kt` 目标：
- `uiString(key: String): UIStrings`
- `langFolder(): String`（zhs/eng + fallback）
- 常量 key：`SELF_TOOLTIP_KEY = "theforget:SelfTooltip"`、`SELF_LABEL_KEY = "theforget:SelfLabel"`

**Step 2: Wire BaseMod string loading**

在 `TheForgetMod`：
- 实现 `EditStringsSubscriber`
- `receiveEditStrings()` 中：
  - 计算 `lang`：优先 `Settings.language` 映射到 `zhs/eng`，否则 fallback `eng`
  - `BaseMod.loadCustomStringsFile(UIStrings::class.java, "theforgetResources/localization/${lang}/UIstrings.json")`

注意：路径必须与 Issue #3 资源命名空间约定一致（`theforgetResources/...`），避免与其他 mod 冲突。

**Step 3: Run tests**

Run: `./gradlew clean test --no-daemon`  
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/kotlin/theforget/TheForgetMod.kt
git add src/main/kotlin/theforget/core/TheForgetLocalization.kt
git commit -m "feat: load UIStrings localization files"
```

---

## Task 3: 顶栏血量 tooltip 替换为【自我】解释（TopPanel.updateTips）

> 调研依据：原版 `TopPanel.updateTips()` 在 `hpHb.hovered` 分支调用 `TipHelper.renderGenericTip(..., LABEL[3], MSG[3])`。

**Files:**
- Create: `src/main/kotlin/theforget/patches/TopPanelSelfTooltipPatch.kt`

**Step 1: Write patch (recommended approach: Prefix + early return only when hovered)**

Patch 目标：只在 “hover HP 区域 + 当前玩家是 TheForget” 时拦截并显示自定义 tooltip。

Kotlin 形态（伪代码结构，具体 import 以实现为准）：

```kotlin
@SpirePatch2(clz = TopPanel::class, method = "updateTips")
object TopPanelSelfTooltipPatch {
  @JvmStatic
  @SpirePrefixPatch
  fun prefix(__instance: TopPanel): SpireReturn<Void> {
    val p = AbstractDungeon.player ?: return SpireReturn.Continue()
    if (p.chosenClass != TheForgetEnums.THE_FORGET) return SpireReturn.Continue()
    if (!__instance.hpHb.hovered) return SpireReturn.Continue()

    val ui = CardCrawlGame.languagePack.getUIString(TheForgetLocalization.SELF_TOOLTIP_KEY)
    TipHelper.renderGenericTip(
      InputHelper.mX.toFloat() - 140f * Settings.scale,
      Settings.HEIGHT.toFloat() - 120f * Settings.scale,
      ui.TEXT[0],
      ui.TEXT[1]
    )
    return SpireReturn.Return(null) // 阻止原版 HP tooltip（避免叠加）
  }
}
```

**Notes / Risks**
- `updateTips()` 是每帧调用的 tooltip 更新；early return 仅在 `hpHb.hovered` 时触发，通常不会影响 gold/deck 等其它 hover。
- `hpHb` 字段若不可见，可使用 `ReflectionHacks.getPrivate(__instance, TopPanel::class.java, "hpHb")` 作为 fallback。

**Step 2: Build**

Run: `./gradlew clean build --no-daemon`  
Expected: PASS

**Step 3: Manual verify (Steam 启动，不用 runMts)**

Run: `./gradlew devWatchInstall --no-daemon -PstsDir=/path/to/SlayTheSpire`  
Then: 用 Steam 启动游戏 → 进入对局 → 鼠标 hover 顶栏血量区域 → tooltip 显示“自我”标题与说明。

**Step 4: Commit**

```bash
git add src/main/kotlin/theforget/patches/TopPanelSelfTooltipPatch.kt
git commit -m "feat: show Self tooltip when hovering HP"
```

---

## Task 4: 角色选择界面把 HP label 替换为“自我”（CharacterOption.renderInfo）

> 调研依据：原版 `CharacterOption.renderInfo(SpriteBatch)` 存在 `FontHelper.renderSmartText(..., TEXT[4] + this.hp, ...)`。

**Files:**
- Create: `src/main/kotlin/theforget/patches/CharSelectSelfLabelPatch.kt`

**Step 1: Preferred implementation: SpireInstrumentPatch (ExprEditor) 替换 renderSmartText 的 text 参数**

思路：只对 `CharacterOption` 当前 option 的角色是本 mod 角色时生效；并且只替换 “以原版 HP label 开头的那一次 renderSmartText 调用”。

伪代码（Javaassist replace 字符串的结构）：
- 在 `FontHelper.renderSmartText(...)` 调用处：
  - 若 `this.c != null && this.c.chosenClass == THE_FORGET`
  - 且 `text.startsWith(CharacterOption.TEXT[4])`
  - 则把 text 改为 `SelfLabel + text.substring(CharacterOption.TEXT[4].length)`

**Step 2: Fallback implementation: 覆盖绘制（如果 Instrument 太脆）**

如果 ExprEditor 因方法重载/签名对不上导致 patch 复杂：
- 允许用 postfix overlay 的方式在 HP label 位置重新绘制“自我”
- 但需要从原版代码里复用相同坐标/字体，否则易错位（仅作 fallback）

**Step 3: Build**

Run: `./gradlew clean build --no-daemon`  
Expected: PASS

**Step 4: Manual verify**

安装 jar 后（`installMod` 或 `devWatchInstall`）→ 进入选人界面 → 选中本角色 → 信息面板 HP 那行显示为“自我”。

**Step 5: Commit**

```bash
git add src/main/kotlin/theforget/patches/CharSelectSelfLabelPatch.kt
git commit -m "feat: rename HP label to Self in character select"
```

---

## Task 5: 测试防回退（本地化 key + 资源存在性）

**Files:**
- Modify: `src/test/kotlin/theforget/ModJarResourcesTest.kt`
- Create: `src/test/kotlin/theforget/LocalizationSmokeTest.kt`

**Step 1: Extend jar resource test**

在 `ModJarResourcesTest` 增加断言：
- jar 中必须包含：
  - `theforgetResources/localization/zhs/UIstrings.json`
  - `theforgetResources/localization/eng/UIstrings.json`

**Step 2: Add a simple smoke test for keys**

`LocalizationSmokeTest`：
- 从 classpath 读取上述两个 UIstrings 文件为文本
- 断言包含：
  - `"theforget:SelfTooltip"`
  - `"theforget:SelfLabel"`

不引入 JSON 解析库，避免依赖膨胀；只做存在性检查即可。

**Step 3: Run tests**

Run: `./gradlew clean test --no-daemon`  
Expected: PASS

**Step 4: Commit**

```bash
git add src/test/kotlin/theforget/ModJarResourcesTest.kt
git add src/test/kotlin/theforget/LocalizationSmokeTest.kt
git commit -m "test: ensure Self UIStrings exist and contain required keys"
```

---

## Optional A: 自定义“自我”血条颜色（AbstractCreature.renderRedHealthBar）

> 调研依据：`keinemod/HealthBarPatch.java` 覆写 `AbstractCreature.renderRedHealthBar()` 自定义颜色并 `SpireReturn.Return()` 阻止原版渲染。

**When to do:** 仅当需要更强的“叙事视觉区分”时做；否则先保持原版红条避免 mod 冲突与渲染风险。

**Files:**
- Create: `src/main/kotlin/theforget/patches/SelfHealthBarColorPatch.kt`

**Implementation constraints**
- 必须只对玩家且仅本角色生效：`__instance is AbstractPlayer && chosenClass == THE_FORGET`
- 注意 `SpriteBatch` 的颜色状态：绘制后恢复原色（或在 return 前设置为 `Color.WHITE`）
- 避免影响怪物血条

**Verification**
- 仅靠 build/test 无法验证视觉：需要 Steam 启动游戏观察。

---

## Optional B: 额外血条（为未来【名望/好感度】做预研）

> 调研依据：`514MOD/Characrer_Koishi.java`（override `renderHealth` 后手绘额外条）；以及某些 mod 对 `AbstractPlayer.renderHealth` 做 patch 插入绘制。

**Recommendation**
- 本 Issue 不实现第二条“自我”血条（冗余）；但可把“额外条绘制”的代码样式记到 `docs/pitfalls.md`，为后续三资源 UI 做准备。

---

## Task 6: Docs / Pitfalls 更新

**Files:**
- Modify: `docs/pitfalls.md`

记录：
- `TopPanel.updateTips()` 拦截 HP tooltip 的 patch 方式（以及为什么不用坐标硬算）
- `CharacterOption.renderInfo()` 替换 HP label 的实现选择（Instrument vs overlay）
- 若做 Optional：`renderRedHealthBar` 的风险点（mod 冲突/颜色恢复）

**Commit**

```bash
git add docs/pitfalls.md
git commit -m "docs: add notes for Self UI patches"
```

---

## Pull Request Plan

1) 推送分支：`git push -u origin issue/4-self-system`  
2) 开 PR：标题 `"[System] 【自我】系统实现"`，正文包含：
   - Summary：UI rename + tooltip
   - Test Plan：`./gradlew clean test --no-daemon`
   - Manual Test：Steam 启动验证 hover + char select
3) PR 文案包含：`Closes #4`

