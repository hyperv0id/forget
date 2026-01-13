# Patching 坑点参考

基于 100+ 个开源 mod 的代码扫描总结，使用 `@SpirePatch` 的常见坑点。

---

## 1. 构造函数 Patch 的方法名陷阱

**现象**：构造函数 patch 失效，编译时无错误但运行时不生效。

**根因**：字节码中构造函数名为 `<init>`，但 SpirePatch 框架约定使用 `<ctor>`。

**推荐做法**：
```java
@SpirePatch(
    clz = StrikeEffect.class,
    method = "<ctor>",  // 使用 <ctor>
    paramtypez = {AbstractCreature.class, float.class, float.class, int.class}
)
```

**证据指针**：
- `Downfall/slimebound/patches/StrikeEffectPatch.java` + `method = "<ctor>"`
- `BaseMod/basemod/patches/com/megacrit/cardcrawl/cards/AbstractCard/CardModifierPatches.java` + `method = "<ctor>"`

**自检命令**：
```bash
rg 'method\s*=\s*"<init>"' --type java
```

---

## 2. 反射私有字段的脆弱性

**现象**：游戏更新后 mod 突然崩溃，报 `NoSuchFieldException`。

**根因**：硬编码字段名字符串，游戏更新时字段名可能改变。

**推荐做法**：
```java
// 脆弱写法（不推荐）
Integer gold = (Integer) ReflectionHacks.getPrivate(__instance, "gold", Integer.class);

// 优先使用 SpireField
public static SpireField<Integer> gold = new SpireField(() -> 0);
```

**证据指针**：
- `wlj-mod/com/github/paopaoyue/wljmod/patch/gold/GoldTextEffectPatch.java` + `Reflect.getPrivate`
- `Downfall/patches/ui/topPanel/GoldToSoulPatches.java` + `ReflectionHacks.getPrivateStatic`

**自检命令**：
```bash
rg 'ReflectionHacks\.getPrivate[^;]+\.[a-zA-Z]+\s*\)' --type java -C 2
```

---

## 3. SpireInsert 与 SpireInstrument 的选择混乱

**现象**：同样功能有的用 `@SpireInsertPatch`，有的用 `@SpireInstrumentPatch`，代码风格不统一。

**根因**：两种方式适用场景不同：
- `SpireInsertPatch` + Locator：更清晰，适合在已知位置插入
- `SpireInstrumentPatch` + ExprEditor：更灵活，适合复杂条件插入

**推荐做法**：
```java
// 场景1：在方法调用前插入（推荐 SpireInsertPatch）
@SpireInsertPatch(locator = Locator.class)
public static void Insert(SpriteBatch sb) { }

// 场景2：条件性替换方法调用（推荐 SpireInstrumentPatch）
@SpireInstrumentPatch
public static ExprEditor Instrument() {
    return new ExprEditor() {
        public void edit(MethodCall m) throws CannotCompileException {
            if (m.getClassName().equals(SpriteBatch.class.getName())) {
                m.replace("{ if(condition) { $proceed($$); } }");
            }
        }
    };
}
```

**证据指针**：
- `mintySpire/patches/BetterAscensionSelectorPatches.java:72` + `@SpireInstrumentPatch`
- `ArknightsTheSpire/patches/PreHealPatcher.java` + `@SpireInsertPatch`

**自检命令**：
```bash
rg '@SpireInsertPatch' --type java | wc -l
rg '@SpireInstrumentPatch' --type java | wc -l
```

---

## 4. Javassist 占位符使用错误

**现象**：patch 运行时崩溃，报 `NotFoundException` 或参数不匹配错误。

**根因**：Javassist 占位符有特定含义：
- `$0` = this（实例方法）或第一个参数（静态方法）
- `$$` = 所有参数
- `$proceed(...)` = 调用原始方法
- `$_` = 返回值

**推荐做法**：
```java
// 正确：传递所有参数
m.replace("{ if(!condition) { $proceed($$); } }");

// 正确：修改返回值
f.replace("$_ = $proceed($$) || alternative();");

// 错误：无参方法用 $$
// m.replace("{ $proceed($$); }")  // 错误！原始方法无参数
```

**证据指针**：
- `mintySpire/patches/BetterAscensionSelectorPatches.java:78` + `$proceed($$)`
- `ThePackmaster/patches/needlework/FineTuneLineWidthPatch.java:57` + `$_ = $proceed($$)`

**自检命令**：
```bash
rg '\$proceed' --type java -B 2 -A 2 | head -60
```

---

## 5. SpireField 初始化的 null.INSTANCE 陷阱

**现象**：使用 SpireField 添加字段时，访问字段抛出 `NullPointerException`。

**根因**：SpireField 构造函数需要 `Supplier<T>` 作为默认值工厂，`null.INSTANCE` 会导致 Supplier 为 null。

**推荐做法**：
```java
// 错误写法
public static SpireField<String> name = new SpireField(null.INSTANCE);

// 正确写法
public static SpireField<String> name = new SpireField(() -> "default");
public static SpireField<Integer> count = new SpireField(() -> 0);
```

**证据指针**：
- `rare-cards-sparkle/RareCardsSparkleFields.java:41-43` + `new SpireField(null.INSTANCE)`
- `ThePackmaster/patches/needlework/StitchPatches.java:36` + `new SpireField(() -> null)`

**自检命令**：
```bash
rg 'new SpireField' --type java -A 1 | grep -E '(null\.INSTANCE|::|\(\) ->)'
```

---

## 6. SpireReturn 泛型与返回值不匹配

**现象**：使用 `@SpirePrefixPatch` 提前返回时，编译错误或运行时类型转换异常。

**根因**：`SpireReturn.Return()` 用于 void 方法，`SpireReturn.Return(value)` 用于非 void 方法。

**推荐做法**：
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
        return SpireReturn.Return(new CustomStance());
    }
    return SpireReturn.Continue();
}
```

**证据指针**：
- `wlj-mod/com/github/paopaoyue/wljmod/patch/gold/GoldTextEffectPatch.java` + `SpireReturn<Void>`
- `Mizuki/patches/StancePatch.java` + `SpireReturn<AbstractStance>`

**自检命令**：
```bash
rg 'SpireReturn\.Return' --type java -B 3
```

---

## 7. Matcher 多匹配导致 patch 位置错误

**现象**：patch 应该影响第 N 次调用，但实际影响的是第 1 次。

**根因**：`Matcher.MethodCallMatcher` 默认只匹配第一个符合条件的调用。

**推荐做法**：
```java
// 场景：只匹配第一次（默认行为）
Matcher finalMatcher = new Matcher.MethodCallMatcher(ExhaustPanel.class, "render");
return LineFinder.findInOrder(ctBehavior, finalMatcher);

// 场景：匹配所有位置
return LineFinder.findAllInOrder(ctBehavior, finalMatcher);

// 场景：匹配第 N 次
int[] allMatches = LineFinder.findAllInOrder(ctBehavior, matcher);
return new int[]{ allMatches[2] };  // 只取第 3 次
```

**证据指针**：
- `ArknightsTheSpire/ui/SpUtil.java:53` + `MethodCallMatcher`
- `LingMod/lingmod/powers/NI3Power.java:65` + `MethodCallMatcher`

**自检命令**：
```bash
rg 'Matcher\.(MethodCall|FieldAccess|NewExpr)' --type java -A 3
```

---

## 8. paramtypez 参数类型错误

**现象**：patch 不生效，无错误提示；或 patch 应用到了错误的重载方法上。

**根因**：`paramtypez` 用于区分重载方法，常见错误：
1. 类型不匹配（如用 `Integer.class` 而非 `int.class`）
2. 参数顺序错误

**推荐做法**：
```java
// 正确：使用基本类型
@SpirePatch(
    clz = AbstractPlayer.class,
    method = "damage",
    paramtypez = {DamageInfo.class}  // 单参数重载
)

// 错误：使用包装类型
// paramtypez = {Integer.class}  // 错误！应该是 int.class
```

**证据指针**：
- `ArknightsTheSpire/patches/PreHealPatcher.java:13` + `paramtypez = {int.class, boolean.class}`

**自检命令**：
```bash
rg 'paramtypez\s*=' --type java -A 1
```

---

## 9. Patch 类命名和组织混乱

**现象**：难以找到特定功能的 patch，同名 patch 冲突。

**根因**：不同 mod 使用不同的组织方式。

**推荐做法**：
```
yourmod/
└── patches/
    ├── com/
    │   └── megacrit/
    │       └── cardcrawl/
    │           ├── cards/
    │           │   └── AbstractCardPatches.java
    │           └── ui/
    │               └── TopPanelPatches.java
    └── locators/
        └── CustomLocator.java
```

**证据指针**：
- `Downfall/slimebound/patches/` - 结构清晰
- `ThePackmaster/patches/turmoilpack/` - 按包组织

**自检命令**：
```bash
find ../resources/mods -type d -name "patches" -exec sh -c 'echo "=== {} ===" && ls {} | head -10' \;
```

---

## 10. Locator 实现模式不一致

**现象**：Locator 代码重复，不清楚何时用 `findInOrder` vs `findAllInOrder`。

**根因**：`findInOrder` 只返回第一个匹配位置，`int[]` 返回值的含义不清晰。

**推荐做法**：
```java
// 模式1：简单的单点插入（内部类）
private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        Matcher finalMatcher = new Matcher.MethodCallMatcher(TargetClass.class, "targetMethod");
        return LineFinder.findInOrder(ctBehavior, finalMatcher);
    }
}

// 模式2：多点插入（findAllInOrder）
private static class Locator extends SpireInsertLocator {
    public int[] Locate(CtBehavior ctBehavior) throws Exception {
        return LineFinder.findAllInOrder(ctBehavior, finalMatcher);
    }
}
```

**证据指针**：
- `ArknightsTheSpire/ui/SpUtil.java:51` - 内部类 Locator
- `rare-cards-sparkle/locators/RenderTipLocator.java` - 独立类

**自检命令**：
```bash
rg 'extends\s+SpireInsertLocator' --type java -A 10 | head -80
```
