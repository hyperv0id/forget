# [System] 【名望】系统实现 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 为 TheForget 引入一个【名望 / Reputation】的“额外资源系统”骨架：顶栏显示 + hover tooltip + zhs/eng 本地化 + 随 run 存档读档（不引入任何战斗/结算机制变化）。

**Architecture:** 只用 BaseMod 的公开 API，尽量不写 `@SpirePatch`：  
1) `BaseMod.addTopPanelItem(...)`：注册一个 `TopPanelItem` 展示名望图标与数值，并在 hover 时显示 tooltip。  
2) `BaseMod.addSaveField(id, CustomSavable)`：把名望值随存档保存/读档恢复（run scope）。  
3) 文案统一放在 `theforgetResources/localization/<lang>/UIstrings.json`，Key 全部以 `theforget:` 前缀防冲突。

**Tech Stack:** Kotlin + BaseMod（TopPanelItem / CustomSavable）+ Gradle `devWatchInstall`（用于安装，不跑启动命令）。

---

## Acceptance Criteria (Issue #5)

- [ ] 顶栏有【名望】显示（图标 + 数值），仅本 mod 生效（不影响原版角色）
- [ ] hover 名望区域显示 tooltip（标题“名望/Reputation”，正文说明含义）
- [ ] 新开局默认名望为 0（或默认值），并且能随存档保存/读档恢复
- [ ] `./gradlew clean test --no-daemon` 通过（至少对本地化 key + jar 资源存在性做 smoke test）

---

## Scope Notes（避免膨胀）

- 本 Issue 不实现“名望如何增长/消耗”的具体玩法，只提供可调用的 `add/set/reset` API。
- 本 Issue 不做额外血条/战斗条 UI（那是后续系统 UI 的增强项）。
- 本 Issue 默认名望是“每局 run 作用域”（随存档），不是跨局永久进度（若要永久进度后续改 `SpireConfig`）。

---

## Task 1: 先补齐 UIStrings（zhs + eng）+ smoke test（RED）

**Files:**
- Modify: `src/test/kotlin/theforget/LocalizationSmokeTest.kt`
- Modify: `src/test/kotlin/theforget/ModJarResourcesTest.kt`
- (下一步才会 Create 对应 JSON 文件与 icon)

**Step 1: Write failing tests**

在 `LocalizationSmokeTest` 中增加断言：
- `theforget:ReputationTooltip`
- `theforget:ReputationLabel`

在 `ModJarResourcesTest` 中增加断言（jar 内必须包含）：
- `theforgetResources/localization/zhs/UIstrings.json`
- `theforgetResources/localization/eng/UIstrings.json`
- `theforgetResources/images/ui/topPanel/reputation.png`

**Step 2: Run tests to verify RED**

Run: `./gradlew clean test --no-daemon`  
Expected: FAIL（缺 key / 缺资源）

---

## Task 2: 补资源（UIStrings + icon）让测试变绿（GREEN）

**Files:**
- Modify: `src/main/resources/theforgetResources/localization/zhs/UIstrings.json`
- Modify: `src/main/resources/theforgetResources/localization/eng/UIstrings.json`
- Create: `src/main/resources/theforgetResources/images/ui/topPanel/reputation.png`

**Step 1: Add UIStrings**

Key 约定：
- `theforget:ReputationTooltip`：`TEXT[0]` 标题，`TEXT[1]` 正文
- `theforget:ReputationLabel`：`TEXT[0]` 用于顶栏 label（如需要）

**Step 2: Add a placeholder icon**

先放一个简单占位图标（后续可替换为正式美术资源），但路径与命名固定，避免未来重构成本。

**Step 3: Run tests to verify GREEN**

Run: `./gradlew clean test --no-daemon`  
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/resources/theforgetResources/localization/zhs/UIstrings.json
git add src/main/resources/theforgetResources/localization/eng/UIstrings.json
git add src/main/resources/theforgetResources/images/ui/topPanel/reputation.png
git add src/test/kotlin/theforget/LocalizationSmokeTest.kt
git add src/test/kotlin/theforget/ModJarResourcesTest.kt
git commit -m "feat: add Reputation UIStrings + icon (tests)"
```

---

## Task 3: 名望值管理 + 存档字段（TDD）

**Files:**
- Create: `src/main/kotlin/theforget/reputation/ReputationState.kt`
- Create: `src/main/kotlin/theforget/reputation/ReputationSaveField.kt`
- Create: `src/test/kotlin/theforget/ReputationSaveFieldTest.kt`

**Step 1: Write failing tests**

`ReputationSaveFieldTest` 覆盖：
- 默认值（reset 后为 0）
- `onSave()` 返回当前值
- `onLoad(null)` 不崩溃并回退到默认值
- `onLoad(value)` 能恢复

**Step 2: Run tests to verify RED**

Run: `./gradlew clean test --no-daemon`  
Expected: FAIL

**Step 3: Minimal implementation**

`ReputationState`（object）：
- `get(): Int`
- `set(value: Int)`
- `add(delta: Int)`
- `reset()`

`ReputationSaveField : CustomSavable<Int>`：
- `onSave()` 读 `ReputationState.get()`
- `onLoad(data)` 调用 `ReputationState.set(data ?: 0)`

**Step 4: Run tests to verify GREEN**

Run: `./gradlew clean test --no-daemon`  
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/theforget/reputation/ReputationState.kt
git add src/main/kotlin/theforget/reputation/ReputationSaveField.kt
git add src/test/kotlin/theforget/ReputationSaveFieldTest.kt
git commit -m "feat: add Reputation state + save field"
```

---

## Task 4: TopPanelItem 展示 + tooltip（不写 patch）

**Files:**
- Create: `src/main/kotlin/theforget/reputation/ReputationTopPanelItem.kt`
- Modify: `src/main/kotlin/theforget/core/TheForgetAssets.kt`
- Modify: `src/main/kotlin/theforget/core/TheForgetLocalization.kt`
- Modify: `src/main/kotlin/theforget/TheForgetMod.kt`

**Step 1: Add assets/localization constants**

`TheForgetAssets`：
- `REPUTATION_ICON = "theforgetResources/images/ui/topPanel/reputation.png"`

`TheForgetLocalization`：
- `REPUTATION_TOOLTIP_KEY`
- `REPUTATION_LABEL_KEY`

**Step 2: Implement `ReputationTopPanelItem`**

行为：
- `update()`：更新 hitbox
- `render(sb)`：画 icon + 当前数值
- `renderHover(sb)`：在 hovered 时用 `TipHelper.renderGenericTip(...)` 渲染 tooltip（文本从 UIStrings 获取）

**Step 3: Wire registration**

在 `TheForgetMod.receivePostInitialize()`：
- `BaseMod.addTopPanelItem(ReputationTopPanelItem())`
- `BaseMod.addSaveField("theforget:Reputation", ReputationSaveField())`
- 选择一个“新开局重置”的 hook（若需要）：`StartGameSubscriber.receiveStartGame()` 调 `ReputationState.reset()`

**Step 4: Run tests**

Run: `./gradlew clean test --no-daemon`  
Expected: PASS

**Step 5: Manual install**

Run: `./gradlew devWatchInstall --no-daemon`（不加 `--continuous`，不启动游戏命令）  
Then: 用 Steam 启动 → 进入对局 → 顶栏看到名望图标与数值；hover 显示 tooltip；存档/读档后数值一致。

**Step 6: Commit**

```bash
git add src/main/kotlin/theforget/reputation/ReputationTopPanelItem.kt
git add src/main/kotlin/theforget/core/TheForgetAssets.kt
git add src/main/kotlin/theforget/core/TheForgetLocalization.kt
git add src/main/kotlin/theforget/TheForgetMod.kt
git commit -m "feat: add Reputation top panel item + save registration"
```

---

## Task 5: 写踩坑记录（可选但推荐）

**Files:**
- Modify: `docs/pitfalls.md`

内容建议：
- TopPanelItem 注册时机与“重复注册”
- SaveField 的 ID 命名与 null 处理
- tooltip 坐标与 `Settings.scale`

Commit:
```bash
git add docs/pitfalls.md
git commit -m "docs: add pitfalls for Reputation system"
```

