# SpirePatch 坑点与最佳实践

本文档系统梳理了 Slay the Spire Mod 开发中使用 `@SpirePatch` 的常见坑点，基于对 100+ 个开源 mod 的代码扫描总结而成。

---

## 1. 构造函数 Patch 的方法名陷阱

### 现象
构造函数 patch 失效，编译时无错误但运行时不生效。

### 根因
构造函数在字节码中名为 `<init>`，但 ModTheSpire 的 SpirePatch 框架约定使用 `<ctor>` 作为 method 参数值。使用 `<init>` 会导致 patch 无法匹配到目标。

### 推荐做法
```java
// 正确写法
@SpirePatch(
    clz = StrikeEffect.class,
    method = "<ctor>",  // 使用 <ctor>
    paramtypez = {AbstractCreature.class, float.class, float.class, int.class}
)
public static class ConstructorPatch {
    // ...
}
```

### 证据指针
- **Downfall**: `slimebound/patches/StrikeEffectPatch.java` + `method = "<ctor>"`
- **BaseMod**: `basemod/patches/com/megacrit/cardcrawl/cards/AbstractCard/CardModifierPatches.java` + `method = "<ctor>"`

### 自检清单
```bash
# 检查是否有错误使用 <init> 的构造函数 patch
cd ../resources/mods
rg 'method\s*=\s*"<init>"' --type java

# 应该看到的都是 <ctor>
rg 'method\s*=\s*"<ctor>"' --type java | head -20
```

---

## 2. 反射私有字段的脆弱性

### 现象
游戏更新后 mod 突然崩溃，报 `NoSuchFieldException` 或字段获取返回 null。

### 根因
使用 `ReflectionHacks.getPrivate()` 时硬编码字段名字符串：
1. 字段名拼写错误在编译期无法发现
2. 游戏更新时原代码字段名可能改变
3. 重构时 IDE 不会自动更新字符串

### 推荐做法
```java
// 脆弱写法（不推荐）
Integer gold = (Integer) ReflectionHacks.getPrivate(
    __instance, "gold", Integer.class  // 字段名硬编码
);

// 优先使用 SpireField（如果只是添加新字段）
public static SpireField<Integer> gold = new SpireField(() -> 0);

// 如果必须反射，添加封装和常量
private static final String GOLD_FIELD = "gold";
Integer gold = Reflect.getPrivate(cls, instance, GOLD_FIELD, Integer.class);
```

### 证据指针
- **wlj-mod**: `wlj-mod-1.3.4/com/github/paopaoyue/wljmod/patch/gold/GoldTextEffectPatch.java` + `Reflect.getPrivate`
- **Downfall**: `downfall/patches/ui/topPanel/GoldToSoulPatches.java` + `ReflectionHacks.getPrivateStatic`
- **ArknightsTheSpire**: `patches/EndTurnButtonPatcher.java` + `ReflectionHacks.getPrivate`

### 自检清单
```bash
# 检查直接反射私有字段的代码
rg 'ReflectionHacks\.getPrivate[^;]+\.[a-zA-Z]+\s*\)' --type java -C 2

# 运行时观察日志
# 启动游戏时加上 -Dspire.logReflection=true 查看反射调用
```

---

## 3. SpireInsert 与 SpireInstrument 的选择混乱

### 现象
同样的功能需求，有的地方用 `@SpireInsertPatch` + Locator，有的用 `@SpireInstrumentPatch` + ExprEditor，导致代码风格不统一。

### 根因
两种方式都能实现代码插入，但各有适用场景：
- `SpireInsertPatch` + Locator：更清晰，适合在已知位置插入
- `SpireInstrumentPatch` + ExprEditor：更灵活，适合复杂的条件插入

### 推荐做法
```java
// 场景1：在方法调用前插入（推荐 SpireInsertPatch）
@SpireInsertPatch(
    locator = Locator.class,
    localvars = {"sb"}
)
public static void Insert(SpriteBatch sb) {
    // 插入的代码
}

private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        Matcher finalMatcher = new Matcher.MethodCallMatcher(
            ExhaustPanel.class, "render"
        );
        return LineFinder.findInOrder(ctBehavior, finalMatcher);
    }
}

// 场景2：条件性替换方法调用（推荐 SpireInstrumentPatch）
@SpireInstrumentPatch
public static ExprEditor Instrument() {
    return new ExprEditor() {
        public void edit(MethodCall m) throws CannotCompileException {
            if (m.getClassName().equals(SpriteBatch.class.getName())
                && m.getMethodName().equals("draw")) {
                m.replace("{ if(condition) { $proceed($$); } }");
            }
        }
    };
}
```

### 证据指针
- **mintySpire**: `mintySpire/patches/BetterAscensionSelectorPatches.java:72` + `@SpireInstrumentPatch`
- **Gensokyo**: `Gensokyo/patches/ProceedButtonPatch.java` + `@SpireInstrumentPatch`
- **ArknightsTheSpire**: `patches/PreHealPatcher.java` + `@SpireInsertPatch`

### 自检清单
```bash
# 检查两种 patch 的使用分布
rg '@SpireInsertPatch' --type java | wc -l
rg '@SpireInstrumentPatch' --type java | wc -l
```

---

## 4. Javassist 占位符使用错误

### 现象
patch 运行时崩溃，报 `NotFoundException` 或参数不匹配错误；或方法调用参数错乱。

### 根因
Javassist 占位符有特定含义，混用会导致运行时错误：
- `$0` = this（实例方法）或第一个参数（静态方法）
- `$1`, `$2`, ... = 方法参数
- `$$` = 所有参数
- `$proceed(...)` = 调用原始方法
- `$_` = 返回值（用于 replace）

### 推荐做法
```java
// 正确用法：传递所有参数
m.replace("{ if(!" + MyPatch.class.getName() + ".condition(this)) { $proceed($$); } }");

// 正确用法：修改返回值
f.replace("$_ = $proceed($$) || " + MyPatch.class.getName() + ".alternative();");

// 错误用法：无参方法用 $$
// m.replace("{ $proceed($$); }")  // 错误！原始方法无参数

// 正确用法：无参方法
m.replace("{ $proceed(); }");
```

### 证据指针
- **mintySpire**: `mintySpire/patches/BetterAscensionSelectorPatches.java:78` + `$proceed($$)`
- **ThePackmaster**: `thePackmaster/patches/needlework/FineTuneLineWidthPatch.java:57` + `$_ = $proceed($$)`
- **loadout**: `patches/MapRoomNodePatch.java` + `$proceed()` vs `$proceed($$)` 混用

### 自检清单
```bash
# 检查 $proceed 使用
rg '\$proceed' --type java -B 2 -A 2 | head -60

# 检查是否有 $0 在静态上下文使用
rg '\$0' --type java -B 5
```

---

## 5. SpireField 初始化的 null.INSTANCE 陷阱

### 现象
使用 SpireField 添加字段时，访问字段抛出 `NullPointerException`。

### 根因
SpireField 的构造函数需要一个 `Supplier<T>` 作为默认值工厂。使用 `null.INSTANCE` 会导致 Supplier 为 null，在首次访问时崩溃。

### 推荐做法
```java
// 错误写法
public static SpireField<String> name = new SpireField(null.INSTANCE);

// 正确写法：提供默认值
public static SpireField<String> name = new SpireField(() -> "default");
public static SpireField<Integer> count = new SpireField(() -> 0);
public static SpireField<CustomData> data = new SpireField(() -> null);

// 或使用构造方法引用
public static SpireField<UIPanel> panel = new SpireField(UIPanel::new);
```

### 证据指针
- **rare-cards-sparkle**: `RareCardsSparkleFields.java:41-43` + `new SpireField(null.INSTANCE)`
- **ThePackmaster**: `thePackmaster/patches/needlework/StitchPatches.java:36` + `new SpireField(() -> null)`
- **ArknightsTheSpire**: `ui/SpUtil.java:84` + `new SpireField(SpUI::new)`

### 自检清单
```bash
# 检查 SpireField 初始化
rg 'new SpireField' --type java -A 1 | grep -E '(null\.INSTANCE|::|\(\) ->)'
```

---

## 6. SpireReturn 泛型与返回值不匹配

### 现象
使用 `@SpirePrefixPatch` 或 `@SpirePostfixPatch` 提前返回时，编译错误或运行时类型转换异常。

### 根因
`SpireReturn.Return()` 用于 void 方法，`SpireReturn.Return(value)` 用于非 void 方法。泛型参数 `<Void>` 与具体类型必须匹配。

### 推荐做法
```java
// void 方法
@SpirePrefixPatch
public static SpireReturn<Void> Prefix(SomeClass __instance) {
    if (shouldSkip) {
        return SpireReturn.Return(null);  // void 方法返回 null
    }
    return SpireReturn.Continue();
}

// 非void 方法
@SpirePrefixPatch
public static SpireReturn<AbstractStance> Prefix(String name) {
    if (name.equals("Custom")) {
        return SpireReturn.Return(new CustomStance());  // 返回实际对象
    }
    return SpireReturn.Continue();
}
```

### 证据指针
- **wlj-mod**: `wlj-mod-1.3.4/com/github/paopaoyue/wljmod/patch/gold/GoldTextEffectPatch.java` + `SpireReturn<Void>` + `SpireReturn.Return()`
- **Mizuki**: `patches/StancePatch.java` + `SpireReturn<AbstractStance>` + `SpireReturn.Return(new ...)`

### 自检清单
```bash
# 检查 SpireReturn 使用
rg 'SpireReturn\.Return' --type java -B 3
```

---

## 7. Matcher 多匹配导致 patch 位置错误

### 现象
patch 应该影响方法中的第 N 次调用，但实际影响的是第 1 次；或 patch 没有生效。

### 根因
`Matcher.MethodCallMatcher` 默认只匹配第一个符合条件的调用。如果方法中有多次调用同一方法，需要使用 `findAllInOrder` 或自定义 Locator。

### 推荐做法
```java
// 场景：只匹配第一次（默认行为）
Matcher finalMatcher = new Matcher.MethodCallMatcher(
    ExhaustPanel.class, "render"
);
return LineFinder.findInOrder(ctBehavior, finalMatcher);

// 场景：匹配所有位置
return LineFinder.findAllInOrder(ctBehavior, finalMatcher);

// 场景：匹配第 N 次（需要自定义）
private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        Matcher matcher = new Matcher.MethodCallMatcher(SpriteBatch.class, "draw");
        int[] allMatches = LineFinder.findAllInOrder(ctBehavior, matcher);
        return new int[]{ allMatches[2] };  // 只取第 3 次
    }
}
```

### 证据指针
- **ArknightsTheSpire**: `ui/SpUtil.java:53` + `MethodCallMatcher`
- **LingMod**: `lingmod/powers/NI3Power.java:65` + `MethodCallMatcher`
- **Palmod**: `BlightOnEnterRoom.java:60` + `FieldAccessMatcher`

### 自检清单
```bash
# 检查 Matcher 使用
rg 'Matcher\.(MethodCall|FieldAccess|NewExpr)' --type java -A 3

# 检查是否有多匹配风险
# 1. 找到使用 Matcher 的 patch
# 2. 打开目标方法源码
# 3. 确认被匹配的方法/字段是否只出现一次
```

---

## 8. paramtypez 参数类型错误

### 现象
patch 不生效，无错误提示；或 patch 应用到了错误的重载方法上。

### 根因
`paramtypez` 用于区分重载方法，常见错误：
1. 类型不匹配（如用 `Integer.class` 而非 `int.class`）
2. 参数顺序错误
3. 缺少 `paramtypez` 导致匹配到错误的重载

### 推荐做法
```java
// 正确：使用基本类型
@SpirePatch(
    clz = AbstractPlayer.class,
    method = "damage",
    paramtypez = {DamageInfo.class}  // 单参数重载
)

// 正确：多参数
@SpirePatch(
    clz = SomeClass.class,
    method = "someMethod",
    paramtypez = {int.class, boolean.class, String.class}  // 顺序和类型都要精确
)

// 错误：使用包装类型
// paramtypez = {Integer.class}  // 错误！应该是 int.class
```

### 证据指针
- **ArknightsTheSpire**: `patches/PreHealPatcher.java:13` + `paramtypez = {int.class, boolean.class}`
- **KeyCuts**: `GridCardSelectScreenPatches.java:185` + `paramtypez = {CardGroup.class, ...}`

### 自检清单
```bash
# 检查 paramtypez 使用
rg 'paramtypez\s*=' --type java -A 1

# 验证类型是否正确
# 1. 复制 paramtypez 中的类型
# 2. 在原游戏中搜索对应方法
# 3. 确认参数类型完全匹配
```

---

## 9. Patch 类命名和组织混乱

### 现象
难以找到特定功能的 patch，同名 patch 冲突，维护困难。

### 根因
不同 mod 使用不同的组织方式：
- 有的全部放在 `patches/` 根目录
- 有的按功能分包
- 命名不统一：`XxxPatch` vs `XxxPatches`

### 推荐做法
```
推荐结构：
yourmod/
└── patches/
    ├── com/
    │   └── megacrit/
    │       └── cardcrawl/
    │           ├── cards/
    │           │   └── AbstractCardPatches.java
    │           ├── characters/
    │           │   └── AbstractPlayerPatches.java
    │           └── ui/
    │               └── TopPanelPatches.java
    └── locators/
        └── CustomLocator.java
```

### 证据指针
- **Downfall**: `slimebound/patches/` + 结构清晰
- **ThePackmaster**: `thePackmaster/patches/turmoilpack/` + 按包组织
- **Mizuki**: `patches/` + 扁平结构

### 自检清单
```bash
# 检查 patches 目录结构
find ../resources/mods -type d -name "patches" -exec sh -c 'echo "=== {} ===" && ls {} | head -10' \;

# 检查命名模式
find ../resources/mods -name "*Patch.java" -o -name "*Patches.java" | head -50
```

---

## 10. Locator 实现模式不一致

### 现象
Locator 代码重复，不清楚何时用 `findInOrder` vs `findAllInOrder`，不清楚返回 `int[]` 的含义。

### 根因
- 内部类 vs 独立类选择随意
- `findInOrder` 只返回第一个匹配位置
- `int[]` 返回值的含义（单个位置 vs 多个位置）

### 推荐做法
```java
// 模式1：简单的单点插入（内部类）
private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        Matcher finalMatcher = new Matcher.MethodCallMatcher(
            TargetClass.class, "targetMethod"
        );
        return LineFinder.findInOrder(ctBehavior, finalMatcher);
    }
}

// 模式2：多点插入（findAllInOrder）
private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        Matcher finalMatcher = new Matcher.MethodCallMatcher(
            TargetClass.class, "targetMethod"
        );
        return LineFinder.findAllInOrder(ctBehavior, finalMatcher);
    }
}

// 模式3：复杂条件（独立类，可复用）
public final class CustomLocator extends SpireInsertLocator {
    private final int occurrence;

    public CustomLocator(int occurrence) {
        this.occurrence = occurrence;
    }

    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        // 自定义逻辑
    }
}
```

### 证据指针
- **ArknightsTheSpire**: `ui/SpUtil.java:51` + 内部类 Locator
- **rare-cards-sparkle**: `locators/RenderTipLocator.java` + 独立类
- **KeyCuts**: `GridCardSelectScreenPatches.java:168` + 多个内部 Locator

### 自检清单
```bash
# 检查 Locator 实现
rg 'extends\s+SpireInsertLocator' --type java -A 10 | head -80

# 检查 findInOrder vs findAllInOrder
rg 'LineFinder\.(findInOrder|findAllInOrder)' --type java
```

---

## 附录：快速验证命令集

```bash
cd ../resources/mods

# 1. 检查构造函数 patch
rg 'method\s*=\s*"<(init|ctor)>"' --type java

# 2. 检查反射使用
rg 'ReflectionHacks\.getPrivate[^;]+\.[a-zA-Z]+\s*\)' --type java

# 3. 检查占位符使用
rg '\$proceed|\$\$|\$_' --type java -C 1

# 4. 检查 SpireField 初始化
rg 'new SpireField' --type java -A 1

# 5. 检查 SpireReturn
rg 'SpireReturn\.Return' --type java -B 2

# 6. 检查 paramtypez
rg 'paramtypez\s*=' --type java -A 1

# 7. 检查 Matcher
rg 'Matcher\.' --type java -A 2

# 8. 检查 Locator
rg 'extends\s+SpireInsertLocator' --type java -A 5
```

---

## 参考资源

- [ModTheSpire GitHub](https://github.com/kiooeht/ModTheSpire)
- [BaseMod Patches 示例](https://github.com/daviscook474/BaseMod)
- [SpirePatch 注解源码](https://github.com/kiooeht/ModTheSpire/blob/master/src/main/java/com/evacipated/cardcrawl/modthespire/SpirePatch.java)
