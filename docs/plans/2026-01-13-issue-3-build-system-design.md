# Issue #3: Build System + Resource Convention (Design)

> **For Claude:** If implementing, use `superpowers:writing-plans` → `superpowers:executing-plans`.

**Issue:** #3 `[Infra] 构建系统配置`  
**Date:** 2026-01-13  
**Goal:** 完成构建系统的“最后 30%”：补齐 `stslib` 依赖、建立资源打包/命名约定（避免跨 mod 路径重名）、提供开发期 `--continuous` 自动 `installMod` 工作流。

---

## 0. 现状与差距（以仓库 main 为准）

仓库已具备（Issue #1 + #2 已完成）：

- Gradle + Kotlin 编译并产出 `TheForget.jar`
- `verifyLocalDeps`：检查 STS / MTS / BaseMod jar 是否存在
- `installMod`：复制 jar 到 `${stsDir}/mods/`
- `runMts`：启动 ModTheSpire（带 `--skip-intro`，并通过 `--mods` 指定 mod 列表）
- 单测：能验证 STS jar 中的占位资源是否存在；起手牌/遗物占位内容

Issue #3 还缺（或需要标准化）的内容：

1) **StSLib**：MTS 生态里几乎是“基础依赖”，不少模组默认依赖；本机已安装（Steam workshop）但 build.gradle/ModTheSpire.json 还没接入。
2) **资源约定**：需要统一“资源根目录前缀”，避免跨 mod 路径冲突/重名，以及后续自定义资源可规模化管理。
3) **开发期热加载（你选择方案 1）**：不做 JVM hotswap，而是“代码/资源变更→自动 build+copy jar→你手动重启游戏或回主菜单再开局”。最稳、最贴近 STS mod 日常。
4) **存在性校验**：不仅要校验 STS jar 内资源（no-assets skeleton 阶段），还要能校验“本 mod jar 内资源都在正确前缀目录下，且必须资源存在”。

---

## 1) 资源路径约定（核心：避免跨 mod 重名）

### 1.1 约定：一切资源都必须在 `theforgetResources/` 下

参考对照（来自本机 `../resources/mods/*`）：

- ThePackmaster：`anniv5Resources/...`
- LingMod：`lingmodResources/...`

本项目统一采用：

```
src/main/resources/theforgetResources/
  images/
    ui/
    charSelect/
    characters/
    cards/
    relics/
    powers/
  localization/
    zhs/
    eng/
  audio/
    sfx/
    voice/
    music/
  shaders/
```

这条约定解决的核心问题：就算别的 mod 也有 `images/ui/...`，我们永远不会用那个“全局路径”，从根上避免撞名。

### 1.2 代码侧约定：禁止散落硬编码路径字符串

新增一个集中模块（名称可选）：

- `theforget.core.TheForgetPaths`（或 `TheForgetResources`）

提供 helper：

- `makeId("X") -> "theforget:X"`
- `makePath("localization/zhs/cards.json") -> "theforgetResources/localization/zhs/cards.json"`
- `makeImagePath("ui/charSelect/button.png") -> "theforgetResources/images/ui/charSelect/button.png"`
- `makeAudioPath("sfx/hit.ogg") -> "theforgetResources/audio/sfx/hit.ogg"`

这会显著降低后期“资源改名/目录迁移”的成本，并减少人工拼字符串引发的路径错误。

**约束（lint 级别建议）：**
- `src/main/kotlin` 中出现 `\"images/\"`、`\"localization/\"` 等裸路径应视为违规（例外：当前仍在复用 STS 内置资源的 `TheForgetAssets` 占位常量）。

---

## 2) 构建/依赖约定

### 2.1 依赖：BaseMod + StSLib 一起接入

目标：

- `build.gradle`：`compileOnly files(stsJar, mtsJar, baseModJar, stslibJar)`
- `verifyLocalDeps`：报错信息里也包含 `StSLib.jar` 缺失提示（和其他 jar 一样可通过 `-P...` 覆盖）
- `ModTheSpire.json`：dependencies 加上 `"stslib"`
- `runMts`：`--mods basemod,stslib,theforget`

路径推断（Linux/Steam）：

- `~/.steam/steam/steamapps/workshop/content/646570/1609158507/StSLib.jar`

并允许通过 `-PstslibJar=/path/to/StSLib.jar` 覆盖。

### 2.2 Jar 打包：资源默认会被打进 jar

Gradle 的 `processResources` 默认会把 `src/main/resources/**` 打进 jar，所以只要我们把资源放在 `src/main/resources/theforgetResources/**`，就天然会进包。

本项目的 Kotlin stdlib 已被打包进 jar（减少运行环境依赖），STS/MTS/BaseMod/StSLib 仍保持 `compileOnly`（避免把别人的 jar 打进我们的 mod jar）。

---

## 3) 开发期“热加载”（方案 1：continuous install）

### 3.1 定义

“热加载”在此 Issue 中定义为：

- 文件变更（Kotlin 源码或 `src/main/resources`）
- 触发自动 `jar` + `installMod`（复制到 STS `mods/`）
- 游戏本体不自动重启（你可以手动重启，或回主菜单再开局）

### 3.2 交付形态

推荐提供二选一（可同时做）：

1) `scripts/dev-watch-install.sh`
   - 内部执行：`./gradlew --continuous installMod --no-daemon`
2) Gradle task：`devWatchInstall`
   - 作为 `Exec` 包一层，方便 README 里一个命令启动

在 STS mod 开发里，这个方式是最稳的，且不需要 OS 级 inotify/watchman 依赖。

---

## 4) 验收与自动化校验

### 4.1 Issue #3 验收标准映射

- `./gradlew build` 生成 jar ✅（已有，但应在加入 stslib 后继续成立）
- jar 可被 MTS 正确加载 ✅（通过 `runMts` 手动验证）
- 热加载脚本可正常工作 ✅（continuous install 能持续运行、变更触发重新 copy）

### 4.2 自动化测试建议

新增单测（JUnit）：

**Test A：mod jar 内资源路径必须带 `theforgetResources/` 前缀**
- Build 后打开 `build/libs/TheForget.jar`（ZipFile）
- 若存在 `images/`、`localization/` 这类“裸前缀”，测试失败（避免和别的 mod 撞目录）

**Test B：必须资源存在**
- 至少包含：`ModTheSpire.json`、（未来）`theforgetResources/localization/...`（可先放占位 json）

> 注：Issue #2 的 `AssetPathsTest` 是验证 STS jar 内占位资源存在；Issue #3 增加的是验证“我们自己的 jar 内资源组织正确”。两者是不同维度的防线。

---

## 5) 取舍（为什么不做 JVM 真热替换）

STS + MTS 的开发中，Kotlin/Java 真正的 class hotswap 有限制（改签名、改字段、改构造等基本都不行），并且会把问题复杂化。

本 Issue 选择的方案（continuous install）满足“开发效率”与“实现复杂度”的平衡：可快速迭代且稳定，且与社区常见开发方式一致。

