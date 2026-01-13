# Issue #3: Build System Configuration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐 STS mod 构建系统的最后关键能力：接入 `stslib`、统一资源前缀目录 `theforgetResources/` 并增加 jar 内资源校验测试，同时提供 `--continuous installMod` 的开发期“热加载”（自动 build+copy jar）。

**Architecture:** 依赖仍使用本地 jar 的 `compileOnly`（STS/MTS/BaseMod/StSLib），通过 `verifyLocalDeps` fail-fast 并给出覆盖参数；资源全部放在 `src/main/resources/theforgetResources/**` 并通过单元测试验证 jar 内路径前缀；热加载脚本用 Gradle `--continuous` 触发 `installMod`。

**Tech Stack:** Kotlin, Gradle, BaseMod, ModTheSpire, StSLib, JUnit5.

---

### Task 1: 写失败测试（资源必须在 theforgetResources 前缀下）

**Files:**
- Create: `src/test/kotlin/theforget/ModJarResourcesTest.kt`

**Step 1: 写 failing test**

创建一个测试，检查 `build/libs/TheForget.jar`：

- jar 文件存在
- jar 内不允许出现以下“裸前缀目录”（因为容易与别的 mod 冲突）：
  - `images/`
  - `localization/`
  - `audio/`
  - `shaders/`
- 若出现这些裸前缀，则 FAIL，并打印出 offending entries（截取前 50 条即可）

示例代码（计划用，不要复用到其他测试）：

```kotlin
val jar = File("build/libs/TheForget.jar")
ZipFile(jar).use { zip ->
  val badPrefixes = listOf("images/", "localization/", "audio/", "shaders/")
  val offenders = zip.entries().asSequence()
    .map { it.name }
    .filter { name -> badPrefixes.any { name.startsWith(it) } }
    .toList()
  assertTrue(offenders.isEmpty(), "Found un-namespaced resource entries:\n" + offenders.take(50).joinToString("\n"))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew clean jar test --no-daemon`  
Expected: FAIL（当前 jar 内包含 `ModTheSpire.json` 但不会有裸前缀目录；如果测试通过，下一步调整测试为“必须存在 theforgetResources/localization/... 占位文件”，让其先红起来。）

**Step 3: Commit**

```bash
git add src/test/kotlin/theforget/ModJarResourcesTest.kt
git commit -m "test: enforce namespaced mod resources"
```

---

### Task 2: 建立 theforgetResources 资源目录与最小占位文件

**Files:**
- Create: `src/main/resources/theforgetResources/localization/zhs/strings.json` (占位即可)

**Step 1: 添加最小占位文件**

内容可以先是最小 JSON，比如：

```json
{}
```

目的：让 Task 1 的测试可以扩展为“必须存在至少一个 theforgetResources 条目”，避免我们忘记切换资源体系。

**Step 2: Update test（让它能验证存在 theforgetResources 前缀）**

在 `ModJarResourcesTest` 里增加一个断言：
- jar entries 里至少有 1 个以 `theforgetResources/` 开头的 entry

**Step 3: Run tests**

Run: `./gradlew clean jar test --no-daemon`  
Expected: PASS

**Step 4: Commit**

```bash
git add src/main/resources/theforgetResources/localization/zhs/strings.json
git add src/test/kotlin/theforget/ModJarResourcesTest.kt
git commit -m "chore: add theforgetResources placeholder + jar resource checks"
```

---

### Task 3: 接入 StSLib（build.gradle + ModTheSpire.json + runMts）

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/ModTheSpire.json`

**Step 1: build.gradle 增加 stslibJar 推断与覆盖参数**

新增：

- `def stslibJarPath = findProperty("stslibJar") ?: (defaultWorkshopDir ? "${defaultWorkshopDir}/1609158507/StSLib.jar" : null)`
- `def stslibJar = file(stslibJarPath ?: "MISSING-StSLib.jar")`

并在：
- `dependencies { compileOnly files(stsJar, mtsJar, baseModJar, stslibJar) }`
- `verifyLocalDeps` 里加入缺失校验与提示

**Step 2: ModTheSpire.json dependencies 加入 stslib**

把：
```json
"dependencies": ["basemod"]
```
改为：
```json
"dependencies": ["basemod", "stslib"]
```

（顺序无所谓，但保持一致）

**Step 3: runMts 增加 stslib**

把 `--mods basemod,theforget` 改为 `--mods basemod,stslib,theforget`

**Step 4: Verify**

Run: `./gradlew verifyLocalDeps --no-daemon`  
Expected: PASS（本机 Steam workshop 有 StSLib.jar）

Run: `./gradlew clean build --no-daemon`  
Expected: PASS

**Step 5: Commit**

```bash
git add build.gradle src/main/resources/ModTheSpire.json
git commit -m "build: add StSLib as compileOnly dependency"
```

---

### Task 4: 增加开发期 continuous install 脚本（热加载方案 1）

**Files:**
- Modify: `build.gradle`
- Modify: `.gitignore`（若需要）
- Modify: `README.md`

**Step 1: 添加 Gradle task `devWatchInstall`**

在 `build.gradle` 里新增：

- `devWatchInstall`（`dependsOn installMod`）
- `group = "development"`
- `description` 说明它用于配合 `--continuous`

**用法：**

- `./gradlew devWatchInstall --continuous --no-daemon`

**Step 2: README 增加开发工作流段落**

新增章节（简短即可）：

- “开发热加载（continuous install）”
- 示例命令：`./gradlew devWatchInstall --continuous --no-daemon`
- 可选参数覆盖：`./gradlew devWatchInstall --continuous --no-daemon -PstsDir=...`

**Step 3: Verify（脚本可运行）**

Run: `./gradlew devWatchInstall --continuous --no-daemon -PstsDir="$HOME/.steam/steam/steamapps/common/SlayTheSpire"`  
Expected: 进入 continuous 状态，检测到变更时会重新执行 `installMod`（复制 jar）

（手动 Ctrl+C 停止即可）

**Step 4: Commit**

```bash
git add build.gradle README.md
git commit -m "dev: add devWatchInstall continuous install task"
```

---

### Task 5: 更新 Issue #3 文档与验收清单

**Files:**
- Modify: `docs/plans/2026-01-13-issue-3-build-system-design.md`

**Step 1: 补充“已实现”勾选与路径**

在 design 文档末尾加一个 checklist，标记：
- stslib 接入
- resources 前缀
- jar 资源测试
- `devWatchInstall` task（`--continuous`）

**Step 2: Run tests**

Run: `./gradlew test --no-daemon`  
Expected: PASS

**Step 3: Commit**

```bash
git add docs/plans/2026-01-13-issue-3-build-system-design.md
git commit -m "docs: finalize issue #3 design with acceptance checklist"
```

---

## Manual Acceptance (Issue #3)

1) `./gradlew clean build --no-daemon` 生成 `build/libs/TheForget.jar`  
2) `./gradlew installMod --no-daemon` 复制到 STS `mods/`  
3) `./gradlew runMts --no-daemon` 可正常加载（日志包含 “The Forget loaded successfully.”）  
4) `./gradlew devWatchInstall --continuous --no-daemon` 持续运行；修改任意 `src/main/kotlin/**` 或 `src/main/resources/**`，观察到自动重新执行 `installMod`
