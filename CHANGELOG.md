# Changelog

## 0.1.0 — MC 1.21.1 (Fabric)

- Same 22 EMI actions as the 1.20.1 branch, ported to 1.21.1 / Java 21. Fabric only: 1.21.1 has no
  Forge, and NeoForge is not part of this branch.
- `displayAllRecipes` is still absent from EMI's released `EmiConfig`, so the action table stays 22.

## 0.1.0 — MC 1.20.1 (Forge + Fabric)

- 22 EMI actions exposed to ControlFlex on Forge and Fabric (MC 1.20.1).
- Actions run through EMI's own interaction dispatchers (`stackInteraction`, `genericInteraction`,
  `recipeInteraction`), so behaviour matches EMI exactly.
- Gates mirror EMI's `keyPressed`: no screen, no EMI, EMI disabled/reloading, or a focused text
  field all suppress the action (except "Toggle EMI" while disabled).
