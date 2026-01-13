# Localization、IDs、Prefs/Save 相关坑点参考

聚焦多语言、ID 命名、配置持久化三大主题的常见陷阱。

---

## 1. 加载时机错误

**现象**：不会报错，但多语言文本无法加载，游戏内显示 `MISSING:` 或 ID。

**根因**：`receiveEditStrings()` 回调的调用时机早于构造函数执行。在构造函数中加载的字符串会被后续的回调覆盖。

**推荐做法**：
```java
public class MyMod implements EditStringsSubscriber {
    public void receiveEditStrings() {
        // 正确：在回调中加载
        String lang = getLocalizationLanguage();
        BaseMod.loadCustomStringsFile(CardStrings.class,
            "localization/" + lang + "/my_cards.json");
    }
}
```

**证据指针**：
- `WljMod/wlj-mod-1.3.4`：在 `receiveEditStrings()` 回调中加载（行 173-206）
- `AyaMod`：在 `receiveEditStrings()` 回调中加载（行 274-285）

**自检命令**：
```bash
# 检查是否有在构造函数中调用 loadCustomStringsFile
grep -rn "loadCustomStringsFile" ../resources/mods --include="*.java" -B 10 | grep -A 10 "public.*Mod.*("
```

---

## 2. UTF-8 编码问题

**现象**：中文、日文等多语言文本显示为乱码或方框。

**根因**：`Gdx.files.internal().readString()` 默认使用系统编码，而非 UTF-8。

**推荐做法**：
```java
// 正确：显式指定 UTF-8
String json = Gdx.files.internal("localization/eng/my_keywords.json")
    .readString(String.valueOf(StandardCharsets.UTF_8));
```

**证据指针**：
- `WljMod`（行 192）：`readString(String.valueOf(StandardCharsets.UTF_8))`
- `AyaMod`（行 290）：`readString(String.valueOf(StandardCharsets.UTF_8))`
- `AnonMod`（行 446）：`readString(String.valueOf(StandardCharsets.UTF_8))`

**自检命令**：
```bash
grep -rn 'readString\(' ../resources/mods --include="*.java" | grep -v 'UTF_8'
```

---

## 3. 语言回退机制缺失

**现象**：游戏语言设置为 `POR`（葡萄牙语）时，找不到对应本地化文件，崩溃或显示 `MISSING:`。

**根因**：仅提供了部分语言（如 `eng`、`zhs`、`zht`），未设置默认回退到 `eng`。

**推荐做法**：
```java
private String getLocalizationLanguage() {
    switch (Settings.language) {
        case ZHS: return "zhs";
        case ZHT: return "zht";
        case JPN: return "jpn";
        // ... 其他支持的语言
        default: return "eng"; // 默认回退到英文
    }
}
```

**证据指针**：
- `WljMod`（行 175-182）：使用 `switch(Settings.language)` 并 `default` 到 `zhs`
- `AyaMod`（行 197-206）：使用 `switch` 并 `default` 到 `eng`

**自检命令**：
```bash
grep -A 10 "getLocalizationLanguage\|switch.*Settings.language" ../resources/mods --include="*.java" | grep -c "default"
```

---

## 4. Keywords 注册时机错误

**现象**：关键词提示文本显示为 ID 或 `MISSING:`。

**根因**：`BaseMod.addKeyword()` 必须在 `receiveEditKeywords()` 回调中调用，而非 `receiveEditStrings()`。

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
            modID.toLowerCase(),  // 前缀
            keyword.PROPER_NAME,
            keyword.NAMES,
            keyword.DESCRIPTION
        );
    }
}
```

**证据指针**：
- `WljMod`（行 149-156）：在 `receiveEditKeywords()` 中调用 `BaseMod.addKeyword()`
- `AyaMod`（行 287-298）：在 `receiveEditKeywords()` 中调用 `BaseMod.addKeyword()`

**自检命令**：
```bash
grep -B 5 -A 5 "addKeyword" ../resources/mods --include="*.java" | grep -B 10 "receiveEditKeywords"
```

---

## 5. ID 前缀缺失

**现象**：与其他 mod 的 ID 冲突，导致卡牌/遗物描述错乱。

**根因**：ID 必须全局唯一，使用 `modID:` 前缀是社区约定。

**推荐做法**：
```java
// 在 JSON 中
{
  "MyMod:Strike": {
    "NAME": "Strike",
    "DESCRIPTION": "Deal !D! damage."
  }
}

// 在代码中
public static final String ID = "MyMod:Strike";
```

**证据指针**：
- `Downfall`：所有 ID 使用 `awakened:` 前缀（如 `awakened:Thunderbolt`）
- `WljMod`：使用 `Wlj:` 前缀（如 `Wlj:Cup`）
- `AyaMod`：使用 `theAya:` 前缀（如 `theAya:FlyingPotion`）

**自检命令**：
```bash
# 检查 JSON 中是否有未使用 modID: 前缀的 ID
grep -rh '"[A-Z][a-zA-Z]*:' ../resources/mods --include="*.json" | grep -v 'ModTheSpire\|base'
```

---

## 6. NL 换行符使用错误

**现象**：卡牌描述中的换行符不生效，显示为字面量 `NL` 或 `\n`。

**根因**：`NL` 是 STS 的特殊标记，会在运行时展开为 `\n`。直接使用 `\n` 不会在描述中正确换行。

**推荐做法**：
```json
// 正确：使用 NL 进行换行
{
  "MyMod:MyCard": {
    "DESCRIPTION": "Gain !B! Block. NL Deal !D! damage. NL Exhaust."
  }
}

// 错误：直接使用 \n
{
  "MyMod:MyCard": {
    "DESCRIPTION": "Gain !B! Block.\nDeal !D! damage.\nExhaust."
  }
}
```

**证据指针**：
- `Downfall`（CardStrings.json）：大量使用 `NL`
- `HSRMod`（ui.json）：混合使用 `NL` 和普通文本

**自检命令**：
```bash
# 检查 JSON 中是否使用了 \n 而非 NL
grep -rh '\\\\n' ../resources/mods --include="*.json" | grep -v ' NL '
```

---

## 7. SpireConfig 创建时机不当

**现象**：
- 在 `initialize()` 静态方法中创建，但未正确加载配置
- 在 `receivePostInitialize()` 中创建，导致早期代码无法访问

**根因**：`SpireConfig` 需要在 `initialize()` 或静态初始化块中创建并 `load()`。

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
            config.load();  // 立即加载
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void initialize() {
        new MyMod();
    }
}
```

**证据指针**：
- `WljMod`（行 96-100）：在构造函数中创建 `SpireConfig`
- `AnonMod`（行 1042-1045）：在静态初始化块中创建 `SpireConfig saves`
- `AyaMod`（行 110-116）：在构造函数中创建并 `load()` 配置

**自检命令**：
```bash
grep -B 5 -A 5 "new SpireConfig" ../resources/mods --include="*.java"
```

---

## 8. getPrefs 时机问题

**现象**：在 `receiveEditCards()` 等早期回调中访问 `config.getBool()`，返回值不正确或抛出异常。

**根因**：虽然 `SpireConfig` 已创建，但某些配置项可能需要延迟加载。

**推荐做法**：
```java
// 使用 getter 方法，提供默认值
public static boolean isMyFeatureEnabled() {
    if (config == null) {
        return false;  // 安全默认值
    }
    try {
        return config.getBool("myFeature");
    } catch (Exception e) {
        return false;
    }
}

// 在需要时才调用
public void receiveEditCards() {
    if (isMyFeatureEnabled()) {
        // 添加特定卡牌
    }
}
```

**证据指针**：
- `WljMod`（行 83-89）：使用 `getVoiceDisabled()` 方法，内部检查 `config == null`

**自检命令**：
```bash
grep -B 3 "config.getBool\|config.getString" ../resources/mods --include="*.java" | grep -c "config == null"
```

---

## 9. onSave/onLoad 实现不完整

**现象**：存档中保存的数据无法正确恢复，读取存档后游戏行为异常。

**根因**：`CustomSavable<T>` 接口需要正确实现 `onSave()` 和 `onLoad(T value)`。

**推荐做法**：
```java
public class MyRelic extends CustomRelic implements CustomSavable<MySaveData> {
    private int myCounter = 0;

    @Override
    public MySaveData onSave() {
        MySaveData data = new MySaveData();
        data.counter = this.myCounter;
        return data;  // 返回实际数据
    }

    @Override
    public void onLoad(MySaveData data) {
        if (data != null) {
            this.myCounter = data.counter;  // 恢复数据
        }
    }
}
```

**证据指针**：
- `GeniusSocietysDangerousGossip`（hsr-mod）：实现 `CustomSavable<Integer>`（行 56-64）
- `Inner`（AnonMod）：实现 `CustomSavable<SoulHeartSave>`（行 34-39）

**自检命令**：
```bash
grep -A 5 "public.*onSave()" ../resources/mods --include="*.java" | grep -c "return null"
```

---

## 10. 版本升级迁移缺失

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

        // 版本检查与迁移
        String version = config.getString("version");
        if ("1.0".equals(version)) {
            migrateFromV1ToV2();
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}

private static void migrateFromV1ToV2() {
    boolean oldSetting = config.getBool("oldSettingName");
    config.setBool("newSettingName", oldSetting);
    config.setString("version", "2.0");
    config.save();
}
```

**自检命令**：
```bash
grep -rn "version.*config\|migrate.*V" ../resources/mods --include="*.java"
```

---

## 11. 存档膨胀问题

**现象**：存档文件异常庞大，加载/保存速度变慢。

**根因**：每次保存都写入大量数据，或保存了冗余信息。

**推荐做法**：
```java
// 只保存必要数据
@Override
public Integer onSave() {
    return this.myCounter;  // 仅保存一个 int
}

// 避免：保存大量数据
// @Override
// public Map<String, Object> onSave() {
//     Map<String, Object> data = new HashMap<>();
//     data.put("entireHistory", this.history);  // 可能有数千条记录
//     return data;
// }
```

**证据指针**：
- `AnonMod`（SavemetricData.java）：使用独立的 SpireConfig (`sp-racing`, `saves`) 存储统计数据

**自检命令**：
```bash
grep -A 10 "public.*onSave()" ../resources/mods --include="*.java" | grep -E "HashMap|ArrayList|LinkedList"
```

---

## 12. abstractSaveString/abstractSaveSpecial 使用场景

**何时使用**：

**`abstractSaveString()`**：
- 需要自定义序列化逻辑
- 数据格式复杂（嵌套对象）
- 需要与其他 mod 交互

**`abstractSaveSpecial()`**：
- 需要保存到特殊位置（非玩家存档）
- 跨 run 持久化数据

**推荐做法**：
```java
// 使用自定义序列化
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

**自检命令**：
```bash
grep -rn "abstractSaveString\|abstractSaveSpecial" ../resources/mods --include="*.java"
```

---

## 回调时序速查

| 操作 | 推荐时机 | 注意事项 |
|------|---------|---------|
| BaseMod.addColor | 主类构造函数 | 必须在 `receiveEditCharacters()` 之前 |
| BaseMod.addCharacter | `receiveEditCharacters()` | 需确保 color 已注册 |
| loadCustomStringsFile | `receiveEditStrings()` | 不在构造函数中调用 |
| BaseMod.addKeyword | `receiveEditKeywords()` | 不在 `receiveEditStrings()` 中调用 |
| BaseMod.addCard | `receiveEditCards()` | 确保 strings 已加载 |
| BaseMod.addRelic | `receiveEditRelics()` | 确保 color 已注册 |
| new SpireConfig | 静态初始化块或构造函数 | 立即调用 `load()` |
| BaseMod.registerModBadge | `receivePostInitialize()` | 创建设置面板 |
