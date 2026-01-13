# Localization、IDs、Prefs/Save 相关坑点

> 面向 STS mod 工程师，聚焦多语言、ID 命名、配置持久化三大主题的常见陷阱。

---

## 目录

1. [Localization 多语言](#1-localization-多语言)
2. [IDs 命名规范](#2-ids-命名规范)
3. [SpireConfig & Save 持久化](#3-spireconfig--save-持久化)
4. [注册时机速查表](#4-注册时机速查表)
5. [推荐目录结构](#5-推荐目录结构)

---

## 1. Localization 多语言

### 1.1 加载时机错误

#### 🔴 **坑点：在构造函数中调用 `loadCustomStringsFile`**

**现象**：不会报错，但多语言文本无法加载，游戏内显示 `MISSING:` 或 ID。

**根因**：`receiveEditStrings()` 回调的调用时机早于构造函数执行。在构造函数中加载的字符串会被后续的回调覆盖。

**证据指针**：
- ✅ **WljMod** (wlj-mod-1.3.4)：在 `receiveEditStrings()` 回调中加载本地化文件（行 173-206）
- ✅ **AyaMod**：在 `receiveEditStrings()` 回调中加载本地化文件（行 274-285）
- ❌ **错误示例**：在主类构造函数中直接调用 `BaseMod.loadCustomStringsFile()`

**推荐做法**：
```java
public class MyMod implements EditStringsSubscriber {
    public void receiveEditStrings() {
        // ✓ 正确：在回调中加载
        String lang = getLocalizationLanguage();
        BaseMod.loadCustomStringsFile(CardStrings.class,
            "localization/" + lang + "/my_cards.json");
    }
}
```

**自检清单**：
- [ ] 所有 `loadCustomStringsFile` 调用都在 `receiveEditStrings()` 回调内
- [ ] 没有在构造函数中直接调用本地化加载方法

---

### 1.2 UTF-8 编码问题

#### 🔴 **坑点：JSON 文件未指定 UTF-8 编码读取**

**现象**：中文、日文等多语言文本显示为乱码或方框。

**根因**：`Gdx.files.internal().readString()` 默认使用系统编码，而非 UTF-8。

**证据指针**：
- ✅ **WljMod**（行 192）：`readString(String.valueOf(StandardCharsets.UTF_8))`
- ✅ **AyaMod**（行 290）：`readString(String.valueOf(StandardCharsets.UTF_8))`
- ✅ **AnonMod**（行 446）：`readString(String.valueOf(StandardCharsets.UTF_8))`

**推荐做法**：
```java
// ✓ 正确：显式指定 UTF-8
String json = Gdx.files.internal("localization/eng/my_keywords.json")
    .readString(String.valueOf(StandardCharsets.UTF_8));
```

**自检清单**：
- [ ] 所有 `readString()` 调用都显式传入 `StandardCharsets.UTF_8`

---

### 1.3 语言回退机制

#### 🟡 **坑点：未处理不支持语言的回退**

**现象**：游戏语言设置为 `POR`（葡萄牙语）时，找不到对应本地化文件，崩溃或显示 `MISSING:`。

**根因**：仅提供了部分语言（如 `eng`、`zhs`、`zht`），未设置默认回退到 `eng`。

**证据指针**：
- ✅ **WljMod**（行 175-182）：使用 `switch(Settings.language)` 并 `default` 到 `zhs`
- ✅ **AyaMod**（行 197-206）：使用 `switch` 并 `default` 到 `eng`
- ⚠️ **AnonMod**（行 476）：使用 `if (Settings.language == GameLanguage.ZHS)` else 模式

**推荐做法**：
```java
private String getLocalizationLanguage() {
    switch (Settings.language) {
        case ZHS: return "zhs";
        case ZHT: return "zht";
        case JPN: return "jpn";
        // ... 其他支持的语言
        default: return "eng"; // ✓ 默认回退到英文
    }
}
```

**自检清单**：
- [ ] `switch` 语句包含 `default` 分支回退到 `eng`
- [ ] 或使用 `if-else` 链并包含 `else` 默认值

---

### 1.4 Keywords 注册时机

#### 🔴 **坑点：Keywords 在 `receiveEditStrings()` 中注册**

**现象**：关键词提示文本显示为 ID 或 `MISSING:`。

**根因**：`BaseMod.addKeyword()` 必须在 `receiveEditKeywords()` 回调中调用，而非 `receiveEditStrings()`。

**证据指针**：
- ✅ **WljMod**（行 149-156）：在 `receiveEditKeywords()` 中调用 `BaseMod.addKeyword()`
- ✅ **AyaMod**（行 287-298）：在 `receiveEditKeywords()` 中调用 `BaseMod.addKeyword()`
- ⚠️ **AnonMod**（行 449-464）：在 `receiveEditKeywords()` 中调用，但未使用 `@SpireEnum` 创建的前缀

**推荐做法**：
```java
public void receiveEditKeywords() {
    // 1. 先读取 JSON
    String json = Gdx.files.internal("localization/" + lang + "/my_keywords.json")
        .readString(String.valueOf(StandardCharsets.UTF_8));
    Keyword[] keywords = new Gson().fromJson(json, Keyword[].class);

    // 2. 在此回调中注册
    for (Keyword keyword : keywords) {
        BaseMod.addKeyword(
            modID.toLowerCase(),  // ✓ 前缀
            keyword.PROPER_NAME,
            keyword.NAMES,
            keyword.DESCRIPTION
        );
    }
}
```

**自检清单**：
- [ ] `addKeyword()` 调用位于 `receiveEditKeywords()` 回调内
- [ ] 传入的第一个参数是 `modID.toLowerCase()`

---

### 1.5 ID 前缀规范

#### 🔴 **坑点：ID 未使用 `modID:` 前缀**

**现象**：与其他 mod 的 ID 冲突，导致卡牌/遗物描述错乱。

**根因**：ID 必须全局唯一，使用 `modID:` 前缀是社区约定。

**证据指针**：
- ✅ **Downfall**：所有 ID 使用 `awakened:` 前缀（如 `awakened:Thunderbolt`）
- ✅ **WljMod**：使用 `Wlj:` 前缀（如 `Wlj:Cup`）
- ✅ **AyaMod**：使用 `theAya:` 前缀（如 `theAya:FlyingPotion`）
- ✅ **HSRMod**：使用 `HSRMod:` 前缀（如 `HSRMod:Trailblazer1`）
- ⚠️ **AnonMod**：部分 ID 未使用前缀（如 `Inner`、`liveboost`）

**推荐做法**：
```java
// ✓ 在 JSON 中
{
  "MyMod:Strike": {
    "NAME": "Strike",
    "DESCRIPTION": "Deal !D! damage."
  }
}

// ✓ 在代码中
public static final String ID = "MyMod:Strike";
```

**自检清单**：
- [ ] 所有 localization JSON 中的 key 都使用 `modID:` 前缀
- [ ] Java 代码中的常量 ID 也使用相同前缀

---

### 1.6 NL 换行符展开问题

#### 🟡 **坑点：混淆 `NL` 与 `\n`**

**现象**：卡牌描述中的换行符不生效，显示为字面量 `NL` 或 `\n`。

**根因**：
- `NL` 是 STS 的特殊标记，会在运行时展开为 `\n`
- 直接使用 `\n` 不会在描述中正确换行

**证据指针**：
- ✅ **Downfall**（CardStrings.json）：大量使用 `NL`（如 `"DESCRIPTION": "Retain. NL Deal !D! damage. NL Exhaust."`）
- ✅ **HSRMod**（ui.json）：混合使用 `NL` 和普通文本

**推荐做法**：
```json
// ✓ 正确：使用 NL 进行换行
{
  "MyMod:MyCard": {
    "DESCRIPTION": "Gain !B! Block. NL Deal !D! damage. NL Exhaust."
  }
}

// ✗ 错误：直接使用 \n
{
  "MyMod:MyCard": {
    "DESCRIPTION": "Gain !B! Block.\nDeal !D! damage.\nExhaust."
  }
}
```

**自检清单**：
- [ ] JSON 文件中的描述使用 ` NL ` 进行换行（前后有空格）
- [ ] 仅在需要字面量 `\n` 时才使用转义符

---

## 2. IDs 命名规范

### 2.1 与 @SpireEnum 的冲突风险

#### 🔴 **坑点：Enum 值名与 Localization ID 混用**

**现象**：使用 `@SpireEnum` 创建的 `CardColor`、`CardTags` 等的 `name()` 返回值可能作为 ID 使用，导致歧义。

**根因**：
- `@SpireEnum` 注解的静态字段在运行时会被赋予唯一名称
- 但这个名称可能与手动指定的 localization ID 不一致

**证据指针**：
- ✅ **AnonMod**（CardTagsEnum.java）：使用 `@SpireEnum` 创建 `Band` tag
- ✅ **LingMod**（ModEnums.java）：使用 `@SpireEnum` 创建多个枚举值
- ⚠️ **常见错误**：将 `@SpireEnum` 字段名直接用于 localization key

**推荐做法**：
```java
// ✓ 正确：明确区分 Enum 值和 Localization ID
public class MyCardTags {
    @SpireEnum
    public static AbstractCard.CardTags MY_TAG;  // Enum 值
}

// JSON 中使用完整的 modID: 前缀
{
  "MyMod:MyCard": {
    "DESCRIPTION": "Has #myModMyTag."
  }
}
```

**自检清单**：
- [ ] Localization JSON 中的 ID 始终使用 `modID:` 前缀
- [ ] 不依赖 `@SpireEnum` 字段的 `.name()` 返回值作为 localization key

---

### 2.2 推荐的 ID 命名风格

#### 🟢 **最佳实践**：

**格式**：`<modID>:<EntityName>`

**示例**：
- 卡牌：`MyMod:Strike`、`MyMod:Defend`
- 遗物：`MyMod:BurningBlood`、`MyMod:Anchor`
- 力量：`MyMod:Weakened`、`MyMod:Energized`
- 药水：`MyMod:FirePotion`、`MyMod:StrengthPotion`

**证据指针**：
- ✅ **Downfall**：`awakened:Thunderbolt`、`awakened:Cryostasis`
- ✅ **HSRMod**：`HSRMod:Trailblazer1`、`HSRMod:March7th0`
- ✅ **WljMod**：`Wlj:Cup`、`Wlj:Frog`（Event ID）

**命名约定**：
1. **modID 部分**：与 ModTheSpire.json 中的 `modid` 字段完全一致（大小写敏感）
2. **分隔符**：使用英文冒号 `:`（唯一合法分隔符）
3. **实体名部分**：
   - 使用 PascalCase（首字母大写）
   - 避免特殊字符（仅字母、数字、下划线）
   - 简洁但描述性强

**自检清单**：
- [ ] 所有 ID 遵循 `<modID>:<EntityName>` 格式
- [ ] modID 与 ModTheSpire.json 中的定义一致
- [ ] 实体名使用 PascalCase，无特殊字符

---

## 3. SpireConfig & Save 持久化

### 3.1 SpireConfig 注册时机

#### 🔴 **坑点：SpireConfig 创建时机不当**

**现象**：
- 情况 A：在 `initialize()` 静态方法中创建，但未正确加载配置
- 情况 B：在 `receivePostInitialize()` 中创建，导致早期代码无法访问

**根因**：`SpireConfig` 需要在 `initialize()` 或静态初始化块中创建并 `load()`，才能在后续回调中使用。

**证据指针**：
- ✅ **WljMod**（行 96-100）：在构造函数中创建 `SpireConfig`
- ✅ **AnonMod**（行 1042-1045）：在静态初始化块中创建 `SpireConfig saves`
- ✅ **AyaMod**（行 110-116）：在构造函数中创建并 `load()` 配置
- ⚠️ **常见错误**：在 `receivePostInitialize()` 中创建配置

**推荐做法**：
```java
@SpireInitializer
public class MyMod {
    public static SpireConfig config;

    static {
        try {
            Properties defaults = new Properties();
            defaults.setProperty("mySetting", "false");
            config = new SpireConfig("MyMod", "MyModConfig", defaults);
            config.load();  // ✓ 立即加载
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void initialize() {
        new MyMod();
    }
}
```

**自检清单**：
- [ ] `SpireConfig` 在静态初始化块或构造函数中创建
- [ ] 创建后立即调用 `config.load()`
- [ ] 不在 `receivePostInitialize()` 等回调中创建配置

---

### 3.2 getPrefs 时机与 lazy init

#### 🟡 **坑点：过早访问未初始化的配置**

**现象**：在 `receiveEditCards()` 等早期回调中访问 `config.getBool()`，返回值不正确或抛出异常。

**根因**：虽然 `SpireConfig` 已创建，但某些配置项可能需要延迟加载（lazy init）。

**证据指针**：
- ✅ **WljMod**（行 83-89）：使用 `getVoiceDisabled()` 方法，内部检查 `config == null`
- ⚠️ **常见错误**：直接在回调中访问 `config.getBool("someKey")`

**推荐做法**：
```java
// ✓ 使用 getter 方法，提供默认值
public static boolean isMyFeatureEnabled() {
    if (config == null) {
        return false;  // ✓ 安全默认值
    }
    try {
        return config.getBool("myFeature");
    } catch (Exception e) {
        return false;
    }
}

// ✓ 在需要时才调用
public void receiveEditCards() {
    if (isMyFeatureEnabled()) {
        // 添加特定卡牌
    }
}
```

**自检清单**：
- [ ] 所有配置访问都通过 getter 方法包装
- [ ] getter 方法包含 `config == null` 检查
- [ ] 提供合理的默认值

---

### 3.3 onSave/onLoad 实现

#### 🔴 **坑点：CustomSavable 实现不完整**

**现象**：
- 存档中保存的数据无法正确恢复
- 读取存档后游戏行为异常

**根因**：`CustomSavable<T>` 接口需要正确实现 `onSave()` 和 `onLoad(T value)`。

**证据指针**：
- ✅ **GeniusSocietysDangerousGossip**（hsr-mod）：实现 `CustomSavable<Integer>`，保存 `goldGained`（行 56-64）
- ✅ **Inner**（AnonMod）：实现 `CustomSavable<SoulHeartSave>`，但目前返回 `null`（行 34-39）
- ⚠️ **常见错误**：`onSave()` 返回 `null`，导致无法保存数据

**推荐做法**：
```java
public class MyRelic extends CustomRelic implements CustomSavable<MySaveData> {
    private int myCounter = 0;

    @Override
    public MySaveData onSave() {
        MySaveData data = new MySaveData();
        data.counter = this.myCounter;
        return data;  // ✓ 返回实际数据
    }

    @Override
    public void onLoad(MySaveData data) {
        if (data != null) {
            this.myCounter = data.counter;  // ✓ 恢复数据
        }
    }
}
```

**自检清单**：
- [ ] `onSave()` 返回非 `null` 的有效数据
- [ ] `onLoad()` 处理 `null` 输入的情况
- [ ] 保存的数据类型是可序列化的（基本类型、POJO）

---

### 3.4 版本升级迁移

#### 🟡 **坑点：配置结构变更导致旧存档失效**

**现象**：mod 更新后，玩家存档无法加载，或配置项丢失。

**根因**：`SpireConfig` 不会自动迁移旧配置到新格式。

**推荐做法**：
```java
static {
    try {
        Properties defaults = new Properties();
        defaults.setProperty("version", "2.0");
        defaults.setProperty("newFeature", "false");
        config = new SpireConfig("MyMod", "MyModConfig", defaults);
        config.load();

        // ✓ 版本检查与迁移
        String version = config.getString("version");
        if ("1.0".equals(version)) {
            migrateFromV1ToV2();
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private static void migrateFromV1ToV2() {
    // 迁移旧配置
    boolean oldSetting = config.getBool("oldSettingName");
    config.setBool("newSettingName", oldSetting);
    config.setString("version", "2.0");
    config.save();
}
```

**自检清单**：
- [ ] 配置文件中包含版本号字段
- [ ] 在加载后检查版本号并执行迁移
- [ ] 迁移后调用 `config.save()` 保存

---

### 3.5 存档膨胀问题

#### 🟡 **坑点：过度保存导致存档文件过大**

**现象**：存档文件异常庞大，加载/保存速度变慢。

**根因**：每次保存都写入大量数据，或保存了冗余信息。

**证据指针**：
- ✅ **AnonMod**（SavemetricData.java）：使用独立的 SpireConfig (`sp-racing`, `saves`) 存储统计数据
- ⚠️ **常见错误**：在 `onSave()` 中保存整个对象图

**推荐做法**：
```java
// ✓ 只保存必要数据
@Override
public Integer onSave() {
    return this.myCounter;  // 仅保存一个 int
}

// ✗ 避免：保存大量数据
@Override
public Map<String, Object> onSave() {
    Map<String, Object> data = new HashMap<>();
    data.put("entireHistory", this.history);  // 可能有数千条记录
    return data;
}
```

**自检清单**：
- [ ] 保存的数据尽可能精简（基本类型优先）
- [ ] 避免保存大型集合或对象图
- [ ] 考虑使用独立文件存储统计数据

---

### 3.6 abstractSaveString/abstractSaveSpecial 使用场景

#### 🟢 **何时使用**：

**`abstractSaveString()`**：
- 需要自定义序列化逻辑
- 数据格式复杂（嵌套对象）
- 需要与其他 mod 交互

**`abstractSaveSpecial()`**：
- 需要保存到特殊位置（非玩家存档）
- 跨 run 持久化数据

**推荐做法**：
```java
// ✓ 使用自定义序列化
@Override
public String onSave() {
    return myCounter + ":" + myFlag;  // 自定义格式
}

@Override
public void onLoad(String data) {
    if (data != null && data.contains(":")) {
        String[] parts = data.split(":");
        this.myCounter = Integer.parseInt(parts[0]);
        this.myFlag = Boolean.parseBoolean(parts[1]);
    }
}
```

**自检清单**：
- [ ] 仅在默认序列化不满足需求时使用自定义方法
- [ ] 自定义格式包含版本标识（便于迁移）

---

## 4. 注册时机速查表

| 操作 | 推荐时机 (Mod lifecycle) | 注意事项 | 证据来源 |
|------|-------------------------|---------|---------|
| **BaseMod.addColor** | 主类构造函数 | 必须在 `receiveEditCharacters()` 之前 | WljMod (行 94), AyaMod (行 105) |
| **BaseMod.addCharacter** | `receiveEditCharacters()` 回调 | 需确保 color 已注册 | WljMod (行 142), AyaMod (行 213) |
| **BaseMod.addLocalization / loadCustomStringsFile** | `receiveEditStrings()` 回调 | ❌ 不在构造函数中调用 | WljMod (行 173), AyaMod (行 274) |
| **BaseMod.addKeyword** | `receiveEditKeywords()` 回调 | ❌ 不在 `receiveEditStrings()` 中调用 | WljMod (行 149), AyaMod (行 287) |
| **BaseMod.addCard / AutoAdd** | `receiveEditCards()` 回调 | 确保 strings 已加载 | WljMod (行 135), AyaMod (行 262) |
| **BaseMod.addRelic** | `receiveEditRelics()` 回调 | 确保 color 已注册 | WljMod (行 158), AyaMod (行 251) |
| **new SpireConfig** | 静态初始化块或构造函数 | 立即调用 `load()` | AnonMod (行 1042), AyaMod (行 110) |
| **config.save()** | 配置变更后立即调用 | 避免丢失设置 | WljMod (行 122), AyaMod (行 229) |
| **CustomSavable.onSave** | 游戏自动调用（存档时） | 返回非 null 数据 | HSRMod GeniusSocietysDangerousGossip (行 56) |
| **CustomSavable.onLoad** | 游戏自动调用（读档时） | 处理 null 输入 | HSRMod GeniusSocietysDangerousGossip (行 61) |
| **BaseMod.registerModBadge** | `receivePostInitialize()` 回调 | 创建设置面板 | WljMod (行 129), AyaMod (行 236) |
| **BaseMod.addEvent** | `receivePostInitialize()` 回调 | 确保 strings 已加载 | WljMod (行 130-131) |
| **BaseMod.addMonster / addBoss** | `receivePostInitialize()` 回调 | 确保 monster strings 已加载 | AnonMod (行 740-813) |

### 关键原则

1. **顺序依赖**：
   - Color → Character/Relic
   - Strings → Cards/Relics/Powers
   - Config → Settings Panel

2. **时机规则**：
   - 资源注册（Color/Character）：构造函数
   - 内容编辑（Cards/Relics/Strings）：对应回调
   - UI 元素（Badge/Panel）：PostInitialize

3. **避免的时序错误**：
   - ❌ 在构造函数中调用 `loadCustomStringsFile()`
   - ❌ 在 `receiveEditStrings()` 中调用 `addKeyword()`
   - ❌ 在 `receivePostInitialize()` 中创建 `SpireConfig`

---

## 5. 推荐目录结构

### 5.1 社区主流结构

基于 **Downfall**、**WljMod**、**AyaMod**、**HSRMod** 的观察：

```
MyMod/
├── ModTheSpire.json              # Mod 元数据
├── src/
│   └── com/
│       └── mymod/
│           ├── MyMod.java        # 主类 (@SpireInitializer)
│           ├── cards/
│           ├── relics/
│           ├── powers/
│           ├── characters/
│           └── util/
├── resources/                     # 或直接放在 mod 根目录
│   ├── localization/             # ✓ 多语言目录
│   │   ├── eng/
│   │   │   ├── cards.json
│   │   │   ├── relics.json
│   │   │   ├── powers.json
│   │   │   ├── keywords.json
│   │   │   └── ...
│   │   ├── zhs/
│   │   │   └── ... (同 eng 结构)
│   │   ├── zht/
│   │   ├── jpn/
│   │   └── kor/
│   ├── images/
│   │   ├── cards/
│   │   ├── relics/
│   │   ├── powers/
│   │   ├── characters/
│   │   └── ui/
│   └── audio/
└── README.md
```

### 5.2 结构规范

#### Localization 文件命名

**推荐命名**（参考 Downfall）：
- `cards.json`（或 `CardStrings.json`）
- `relics.json`（或 `RelicStrings.json`）
- `powers.json`（或 `PowerStrings.json`）
- `keywords.json`（或 `KeywordStrings.json`）
- `events.json`
- `potions.json`
- `monsters.json`
- `characters.json`
- `ui.json`

**证据指针**：
- ✅ **Downfall**：使用标准类型名（`CardStrings.json`）
- ✅ **WljMod**：使用简短名称（`wlj_cards.json`）
- ✅ **AnonMod**：使用带语言后缀的名称（`Anon_cards-zh.json`）

#### Resources 目录布局

**选项 A**：ModID + Resources（推荐）
```
MyModResources/
├── localization/
├── images/
└── audio/
```

**选项 B**：直接使用 modID
```
mymod/
├── localization/
├── images/
└── audio/
```

**选项 C**：平铺结构（AyaMod 风格）
```
theAyaResources/
└── localization/
```

### 5.3 文件路径引用

**推荐做法**：
```java
// ✓ 使用常量避免硬编码
public class MyMod {
    public static final String MOD_ID = "MyMod";
    public static final String RESOURCES_FOLDER = MOD_ID + "Resources";

    public static String makeCardPath(String resourcePath) {
        return RESOURCES_FOLDER + "/images/cards/" + resourcePath;
    }

    public static String makeRelicPath(String resourcePath) {
        return RESOURCES_FOLDER + "/images/relics/" + resourcePath;
    }
}

// 使用
Texture texture = ImageMaster.loadImage(MyMod.makeCardPath("my_card.png"));
```

**证据指针**：
- ✅ **AyaMod**（行 121-139）：提供 `makeCardPath()`、`makeRelicPath()` 等工具方法
- ✅ **WljMod**：直接使用相对路径（如 `"image/512/bg_attack.png"`）

---

## 附录：证据来源汇总

### Localization 相关

| Mod | 证据 | 位置 |
|-----|------|------|
| **WljMod** | `receiveEditStrings()` 回调加载 | WljMod.java:173-206 |
| **AyaMod** | `receiveEditStrings()` 回调加载 | AyaMod.java:274-285 |
| **AnonMod** | UTF-8 显式指定 | AnonMod.java:446 |
| **Downfall** | `awakened:` 前缀 | CardStrings.json |
| **HSRMod** | `HSRMod:` 前缀 | cards.json |

### SpireConfig 相关

| Mod | 证据 | 位置 |
|-----|------|------|
| **AnonMod** | 静态初始化块创建 | AnonMod.java:1042-1045 |
| **AyaMod** | 构造函数创建并 load | AyaMod.java:110-116 |
| **WljMod** | null 检查 + getter | WljMod.java:83-89 |

### onSave/onLoad 相关

| Mod | 证据 | 位置 |
|-----|------|------|
| **HSRMod** | GeniusSocietysDangerousGossip | GeniusSocietysDangerousGossip.java:56-64 |
| **AnonMod** | Inner relic | Inner.java:34-39 |

### 目录结构相关

| Mod | 结构 | 路径 |
|-----|------|------|
| **Downfall** | `awakenedResources/localization/eng/` | 多语言分目录 |
| **AnonMod** | `localization/` 平铺 | 所有语言文件在同一目录 |
| **HSRMod** | `HSRModResources/localization/ENG/` | 大写语言代码 |

---

## 总结

### 高危坑点（必须避免）

1. ❌ 在构造函数中调用 `loadCustomStringsFile()`
2. ❌ JSON 文件读取未指定 UTF-8 编码
3. ❌ ID 未使用 `modID:` 前缀
4. ❌ 在 `receiveEditStrings()` 中调用 `addKeyword()`
5. ❌ `CustomSavable.onSave()` 返回 `null`

### 推荐实践

1. ✅ 严格遵守回调时序（参考速查表）
2. ✅ 所有 ID 使用 `<modID>:<EntityName>` 格式
3. ✅ 显式指定 UTF-8 编码读取 JSON
4. ✅ 提供语言回退机制（默认 `eng`）
5. ✅ SpireConfig 在静态初始化中创建并 `load()`

---

*文档版本：2026-01-13*
*证据来源：../resources/mods 中的 30+ 社区 mod*
