# cfx-compat-emi

ControlFlex bridge mod for [EMI](https://modrinth.com/mod/emi). Exposes EMI's actions as bindable
ControlFlex actions, so a controller can drive the recipe viewer without touching a keyboard.

Requires **Minecraft 1.18.2**, **ControlFlex ≥ 0.8.8** and **EMI** (0.7.x). This branch is
**Fabric only**: EMI never published a Forge build for 1.18.2, and the bridge cannot run without
EMI.

## What it does

EMI stores its keybinds in its own config (`binds.*`) and matches them inside its screens — it
registers no Minecraft `KeyMapping`, which is why its actions never appear in vanilla Controls or
in ControlFlex's mod-key list.

This mod registers 18 pseudo key mappings that ControlFlex discovers, and forwards each press back
into EMI through EMI's own interaction functions. Nothing is injected as fake input, and EMI's
own key settings are left untouched.

| Group | Actions |
|---|---|
| Screen | Toggle EMI, Focus Search, Clear Search |
| Browse | View Recipes, View Uses, Add to Favorites, View Recipe Tree, View Stack Tree, Back |
| Craft | Craft One, Craft a Stack, to Inventory (one / stack), to Cursor (one) |
| Creative | Cheat One / a Stack to Inventory, Cheat One / a Stack to Cursor |

All eighteen live in the GUI layer and are listed under "EMI" in ControlFlex's binding settings.
EMI 0.7.3 — the last 1.18.2 build — has no edit mode, so "hide item" does not exist here, and it
also predates the "forward" and "copy recipe id" binds.

## Using it

1. Bind a controller button to an action in ControlFlex.
2. Open an EMI screen (inventory, recipe screen) and **point the cursor at the item first** — most
   actions act on the hovered stack.
3. Press the button.

Crafting, hiding and cheat-give actions only apply to stacks hovered in EMI's sidebar, which is
EMI's own rule.

The actions follow EMI's own gating: while a text field has focus (anvil rename box, sign, EMI's
search bar), or while EMI is switched off or reloading, nothing fires except "Toggle EMI".

## Not supported

- `defaultStack`, `deleteCursorStack` and `showCraft` — the first two are inline logic deep inside
  EMI's recipe screen, the third is a *held* state rather than an action.
- `hideStack` / `hideStackById` — EMI 0.7.3 has no edit mode, so there is nothing to bind.
- `forward` and `copyId` — those binds do not exist before EMI 1.1, and 0.7.3 has no recipe-level
  dispatcher either, so favourites here are stack-only.
- Rebinding EMI's own keys. ControlFlex bindings decide which controller button fires an action;
  they do not rewrite EMI's `binds.*` config.

## Building

```bash
./gradlew :fabric:build
```

The jar lands in `fabric/build/libs`. Sources compile against official Mojang mappings; Fabric uses
Loom 1.7. Gradle 8.8 runs on JDK 17–22 only — if your default JDK is newer, export `JAVA_HOME`
pointing at a supported one before invoking the wrapper.

## Licence

MIT.
