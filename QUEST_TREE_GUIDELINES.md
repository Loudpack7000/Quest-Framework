# Quest Tree Implementation Guidelines (Resumable, Deterministic)

Use this checklist and pattern for every quest tree to ensure perfect resumability and consistent behavior.

## Core principles
- Source of truth first: Use DreamBot Quests API for started/finished state; use known quest varbit/config for phase details. Never rely solely on session flags.
- Deterministic decision order: Smart root decides by quest phase first, not by incidental items. Prefer location gating (go to the right area) before micro-requirements (apron/job/etc.).
- Idempotent nodes: Each node must be safe to call repeatedly and exit early via state checks (inventory, varbit/config, location).
- Location-aware flow: If you’re not in the correct region, walk/teleport first; then perform item/dialogue/object actions.

## Minimal contract per quest
1) Constants
- questName, configId/varbitId (if known), key tiles/areas, item names.

2) Smart root decision
- Read API: `Quests.isStarted(FreeQuest.X)`, `Quests.isFinished(FreeQuest.X)`; fallback to config/varbit when API fails.
- Compute: `started`, `finished`.
- If `finished` → mark complete.
- Else if `!started` → talk-to start NPC.
- Else decide next branch strictly by phase:
  - Check most-advanced artifacts first (e.g., casket/message/key) → corresponding branch.
  - Else check “location prerequisite” (e.g., needToTravelToArea()).
  - Else check “obtain items/do sub-steps” in-area.

3) Branch helpers
- Small, pure functions using: inventory, location, varbit/config. Avoid session-only flags; if you must cache, do not trust cache over authoritative checks.

4) Node design
- Implement `shouldSkip()` wherever relevant (item already owned, already in dialogue, already in area, object state open, etc.).
- Interactions are guarded with `Sleep.sleepUntil` when waiting for state changes; prefer existing nodes: `TalkToNPCNode`, `WalkToLocationNode`, `InteractWithObjectNode`, `UseItemOnObjectNode`.
- Avoid hard-coding ground items via GroundItems API unless available; prefer object interactions where possible.

5) Logging & debugging
- Log decisions at the smart root with reason strings.
- On failure, return retry/in-progress appropriately rather than forcing success.

## Example smart decision skeleton
```java
smart = new DecisionNode("smart", "Decide next step") {
  protected String makeDecision() {
    int cfg = PlayerSettings.getConfig(CONFIG_ID);
    boolean started = Quests.isStarted(FreeQuest.MY_QUEST) || cfg >= STARTED_VAL;
    boolean finished = Quests.isFinished(FreeQuest.MY_QUEST) || cfg >= COMPLETE_VAL;

    if (finished) return "COMPLETE";
    if (!started) return "START_DIALOGUE";

    if (Inventory.contains("Casket")) return "OPEN_CASKET";
    if (Inventory.contains("Message")) return "DIG_TREASURE";

    if (needToTravelToArea()) return "TRAVEL_AREA";
    if (needToCollectX()) return "COLLECT_X";

    return "FALLBACK";
  }
};
```

## Resumability smoke tests
- Start mid-quest with partial items; ensure the smart root goes to the correct branch without performing earlier steps.
- Log out/in and resume; ensure it re-detects started/finished/phase properly.
- Move to wrong area; ensure it first travels to the right area before micro-steps.

## Notes
- Keep “smart” narrow and declarative; push how-tos into nodes.
- Prefer tiles/areas captured by the recorder for consistent navigation.
