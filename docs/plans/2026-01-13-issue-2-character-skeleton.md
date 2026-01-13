# Issue #2: Character Skeleton Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the basic character framework (CustomPlayer), CardPool/RelicPool managers, and register the character so it is selectable at game start and can enter combat.

**Architecture:** Keep the implementation minimal and “no-assets” by referencing existing Slay the Spire textures (from `desktop-1.0.jar`) for the character button/portrait and animation (Ironclad skeleton). Centralize resource paths and IDs into a small `TheForgetAssets`/`TheForgetIds` module so they can be validated via unit tests without launching the game.

**Tech Stack:** Kotlin, BaseMod (CustomPlayer + subscriber hooks), ModTheSpire (SpireEnum, SpireInitializer), Gradle.

---

### Task 1: Add test framework + write failing tests (TDD)

**Files:**
- Modify: `build.gradle`
- Create: `src/test/kotlin/theforget/AssetPathsTest.kt`

**Step 1: Write failing test (compile failure is OK)**

Create `AssetPathsTest` that references a not-yet-created `theforget.core.TheForgetAssets` object and asserts:
- STS jar exists (path provided by Gradle system property)
- Each asset path used by the mod exists as an entry inside the STS jar (Zip)

**Step 2: Run test to verify it fails**

Run: `./gradlew test --no-daemon`
Expected: FAIL (missing `TheForgetAssets` / missing configuration).

---

### Task 2: Implement assets/constants module

**Files:**
- Create: `src/main/kotlin/theforget/core/TheForgetAssets.kt`

**Step 1: Minimal implementation**

Implement `object TheForgetAssets` containing the STS internal paths used for:
- charSelect button + portrait
- Ironclad skeleton atlas/json
- shoulder/corpse textures

**Step 2: Run tests**

Run: `./gradlew test --no-daemon`
Expected: PASS.

---

### Task 3: Add PlayerClass enum + character skeleton

**Files:**
- Create: `src/main/kotlin/theforget/enums/TheForgetEnums.kt`
- Create: `src/main/kotlin/theforget/characters/TheForgetCharacter.kt`

**Step 1: Add SpireEnum**

Define:
- `@SpireEnum @JvmField lateinit var THE_FORGET: AbstractPlayer.PlayerClass`

**Step 2: Implement CustomPlayer**

Implement `TheForgetCharacter : CustomPlayer`:
- Uses `SpineAnimation` with Ironclad skeleton
- Calls `initializeClass(...)` and `EnergyManager(3)`
- Provides basic starter deck using built-in red `Strike_R`/`Defend_R` IDs
- Provides basic starter relic set (placeholder: `Burning Blood`)
- Implements required overrides (`getLoadout`, `newInstance`, etc.)

**Step 3: Compile**

Run: `./gradlew clean build --no-daemon`
Expected: PASS.

---

### Task 4: Register character + add pool managers

**Files:**
- Modify: `src/main/kotlin/theforget/TheForgetMod.kt`
- Create: `src/main/kotlin/theforget/content/CardPoolManager.kt`
- Create: `src/main/kotlin/theforget/content/RelicPoolManager.kt`

**Step 1: Pool managers**

Add minimal managers returning:
- `startingDeckIds(): List<String>`
- `startingRelicIds(): List<String>`

**Step 2: Register character**

Update `TheForgetMod` to implement `EditCharactersSubscriber` and call:

- `BaseMod.addCharacter(TheForgetCharacter(CardCrawlGame.playerName), ...button..., ...portrait..., THE_FORGET)`

**Step 3: Build**

Run: `./gradlew clean build --no-daemon`
Expected: PASS.

---

### Task 5: Manual verification (acceptance)

**Steps (manual):**
- Run: `./gradlew installMod --no-daemon`
- Run: `./gradlew runMts --no-daemon`
- Verify: New game character select shows “The Forget” and entering a run reaches the first combat without crash.

