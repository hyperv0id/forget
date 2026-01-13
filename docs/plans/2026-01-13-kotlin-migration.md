# Kotlin Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan.

**Goal:** Replace the minimal mod skeleton from Java to Kotlin while keeping Slay the Spire modding constraints (Java 8 bytecode) and ensuring the produced mod jar is self-contained (ships Kotlin runtime).

**Architecture:** Compile Kotlin to JVM target 1.8 and build a single distributable jar that includes Kotlin stdlib, but keeps STS / ModTheSpire / BaseMod as external `compileOnly` dependencies. Use the Gradle Shadow plugin to produce `TheForget.jar` and have `installMod` copy that jar into the STS `mods/` folder.

**Tech Stack:** Gradle 8.7, Kotlin JVM plugin, Shadow plugin, Java toolchain target 8 (`--release 8` for Java sources, `jvmTarget=1.8` for Kotlin).

---

### Task 1: Update Gradle to compile Kotlin and build a shaded jar

**Files:**
- Modify: `build.gradle`

**Step 1: Make the build fail (verification)**

Run: `./gradlew clean build`
Expected: PASS (baseline)

**Step 2: Add Kotlin + Shadow plugins**

- Add `org.jetbrains.kotlin.jvm`
- Add `com.github.johnrengelman.shadow`

**Step 3: Configure Kotlin compilation**

- Set `jvmTarget = "1.8"`
- Keep existing `--release 8` for JavaCompile

**Step 4: Add Kotlin stdlib to the shaded jar**

- Add `implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:<version>")`
- Configure `shadowJar` to output `TheForget.jar` (no classifier)
- Make `installMod` copy `shadowJar` output (not the plain `jar`)

**Step 5: Verify build**

Run: `./gradlew clean build`
Expected: PASS; jar produced under `build/libs/TheForget.jar`

---

### Task 2: Port mod entrypoint from Java to Kotlin

**Files:**
- Delete: `src/main/java/theforget/TheForgetMod.java`
- Create: `src/main/kotlin/theforget/TheForgetMod.kt`

**Step 1: Implement Kotlin initializer**

- Keep `@SpireInitializer`
- Provide `@JvmStatic fun initialize()`
- Subscribe via `BaseMod.subscribe(this)`
- Add robust error logging (catch exceptions and log stacktrace)

**Step 2: Verify jar contains expected entries**

Run: `jar tf build/libs/TheForget.jar | rg -n "ModTheSpire\\.json|theforget/TheForgetMod\\.class"`
Expected: Both lines present.

---

### Task 3: Verify dev workflow tasks still work

**Files:**
- (No code changes expected)

**Step 1: Install jar into STS mods**

Run: `./gradlew installMod`
Expected: `~/.steam/steam/steamapps/common/SlayTheSpire/mods/TheForget.jar` exists.

**Step 2: (Optional) Launch ModTheSpire**

Run: `./gradlew runMts`
Expected: Game starts and logs include `The Forget loaded successfully.`

---

### Task 4: Commit

```bash
git add build.gradle src/main/kotlin src/main/resources/ModTheSpire.json docs/plans/2026-01-13-kotlin-migration.md
git rm src/main/java/theforget/TheForgetMod.java
git commit -m "refactor: migrate mod skeleton to Kotlin"
```

