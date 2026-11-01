# Changelog

## 0.8.8 — MC 1.21.1 (Fabric + NeoForge)

- Same 22 EMI actions as the 1.20.1 branch, ported to 1.21.1 / Java 21, now on Fabric **and**
  NeoForge (1.21.1 has no Forge). Both loaders compile the same `common/` sources and run the same
  18-test suite.
- NeoForge wiring takes both event buses from the `ModContainer` injected into the `@Mod`
  constructor, avoiding 21.1's deprecated `@EventBusSubscriber(bus = …)`.
- `displayAllRecipes` is still absent from EMI's released `EmiConfig`, so the action table stays 22.

## 0.8.8 — MC 1.20.1 (Forge + Fabric)

- 22 EMI actions exposed to ControlFlex on Forge and Fabric (MC 1.20.1).
- Actions run through EMI's own interaction dispatchers (`stackInteraction`, `genericInteraction`,
  `recipeInteraction`), so behaviour matches EMI exactly.
- Gates mirror EMI's `keyPressed`: no screen, no EMI, EMI disabled/reloading, or a focused text
  field all suppress the action (except "Toggle EMI" while disabled).
