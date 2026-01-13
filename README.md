# The Forget

杀戮尖塔（Slay the Spire）角色模组：**Forget for Get**（遗忘是为了获得）。

## Issue #1：ModLoader 选择与集成（BaseMod + ModTheSpire）

### 为什么是 “ModTheSpire + BaseMod”？

- **ModTheSpire (MTS)**：实际的 *mod loader / launcher*，负责扫描 `mods/`、加载补丁、启动游戏。
- **BaseMod**：社区事实标准的 *API 层*（订阅事件、卡牌/遗物/角色注册等），大多数模组都会依赖它。

所以结论不是二选一，而是：**用 ModTheSpire 启动，用 BaseMod 写模组。**

## 开发环境要求

- JDK：任意现代 JDK 均可（本项目用 `--release 8` 编译为 Java 8 目标，兼容 STS 生态）。
- Steam 版 STS（推荐）：用于拿到 `desktop-1.0.jar` 与 Workshop 的 `BaseMod.jar` / `ModTheSpire.jar`。

## 快速开始（Linux / Steam 默认路径）

默认情况下，`build.gradle` 会去这些位置找依赖：

- `~/.steam/steam/steamapps/common/SlayTheSpire/desktop-1.0.jar`
- `~/.steam/steam/steamapps/workshop/content/646570/1605060445/ModTheSpire.jar`
- `~/.steam/steam/steamapps/workshop/content/646570/1605833019/BaseMod.jar`

确保你已在 Workshop 订阅并安装：

- ModTheSpire (Workshop ID: `1605060445`)
- BaseMod (Workshop ID: `1605833019`)

然后：

```bash
./gradlew clean build
./gradlew installMod
./gradlew runMts
```

### 如何验证加载成功？

启动后，在 ModTheSpire / 控制台日志中应看到类似：

- `The Forget loaded successfully.`

（该日志由 `theforget.TheForgetMod` 打出。）

## 自定义依赖路径（Windows/macOS/非 Steam 安装）

可以通过 Gradle 属性覆盖默认路径：

```bash
./gradlew clean build \
  -PstsDir="/path/to/SlayTheSpire" \
  -PmtsJar="/path/to/ModTheSpire.jar" \
  -PbaseModJar="/path/to/BaseMod.jar"
```

也可以只覆盖其中一个。
