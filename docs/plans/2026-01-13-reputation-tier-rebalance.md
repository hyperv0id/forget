# [System] 【名望】分段区间改版 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task.

**Goal:** 把【名望 / Reputation】从“单轴数值（当前还被强制 ≥0）”升级为**允许负值**、并按你给的 4 个区间提供明确的玩法分段（清理污染权限 / 好感度获取效率 / 极低名望的战斗副作用钩子），同时同步文档与 issue。

**Architecture:** 以“可复用的规则层”为核心：  
1) `ReputationState` 只负责 run-scoped `Int` 存取（不做硬限制/不封顶）。  
2) `ReputationTier` + `ReputationRules`（纯函数/无状态）负责把数值映射到玩法分段与倍率/开关。  
3) UI（TopPanel tooltip）仅展示当前数值/分段与效果摘要；具体系统（污染清理、模仿牌、副作用）后续各自接入 `ReputationRules`。

**Tech Stack:** Kotlin + BaseMod（已存在的 TopPanelItem / CustomSavable）+ JUnit5（现有测试框架）+ Gradle

---

## Design Snapshot（本计划假设）

你当前给出的新分段：

| Tier | 数值范围 | 清理污染 | 好感度获取 | 战斗特性 |
|---|---:|---|---|---|
| 高名望 | +4 ~ +6 | 自动/免费 | 双倍效率 | 无 |
| 正名望 | +1 ~ +3 | 可用 | 正常 | 无 |
| 负名望 | 0 ~ -3 | 不可用 | 减半/变慢 | 无（“喘息区”） |
| 极低 | -4 ~ -6 | 不可用 | 停止 | 模仿牌耗血 + 增伤 |

**确认口径：** 名望数值**不做硬限制/不封顶**；玩法效果按 4 档分段生效，超出 `+6/-6` 的数值属于“同档延伸”。  
**默认平衡锚点：** 以 `[-6, +6]` 作为“通常可达范围”的调参参考（不是硬上限）。

---

## Acceptance Criteria

- [ ] `ReputationState` 允许负值且不做硬限制（可存取任意 `Int`）
- [ ] 代码内可计算当前 `ReputationTier`（4 档），并提供可复用的规则查询（是否可清理污染、好感度倍率/是否停止、是否启用模仿耗血/增伤）
- [ ] 顶栏名望显示对正值加 `+` 号（可选，但建议），tooltip 展示当前分段与关键效果摘要
- [ ] 文档（Roadmap + 设计文档）与新分段一致，不再出现旧范围（如 -31~+32）或“<0 就立刻有副作用”的描述
- [ ] `./gradlew clean test --no-daemon` 通过

---

### Task 1: 先写测试锁定“负值 + 不封顶 + 分段映射”（RED）

**Files:**
- Create: `src/test/kotlin/theforget/ReputationStateTest.kt`
- Modify: `src/test/kotlin/theforget/ReputationSaveFieldTest.kt`

**Step 1: Write failing tests（ReputationState）**

```kotlin
package theforget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import theforget.reputation.ReputationState
import theforget.reputation.ReputationTier

class ReputationStateTest {
    @Test
    fun `set allows negative and does not clamp`() {
        ReputationState.reset()
        ReputationState.set(-1)
        assertEquals(-1, ReputationState.get())

        ReputationState.set(999)
        assertEquals(999, ReputationState.get())

        ReputationState.set(-999)
        assertEquals(-999, ReputationState.get())
    }

    @Test
    fun `tier mapping follows spec`() {
        ReputationState.reset()

        ReputationState.set(6);  assertEquals(ReputationTier.HIGH, ReputationState.tier())
        ReputationState.set(4);  assertEquals(ReputationTier.HIGH, ReputationState.tier())
        ReputationState.set(3);  assertEquals(ReputationTier.POSITIVE, ReputationState.tier())
        ReputationState.set(1);  assertEquals(ReputationTier.POSITIVE, ReputationState.tier())
        ReputationState.set(0);  assertEquals(ReputationTier.NEGATIVE, ReputationState.tier())
        ReputationState.set(-3); assertEquals(ReputationTier.NEGATIVE, ReputationState.tier())
        ReputationState.set(-4); assertEquals(ReputationTier.EXTREMELY_LOW, ReputationState.tier())
        ReputationState.set(-6); assertEquals(ReputationTier.EXTREMELY_LOW, ReputationState.tier())
    }
}
```

**Step 2: Update existing save-field tests**

为 `ReputationSaveFieldTest` 增加一个负值用例（确保存档读档不把负值抹掉）。

**Step 3: Run tests to verify RED**

Run: `./gradlew clean test --no-daemon`  
Expected: FAIL（目前 `ReputationState.set()` 会强制 >=0，且不存在 tier）

---

### Task 2: 实现 ReputationState 的边界与分段（GREEN）

**Files:**
- Modify: `src/main/kotlin/theforget/reputation/ReputationState.kt`
- Create: `src/main/kotlin/theforget/reputation/ReputationTier.kt`

**Step 1: Minimal implementation**

`ReputationTier`：

```kotlin
package theforget.reputation

enum class ReputationTier {
    HIGH,
    POSITIVE,
    NEGATIVE,
    EXTREMELY_LOW,
}
```

`ReputationState`：
- `set(newValue)` 不做硬限制（直接保存）
- 增加 `tier(): ReputationTier`（按区间映射）

映射建议实现：

```kotlin
fun tier(): ReputationTier =
    when {
        value >= 4 -> ReputationTier.HIGH
        value >= 1 -> ReputationTier.POSITIVE
        value >= -3 -> ReputationTier.NEGATIVE
        else -> ReputationTier.EXTREMELY_LOW
    }
```

**Step 2: Run tests to verify GREEN**

Run: `./gradlew clean test --no-daemon`  
Expected: PASS

---

### Task 3: 增加“玩法规则查询层”（可被污染/好感/模仿系统复用）

**Files:**
- Create: `src/main/kotlin/theforget/reputation/ReputationRules.kt`
- (Optional) Modify: `src/main/kotlin/theforget/reputation/ReputationState.kt`

**Step 1: Write failing tests（如果你希望规则层可单测）**

Create: `src/test/kotlin/theforget/ReputationRulesTest.kt`

```kotlin
@Test
fun `rules follow tiers`() {
  assertEquals(true, ReputationRules.canCleanPollution(4))
  assertEquals(true, ReputationRules.isPollutionCleanAutomatic(6))
  assertEquals(false, ReputationRules.canCleanPollution(0))
  assertEquals(2.0, ReputationRules.affinityGainMultiplier(4))
  assertEquals(0.5, ReputationRules.affinityGainMultiplier(0))
  assertEquals(0.0, ReputationRules.affinityGainMultiplier(-4))
  assertEquals(true, ReputationRules.isImitateBloodCostEnabled(-4))
  assertEquals(false, ReputationRules.isImitateBloodCostEnabled(-3))
}
```

**Step 2: Implement rules (pure functions)**

建议 API（避免浮点也行）：
- `canCleanPollution(reputation: Int): Boolean`
- `isPollutionCleanAutomatic(reputation: Int): Boolean`
- `affinityGainMultiplier(reputation: Int): Double`（或返回 `Int` 分子/分母）
- `isImitateBloodCostEnabled(reputation: Int): Boolean`
- `isImitateDamageBonusEnabled(reputation: Int): Boolean`（先用 bool 钩子；增伤数值作为后续调参项）

**Step 3: Wire convenience helpers (optional)**

在 `ReputationState` 增加：
- `fun canCleanPollution(): Boolean = ReputationRules.canCleanPollution(get())`
- `fun affinityGainMultiplier(): Double = ReputationRules.affinityGainMultiplier(get())`
…这样其他系统调用更方便。

---

### Task 4: UI 表现与本地化同步（Tooltip 显示分段效果）

**Files:**
- Modify: `src/main/kotlin/theforget/reputation/ReputationTopPanelItem.kt`
- Modify: `src/main/resources/theforgetResources/localization/zhs/UIstrings.json`
- Modify: `src/main/resources/theforgetResources/localization/eng/UIstrings.json`

**Step 1: Update top panel number formatting**

将正值显示为 `+N`，负值显示为 `-N`，0 显示 `0`。

**Step 2: Update tooltip template**

建议 tooltip 正文至少包含：
- 当前名望数值
- 当前分段名称（高名望/正名望/负名望/极低）
- 三个关键效果（清理污染 / 好感度获取 / 极低副作用）

实现方式建议：
- `UIstrings.json` 里增加 `{1}` 作为 “tier 名称”
- `ReputationTopPanelItem` 里 `.replace("{0}", valueStr).replace("{1}", tierName)`

---

### Task 5: 文档与 Roadmap 对齐（删掉旧范围/旧逻辑）

**Files:**
- Modify: `Roadmap.md`
- (Recommend) Create: `docs/design/design-v0.06.md`
- (Optional) Modify: `docs/结局.md`

**Step 1: Roadmap.md 更新**

把：
- “双向资源轴实现（-31 到 +32）”
- “名望 < 0：全局修正效果…”
- “名望 < 0 时额外 Lose HP(1)”

改成新分段描述（尤其强调 `0 ~ -3` 为“喘息区”且**没有**模仿扣血副作用；副作用只在 `-4 ~ -6`）。

**Step 2: 设计文档落盘**

建议新增 `docs/design/design-v0.06.md`，把你这张分段表作为“当前版本 spec”，避免反复改旧版本设计文档导致历史不可追溯。

**Step 3: 结局文案（如需要）**

把“名望极高（满值 High Light）”改成明确区间：`高名望（+4~+6）`；“临界爆发点”改成对应数值点（例如 `-3/-4` 边界或你指定的某个点）。

---

### Task 6: Issue 层面同步（把改动变成可追踪工作）

**Where:** GitHub Issues

**Step 1: 新建/更新一个 design discussion issue**

用 `.github/ISSUE_TEMPLATE/design_discussion.yml` 新建 “名望分段改版”：
- 背景：旧设计是连续轴（甚至曾设想 -31~+32），但当前玩法需要明确的喘息区与极低区副作用
- 提案：贴这张分段表
- 验收标准：引用本计划 Acceptance Criteria

**Step 2: Update 关联 issue/roadmap 链接**

如果 Issue #5 之前是“名望系统骨架”，这里建议作为 follow-up（例如 #5b / #6），避免把“UI骨架实现”与“玩法规则/分段”混在同一个 issue。
