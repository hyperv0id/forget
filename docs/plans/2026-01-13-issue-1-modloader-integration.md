# ModLoader Integration (Issue #1) Implementation Plan

> **For Codex:** This plan is executed directly on branch `issue/1-modloader-integration`.

**Goal:** Pick and integrate the Slay the Spire mod loading stack (ModTheSpire + BaseMod), so this repo can build a minimal mod jar and launch it with visible “mod loaded” logs.

**Architecture:** Use **ModTheSpire** as the loader/launcher and **BaseMod** as the community API layer. The project builds a minimal `@SpireInitializer` entrypoint and `ModTheSpire.json` metadata, with Gradle tasks to (1) build the jar, (2) copy it into STS `mods/`, and (3) launch ModTheSpire.

**Tech Stack:** Java 8 target (compiled with modern JDK via `--release 8`), Gradle wrapper, system-file dependencies for STS / ModTheSpire / BaseMod.

---

### Task 1: Add Gradle wrapper + build skeleton

**Files:**
- Create: `settings.gradle`
- Create/Modify: `build.gradle`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Modify: `.gitignore`

**Steps:**
1. Ensure the repo has a Gradle build (`settings.gradle`, `build.gradle`).
2. Generate Gradle wrapper (pinned Gradle version).
3. Add `.gradle/` + `build/` to `.gitignore`.

**Verify:**
- Run: `./gradlew --version`
- Expected: prints Gradle version and JVM info.

---

### Task 2: Integrate STS / ModTheSpire / BaseMod as compile-time deps

**Files:**
- Modify: `build.gradle`

**Steps:**
1. Add default jar paths for Linux Steam install:
   - STS: `~/.steam/steam/steamapps/common/SlayTheSpire/desktop-1.0.jar`
   - MTS: `~/.steam/steam/steamapps/workshop/content/646570/1605060445/ModTheSpire.jar`
   - BaseMod: `~/.steam/steam/steamapps/workshop/content/646570/1605833019/BaseMod.jar`
2. Allow overrides via Gradle properties (`-PstsDir=...`, `-PmtsJar=...`, `-PbaseModJar=...`).
3. Add a small “verify deps exist” task and make compilation depend on it (fail-fast with helpful error).

**Verify:**
- Run: `./gradlew clean compileJava`
- Expected: compiles with no missing-jar errors.

---

### Task 3: Add minimal mod entrypoint and metadata

**Files:**
- Create: `src/main/java/theforget/TheForgetMod.java`
- Create: `src/main/resources/ModTheSpire.json`

**Steps:**
1. Add `@SpireInitializer` class that subscribes to BaseMod and logs during init.
2. Add `ModTheSpire.json` with `dependencies: ["basemod"]`.

**Verify:**
- Run: `./gradlew clean jar`
- Expected: jar produced under `build/libs/`.

---

### Task 4: Add dev workflow tasks + README setup instructions

**Files:**
- Modify: `build.gradle`
- Modify: `README.md`

**Steps:**
1. Add `installMod` task to copy jar into STS `mods/`.
2. Add `runMts` task to launch ModTheSpire with `--mods basemod,theforget`.
3. Document setup + verification checklist in README.

**Verify:**
- Run: `./gradlew installMod`
- Run: `./gradlew runMts`
- Expected: ModTheSpire launches and logs include “The Forget loaded”.

