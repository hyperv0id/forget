# The Forget 开发踩坑记录

> 更新时间：2026-01-13  
> 覆盖范围：Issue #2（角色骨架 / Character skeleton），以及周边基础设施体验

本文用于记录“当时踩过、后来证明确实会坑一次”的点，避免反复浪费时间。

---

## 1) 资源目录命名：`../resources/mods`（不是 `../resources/mod`）

一开始我误判了本地模组源码/解包目录的位置，导致对照参考模组的路径不对。

你本机实际结构是：

- `../resources/desktop-1.0.jar`：STS 主 jar（用于 compileOnly + 测试校验）
- `../resources/mods/*`：大量 mod 的解包目录（包含 `.java` / `.kt` / `ModTheSpire.json` 等）
- `../lingmod/`：另一个可对照的 Ling 模组源码工程（Maven 工程）

快速定位某个模组有没有角色实现（CustomPlayer）：

```bash
rg -n "extends CustomPlayer|: CustomPlayer\\(" ../resources/mods -S --glob='*.java' --glob='*.kt'
```

---

## 2) 选中角色后“抖动很久”：根因通常是 ScreenShake 参数太激进

现象：在角色选择界面点击/选中角色后，屏幕抖动明显且持续时间长，主观上像“抖动/抖屏/晃动不停”。

根因：`doCharSelectScreenSelectEffect()` 的震动参数设置过强（例如 `HIGH + XLONG`）。
这不是“角色渲染抖动”，而是“屏幕震动”本身持续太久导致。

对照结果（原版 + 多个模组的共识）：

- 原版 Ironclad：`MED + SHORT`
- ThePackmaster：`LOW + SHORT`
- DuskMod：`MED + SHORT`
- AcaciaTheSpire：`MED + SHORT`
- LingMod：也不会用 `HIGH + XLONG` 这种长震动

在本项目里的修复：把 `TheForgetCharacter.doCharSelectScreenSelectEffect()` 的参数对齐原版。

相关代码：
- `src/main/kotlin/theforget/characters/TheForgetCharacter.kt`

---

## 3) CustomPlayer 的能量球资源不能随便传 `null`

现象：角色可选，但进入 run / 进入战斗后容易出现崩溃或 UI 异常（能量球相关）。

根因：`basemod.abstracts.CustomPlayer` 的部分构造函数会用 `orbTextures`/`orbVfxPath` 初始化能量球实现。
如果传 `null`，有些情况下会在运行期触发 NPE 或渲染异常（取决于 BaseMod 版本与调用路径）。

业界做法（从多个模组一致观察到）：

- 要么提供自己的 orb 资源（layer1..6 + layer1d..5d + vfx.png）
- 要么在“无资源占位阶段”直接复用 STS 内置能量球资源路径

本项目当前采取的是第二种（no-assets skeleton 阶段），并且把资源路径集中在一个地方，方便替换：

- `src/main/kotlin/theforget/core/TheForgetAssets.kt`
  - `ENERGY_ORB_TEXTURES`
  - `ENERGY_ORB_VFX`
  - `ENERGY_ORB_LAYER_SPEEDS`

同时在测试里校验这些路径确实存在于 `desktop-1.0.jar`：

- `src/test/kotlin/theforget/AssetPathsTest.kt`

注意：当你后续换成**自定义资源打包进 mod jar**时，这个测试需要同步改造（否则会因为不再依赖 STS 内置资源而失败）。

---

## 4) “先复用战士资源/红色卡色”的影响与边界

当前 skeleton 为了跑起来，复用了大量 Ironclad 资产，并把卡色临时设为 `RED`：

- 选择按钮/头像/Spine skeleton/肩膀/尸体：复用 Ironclad
- 能量球：复用 STS 内置红色 orb
- `getCardColor()`：返回 `AbstractCard.CardColor.RED`
- 起手卡/遗物：使用 STS 内置 `"Strike_R"`, `"Defend_R"`, `"Burning Blood"` 占位

影响评估（简要）：

- **视觉层面**：零技术风险，只是“看起来像战士”，后续换资源即可
- **卡色层面**：这是对后续影响最大的部分  
  如果以后要做“完全独立角色卡色”，需要新增 `@SpireEnum` 的 `AbstractCard.CardColor` + `CardLibrary.LibraryType` 并 `BaseMod.addColor(...)` 注册全套卡面资源
- **测试层面**：当前测试在验证“STS jar 内存在占位资源路径”，后续改成自定义资源后要调整测试策略

结论：现在先这样是 OK 的（MVP 优先），但“自定义卡色/自定义资源”迟早要独立出来做一个后续 issue。

---

## 5) `gh pr create` 的 shell 反引号坑（命令行写 PR 描述时）

在 bash 里，如果 PR body 里写了未转义的反引号（例如 `` `TheForgetCharacter` ``），bash 会把它当命令替换执行，导致出现：

> `/usr/bin/bash: ...: 未找到命令`

解决方式：

- 用单引号包裹整个 `--body '...'`（并避免在其中再写单引号），或
- 使用 `--body-file` 指向一个临时 markdown 文件（最稳），或
- 改用 `gh pr edit --body '...'` 事后修正文案

---

## 参考模组（本机对照来源）

以下目录来自 `../resources/mods/`，用于对照常见实现姿势（不代表许可/可复制粘贴到本仓库）：

- `../resources/mods/LingMod/`（含 `lingmod/character/Ling.java`）
- `../resources/mods/ThePackmaster/`
- `../resources/mods/DuskMod/`
- `../resources/mods/AcaciaTheSpire/`

