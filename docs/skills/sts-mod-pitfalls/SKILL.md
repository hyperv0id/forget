---
name: sts-mod-pitfalls
description: Slay the Spire Mod 开发踩坑规范。提供 Patching、CustomPlayer、Localization 等场景的常见坑点、证据指针和自检方法。处理 @SpirePatch、Spine 动画、SpireConfig、Localization 时使用。
---

# STS Mod 开发踩坑规范

## 何时使用

开发 Slay the Spire mod 时遇到以下场景时激活：

- 使用 `@SpirePatch` 进行代码注入
- 实现 `CustomPlayer` 自定义角色
- 处理 Spine 动画/资源加载
- 配置多语言文案
- 实现存档持久化

## 快速索引

| 主题 | 坑点数量 | 关键风险 |
|------|---------|---------|
| Patching | 10 类 | Patch 无效/崩溃 |
| CustomPlayer | 14 条 | 资源加载失败 |
| Localization | 12 项 | 文案不显示/乱码 |

## 核心原则

1. **资源路径规范**：使用 `ModTheSpire.json` 声明的 resources 前缀
2. **回调时序规则**：receiveEditStrings → receiveEditCards → receiveEditRelics → receivePostInitialize
3. **Patch 注入策略**：优先 SpireInsertPatch，再用 SpireInstrumentPatch
4. **ID 命名约定**：所有 ID 使用 `modID:EntityName` 格式

## 详细参考

- **Patching**：见 [references/PATCHING.md](references/PATCHING.md) - 10 类常见坑点
- **CustomPlayer**：见 [references/CUSTOMPLAYER.md](references/CUSTOMPLAYER.md) - 14 条资源相关坑点
- **Localization**：见 [references/LOCALIZATION.md](references/LOCALIZATION.md) - 12 项多语言配置坑点
- **检查清单**：见 [templates/checklist.md](templates/checklist.md) - 开发前/中/后自检清单

## 坑点总览

### Patching 坑点

1. **构造函数 Patch**：用 `<ctor>` 而非 `<init>`
2. **反射私有字段**：硬编码字符串的脆弱性
3. **SpireInsert vs SpireInstrument**：选择策略混乱
4. **Javassist 占位符**：`$proceed`/`$$`/`$_` 使用错误
5. **SpireField 初始化**：`null.INSTANCE` 陷阱
6. **SpireReturn 泛型**：与返回值类型不匹配
7. **Matcher 多匹配**：导致 patch 位置错误
8. **paramtypez 参数**：类型错误或顺序错误
9. **Patch 类命名**：组织结构混乱
10. **Locator 实现**：模式不一致

### CustomPlayer/资源坑点

1. **路径大小写敏感**：Windows 正常，Linux 崩溃
2. **打包进 jar 后路径变化**：File API 无法访问
3. **atlas-json 配对错误**：文件不匹配或版本不一致
4. **资源未在 ModTheSpire.json 声明**：打包后加载失败
5. **orbTextures/orbVfxPath 传 null**：复用原版红色能量球
6. **layer1..6d 资源缺失**：无能量状态显示异常
7. **layerSpeeds 数组长度不匹配**：动画速度异常
8. **ScreenShake 参数过强**：选中角色抖动很久
9. **选中效果/头像资源缺失**：显示黑色方块
10. **loadAnimation 调用时机错误**：模型不显示
11. **tooltip 坐标硬编码**：画到屏幕外
12. **TipHelper.renderGenericTip 不展开 NL**：显示字面量
13. **updateTips 回调顺序问题**：tooltip 延迟显示
14. **占位复用原版资源边界**：哪些行为危险

### Localization/配置坑点

1. **加载时机错误**：构造函数中调用 loadCustomStringsFile
2. **UTF-8 编码问题**：中文显示乱码
3. **语言回退机制缺失**：不支持语言崩溃
4. **Keywords 注册时机错误**：在 receiveEditStrings 中调用
5. **ID 前缀缺失**：与其他 mod 冲突
6. **NL 换行符使用错误**：混淆 NL 与 \n
7. **SpireConfig 创建时机不当**：早期代码无法访问
8. **getPrefs 时机问题**：过早访问未初始化配置
9. **onSave/onLoad 实现不完整**：存档数据无法恢复
10. **版本升级迁移缺失**：旧存档失效
11. **存档膨胀问题**：保存过多数据
12. **abstractSaveString/abstractSaveSpecial 使用场景**：何时使用

## 证据来源

本地解包 mod：`../resources/mods/`（119 个 mod）

主要参考：
- **LingMod**：`lingmod/character/Ling.java` - 角色实现、路径管理
- **Downfall**：`slimebound/patches/` - Patch 组织结构
- **BaseMod**：`basemod/abstracts/CustomPlayer.java` - 基类实现
- **StSLib**：`StSLib/patches/` - 高级 Patch 技术
- **WljMod**：`WljMod.java` - 完整的回调时序
- **AyaMod**：`AyaMod.java` - 资源路径工具方法
- **AnonMod**：`characters/char_Anon.java` - 能量球配置

## 快速自检

```bash
# 检查构造函数 patch
rg 'method\s*=\s*"<init>"' --type java

# 检查 UTF-8 编码
rg 'readString\(' --type java | grep -v UTF_8

# 检查 ID 前缀
rg '"[A-Z][a-zA-Z]+:' --type json | grep -v 'ModTheSpire|base'

# 检查 SpireField 初始化
rg 'new SpireField' --type java -A 1 | grep -E '(null\.INSTANCE|::|\(\) ->)'
```
