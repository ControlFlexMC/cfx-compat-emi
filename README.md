# cfx-compat-emi

ControlFlex bridge mod for [EMI](https://modrinth.com/mod/emi). Exposes EMI's actions as bindable
ControlFlex actions, so a controller can drive the recipe viewer without touching a keyboard.

Requires **Minecraft 1.20.1**, **ControlFlex ≥ 0.8.9** and **EMI** (1.1.x). Forge and Fabric are
both supported from the same source.

## What it does

EMI stores its keybinds in its own config (`binds.*`) and matches them inside its screens — it
registers no Minecraft `KeyMapping`, which is why its actions never appear in vanilla Controls or
in ControlFlex's mod-key list.

This mod registers 22 pseudo key mappings that ControlFlex discovers, and forwards each press back
into EMI through EMI's own interaction functions. Nothing is injected as fake input, and EMI's
own key settings are left untouched.

| Group | Actions |
|---|---|
| Screen | Toggle EMI, Focus Search, Clear Search |
| Browse | View Recipes, View Uses, Add to Favorites, View Recipe Tree, View Stack Tree, Back, Forward |
| Craft | Craft One, Craft a Stack, to Inventory (one / stack), to Cursor (one) |
| Creative | Cheat One / a Stack to Inventory, Cheat One / a Stack to Cursor |
| Manage | Hide Item, Hide Item by ID, Copy Recipe ID |

All twenty-two live in the GUI layer and are listed under "Control Flex x EMI" in ControlFlex's
binding settings.

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
- `displayAllRecipes` — present in EMI's source tree but not in the released 1.1.24 jar.
- Rebinding EMI's own keys. ControlFlex bindings decide which controller button fires an action;
  they do not rewrite EMI's `binds.*` config.

## Building

```bash
./gradlew :forge:build :fabric:build
```

Jars land in `forge/build/libs` and `fabric/build/libs`. Both loaders compile against official
Mojang mappings; Fabric uses Loom 1.7 (EMI's artifact is built with it).

## Licence

MIT.
