# STS Mods Pitfalls Survey Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 调查本机 `../resources/mods/` 下的已解包模组实现方式，补充 `docs/pitfalls.md` 的“踩坑记录 + 最佳实践”，减少后续开发反复试错。

**Architecture:** 用脚本/检索把 119 个模组的“共性写法/高频模式”抽成证据清单（mod + 文件路径 + 片段关键词），再把这些证据归纳成可操作的坑点条目（现象/根因/解决/检查清单），最后回填到 `docs/pitfalls.md` 并与本项目现有代码对齐。

**Tech Stack:** `bash`, `rg` (ripgrep), `jq` (可选), `python` (可选), Markdown

---

## Task 1: 确定输出规范与边界

**Files:**
- Modify: `docs/pitfalls.md`
- Create (optional, 用于留证据): `docs/references/mod-survey/README.md`
- Create (optional): `docs/references/mod-survey/evidence.md`

**Step 1: 约束“允许写进文档的内容”**
- 只写“现象/根因/解决/检查项”，不要复制粘贴任何第三方 mod 的成段代码
- 如需举例，只能给“关键词/伪代码/接口名”，并给出对照来源的 `modName/relativePath` 作为指引
- 每条坑点尽量能落到“我下一步应该改哪”或“我下一步应该怎么验证”

**Step 2: 定义坑点条目的统一模板**
- 标题：一句话描述坑点（不要太泛）
- 现象：游戏里看到什么/日志里报什么
- 根因：为什么会这样（BaseMod/MTS/STS 机制点）
- 解决：推荐做法 + 最小示例（伪代码/关键 API）
- 快速自检：1~3 条 checklist（能用命令/测试/日志验证更好）

---

## Task 2: 盘点 119 个 mod 的基本画像（语言/结构/依赖）

**Files:**
- Create (optional): `docs/references/mod-survey/inventory.md`

**Step 1: 收集 ModTheSpire.json 元信息**

Run:
```bash
find ../resources/mods -maxdepth 2 -name 'ModTheSpire.json' -print
```

**Step 2: 抽取依赖与版本线索（BaseMod / StSLib / MTS）**

Run:
```bash
rg -n '"dependencies"|"modid"|"name"|"version"|"author"' ../resources/mods -S --glob='ModTheSpire.json'
```

**Step 3: 结构画像（典型目录约定）**

Run:
```bash
find ../resources/mods -maxdepth 2 -type d -print | rg -n '/(cards|relics|characters|patches|powers|localization|img|images|audio|effects)(/|$)' -S
```

---

## Task 3: 按主题做“高频模式扫描”（为坑点条目找证据）

**Files:**
- Modify: `docs/references/mod-survey/evidence.md` (optional)

**Step 1: CustomPlayer / 角色骨架 / 动画与资源加载**
Run:
```bash
rg -n "extends CustomPlayer|new CustomPlayer\\(|loadAnimation\\(|Spine|skeleton|atlas|\\.json" ../resources/mods -S --glob='*.java' --glob='*.kt'
```

**Step 2: BaseMod Subscriber 生命周期与注册时机**
Run:
```bash
rg -n "receive(Edit|PostInitialize|OnStart|StartGame|AddAudio|EditKeywords|EditStrings|EditCharacters|EditCards|EditRelics)" ../resources/mods -S --glob='*.java' --glob='*.kt'
```

**Step 3: @SpireEnum 冲突与声明习惯**
Run:
```bash
rg -n "@SpireEnum" ../resources/mods -S --glob='*.java' --glob='*.kt'
```

**Step 4: SpirePatch / InsertPatch / Locator / LineFinder**
Run:
```bash
rg -n "@SpirePatch|@SpireInsertPatch|SpireInsertLocator|LineFinder\\.|ExprEditor|javassist" ../resources/mods -S --glob='*.java' --glob='*.kt'
```

**Step 5: Localization（编码/前缀/回退/keywords）**
Run:
```bash
rg -n "localization|CardCrawlGame\\.languagePack|loadCustomStrings|addKeyword|keywords\\.json|strings\\.json|UTF-8" ../resources/mods -S --glob='*.java' --glob='*.kt' --glob='*.json'
```

**Step 6: Prefs / SaveData / config**
Run:
```bash
rg -n "SpireConfig|Preferences|getPrefs\\(|save\\(|load\\(|onSave\\(|onLoad\\(" ../resources/mods -S --glob='*.java' --glob='*.kt'
```

---

## Task 4: 从证据归纳“可写进 pitfalls.md 的条目”

**Files:**
- Modify: `docs/pitfalls.md`

**Step 1: 按类别扩展章节（建议顺序）**
- CustomPlayer（动画/资源/能量球/选择界面）
- Patching（method/ctor/locator/static call/patch order）
- Enums & IDs（@SpireEnum + ID 前缀 + 冲突）
- Localization（字符串/keywords/编码/回退）
- 资源加载与打包（路径、大小写、jar 内外差异）
- Save & Config（SpireConfig/Prefs 的时机与兼容）

**Step 2: 每条坑点至少满足一个“可验证”要素**
- 有可复现症状（或日志关键词）
- 有明确根因（机制点）
- 有明确解决动作（修改点）
- 有自检办法（命令/测试/观察点）

---

## Task 5: 与本项目现状对齐（避免写成“别人的坑”）

**Files:**
- Modify: `docs/pitfalls.md`
- Reference: `src/main/kotlin/theforget/**`

**Step 1: 对每条新增坑点标注“本项目是否已规避/是否待办”**
- 已规避：写“本项目现状/相关文件”即可
- 未规避：写“建议后续 issue：xxx”

**Step 2: 保持文风一致**
- 继续使用现有格式：编号条目 + 现象/根因/解决 + 必要时命令块

---

## Task 6: 最终检查与交付

**Files:**
- Modify: `docs/pitfalls.md`

**Step 1: 合规检查**
- 没有出现第三方 mod 的大段代码
- 所有引用都只到“mod 名 + 文件路径 + 关键词”

**Step 2: 可读性检查**
- 目录结构清晰（按类别组织）
- 每条尽量能让开发者“5 分钟内定位到问题”

