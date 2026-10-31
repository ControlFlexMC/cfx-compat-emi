# Control Flex × EMI Compat Bridge — Design

> **Branch note (1.19.2):** this document was written for the 1.20.1 branch. 1.19.2 keeps the same
> architecture; the differences all come from EMI's version. (1) Loaders are **Forge 43.2.0 +
> Fabric 0.14.21**; (2) MC 1.19.2 / Java 17; (3) only **EMI 1.0.9** exists for 1.19.2 — its
> `EmiConfig` has 23 `EmiBind` fields with **no `forward` and no `copyId`**, and there is no
> `recipeInteraction`, so the action table is **20 entries** and `FAVORITE` degrades to stack-only;
> (4) there is no `EmiScreenBase`, so the "EMI is in this screen" gate uses EMI's own `EmiScreen`
> marker interface (`HandledScreen` gets it from EMI's mixin, `RecipeScreen` / `BoMScreen` implement
> it directly) — which also removes the recipe-tree special case this document describes.

- Project: `cfx-compat-emi`
- Targets: Minecraft 1.20.1 / Forge 47.x + Fabric 0.15.x
- Depends on: ControlFlex ≥ 0.8.9, EMI 1.1.24 (`dev.emi:emi-*`)
- Status: design approved, ready to implement

## 1. Background

All 26 of EMI's keybinds live in its own system: `dev.emi.emi.input.EmiBind` values are persisted in EMI's QDCSS config (`binds.*`) and matched inside EMI's screens with `matchesKey` / `matchesMouse`. EMI registers **no Minecraft `KeyMapping` at all**.

ControlFlex builds its dynamic action pool by scanning `mc.options.keyMappings` (`ModCompatManager.scanModdedKeyMappings()`), so EMI is an empty set in ControlFlex's mod-compat list — and, for the same reason, invisible in vanilla Options → Controls → Key Binds. Players cannot trigger view-recipe, favorite, or crafting from a controller.

Research notes: `control-flex-1.20.1/docs/research/research-emi-compat-bridge.md`. This design is the implementation-ready version of that research, with one of its conclusions corrected (see D2).

## 2. Goals

**v1 ships the action layer only: press a controller button, and the thing actually happens inside EMI.**

- 22 EMI actions can be bound, rebound, and layered in ControlFlex;
- Actions execute correctly inside EMI screens (view recipe/uses, favorite, crafting, hiding, cheat-give, back/forward, search control);
- Forge and Fabric behave identically;
- No changes to ControlFlex itself, no mixins.

## 3. Non-goals

- **Rebinding EMI's own keys.** A ControlFlex binding does not write back to EMI's QDCSS config. What the player changes is "which controller button fires which action", not "what EMI's R key is".
- **Three leftover actions:** `defaultStack`, `deleteCursorStack`, `showCraft`. The first two are inline logic inside `RecipeScreen` / `mouseReleased`; opening a mixin for them is not worth it. `showCraft` is a *held state* polled via `isHeld()`, a different semantic class from the other 22 instantaneous actions.
- **`displayAllRecipes`:** the EMI 1.1.24 source tree has this `EmiBind`, but the released jar does not (25 fields measured). The bridge tracks the release, so it is not exposed.
- **Presentation polish:** guide glyphs ("press X to view recipes"), radial-menu icons, target fallback when there is no cursor. Deferred to v2.
- **NeoForge / 1.21.** This branch is 1.20.1 forge + fabric only.
- **Changes to ControlFlex or control-flex-api.**

## 4. Anti-goals (works technically, still a failure)

- Requires hand-editing JSON or config files to function.
- Double-fires against EMI's own binds (one press, two executions).
- Throws or spams logs when an action is pressed outside an EMI screen.
- The bridge is installed but ControlFlex shows 22 dead keys that do nothing.

## 5. Constraints

- **No mixins.** EMI and ControlFlex already expose what we need (`EmiScreenManager.stackInteraction` / `genericInteraction` / `recipeInteraction` / `toggleVisibility` are all `public static`).
- **Cross-mapping.** EMI's xplat artifact is Yarn-named on Fabric (`MinecraftClient`, `InputUtil`) and Mojmap-named on Forge (`Minecraft`, `InputConstants`). `common/src/main/java` is added as a `srcDir` to each loader subproject and therefore **compiled twice**, so it must contain no EMI or MC types.
- **Client-only mod** (`environment: client`); no server side.
- **EMI is a hard dependency**; the mod is meaningless without it.

## 6. Key decisions

### D1 — Register pseudo `KeyMapping`s so ControlFlex discovers them

`scanModdedKeyMappings()` filters only vanilla names and the `key.controlflex.` prefix; everything else is collected, and `registerDynamicActions()` registers each as a `groupKey:keyName` dynamic action **without requiring a compat JSON**.

Registering 22 `KeyMapping`s is therefore enough for ControlFlex to pick them up, with zero changes on the ControlFlex side. It is the only path to the binding list without patching ControlFlex.

### D2 — Trigger from `KeyMapping` state, not `IActionStateProvider`

The research assumed polling `isGuiActionActive(actionId)`. **That is wrong.**

In `BindingMapper`'s GUI phase, only `HOLD` / `TOGGLE` / `DELAY_HOLD` / `LONG_HOLD` / `DOUBLE_HOLD` / `DOUBLE_TOGGLE` add the action to `activeGuiActions`. `PRESS` / `TAP` / `RELEASE` / `LONG_PRESS` / `DELAY_PRESS` / `DOUBLE_PRESS` / `DOUBLE_TAP` only call `addPendingGuiRelease()`, so `isGuiActionActive()` is always false for them. A controller's most natural mapping (TAP) would never work.

Instead we use something ControlFlex always does: `GuiActionHandler.handleDynamicCompatAction()` unconditionally calls `controlflex$setPressed(true)` (except for the virtualKbm channel). So:

- `KeyMapping.isDown()` → held;
- `KeyMapping.consumeClick()` → one-shot click.

Note that `controlflex$setPressed(true)` is `setDown(true)` plus an **unconditional** `clickCount++` (every call increments, with no transition check), so under HOLD mode `clickCount` grows every tick. The detector must look at the rising edge of `isDown` first, use `clicked` only as a fallback for "the press already ended but happened within this frame", and must drain `clicked` every frame (`consumeClick()` consumes on read).

Also, `processPendingGuiReleases` documents that release happens on a **subsequent tick** ("The release must be dispatched on a subsequent tick"), so `isDown` is still true on the press frame — the rising-edge path is the primary one and `consumeClick()` is the safety net.

Combining both signals covers every trigger mode and drops the dependency on `IActionStateProvider`. The bridge's only ControlFlex API dependency becomes `ICompatAssetInstaller` (0.8.5+).

### D3 — Call EMI's dispatchers with a predicate; do not inject keys

Signatures such as `EmiScreenManager.stackInteraction(EmiStackInteraction, Function<EmiBind, Boolean>)` accept a predicate that matches exactly one bind, so we reuse EMI's complete behaviour branches (crafting, cheat, hiding) as-is.

This is better than injecting keys because it does not depend on `EmiInput.getCurrentModifiers()` reading real GLFW state (`Screen.hasControlDown()` / `InputUtil.isKeyPressed`) and is not constrained by scancode matching. The focus-yield rule is not left to the host screen's `keyPressed`; it is reproduced explicitly in D7.

### D4 — All actions belong to the GUI layer

EMI only means anything with a screen open. ControlFlex resolves unconfigured dynamic actions to the `MAIN` layer (the `isEmpty()` branch of `resolveDefaultLayers()`) and never dispatches them in the GUI phase. The bridge must therefore ship a compat JSON that lists all 22 keys under `screenKeys` and declares `channels: ["keyMapping"]` explicitly. `ModCompatConfig.DEFAULT_SCREEN` is `{keyMapping, screenInput}` — without the declaration, ControlFlex *will* take the `screenInput` channel and push `UNKNOWN_KEY` (code -1) into `screen.keyPressed`.

### D5 — Own keybinding namespace

Do not reuse EMI's `key.emi.*` strings as `KeyMapping` names. `KeyMapping` names are global identifiers (Forge's `KeyMapping.MAP` is keyed by name); if EMI ever registers bindings with the same names, the two collide and ours silently dies.

Use `key.cfx_compat_emi.<action>` plus our own lang files (22 entries + 1 category — negligible cost); the category is likewise `key.categories.cfx_compat_emi`. **The group is not decided by the prefix**: ControlFlex groups by the compat JSON's `mod_id` (its ownership index), so these keys still appear under "EMI". The prefix only avoids colliding with any `KeyMapping` EMI registers in the future.

### D6 — `common` carries all mapping-dependent code (corrected during implementation)

The plan was "`common` holds mapping-agnostic code, EMI calls are duplicated per loader". **That premise did not hold**: Fabric also uses `loom.officialMojangMappings()` (matching `cfx-compat-create`), so both loaders see identical MC/EMI type names; two copies would add drift without value.

Final layout: `common` holds `EmiKeys` / `EmiDispatch` / `EmiTicker` (EMI and MC types included); each loader keeps only registration and the tick hook (~25 lines). The cost is that `common` is now mapping-bound: if a loader ever moves to Yarn, those three files must move back into the loader trees.

### D7 — Mirror EMI's own gates; do not get clever

ControlFlex's philosophy is "a controller button is a command, not input" (blur the text field, then send the key). This bridge **does not** adopt that: pressing a controller button means "perform one of EMI's actions on my behalf". If EMI itself would ignore that key, forwarding it anyway produces the absurd case of typing in an anvil while the recipe viewer flips pages.

Gate order therefore matches `EmiScreenManager.keyPressed()` exactly:

1. No screen / `EmiScreenBase.getCurrent()` empty → drop;
2. `EmiScreenManager.isDisabled()` (i.e. `!EmiReloadManager.isLoaded() || !EmiConfig.enabled`) → only `toggleVisibility` passes, everything else is dropped. This covers both "EMI switched off" and "EMI reloading", so the reload window cannot throw repeatedly and fill the log;
3. Any `isActive() && visible` `TextFieldWidget` in the screen element tree → drop everything. EMI's `hasFocusedTextField(screen, 10)` is private, so each loader reimplements it: recurse through `ParentElement.children()`, depth 10.

State the side effect plainly: while typing in an anvil rename box, a sign, a book, or another mod's input field, bound actions do not fire — same behaviour as EMI's own keybinds.

### D8 — `controlflex` is a hard dependency

The bridge is meaningless without ControlFlex, so follow the ecosystem convention: Forge `mods.toml` with `mandatory=true`, `ordering="AFTER"`, `versionRange="${controlflex_version_range}"`; Fabric `fabric.mod.json` `depends` with the same range. No runtime `isModLoaded("controlflex")` check (the loader already guarantees it), and no "loads without ControlFlex" branch.

### D9 — Recipe context is the union of both sources

`EmiApi.getRecipeContext(EmiIngredient)` only returns non-null for `EmiFavorite`, so using it alone turns `copyId` into a dead key on recipe screens. `EmiStackInteraction.getRecipeContext()` is public and returns non-null both for stacks hovered on a recipe screen and for sidebar favourites.

So `RECIPE`-kind actions resolve context as: `hovered.getRecipeContext()` first, falling back to `EmiApi.getRecipeContext(hovered.getStack())`.

**Measured correction:** only sidebar favourites (and craft-result slots that carry a recipe context) return non-null. `RecipeScreen` is not a `HandledScreen`, so `EmiApi.getHoveredStack()` there falls back to `lastStackTooltipRendered` with a null recipe; EMI itself uses the private `group.recipe`, unreachable without mixins. **`copyId` is therefore a no-op on recipe screens**; v1 accepts this and says so in the README.

## 7. Architecture

```
cfx-compat-emi/
├── settings.gradle                 include 'forge', 'fabric'
├── gradle.properties               mod metadata + versions
├── common/src/main/java/com/ifels/cfx/emi/
│   ├── CfxEmiPlugin.java           IControlFlexPlugin: exports the compat JSON
│   ├── EmiAction.java              action table (id / bindKey / kind)
│   ├── ActionKind.java             STACK / STACK_THEN_RECIPE / RECIPE / GENERIC
│   ├── EdgeDetector.java           press/click dedup state machine (plain Java)
│   ├── EmiKeys.java                22 KeyMappings + lookup table
│   ├── EmiDispatch.java            EmiConfig / EmiScreenManager calls (predicate dispatch)
│   └── EmiTicker.java              per-frame sampling + gates + firing
├── common/src/main/resources/
│   ├── assets/cfx_compat_emi/compat/cfx_compat_emi.json
│   ├── assets/cfx_compat_emi/lang/{en_us,zh_cn}.json
│   └── META-INF/services/com.ifels.controlflex.api.IControlFlexPlugin
├── forge/src/main/java/com/ifels/cfx/emi/forge/
│   └── CfxEmiMod.java              @Mod: RegisterKeyMappingsEvent + ClientTickEvent.Post
└── fabric/src/main/java/com/ifels/cfx/emi/fabric/
    └── CfxEmiMod.java              ClientModInitializer: KeyBindingHelper + END_CLIENT_TICK
```

### 7.1 `CfxEmiPlugin` (common)

Implements `IControlFlexPlugin` and does exactly one thing: in `onInstallCompatConfigs(ICompatAssetInstaller)`, install `/assets/cfx_compat_emi/compat/cfx_compat_emi.json` as `mods/cfx_compat_emi.json`.

Implementation note: v1 calls `installer.install(...)` directly instead of copying epicfight's SHA-1 gate, so ControlFlex rewrites an identical file on each launch. Acceptable (a few dozen bytes, no side effects); add `needsOverwrite` if the file ever grows.

`getModId()` returns `cfx_compat_emi`; `onControlFlexReady()` logs one line (v1 needs no runtime service from ControlFlex).

SPI file: `META-INF/services/com.ifels.controlflex.api.IControlFlexPlugin`.

### 7.2 `EmiAction` (common)

An enum with 22 entries and three fields:

| Field | Meaning |
|---|---|
| `id` | ControlFlex action name, `key.cfx_compat_emi.<name>` |
| `bindKey` | Name of the corresponding `EmiBind` field (`viewRecipes`, …), used only by the per-loader switch |
| `kind` | Dispatch category, see §8.4 |

Keeping `id` and `bindKey` separate lets `common` avoid referencing EMI types while giving the per-loader switch a stable key.

### 7.3 `EdgeDetector` (common)

One instance per action, a pure state machine:

```java
public boolean update(boolean isDown, boolean clicked) {
    boolean fire = (isDown && !lastDown) || (!isDown && clicked);
    lastDown = isDown;
    return fire;
}
```

- Held (HOLD mode): `isDown` stays true, fires on the first frame only;
- Tap (TAP/PRESS mode): if press and release happen between two samples, `isDown` is already false but `consumeClick()` is true → fire;
- Two taps within one tick: `clickCount` reaches 2 but `consumeClick()` returns true once → fires once (acceptable).

The caller **must drain `clicked` every frame** (`consumeClick()` consumes on read), otherwise gated-out frames accumulate clicks that fire later by surprise.

## 8. Detailed design

### 8.1 Key registration

- 22 `KeyMapping`s: name `key.cfx_compat_emi.<snake_case bindKey>`, category `key.categories.cfx_compat_emi`, default key `InputConstants.UNKNOWN_KEY` (`InputConstants.UNKNOWN`).
- An empty default is deliberate: a pseudo key bound to a real key would also fire on real keystrokes and could double-fire against EMI's own binds.
- **`controlflex` is guaranteed present by the loader** (D8); no runtime `isModLoaded` check. The mod itself is client-only, so no extra dist check is needed.
- Registration point: Forge `RegisterKeyMappingsEvent`; Fabric `KeyBindingHelper.registerKeyBinding` (in `ClientModInitializer`).
- Ordering is safe: ControlFlex scans during deferred init (`ModCompatManager.initialize()`), after every mod has registered its keys.
- On Forge, a directly constructed `KeyMapping` with a custom category enters `KeyBindsScreen`'s `Arrays.sort`; ControlFlex's `KeyMappingAccessorMixin` already rewrites `compareTo` to tolerate categories missing from `CATEGORY_SORT_ORDER`, so there is no NPE (on Fabric, `KeyBindingHelper` compensates via `addCategory`). That dependency is covered by the hard dependency too.

### 8.2 Trigger loop

Forge: `ClientTickEvent.Post`; Fabric: `ClientTickEvents.END_CLIENT_TICK`. Every frame:

```
Screen screen = mc.screen;
if (screen == null) { drainAll(); resetAll(); wasInEmi = false; return; }
if (EmiScreenBase.getCurrent().isEmpty()) { drainAll(); resetAll(); wasInEmi = false; return; }
if (!wasInEmi) { drainAll(); resetAll(); wasInEmi = true; }   // first frame inside EMI: drop stale presses

boolean disabled = EmiScreenManager.isDisabled();
boolean focusedField = !disabled && hasFocusedTextField(screen);  // disabled: the toggle is checked first

List<EmiAction> fire = new ArrayList<>();
for (EmiAction action : EmiAction.values()) {
    KeyMapping km = keyMap.get(action);
    boolean down = km.isDown();
    boolean clicked = km.consumeClick();
    if (!down && !clicked) continue;                 // fast path for most frames

    if (disabled && action != EmiAction.TOGGLE_VISIBILITY) continue;
    if (focusedField) continue;

    if (detectors[action].update(down, clicked)) fire.add(action);
}
if (fire.isEmpty()) return;

EmiScreenManager.recalculate();                      // mirror EMI's keyPressed prologue
for (EmiAction action : fire) dispatch(action);      // see 8.4
```

Three helpers:

- `drainAll()`: call `consumeClick()` once per key so clicks made outside EMI screens do not pile up and all fire the next time an EMI screen opens.
- `resetAll()`: reset `lastDown` on all 22 `EdgeDetector`s. **This one is required**: if the button is already held when the screen opens (say the same button opens the inventory), the first frame sees `isDown == true` with `lastDown == false` and fires a phantom action.
- `recalculate()` runs at most once per frame, never inside the loop.

### 8.3 Gates

Evaluated in order, short-circuiting; a failed gate discards the input:

| Gate | Test | Rationale |
|---|---|---|
| Screen open | `mc.screen != null` | EMI only works inside screens |
| EMI present | `!EmiScreenBase.getCurrent().isEmpty()`, or the screen is a `BoMScreen` (recipe tree) | `keyPressed` bails on an empty base; the recipe tree never appears in `EmiScreenBase.of()`, so without the exception even "back" is dead inside it |
| EMI ready | when `EmiScreenManager.isDisabled()`, only `toggleVisibility` passes | Mirrors the branch order: with EMI off or reloading, only the toggle works — otherwise a controller could not turn EMI back on |
| No focused text field | no `isActive() && visible` `TextFieldWidget` in the screen element tree, and `EmiApi.isSearchFocused()` is false | EMI's search box is hand-rendered and **not in the element tree**; walking the tree alone misses it |

All four gates match EMI's own keybind behaviour (see D7). `EmiApi.isSearchFocused()` is no longer used on its own: EMI's search box is itself a `TextFieldWidget` in that tree and is covered by the fourth gate.

Not gated, but they fail silently by EMI's own semantics (no extra handling): `hideStack` needs `EmiConfig.editMode`; `cheat*` need creative mode plus `SidebarEmiStackInteraction`; `craft*` need a stack hovered in the sidebar. All return false and throw nothing.

### 8.4 Dispatch routing

| kind | Call |
|---|---|
| `STACK` | `EmiScreenManager.stackInteraction(EmiApi.getHoveredStack(true), b -> b == EmiConfig.<bindKey>)` |
| `STACK_THEN_RECIPE` | `STACK` first; if false and `resolveRecipeContext(hovered) != null`, then `recipeInteraction(ctx, ...)` (used by `favorite`) |
| `RECIPE` | `EmiScreenManager.recipeInteraction(resolveRecipeContext(hovered), b -> b == EmiConfig.<bindKey>)`, where `resolveRecipeContext` prefers `hovered.getRecipeContext()` and falls back to `EmiApi.getRecipeContext(hovered.getStack())` (D9) |
| `GENERIC` | `EmiScreenManager.genericInteraction(b -> b == EmiConfig.<bindKey>)` |

> **Implementation correction:** v1 has no `API` kind. EMI 1.1.24's released jar has no `displayAllRecipes` bind (the source tree does, the release does not), so "show all recipes" is not in the action table; `EmiApi.displayAllRecipes()` still exists but is out of v1 scope.

Everything runs on the client thread, from a rendering/event-safe context. Return values are ignored (v1 gives no UI feedback for a failed action).

### 8.5 Action table (22)

| # | bindKey | kind | Notes |
|---|---|---|---|
| 1 | `toggleVisibility` | GENERIC | Toggle EMI |
| 2 | `focusSearch` | GENERIC | |
| 3 | `clearSearch` | GENERIC | |
| 4 | `viewRecipes` | STACK | |
| 5 | `viewUses` | STACK | |
| 6 | `favorite` | STACK_THEN_RECIPE | Falls back to recipe-level favorite |
| 7 | `viewStackTree` | STACK | Needs a `recipeContext` |
| 8 | `viewTree` | GENERIC | |
| 9 | `back` | GENERIC | |
| 10 | `forward` | GENERIC | |
| 11 | `craftOne` | STACK | Only meaningful on sidebar stacks |
| 12 | `craftAll` | STACK | Same |
| 13 | `craftOneToInventory` | STACK | Same |
| 14 | `craftAllToInventory` | STACK | Same |
| 15 | `craftOneToCursor` | STACK | Same |
| 16 | `cheatOneToInventory` | STACK | Creative only |
| 17 | `cheatStackToInventory` | STACK | Creative only |
| 18 | `cheatOneToCursor` | STACK | Creative only |
| 19 | `cheatStackToCursor` | STACK | Creative only |
| 20 | `hideStack` | STACK | Needs `EmiConfig.editMode` |
| 21 | `hideStackById` | STACK | Same |
| 22 | `copyId` | RECIPE | Only where a recipe context exists, e.g. sidebar favourites (D9) |

### 8.6 Compat JSON

`/assets/cfx_compat_emi/compat/cfx_compat_emi.json` → installed to `config/controlflex/compat/mods/cfx_compat_emi.json`:

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
        "key.cfx_compat_emi.view_recipes": {},
        "...": {}
      }
    }
  ]
}
```

Notes:

- `mod_id` is `emi`: the config declares "these are EMI's actions". Rule ① of `deriveGroupKey` uses the config's ownership index (`compatOwnedKeyToModId`) to attribute `key.cfx_compat_emi.*` to `emi`, so the 22 actions show up under the **EMI** group (display name taken from the mod list) — the discoverable place a player looks for controller bindings.
- The config arrives late (the bridge exports it during `discoverPlugins()`), so ControlFlex then calls `reattributeModIdLessKeys()`, migrating the already-registered `cfx_compat_emi:*` ids to `emi:*` and moving saved bindings with them (`BindingRegistry.reattributeActionId`) — no rebinding needed.
- The file stays named `cfx_compat_emi.json`, not `emi.json`: the installer overwrites unconditionally, so a future official/community `emi.json` with the same name would clobber ours; different names coexist and load order decides (see the §12 risk row).
- `channels: ["keyMapping"]` is declared explicitly so ControlFlex does not take the `screenInput` channel and push `UNKNOWN_KEY` into `screen.keyPressed`.
- No `ignoreKeys`: all 22 keys are implemented, nothing to hide.
- No `dependencies`: this config is for the bridge itself.

### 8.7 Language resources

`assets/cfx_compat_emi/lang/en_us.json` and `zh_cn.json`: 22 action names plus `key.categories.cfx_compat_emi`. Wording should match EMI's own terminology ("View Recipes" / "查看配方") so players can cross-reference EMI's Binds screen.

### 8.8 Version gating

- Loader metadata hard-depends on `controlflex` ≥ 0.8.9 and `emi` (any 1.20.1 build). Both are mandatory: the loader refuses to start the bridge, and there is no runtime `isModLoaded` branch (D8).
- **Range syntax differs per loader:** Forge's `mods.toml` uses Maven ranges (`[0.8.9,)`); Fabric's loader **does not support bracket ranges** — `VersionPredicateParser` understands only `>=` / `<=` / `<` / `>` / `~` / `^` / `=` plus space-separated terms. `[0.8.9,)` is parsed as a literal version and compared with `EQUAL`, which can never match `0.8.9`. `fabric.mod.json` therefore uses `>=0.8.9` / `>=1.1.0`, fed by `controlflex_version_range_fabric`.
- No extra runtime version check in v1 (the only API surface used is `ICompatAssetInstaller`, stable since 0.8.5; the real risk is on EMI's side, see §12).

### 8.9 Logging

Prefix `[cfx-compat-emi]`. Log at four points only: how many keys were registered, whether ControlFlex is ready, whether the compat JSON was installed or skipped (SHA-1 match), and dispatch exceptions (`catch Throwable`, warn and drop — never let the tick crash).

## 9. Sequence: one press

```
Controller X
  └─ ControlFlex BindingMapper (GUI phase, TAP)
       ├─ GuiActionHandler.handleDynamicCompatAction("cfx_compat_emi:key.cfx_compat_emi.view_recipes")
       └─ controlflex$setPressed(true)  →  clickCount++
Bridge tick
  ├─ sample isDown() / consumeClick()
  ├─ gates: screen → EMI present → search not focused
  ├─ EdgeDetector → fire
  ├─ EmiScreenManager.recalculate()
  └─ stackInteraction(EmiApi.getHoveredStack(true), b -> b == EmiConfig.viewRecipes)
       └─ EmiApi.displayRecipes(ingredient) + focusRecipe(context)
```

## 10. Project layout and build

- `settings.gradle` mirrors `cfx-compat-create`: `include 'forge', 'fabric'`, plugins `net.neoforged.moddev.legacyforge` + `fabric-loom`.
- Both loader subprojects pull `common` into their own source set: `main.java.srcDir(rootProject.file('common/src/main/java'))` plus `main.resources.srcDir(rootProject.file('common/src/main/resources'))`, so the JSON and lang files exist only once.
- Fabric uses `fabric-loom 1.7-SNAPSHOT` (EMI's artifact requires ≥1.7.435); Forge uses `net.neoforged.moddev.legacyforge 2.0.141`.
- Dependencies:
  - `compileOnly "com.github.ControlFlexMC:control-flex-api:${controlflex_api_version}"` (JitPack)
  - Forge: `compileOnly fg.deobf("dev.emi:emi-forge:${emi_version}")` (from `repo.sleeping.town`)
  - Fabric: `modCompileOnly "dev.emi:emi-fabric:${emi_version}"` plus `modLocalRuntime` for local runs
- Key `gradle.properties` entries: `mod_id=cfx_compat_emi`, `mod_name=Control Flex x EMI`, `minecraft_version=1.20.1`, `emi_version=1.1.24+1.20.1`, `controlflex_version_range=[0.8.9,)`.
- No mixin config, no refmap, no `mixins.json`.
- Tests for `EdgeDetector` live in each loader's `src/test` (same trick as `cfx-compat-create`: `compileClasspath += sourceSets.main.output`).

## 11. Testing and acceptance

**Unit:** four `EdgeDetector` paths — held for several frames fires once, a tap spanning frames fires, no input fires nothing, two clicks in one frame fire once.

**In-game acceptance (run on both Forge and Fabric):**

1. ControlFlex settings show a "Control Flex x EMI" group with exactly 22 entries, no duplicates, no raw `key.cfx_compat_emi.*` strings leaking.
2. Bind "View Recipes" to X, open the inventory, hover an item, press X → recipe screen opens.
3. Hover a stack in EMI's sidebar, bind "Craft a stack", press once → crafting executes.
4. Bind "Favorite", press once → the item shows up in the favorites sidebar.
5. With the search field focused, press any action → nothing fires (same as EMI's own keys); it works again once the field loses focus.
6. In-game (no screen), press an action → nothing happens and nothing spams the log.
7. Launch without ControlFlex → the loader reports the missing dependency and refuses to load the bridge; no keys are registered.
8. Uninstall the bridge and launch → no crash; the leftover names in `options.txt` are ignored.
9. Turn EMI off in its config, or trigger a reload → nothing fires except "toggle EMI", and the log stays free of warnings.
10. Press an action while typing in an anvil rename box or on a sign → nothing fires.

## 12. Risk register

| Risk | Impact | Handling |
|---|---|---|
| EMI changes `EmiScreenManager` / `EmiConfig` signatures | Compile error, or `NoSuchMethodError` at runtime | Bridge versions track EMI; `dispatch` wraps everything in `catch Throwable` |
| `getHoveredStack` depends on `lastMouseX/Y` | Actions do nothing without a cursor | Accepted in v1 (ControlFlex ships a virtual cursor by default); docs say "point at the item first" |
| Player binds with HOLD mode | Fires every tick (EMI would stack screens) | `EdgeDetector` fires only on the first frame of a hold, so this is handled |
| ControlFlex changes GUI dispatch semantics (no more `setPressed`) | The trigger chain breaks | Acceptance case 2 is the regression sentinel; if needed, add a stable contract on the ControlFlex side |
| 22 keys pollute `options.txt` | Leftovers after uninstall | Accepted; documented |
| `EmiConfig.editMode` / creative gates | Actions fail silently, players get confused | Accepted in v1; v2 adds "action unavailable" feedback |
| EMI's Forge compile coordinates are uncertain | Build gets stuck | `NeoGradle legacyforge` may not support `fg.deobf`; fall back to `dev.emi:emi-xplat-mojmap` (EMI's README lists it for MultiLoader / VanillaGradle). Verify in the very first implementation step |
| One button bound to two EMI actions | Both execute | Same semantics ControlFlex gives every action; no dedup, documented |
| A future official/community `emi.json` | Two configs contend for `compatConfigs["emi"]`; the loser is dropped, and if we lose, the 22 actions fall back to MAIN and die silently | Different file name lowers the odds; the `[ModCompat] Re-attributed key` log and the group header make it diagnosable; falling back to our own group key is the escape hatch |

## 14. Review log

### Round 1 (API / fact check, 2026-10-31)

| ID | Finding | Action |
|---|---|---|
| R1.1 | `ModCompatConfig.DEFAULT_SCREEN` = `{keyMapping, screenInput}`; omitting `channels` under `screenKeys` always takes `screenInput` | D4 changed from "may" to a definite statement, with the constant cited |
| R1.2 | `controlflex$setPressed(true)` = `setDown(true)` plus an **unconditional** `clickCount++`, so HOLD mode grows it every tick | D2 wording fixed (the earlier "increments only on transitions" was wrong); detector order fixed to "rising edge first, `clicked` as fallback" |
| R1.3 | `processPendingGuiReleases` documents that release happens on a subsequent tick | D2 notes that the rising edge is the primary path and `consumeClick()` is the safety net |
| R1.4 | How to wire EMI's Forge artifact is unclear (`legacyforge` vs `fg.deobf`) | Added to the risk register; the first plan task is proving dependency resolution |
| R1.5 | ControlFlex's `KeyMappingAccessorMixin` rewrites `compareTo` to tolerate custom categories missing from `CATEGORY_SORT_ORDER` | Noted in §8.1: a directly constructed `KeyMapping` with a custom category cannot NPE on Forge, given ControlFlex is present (guaranteed by the hard dependency) |

### Round 2 (design / edge cases, 2026-10-31)

| ID | Finding | Action |
|---|---|---|
| R2.1 | Button already held before an EMI screen opens (same button opens the inventory) → phantom fire on the first frame | §8.2 adds `drainAll()` + `resetAll()` on entering an EMI screen |
| R2.2 | Several actions firing in one frame called `recalculate()` repeatedly | At most once per frame |
| R2.3 | With EMI disabled, still checking "EMI present" and "no focused field" would leave players unable to turn EMI back on with a controller | Gate order rewritten: `base.isEmpty()` first, `isDisabled()` passes only the toggle and skips the focus check — matching EMI's branch order |
| R2.4 | One button bound to two EMI actions fires both | Accepted as ControlFlex's uniform semantics; no dedup, added to the risk register |
| R2.5 | `toggleVisibility` goes through `genericInteraction` while EMI is enabled, so it is also subject to the focus gate | Noted in §8.3: that is EMI's own behaviour, not a deviation of ours |

### Round 3 (implementation check, 2026-10-31)

| ID | Finding | Action |
|---|---|---|
| R3.1 | The **EMI 1.1.24 release jar** has only 25 `EmiBind` fields and no `displayAllRecipes` (the `research/emi` checkout is ahead of the release) | Action table 26 → 25 → 22; dropped `DISPLAY_ALL_RECIPES` and `ActionKind.API`; JSON / lang / tests updated |
| R3.2 | `EmiBind` lives in `dev.emi.emi.input`, not `.config` | Import fixed |
| R3.3 | Mojmap 1.20.1 has no `ParentElement`; the element-tree interface is `ContainerEventHandler` | `hasFocusedTextField` rewritten as `Screen.children()` + `ContainerEventHandler` recursion |
| R3.4 | 1.20.1's `KeyMapping` constructor only takes an `int` key code | Use `InputConstants.UNKNOWN.getValue()` |
| R3.5 | `compileOnly` does not reach `testCompileClasspath`, so the plugin test failed to compile | Both loaders add `testImplementation control-flex-api` |
| R3.6 | EMI's Fabric artifact is built with Loom 1.7.435; Loom 1.6 refuses to configure the project | `settings.gradle` bumped to `fabric-loom 1.7-SNAPSHOT` |
| R3.7 | Both loaders compile against Mojmap, so per-loader copies of the dispatch are pointless | D6 changed: `common` holds `EmiKeys` / `EmiDispatch` / `EmiTicker`; loaders keep only wiring |

### Round 4 (whole-branch review, 2026-10-31)

| ID | Finding | Action |
|---|---|---|
| R4.1 | **EMI's search box is not in the screen element tree** (its `addWidgets()` positions it without adding a child), so the tree scan never sees it — all 22 actions fire while the player types | Gate adds `EmiApi.isSearchFocused()`, extracted as `EmiGate.focusBlocks(...)` with 7 unit tests |
| R4.2 | The recipe tree (`BoMScreen`) never produces an `EmiScreenBase`, so even "back" was dead inside it | Gate adds a `BoMScreen` exception, extracted as `EmiGate.emiScreenActive(...)` |
| R4.3 | The entry frame only skipped one frame; a HOLD binding still misfired on the second (CF's suppression happened to cover it) | `EdgeDetector.seed()` records the real held state; the entry frame seeds without firing |
| R4.4 | `recalculate()` sat outside the try, and ran while EMI was reloading | Moved inside the try, only called when `!disabled` |
| R4.5 | A clean clone could not build: the wrapper jar was ignored by `*.jar`, and `org.gradle.java.home` pinned a local path | `.gitignore` un-ignores `gradle/wrapper/gradle-wrapper.jar`; the property became a comment, README explains JAVA_HOME |
| R4.6 | `copyId` has no recipe context on a recipe screen (`RecipeScreen` is not a `HandledScreen`) | No code change; D9 / action table / README now say "sidebar favourites only" |
| R4.7 | Only 1 of 4 logging points from §8.9 existed (install result discarded, no ready line) | Plugin logs install failure and readiness |

### Round 5 (real launch, 2026-11-01)

| ID | Finding | Action |
|---|---|---|
| R5.1 | The Fabric instance failed to start: `requires version [0.8.9,) of mod 'ControlFlex', but only the wrong version is present: 0.8.9` | Fabric's loader has no bracket ranges (its `VersionPredicateParser` only understands operator prefixes), so the bracket string is compared as a literal version for equality → switched to `>=0.8.9` / `>=1.1.0`; Forge's `mods.toml` keeps the Maven range. Repackaged and deployed to the 1.20.1-fabric instance |

### Round 6 (group attribution, 2026-11-01)

| ID | Finding | Action |
|---|---|---|
| R6.1 | Actions appeared under "Control Flex x EMI" while players intuitively look for controller bindings inside EMI | Compat JSON `mod_id` / `mod_name` / `home_page` switched to EMI; the group header comes from the `mod_id`'s mod display name, not from `mod_name`. ControlFlex's ownership index (rule ① of `deriveGroupKey`) plus `reattributeModIdLessKeys()` migrate the registered `cfx_compat_emi:*` ids — and their saved bindings — to `emi:*` |

## 13. Left to the implementer

- Class and method names may change, and `EmiAction`'s field split may change (as long as `common` holds no MC/EMI types).
- The exception strategy inside `dispatch` may be refined (v1 only requires "no crash, no log spam").
- Whether `EdgeDetector` should move into the per-loader code, if the two loaders turn out to need different sampling points.
- Whether to log unavailable actions beyond `forward` / `back` (v1 does not).
