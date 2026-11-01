# Changelog

## 0.8.8

- 22 EMI actions exposed to ControlFlex on Forge and Fabric (MC 1.20.1).
- Actions run through EMI's own interaction dispatchers (`stackInteraction`, `genericInteraction`,
  `recipeInteraction`), so behaviour matches EMI exactly.
- Gates mirror EMI's `keyPressed`: no screen, no EMI, EMI disabled/reloading, or a focused text
  field all suppress the action (except "Toggle EMI" while disabled).
