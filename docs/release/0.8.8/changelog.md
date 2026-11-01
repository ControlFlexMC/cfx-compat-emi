> [中文版](changelog.zh.md)

## 0.8.8 — First Release

Drive EMI's recipe viewer from the controller.

### Fixed

*   EMI keeps every key bind in its own config and registers no Minecraft `KeyMapping`, so Control Flex's action list never contained it: **the keyboard drove the recipe viewer, the pad did not**. The bridge registers 22 pseudo keys that Control Flex picks up on its own, and hands each press to EMI's own handlers — so the pad now does what the keyboard does.

### Added

*   **Browse** — view recipes and uses, favourite, stack tree, recipe tree, back and forward.
*   **Search and toggle** — focus the search bar, clear it, switch EMI on and off.
*   **Craft** — one or a stack, to the inventory or to the cursor.
*   **Creative** — cheat-give one item or a stack, to the inventory or to the cursor.
*   **Manage** — hide an item, hide by ID, copy a recipe ID.
*   **Fully rebindable** — all 22 sit together in Control Flex's binding settings, on the GUI layer, with the same trigger types and layers as any built-in key.
*   **EMI's own rules, not a copy** — a press goes through `stackInteraction` / `genericInteraction` / `recipeInteraction`, so crafting, hiding and cheat-give behave exactly as they do from the keyboard. Nothing is injected as fake input and EMI's `binds.*` is never written, so your keyboard setup is untouched and one press fires once.
*   **Both loaders, one source** — identical behaviour on Forge and Fabric; no mixins, no patched classes.

### Notes

*   Actions act on the stack under the cursor — EMI's own targeting rule. Point at the item, then press.
*   Crafting, hiding and cheat-give only apply to stacks hovered in **EMI's sidebar**.
*   While a text field has focus (EMI's search box, an anvil rename box, a sign), or while EMI is off or reloading, nothing fires except "Toggle EMI" — EMI's own behaviour, not a lockdown of ours.
*   "Copy Recipe ID" works from sidebar favourites; on a recipe screen EMI keeps that context private, so it is a no-op there.
*   `defaultStack`, `deleteCursorStack` and `showCraft` are not bridged — the first two are inline logic inside EMI's recipe screen, the third is a held state rather than a press.

Requires **Control Flex 0.8.8+** and **EMI 1.1.0+** (tested on 1.1.24), on Minecraft 1.20.1 — Forge or Fabric. Client-side only.
