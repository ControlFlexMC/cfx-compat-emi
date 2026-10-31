# cfx-compat-emi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a ControlFlex bridge mod that exposes 23 EMI actions as bindable ControlFlex actions on MC 1.20.1, for both Forge and Fabric.

**Architecture:** The bridge registers 23 pseudo `KeyMapping`s that ControlFlex auto-discovers during its `mc.options.keyMappings` scan. A per-loader client tick samples `KeyMapping.isDown()` / `consumeClick()`, applies EMI-faithful gates, and calls EMI's public dispatchers (`EmiScreenManager.stackInteraction` / `genericInteraction` / `recipeInteraction`) with a predicate matching exactly one `EmiBind`. No mixins, no changes to ControlFlex.

**Tech Stack:** Java 17, Gradle 8.8, `fabric-loom` (Fabric) + `net.neoforged.moddev.legacyforge` (Forge), ControlFlex API 0.9.2 from JitPack, EMI 1.1.24.

**Spec:** `docs/zh/design.md` (authoritative) and `docs/en/design.md` (mirror). Read both before starting; section numbers in this plan refer to the Chinese doc.

## Global Constraints

- Target: Minecraft `1.20.1`, Forge `47.x`, Fabric loader `0.15.x`, Java 17.
- Hard dependencies: `controlflex` ≥ `0.8.9`, `emi` (any 1.20.1 build). Both `mandatory` in `mods.toml`, both in Fabric `depends`.
- Mod id `cfx_compat_emi`, name `Control Flex x EMI`, package `com.ifels.cfx.emi`.
- Exactly 23 actions. Never 24; `displayAllRecipes` is already one of the 26 EMI binds.
- `common/src/main/java` must contain no `net.minecraft.*` and no `dev.emi.*` types.
- No mixins, no refmap, no `mixins.json`.
- KeyMapping name prefix `key.cfx_compat_emi.`, category `key.categories.cfx_compat_emi`, default key `InputConstants.UNKNOWN`.
- compat JSON `mod_id` must equal `cfx_compat_emi`; `screenKeys.channels` must be exactly `["keyMapping"]`.
- Log prefix `[cfx-compat-emi]`.

## Review Focus

1. **Stale held button on screen entry** — player opens the inventory with the same button bound to an EMI action; the first frame must not fire. Pinned by Task 3 (`EdgeDetector` reset) and Task 5/6 (`resetAll` on entry).
2. **EMI disabled or reloading** — every action except `toggleVisibility` must be inert, with no log spam. Pinned by the `disabled` branch in Tasks 5/6 and by `EdgeDetector` tests staying silent.
3. **Focused text field** — typing in an anvil/sign/EMI search box must suppress all actions. Pinned by `hasFocusedTextField` in Tasks 5/6.
4. **No cursor / nothing hovered** — actions must be no-ops rather than throwing. Pinned by dispatch null-guards in Tasks 5/6.
5. **compat JSON not installed (SHA-1 path, missing dir)** — actions must still not crash, and the log must say why. Pinned by Task 4's installer test and Task 5/6 exception handling.

---

### Task 1: Scaffold and prove dependency resolution

**Files:**
- Create: `settings.gradle`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- Create: `forge/build.gradle`, `fabric/build.gradle`
- Create: `forge/src/main/resources/META-INF/mods.toml`, `forge/src/main/resources/pack.mcmeta`
- Create: `fabric/src/main/resources/fabric.mod.json`
- Create: `forge/src/main/java/com/ifels/cfx/emi/forge/CfxEmiMod.java` (stub class, no-op)
- Create: `fabric/src/main/java/com/ifels/cfx/emi/fabric/CfxEmiMod.java` (stub class, no-op)
- Modify: `README.md`, `.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces: `com.ifels.cfx.emi.forge.CfxEmiMod` (Forge `@Mod` entry, client init hook) and `com.ifels.cfx.emi.fabric.CfxEmiMod` (`ClientModInitializer`) — both later tasks extend these.

- [ ] **Step 1: Copy the multi-loader skeleton from `cfx-compat-create`**

Copy `settings.gradle`, `gradle/`, `gradlew`, `gradlew.bat`, `.gitignore`, and the `pluginManagement` block verbatim; rename project to `cfx-compat-emi`. Keep `include 'forge', 'fabric'`.

- [ ] **Step 2: Fill `gradle.properties`**

```
mod_id=cfx_compat_emi
mod_name=Control Flex x EMI
mod_version=0.1.0
build_id=0
mod_authors=ifels
mod_license=MIT
mod_description=EMI action bridge for ControlFlex
maven_group=com.ifels
minecraft_version=1.20.1
minecraft_version_range=[1.20.1,1.20.2)
forge_version=47.2.0
fabric_loader_version=0.15.7
fabric_api_version=0.83.0+1.20.1
java_version=17
emi_version=1.1.24+1.20.1
controlflex_mod_id=controlflex
controlflex_version_range=[0.8.9,)
controlflex_api_version=0.9.2
```

- [ ] **Step 3: Write both loader builds**

Forge: `legacyForge { version = "${minecraft_version}-${forge_version}" }`, `sourceSets.main.java.srcDir(rootProject.file('common/src/main/java'))`, repositories `mavenLocal()`, `mavenCentral()`, JitPack, `https://repo.sleeping.town/`, dependencies `compileOnly "com.github.ControlFlexMC:control-flex-api:${controlflex_api_version}"` and EMI (see Step 4).

Fabric: `loom { }` with `runDir 'run-fabric'`, same `srcDir`, `modCompileOnly "dev.emi:emi-fabric:${emi_version}"` + `modLocalRuntime`.

- [ ] **Step 4: Prove EMI's Forge coordinate resolves**

Try in order, record which works in the plan's commit message:
1. `compileOnly fg.deobf("dev.emi:emi-forge:${emi_version}")`
2. `compileOnly "dev.emi:emi-xplat-mojmap:${emi_version}"`
3. `compileOnly files("libs/emi-forge-${emi_version}.jar")` as last resort

Run: `./gradlew :forge:dependencies --configuration compileClasspath | grep -i emi`

Expected: EMI classes appear, no `Could not resolve` for `dev.emi`.

- [ ] **Step 5: Write loader metadata**

`mods.toml`: `modLoader="javafml"`, `loaderVersion="[47,)"`, `license`, one `[[mods]]` with `modId`, `displayName`, `version="${file.jarVersion}"`, `displayTest="IGNORE_ALL_VERSION"`; `[[dependencies.${mod_id}]]` for `forge`, `minecraft`, `controlflex` (`mandatory=true`, `ordering="AFTER"`, `versionRange="${controlflex_version_range}"`), `emi` (`mandatory=true`, `ordering="AFTER"`).

`fabric.mod.json`: `id: "${mod_id}"`, `environment: "client"`, entrypoint `client: ["com.ifels.cfx.emi.fabric.CfxEmiMod"]`, `depends` with `fabricloader`, `fabric-api`, `minecraft`, `java`, `controlflex`, `emi`. No `mixins` array.

- [ ] **Step 6: Build**

Run: `./gradlew :forge:build :fabric:build`
Expected: `BUILD SUCCESSFUL`, two jars under `forge/build/libs` and `fabric/build/libs`.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "build: scaffold forge+fabric multi-loader project for cfx-compat-emi"
```

---

### Task 2: Action table in `common`

**Files:**
- Create: `common/src/main/java/com/ifels/cfx/emi/ActionKind.java`
- Create: `common/src/main/java/com/ifels/cfx/emi/EmiAction.java`
- Test: `forge/src/test/java/com/ifels/cfx/emi/EmiActionTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum ActionKind { STACK, STACK_THEN_RECIPE, RECIPE, GENERIC, API }`; `enum EmiAction` with `String id()` (`"key.cfx_compat_emi." + path`), `String bindKey()` (EMI field name), `ActionKind kind()`; constants in the order of spec §8.5.

- [ ] **Step 1: Write the failing test**

```java
class EmiActionTest {
    @Test void hasExactlyTwentyThreeActions() {
        assertEquals(23, EmiAction.values().length);
    }
    @Test void idsAreUniqueAndPrefixed() {
        Set<String> ids = new HashSet<>();
        for (EmiAction a : EmiAction.values()) {
            assertTrue(a.id().startsWith("key.cfx_compat_emi."));
            assertTrue(ids.add(a.id()), "duplicate: " + a.id());
        }
    }
    @Test void kindAssignmentMatchesSpec() {
        assertEquals(ActionKind.API, EmiAction.DISPLAY_ALL_RECIPES.kind());
        assertEquals(ActionKind.STACK_THEN_RECIPE, EmiAction.FAVORITE.kind());
        assertEquals(ActionKind.RECIPE, EmiAction.COPY_ID.kind());
        assertEquals(ActionKind.GENERIC, EmiAction.TOGGLE_VISIBILITY.kind());
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :forge:test --tests '*EmiActionTest*'`
Expected: compile error, `EmiAction` not found.

- [ ] **Step 3: Implement `ActionKind` and `EmiAction`**

23 constants with the exact `bindKey` spellings from spec §8.5: `toggleVisibility, focusSearch, clearSearch, displayAllRecipes, viewRecipes, viewUses, favorite, viewStackTree, viewTree, back, forward, craftOne, craftAll, craftOneToInventory, craftAllToInventory, craftOneToCursor, cheatOneToInventory, cheatStackToInventory, cheatOneToCursor, cheatStackToCursor, hideStack, hideStackById, copyId`.

Enum constant names are the `UPPER_SNAKE` form of `bindKey` (`VIEW_RECIPES`). `id()` derives the path by converting `bindKey` to snake_case (`view_recipes`).

- [ ] **Step 4: Run tests**

Run: `./gradlew :forge:test --tests '*EmiActionTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/ifels/cfx/emi forge/src/test/java/com/ifels/cfx/emi/EmiActionTest.java
git commit -m "feat: add the 23-entry EMI action table"
```

---

### Task 3: `EdgeDetector`

**Files:**
- Create: `common/src/main/java/com/ifels/cfx/emi/EdgeDetector.java`
- Test: `forge/src/test/java/com/ifels/cfx/emi/EdgeDetectorTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `final class EdgeDetector { boolean update(boolean isDown, boolean clicked); void reset(); }` — `update` returns true on a rising edge of `isDown`, or on `!isDown && clicked`.

- [ ] **Step 1: Write the failing tests**

```java
class EdgeDetectorTest {
    @Test void firesOnceWhenHeld() {
        EdgeDetector d = new EdgeDetector();
        assertTrue(d.update(true, false));
        assertFalse(d.update(true, false));
        assertFalse(d.update(true, true));   // HOLD mode bumps clickCount every tick
    }
    @Test void firesWhenTapEndedBetweenFrames() {
        EdgeDetector d = new EdgeDetector();
        assertTrue(d.update(false, true));
    }
    @Test void silentWithoutInput() {
        EdgeDetector d = new EdgeDetector();
        assertFalse(d.update(false, false));
    }
    @Test void firesOnceForTwoClicksInOneFrame() {
        EdgeDetector d = new EdgeDetector();
        assertTrue(d.update(false, true));
        assertFalse(d.update(false, false));
    }
    @Test void resetClearsLastDownSoHeldButtonFires() {
        EdgeDetector d = new EdgeDetector();
        d.update(true, false);
        d.reset();
        assertTrue(d.update(true, false));   // after reset a held key counts as a new press...
    }
}
```

Note for the implementer: the last test documents the chosen semantics — `reset()` clears `lastDown`, so a button still held right after entering a screen WOULD fire. That is why the ticker drains and resets **before** sampling and skips that frame (see Task 5). Keep `reset()` as written; the caller owns the skip.

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew :forge:test --tests '*EdgeDetectorTest*'`
Expected: compile error.

- [ ] **Step 3: Implement**

```java
public final class EdgeDetector {
    private boolean lastDown;
    public boolean update(boolean isDown, boolean clicked) {
        boolean fire = (isDown && !lastDown) || (!isDown && clicked);
        lastDown = isDown;
        return fire;
    }
    public void reset() { lastDown = false; }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :forge:test --tests '*EdgeDetectorTest*'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/ifels/cfx/emi/EdgeDetector.java forge/src/test/java/com/ifels/cfx/emi/EdgeDetectorTest.java
git commit -m "feat: add press/click edge detector"
```

---

### Task 4: Compat JSON, lang, plugin, SPI

**Files:**
- Create: `common/src/main/java/com/ifels/cfx/emi/CfxEmiPlugin.java`
- Create: `forge/src/main/resources/assets/cfx_compat_emi/compat/cfx_compat_emi.json`
- Create: `forge/src/main/resources/assets/cfx_compat_emi/lang/en_us.json`, `zh_cn.json`
- Create: `forge/src/main/resources/META-INF/services/com.ifels.controlflex.api.IControlFlexPlugin`
- Create: same three assets + services file under `fabric/src/main/resources/`
- Test: `forge/src/test/java/com/ifels/cfx/emi/CfxEmiPluginTest.java`

**Interfaces:**
- Consumes: `EmiAction.values()` (Task 2).
- Produces: `CfxEmiPlugin implements IControlFlexPlugin` with `MOD_ID = "cfx_compat_emi"`, `getModId()`, `onInstallCompatConfigs(ICompatAssetInstaller)`; resource path constant `COMPAT_RESOURCE = "/assets/cfx_compat_emi/compat/cfx_compat_emi.json"`.

- [ ] **Step 1: Write the failing tests**

```java
class CfxEmiPluginTest {
    @Test void installsCompatJson() {
        List<String[]> calls = new ArrayList<>();
        new CfxEmiPlugin().onInstallCompatConfigs((res, name) -> {
            calls.add(new String[]{res, name});
            return true;
        });
        assertEquals(1, calls.size());
        assertEquals("/assets/cfx_compat_emi/compat/cfx_compat_emi.json", calls.get(0)[0]);
        assertEquals("cfx_compat_emi.json", calls.get(0)[1]);
    }

    @Test void compatJsonListsEveryAction() throws Exception {
        String json = new String(getClass().getResourceAsStream(
                CfxEmiPlugin.COMPAT_RESOURCE).readAllBytes(), StandardCharsets.UTF_8);
        for (EmiAction a : EmiAction.values()) {
            assertTrue(json.contains("\"" + a.id() + "\""), "missing " + a.id());
        }
        assertTrue(json.contains("\"channels\": [\"keyMapping\"]"));
        assertTrue(json.contains("\"mod_id\": \"cfx_compat_emi\""));
    }
}
```

- [ ] **Step 2: Run and watch it fail**

Run: `./gradlew :forge:test --tests '*CfxEmiPluginTest*'`
Expected: compile error / missing resource.

- [ ] **Step 3: Write the assets**

Compat JSON (23 `screenKeys` entries + the `channels` reserved key):

```json
{
  "mod_id": "cfx_compat_emi",
  "mod_name": "Control Flex x EMI",
  "home_page": "https://github.com/ControlFlexMC/cfx-compat-emi",
  "config_version": "1.0.0",
  "mod_versions": [
    {
      "version": "",
      "screenKeys": {
        "channels": ["keyMapping"],
        "key.cfx_compat_emi.toggle_visibility": {},
        "... one entry per action ...": {}
      }
    }
  ]
}
```

Lang: `key.cfx_compat_emi.<path>` → `Toggle EMI`, `View Recipes`, … and `key.categories.cfx_compat_emi` → `Control Flex x EMI`. Chinese mirrors EMI's own wording (`查看配方`, `查看用途`, `收藏`, `合成`, …).

- [ ] **Step 4: Implement the plugin**

```java
public final class CfxEmiPlugin implements IControlFlexPlugin {
    public static final String MOD_ID = "cfx_compat_emi";
    public static final String COMPAT_RESOURCE = "/assets/cfx_compat_emi/compat/cfx_compat_emi.json";
    @Override public String getModId() { return MOD_ID; }
    @Override public void onInstallCompatConfigs(ICompatAssetInstaller installer) {
        installer.install(COMPAT_RESOURCE, "cfx_compat_emi.json");
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :forge:test --tests '*CfxEmiPluginTest*'`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add common forge/src/main/resources fabric/src/main/resources forge/src/test/java/com/ifels/cfx/emi/CfxEmiPluginTest.java
git commit -m "feat: ship compat JSON, lang, and the ControlFlex plugin entry"
```

---

### Task 5: Forge runtime (registration, ticker, dispatch)

**Files:**
- Create: `forge/src/main/java/com/ifels/cfx/emi/forge/EmiKeys.java`
- Create: `forge/src/main/java/com/ifels/cfx/emi/forge/EmiTicker.java`
- Create: `forge/src/main/java/com/ifels/cfx/emi/forge/EmiDispatch.java`
- Modify: `forge/src/main/java/com/ifels/cfx/emi/forge/CfxEmiMod.java`

**Interfaces:**
- Consumes: `EmiAction`, `EdgeDetector`, `ActionKind`.
- Produces: none downstream (Fabric is an independent copy).

- [ ] **Step 1: `EmiKeys`** — `EnumMap<EmiAction, KeyMapping>`, `registerAll(Consumer<KeyMapping>)` builds `new KeyMapping(action.id(), InputConstants.UNKNOWN, "key.categories.cfx_compat_emi")` per action, `get(EmiAction)`.

- [ ] **Step 2: `EmiDispatch`** — `static boolean fire(EmiAction action)` with a `switch` expression over all 23 constants and **no `default`** (the compiler enforces coverage when the enum grows). Resolve `EmiStackInteraction hovered = EmiApi.getHoveredStack(true)` and `EmiIngredient stack = hovered.getStack()`; return false when `stack.isEmpty()` for `STACK*`.

```java
STACK               -> EmiScreenManager.stackInteraction(hovered, b -> b == bind(action));
STACK_THEN_RECIPE   -> EmiScreenManager.stackInteraction(hovered, b -> b == bind(action))
                       || EmiScreenManager.recipeInteraction(context(hovered), b -> b == bind(action));
RECIPE              -> EmiScreenManager.recipeInteraction(context(hovered), b -> b == bind(action));
GENERIC             -> EmiScreenManager.genericInteraction(b -> b == bind(action));
API                 -> { EmiApi.displayAllRecipes(); yield true; }
```

`bind(action)` is a `switch` returning the `EmiConfig.<bindKey>` field; `context(hovered)` returns `hovered.getRecipeContext()` and falls back to `EmiApi.getRecipeContext(hovered.getStack())`.

- [ ] **Step 3: `EmiTicker.tick()`** — copy the loop from spec §8.2 verbatim, with `drainAll`, `resetAll`, `wasInEmi`, `disabled`, `focusedField` and the once-per-frame `EmiScreenManager.recalculate()`. Wrap each `fire()` in `try/catch (Throwable t)` logging `[cfx-compat-emi] dispatch failed for {}` at warn.

- [ ] **Step 4: `hasFocusedTextField`** — recursive over `screen.children()`, depth 10, `child instanceof EditBox box && box.isActive() && box.visible`, recurse into `ParentElement`.

- [ ] **Step 5: Wire `CfxEmiMod`** — `@Mod("cfx_compat_emi")`, client-only; on `RegisterKeyMappingsEvent` call `EmiKeys.registerAll(event::register)`; on `ClientTickEvent.Post` call `EmiTicker.tick()` if `Minecraft.getInstance().screen != null || !EmiKeys.isEmpty()`.

- [ ] **Step 6: Build**

Run: `./gradlew :forge:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Manual acceptance (Forge)** — run the 10 cases from spec §11 in a dev client with EMI + ControlFlex installed; record results in the commit message.

- [ ] **Step 8: Commit**

```bash
git add forge/src/main/java
git commit -m "feat(forge): register EMI pseudo-keys and dispatch via EMI interaction API"
```

---

### Task 6: Fabric runtime (registration, ticker, dispatch)

**Files:**
- Create: `fabric/src/main/java/com/ifels/cfx/emi/fabric/EmiKeys.java`, `EmiTicker.java`, `EmiDispatch.java`
- Modify: `fabric/src/main/java/com/ifels/cfx/emi/fabric/CfxEmiMod.java`

**Interfaces:**
- Consumes: same as Task 5.
- Produces: none.

- [ ] **Step 1: Port `EmiKeys`** — `KeyBindingHelper.registerKeyBinding(new KeyMapping(...))`, same names/category, `ClientTickEvents.END_CLIENT_TICK`.

- [ ] **Step 2: Port `EmiDispatch` and `EmiTicker`** — identical logic, Yarn type names: `MinecraftClient`, `TextFieldWidget`, `InputConstants`, `Screen`.

- [ ] **Step 3: Port `hasFocusedTextField`** — `child instanceof TextFieldWidget tfw && tfw.isActive() && tfw.visible`.

- [ ] **Step 4: Build**

Run: `./gradlew :fabric:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Manual acceptance (Fabric)** — the same 10 cases.

- [ ] **Step 6: Commit**

```bash
git add fabric/src/main/java
git commit -m "feat(fabric): mirror the EMI action bridge for Fabric"
```

---

### Task 7: README, docs links, final verification

**Files:**
- Modify: `README.md`
- Create: `CHANGELOG.md`

**Interfaces:** none.

- [ ] **Step 1: README** — what it does, the 23 actions table, install requirements (`controlflex ≥ 0.8.9`, `emi`), the "point at the item first" note, the "does not rebind EMI's own keys" note, and the known limitations (`defaultStack`, `deleteCursorStack`, `showCraft` unsupported).

- [ ] **Step 2: Full build + tests**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, all unit tests pass.

- [ ] **Step 3: Grep for accidental mapping leakage in `common`**

Run: `grep -rn "net.minecraft\|dev.emi" common/src/main/java`
Expected: no matches.

- [ ] **Step 4: Commit**

```bash
git add README.md CHANGELOG.md
git commit -m "docs: describe the EMI action bridge and its limits"
```
