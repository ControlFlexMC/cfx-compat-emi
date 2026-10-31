# Changelog

## 0.1.0 — MC 1.19.2 (Forge + Fabric)

- 20 EMI actions exposed to ControlFlex on Forge and Fabric. EMI 1.0.9 — the last 1.19.2 release —
  has no `forward` or `copyId` bind and no recipe-level dispatcher, so favourites are stack-only
  here and those two actions are absent.
- The screen gate uses EMI's own `EmiScreen` marker interface, which `HandledScreen` gains through
  EMI's mixin and `RecipeScreen` / `BoMScreen` implement directly. EMI 1.0.9 has no
  `EmiScreenBase`, so there is no recipe-tree special case to make.
- Same architecture as the newer branches: pseudo `KeyMapping`s, per-tick sampling, and dispatch
  through `EmiScreenManager.stackInteraction` / `genericInteraction`.

## 0.1.0 — MC 1.20.1 (Forge + Fabric)

- 22 EMI actions exposed to ControlFlex on Forge and Fabric (MC 1.20.1).
- Actions run through EMI's own interaction dispatchers (`stackInteraction`, `genericInteraction`,
  `recipeInteraction`), so behaviour matches EMI exactly.
- Gates mirror EMI's `keyPressed`: no screen, no EMI, EMI disabled/reloading, or a focused text
  field all suppress the action (except "Toggle EMI" while disabled).
