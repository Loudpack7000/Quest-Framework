# Quest API Integration Guide

## Overview
This document outlines how to integrate DreamBot's built-in quest API methods into our quest automation system for enhanced discovery and more robust implementation.

## DreamBot Quest API Methods

### Core Quest Detection
```java
// Instead of manual config checking
Quests.isStarted(FreeQuest.QUEST_NAME)     // Has quest started?
Quests.isFinished(FreeQuest.QUEST_NAME)    // Is quest complete?
```

### Quest State Information
```java
// Get detailed quest state
Quest.State state = FreeQuest.QUEST_NAME.getState();
// Returns: NOT_STARTED, IN_PROGRESS, FINISHED

// Check quest requirements
boolean canStart = FreeQuest.QUEST_NAME.hasRequirements();
```

### Available Free Quests
- `FreeQuest.DRAGON_SLAYER`
- `FreeQuest.THE_KNIGHTS_SWORD`
- `FreeQuest.PRINCE_ALI_RESCUE`
- `FreeQuest.PIRATES_TREASURE`
- `FreeQuest.GOBLIN_DIPLOMACY`
- And many more...

## Implementation Strategy

### Phase 1: Enhanced Discovery (QuestEventLogger.java)
- **Add API methods** for better quest detection during manual runs
- **Log quest states** and requirement validation
- **Capture progression** more accurately

### Phase 2: Robust Implementation (QuestTree.java)
- **Replace manual config checking** with API methods
- **Add requirement validation** before quest start
- **Implement automatic error handling**

## File Updates Required

### QuestEventLogger.java
- Add `Quests.isStarted()` checks
- Add `FreeQuest.getState()` logging
- Add `hasRequirements()` validation logging

### Individual Quest Trees
- Replace `PlayerSettings.getConfig()` with `Quests.isStarted/isFinished`
- Add requirement checking before quest execution
- Implement `isQuestComplete()` using API methods

## Benefits

1. **More Reliable Discovery** - Built-in quest detection
2. **Better Error Handling** - Automatic requirement validation
3. **Cleaner Code** - Less manual config management
4. **Future-Proof** - DreamBot handles quest changes internally

## Testing Strategy

### Step 1: Test with Short Quest
- Choose a simple quest (e.g., Goblin Diplomacy, Sheep Shearer)
- Run manually with enhanced logging
- Verify API methods work correctly

### Step 2: Implement Full Quest
- Use discovered progression data
- Implement with API integration
- Test automation thoroughly

### Step 3: Scale to Complex Quests
- Apply same pattern to Dragon Slayer
- Ensure robust error handling
- Validate all edge cases

## Code Examples

### Enhanced Quest Detection
```java
@Override
public void checkForEvents() {
    // Check all available free quests
    for (FreeQuest quest : FreeQuest.values()) {
        if (Quests.isStarted(quest)) {
            Quest.State state = quest.getState();
            log("QUEST_DETECTED: " + quest.name() + " - State: " + state);
            
            if (!quest.hasRequirements()) {
                log("QUEST_REQUIREMENTS: " + quest.name() + " - Missing requirements");
            }
        }
    }
}
```

### Quest Tree Implementation
```java
@Override
public boolean isQuestComplete() {
    return Quests.isFinished(FreeQuest.QUEST_NAME) || super.isQuestComplete();
}

@Override
protected void buildTree() {
    smart = new QuestNode("smart_decision", "Quest decision logic") {
        @Override
        public ExecutionResult execute() {
            // Check requirements first
            if (!FreeQuest.QUEST_NAME.hasRequirements()) {
                return ExecutionResult.failure("Missing quest requirements");
            }
            
            // Check quest status
            if (Quests.isFinished(FreeQuest.QUEST_NAME)) {
                return ExecutionResult.success(finishNode, "Quest complete");
            }
            
            if (!Quests.isStarted(FreeQuest.QUEST_NAME)) {
                return ExecutionResult.success(startNode, "Start quest");
            }
            
            // Continue with discovered progression logic...
        }
    };
}
```

## Next Steps

1. **Update QuestEventLogger.java** with new API methods
2. **Test with short quest** to verify functionality
3. **Implement Dragon Slayer** using discovered data + API integration
4. **Update existing quests** to use new API methods
5. **Document any issues** or improvements discovered

## Notes

- API methods are more reliable than manual config checking
- Requirement validation prevents quest failures
- State detection provides better progress tracking
- Integration maintains our existing quest architecture 