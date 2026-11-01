# Control Flex × EMI

A compatibility bridge that puts **EMI's recipe viewer on your controller** — view recipes, favorite, craft, hide and cheat-give, without touching a keyboard.

---

## What This Does

Without this mod, a controller **cannot drive EMI at all**.

EMI keeps its keybinds in its own system: every bind lives in EMI's `binds.*` config and is matched inside EMI's own screens (`matchesKey` / `matchesMouse`). EMI registers **no Minecraft `KeyMapping`** — not one.

ControlFlex builds its action pool by scanning `mc.options.keyMappings`. So EMI is an empty set in ControlFlex's mod list, and equally invisible in vanilla Options → Controls → Key Binds.

**The result: the keyboard drives EMI, the controller cannot.** Not because nothing is bound, but because there is nothing for ControlFlex to bind to.

This bridge registers **pseudo key mappings** that ControlFlex discovers on its own, and forwards each press into EMI through EMI's own interaction entry points.

**The result:** point the cursor at a stack, press a bound button, and EMI does exactly what it does when you press its own key.

---

## Features

- 🎮 **EMI's actions on the pad** — view recipes / uses, favorite, stack tree, recipe tree, back and forward.
- 🔍 **Search and toggle** — focus the search bar, clear it, switch EMI on and off.
- 🧪 **Crafting without a keyboard** — craft one / a stack, to inventory or to cursor.
- ✨ **Creative cheat-give** — one item or a stack, to inventory or to cursor.
- 🗑️ **Manage what you see** — hide an item, hide by ID, copy a recipe ID.
- 🎯 **Acts on the hovered stack** — EMI's own targeting rule, no extra logic layered on top.
- 🧩 **No fake input** — nothing is injected into key state, and no synthetic key press is pushed into a screen's `keyPressed`. EMI's own dispatchers do the work.
- 🎛️ **Fully rebindable** — every action appears under "Control Flex x EMI" in ControlFlex's binding settings, with the same trigger types and layers as any built-in key.
- 🛡️ **Respects EMI's own gating** — while a text field has focus, or EMI is switched off or reloading, nothing fires except "Toggle EMI".
- ⌨️ **Keyboard still works** — the bridge never writes EMI's `binds.*` config, so your keyboard setup is untouched.
- 🧱 **No mixins, no patching** — it only calls public EMI API; ControlFlex and EMI stay unmodified.

---

## Dependencies (Required)

Every Minecraft version is a separate JAR — pick the row that matches your game, then the JAR for your loader.

| Minecraft | Loaders | [ControlFlex](https://www.curseforge.com/minecraft/mc-mods/controlflex) | [EMI](https://modrinth.com/mod/emi) |
|:---------:|:--------:|:------:|:---:|
| 1.18.2 | Fabric | ≥ 0.8.8 | ≥ 0.7.3 |
| 1.19.2 | Forge · Fabric | ≥ 0.8.8 | ≥ 1.0.9 |
| 1.20.1 | Forge · Fabric | ≥ 0.8.8 | ≥ 1.1.0 |
| 1.21.1 | Fabric · NeoForge | ≥ 0.8.8 | ≥ 1.1.0 |

Built and tested against EMI 0.7.3 (1.18.2), 1.0.9 (1.19.2), 1.1.24 (1.20.1 and 1.21.1).

This is a **client-side** mod. EMI and ControlFlex are both required, in every version — the bridge does nothing without them.

---

## The Actions

| Group | Actions |
|---|---|
| Screen | Toggle EMI · Focus Search · Clear Search |
| Browse | View Recipes · View Uses · Add to Favorites · View Stack Tree · View Recipe Tree · Back · Forward |
| Craft | Craft One · Craft a Stack · Craft One to Inventory · Craft a Stack to Inventory · Craft One to Cursor |
| Creative | Cheat One to Inventory · Cheat a Stack to Inventory · Cheat One to Cursor · Cheat a Stack to Cursor |
| Manage | Hide Item · Hide Item by ID · Copy Recipe ID |

The list grows with EMI: **1.20.1** and **1.21.1** expose all 22 actions, **1.19.2** has 20 (no Forward, no Copy Recipe ID), and **1.18.2** has 18 (those two plus no Hide Item / Hide Item by ID) — the older EMI lines simply have no such feature to drive.

---

## Compatibility

- ✅ Works in singleplayer and multiplayer — client-side only, nothing is needed on the server.
- ✅ One source, identical behaviour on every loader — Forge · Fabric on 1.19.2 and 1.20.1, Fabric · NeoForge on 1.21.1, Fabric only on 1.18.2.
- ✅ Works alongside EMI's own keyboard binds. One button press fires the action once — no double execution.
- 📌 Crafting, hiding and cheat-give only apply to stacks hovered in **EMI's sidebar** — that is EMI's own rule, not something this bridge adds.
- 📌 Actions are bound in ControlFlex. This mod adds no key settings of its own.

---

## How It Works

```
a bound controller button
            ↓
ControlFlex fires the pseudo key   key.cfx_compat_emi.view_recipes
            ↓
cfx-compat-emi sees the down-edge on the client tick
            ↓
EMI's own dispatcher   EmiScreenManager.stackInteraction(...)
            ↓
EMI behaves exactly as if its own key had been pressed
```

ControlFlex *writes* key state rather than firing key events, so the bridge watches its pseudo keys for a down-edge each client tick and routes that edge into the matching EMI call — `stackInteraction` for stack actions, `genericInteraction` for navigation and search, `recipeInteraction` for recipe-scoped ones.

The bridge also ships a small compat config that lists every action key under ControlFlex's **GUI layer**. Without it ControlFlex would keep them on the MAIN layer and never dispatch them while a screen is open — the keys would look bound and do nothing.

---

## Not Supported

- `defaultStack`, `deleteCursorStack` and `showCraft` — the first two are inline logic deep inside EMI's recipe screen; the third is a *held* state rather than a one-shot action.
- `displayAllRecipes` — present in EMI's 1.20.1 source tree but not in the released 1.1.24 jar.
- **"Copy Recipe ID" on a recipe screen** — EMI keeps that context in a private field. It works from sidebar favourites instead.
- **Rebinding EMI's own keys.** A ControlFlex binding decides *which controller button* fires an action; it does not rewrite EMI's `binds.*`.

---

## Notes

- This mod is **client-side only** — it does not need to be installed on servers, and it will not affect anything server-side.
- If an action seems dead, check that an EMI screen is actually open and the cursor is over the stack you mean — EMI ignores the same press on a keyboard.
- Install the JAR that matches both your Minecraft version and your loader (Forge / Fabric / NeoForge).
