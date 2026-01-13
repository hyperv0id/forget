# The Forget 开发踩坑记录

> 更新时间：2026-01-13  
> 覆盖范围：Issue #2（角色骨架 / Character skeleton）、Issue #3（构建系统/资源约定）、Issue #4（自我/HP UI 文案）、Issue #5（名望系统骨架），以及周边基础设施体验

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

## 6) `gh pr comment` / `gh pr edit`：不要把 `\n` 当换行写进去

现象：你在 `gh pr comment -b "line1\nline2"` 或 `gh pr edit --body "..."` 里手写 `\n`，最后 GitHub 上显示的是字面量 `\\n`（而不是换行）。

根因：这类命令的 `-b/--body` 参数接收的是“普通字符串”，`\n` 不一定会被 shell 或 gh CLI 当作换行转义；最终会被当作字符序列写入 GitHub。

解决方式（推荐顺序）：

- **用文件输入**（最稳）：`gh pr comment ... -F /tmp/comment.md` / `gh pr edit ... --body-file /tmp/body.md`
- 或者：直接在参数里写真实换行（不写 `\n`）

---

## 7) TopPanel（战斗内顶栏）Tooltip：坐标不要硬编码

现象：战斗内 hover 顶栏血量区域，tooltip “应该出现但看不到”，像是没生效。

根因：顶栏血量在左上角；如果 tooltip 坐标用了硬编码（例如 `y = HEIGHT - 120*scale`），很容易把 tooltip 画到屏幕外。
原版 `TopPanel.updateTips()` 的逻辑是：

- `x = InputHelper.mX - TIP_OFF_X`
- `y = TIP_Y`

解决方式：

- patch `TopPanel.updateTips()` 时，tooltip 的坐标也复用原版的 `TIP_OFF_X/TIP_Y`（必要时用 `ReflectionHacks.getPrivateStatic` 读取）。

---

## 8) `TipHelper.renderGenericTip` 不会自动展开 `NL`（会把 NL 画出来）

现象：tooltip 文案里写了 `NL`，结果游戏里显示出字面量 “NL”，而不是换行。

根因：`NL` 是 STS 文本资产里“常用但不统一”的换行约定；卡牌描述等地方常见，但 `TipHelper.renderGenericTip` 并不会帮你把 `NL` token 展开成换行。

解决方式：

- tooltip 渲染前手动 normalize：把 `NL` 替换为实际换行符 `\n`（并清理换行两侧空格）。

---

## 9) Javassist `ExprEditor.replace`：static call 里别用 `$0.xxx`

现象：`./gradlew runMts` / 游戏启动的 “Injecting patches” 阶段报：

> `javassist.CannotCompileException: [source error] no such field: $0/c`

根因：我们 patch `CharacterOption.renderInfo()` 时，是在 `ExprEditor` 里替换 `FontHelper.renderSmartText(...)` 调用。
但是 `FontHelper.renderSmartText` 是 **static 方法**：

- `$0` 不指向 `CharacterOption`（甚至可能不存在/语义不同），因此 `$0.c` 会编译失败
- 这类场景应该用 `this`（正在被 instrument 的方法所属实例）访问字段：`this.c`

解决方式：

- 在 replace 的代码块里用 `this` 来访问 `CharacterOption` 实例字段（例如 `this.c`）
- Kotlin multiline string 里如果要输出 `$proceed` / `$3` 这类 Javassist 变量，要记得规避 Kotlin 的 `$` 插值（用 `${'$'}`）。

---

## 10) BaseMod `addSaveField` + Kotlin：需要显式泛型，否则推断会失败

现象：在 Kotlin 里调用 `BaseMod.addSaveField(key, handler)` 可能直接编译失败：

> `Not enough information to infer type variable T`

根因：BaseMod 的签名是：

```java
public static <T> void addSaveField(String key, CustomSavableRaw saveField)
```

它虽然有 `<T>`，但实际参数只有 `CustomSavableRaw`，Kotlin 无法从参数推断出 `T`，于是报错。

解决方式：

- 在 Kotlin 调用处显式标注泛型（推荐）：`BaseMod.addSaveField<Int?>(...)`
- 或者把 saveField 变量显式声明为 `CustomSavableRaw`（但可读性更差）

本项目中的做法（Issue #5）：
- `src/main/kotlin/theforget/TheForgetMod.kt`：`BaseMod.addSaveField<Int?>(REPUTATION_SAVE_FIELD_ID, reputationSaveField)`
- `src/main/kotlin/theforget/reputation/ReputationSaveField.kt`：`CustomSavable<Int?>` 并在 `onLoad(null)` 时回退默认值

---

## 11) 测试编译 classpath：测试也需要 STS/BaseMod jar 才能编译通过

现象：你在 main source 里引用了 BaseMod/STS 类型（例如 `CustomSavable` / `AbstractDungeon`），即使 production 编译依赖是 `compileOnly`，也可能导致 `./gradlew test` 在 `compileTestKotlin` 阶段失败：

> `unresolved supertypes: basemod.abstracts.CustomSavable`

根因：测试代码（以及测试编译阶段）也会编译 production classes；如果 test classpath 没有 BaseMod 这些 jar，Kotlin 编译器会认为 supertypes 缺失。

解决方式：

- 在 `build.gradle` 里为 tests 增加同一组本地 jar：
  - `testImplementation files(stsJar, mtsJar, baseModJar, stslibJar)`

注意：这不代表把这些 jar “打进 mod jar”，只是让测试能编译/运行。

---

## 12) TopPanelItem 只对本角色添加：否则会“白占位/与 UI mod 冲突”

现象：用 `BaseMod.addTopPanelItem(...)` 做额外资源 UI 很方便，但如果你在 mod 初始化时无条件 add：

- 其它角色也会在顶栏看到一个“空图标/无意义 UI”
- 一些 UI mod（例如隐藏顶栏/改布局）可能更容易出现冲突或遮挡

解决方式（Issue #5 的经验）：

- 在 `PostDungeonInitializeSubscriber`（拿到 `AbstractDungeon.player` 之后）再决定 add/remove
- 并且维护一个布尔标记避免重复 add（BaseMod/TopPanelGroup 的顺序是“注册顺序”，重复 add 会造成多份）

本项目做法：
- `src/main/kotlin/theforget/TheForgetMod.kt`：`receivePostDungeonInitialize()` 时按 `chosenClass` 动态 add/remove 名望 item

---

## 参考模组（本机对照来源）

以下目录来自 `../resources/mods/`，用于对照常见实现姿势（不代表许可/可复制粘贴到本仓库）：

- `../resources/mods/LingMod/`（含 `lingmod/character/Ling.java`）
- `../resources/mods/ThePackmaster/`
- `../resources/mods/DuskMod/`
- `../resources/mods/AcaciaTheSpire/`
