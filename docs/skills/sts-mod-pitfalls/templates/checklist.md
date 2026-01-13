# STS Mod 开发检查清单

本文档提供开发前、中、后的自检清单，帮助避免常见坑点。

## 开发前检查

### 基础配置
- [ ] Mod ID 已定义并与 ModTheSpire.json 一致
- [ ] 资源路径前缀已在 ModTheSpire.json 声明
- [ ] 目录结构符合社区规范

### 角色开发
- [ ] 卡色使用 `@SpireEnum` 创建自定义枚举
- [ ] 能量球资源完整（11 层纹理：layer1..5 + layer6 + layer1d..5d）
- [ ] layerSpeeds 数组长度为 5（对应 5 层动画）
- [ ] 角色选择资源存在（shoulder.png, shoulder2.png, corpse.png）
- [ ] Spine 动画文件配对（.atlas + .json 同名）

---

## Patching 检查

### 构造函数 Patch
- [ ] 构造函数 patch 使用 `<ctor>` 而非 `<init>`
- [ ] paramtypez 使用基本类型（`int.class` 而非 `Integer.class`）

### Javassist 占位符
- [ ] 无参方法使用 `$proceed()` 而非 `$proceed($$)`
- [ ] 静态方法不使用 `$0` 访问实例
- [ ] 返回值修改使用 `$_ = $proceed($$)`

### SpireField
- [ ] SpireField 初始化提供 Supplier（`new SpireField(() -> defaultValue)`）
- [ ] 不使用 `null.INSTANCE`

### SpireReturn
- [ ] void 方法使用 `SpireReturn<Void>` + `SpireReturn.Return(null)`
- [ ] 非void 方法使用具体类型 + `SpireReturn.Return(value)`

### Matcher 和 Locator
- [ ] 确认目标方法中匹配的方法/字段只出现一次，或使用 `findAllInOrder`
- [ ] Locator 实现选择合适：简单用内部类，复用用独立类

---

## Localization 检查

### 加载时机
- [ ] `loadCustomStringsFile` 在 `receiveEditStrings()` 中调用
- [ ] 不在构造函数中调用本地化加载方法

### 编码和回退
- [ ] `readString()` 显式指定 `StandardCharsets.UTF_8`
- [ ] 语言回退到 `eng`（switch 包含 `default` 分支）

### Keywords
- [ ] `addKeyword()` 在 `receiveEditKeywords()` 回调内调用
- [ ] 传入的第一个参数是 `modID.toLowerCase()`

### ID 命名
- [ ] 所有 ID 使用 `modID:EntityName` 格式
- [ ] JSON 文件中的 key 都使用 `modID:` 前缀
- [ ] 描述使用 ` NL ` 进行换行（前后有空格）

---

## 资源和配置检查

### 资源路径
- [ ] 资源路径前缀与 ModTheSpire.json 声明一致
- [ ] 使用 `Gdx.files.internal()` 而非 `File API`
- [ ] 路径大小写与实际文件名匹配（考虑 Linux 兼容）

### SpireConfig
- [ ] SpireConfig 在静态初始化块或构造函数中创建
- [ ] 创建后立即调用 `config.load()`
- [ ] 不在 `receivePostInitialize()` 等回调中创建配置
- [ ] 配置访问通过 getter 方法包装，包含 `config == null` 检查

### 存档持久化
- [ ] `CustomSavable.onSave()` 返回非 null 数据
- [ ] `onLoad()` 处理 null 输入的情况
- [ ] 保存的数据尽可能精简（基本类型优先）
- [ ] 配置文件包含版本号字段
- [ ] 有版本迁移逻辑

---

## UI 和渲染检查

### Tooltip
- [ ] tooltip 坐标使用 `Settings.scale` 缩放
- [ ] 不使用硬编码坐标（如 `1500.0F, 700.0F`）
- [ ] 处理 NL 换行符（手动替换或分段）
- [ ] TopPanel 的 `onHover()` 调用 `super.onHover()`

### ScreenShake
- [ ] 角色选择震动使用 `ShakeIntensity.LOW/MED` + `ShakeDur.SHORT`
- [ ] 避免使用 `HIGH` + `LONG`/`VERY_LONG`

---

## 自测验证命令

```bash
# ===== Patching 检查 =====

# 检查构造函数 patch 是否错误使用 <init>
rg 'method\s*=\s*"<init>"' --type java

# 检查 paramtypez 是否使用包装类型
rg 'paramtypez.*Integer\.class\|paramtypez.*Boolean\.class' --type java

# 检查 SpireField 初始化
rg 'new SpireField' --type java -A 1 | grep -E 'null\.INSTANCE'

# 检查 Javassist 占位符使用
rg '\$proceed' --type java -B 2 -A 2 | head -60

# ===== Localization 检查 =====

# 检查 UTF-8 编码
rg 'readString\(' --type java | grep -v 'UTF_8'

# 检查 ID 前缀
rg '"[A-Z][a-zA-Z]+:' --type json | grep -v 'ModTheSpire|base'

# 检查 Keywords 注册位置
grep -B 10 'addKeyword' --type java | grep -A 5 'receiveEditStrings'

# ===== 资源检查 =====

# 检查是否使用 File API
grep -rn "new File\(" --include="*.java"

# 检查 atlas/json 配对
find . -name "*.atlas" | while read f; do
  json="${f%.atlas}.json"
  if [ ! -f "$json" ]; then echo "Missing pair: $f"; fi
done

# 检查 orbTextures 是否传 null
grep -rn "orbTextures.*null\|orbVfxPath.*null" --include="*.java"

# 检查硬编码坐标（未使用 Settings.scale）
grep -rn "renderGenericTip.*[0-9]\+\.[0-9]\+F" --include="*.java" | grep -v "Settings.scale"

# 检查过激的 ScreenShake 参数
grep -rn "ShakeIntensity.HIGH\|ShakeDur.LONG\|ShakeDur.VERY_LONG" --include="*.java"

# ===== 配置检查 =====

# 检查 SpireConfig 创建位置
grep -B 10 "new SpireConfig" --include="*.java" | grep -E "receivePostInitialize|static"

# 检查 onSave 返回 null
grep -A 3 "public.*onSave()" --include="*.java" | grep "return null"
```

---

## 常见问题速查

### 问题：patch 不生效
1. 检查 `method` 参数：构造函数用 `<ctor>`
2. 检查 `paramtypez`：使用基本类型，顺序正确
3. 检查 Matcher：确认目标方法中只匹配一次

### 问题：多语言文本不显示
1. 检查 `loadCustomStringsFile` 是否在 `receiveEditStrings()` 中调用
2. 检查 `readString()` 是否指定 `UTF_8`
3. 检查 ID 是否使用 `modID:` 前缀

### 问题：资源加载失败
1. 检查 ModTheSpire.json 中的 resources 声明
2. 检查路径大小写（Linux 兼容）
3. 检查是否使用 `Gdx.files.internal()`

### 问题：tooltip 显示异常
1. 检查坐标是否使用 `Settings.scale`
2. 检查 NL 是否手动处理
3. 检查 `onHover()` 是否调用 `super.onHover()`

### 问题：存档数据丢失
1. 检查 `onSave()` 是否返回非 null
2. 检查 `onLoad()` 是否处理 null
3. 检查配置版本迁移逻辑
