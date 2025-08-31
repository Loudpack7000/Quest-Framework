# DreamBot API Best Practices

This document contains proven best practices for using the DreamBot API effectively in quest automation and general scripting.

"Remember, we are building an automated OSRS quest system for DreamBot. My goal is a robust, reliable bot that properly progresses through quest stages identified by specific Config/Varbit values." 


---

## Banking API

### `Bank.open()` - Intelligent Bank Navigation ⭐

**What it does:**
- Automatically finds and navigates to the closest bank to the player's current location
- Opens the bank interface once arrived
- Handles all the complex pathfinding and navigation logic internally

**Why use it:**
- ✅ **Intelligent** - Always chooses the optimal bank based on location
- ✅ **Reliable** - Uses DreamBot's well-tested navigation system
- ✅ **Simple** - Single method call replaces 60+ lines of custom navigation
- ✅ **Efficient** - No more forced walks to hardcoded locations

**How to use it:**
```java
// Simple usage - DreamBot handles everything
if (!Bank.open()) {
    Logger.log("Failed to open bank");
    return false;
}

// Best practice: Wait for interface to be ready
if (!Sleep.sleepUntil(() -> Bank.isOpen(), 15000)) {
    Logger.log("Bank interface did not open within timeout");
    return false;
}

// Now bank is open and ready for operations
if (Bank.contains("Egg")) {
    Bank.withdraw("Egg", 1);
}
```

**❌ DON'T do this (old approach):**
```java
// Hardcoded bank location - inefficient!
private static final Area DRAYNOR_BANK = new Area(3092, 3240, 3098, 3246, 0);

// Manual navigation - complex and error-prone
if (!DRAYNOR_BANK.contains(Players.getLocal())) {
    Walking.walk(DRAYNOR_BANK.getRandomTile());
    Sleep.sleepUntil(() -> DRAYNOR_BANK.contains(Players.getLocal()), 40000);
}
// ... 60+ more lines of navigation logic
```

**✅ DO this (best practice):**
```java
// Let DreamBot handle navigation automatically
if (!Bank.open()) {
    return false;
}
Sleep.sleepUntil(() -> Bank.isOpen(), 15000);
```

**Key Benefits Observed:**
- **Cook's Assistant Script**: Successfully uses closest bank instead of forcing Draynor trips
- **Performance**: Significantly faster execution when starting from different locations
- **Reliability**: Zero navigation failures during testing
- **Code Quality**: Reduced from 60+ lines to 2-3 lines

---

## Timing & Synchronization API

### `Sleep.sleepUntil()` - Smart Waiting ⭐

**What it does:**
- Waits for a specific condition to become true
- Checks the condition repeatedly until either it's true or timeout is reached
- Much more efficient than fixed Sleep.sleep() calls

**Why use it:**
- ✅ **Efficient** - Returns immediately when condition is met
- ✅ **Reliable** - Ensures expected state is reached before continuing
- ✅ **Flexible** - Works with any boolean condition/lambda
- ✅ **Timeout Protection** - Won't wait forever

**Best Use Cases:**

```java
// 1. NAVIGATION - Wait for arrival at destination
if (!Walking.walk(targetTile)) return false;
if (!Sleep.sleepUntil(() -> targetArea.contains(Players.getLocal()), 15000)) {
    Logger.log("Failed to reach destination");
    return false;
}

// 2. INTERFACE OPENING - Wait for interface to be ready
if (!Bank.open()) return false;
if (!Sleep.sleepUntil(() -> Bank.isOpen(), 10000)) {
    Logger.log("Bank interface did not open");
    return false;
}

// 3. DIALOGUE STATES - Wait for dialogue to appear
if (!npc.interact("Talk-to")) return false;
if (!Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
    Logger.log("Dialogue did not start");
    return false;
}

// 4. QUEST COMPLETION - Wait for quest state changes
if (!Sleep.sleepUntil(this::isQuestComplete, 10000)) {
    // Fallback: Manual check in case of timing issues
    if (PlayerSettings.getConfig(questConfigId) >= completionValue) {
        Logger.log("Quest completed (manual verification)");
        return true;
    }
    return false;
}

// 5. INVENTORY CHANGES - Wait for items to appear/disappear
int beforeCount = Inventory.count(itemName);
if (!Bank.withdraw(itemName, 1)) return false;
if (!Sleep.sleepUntil(() -> Inventory.count(itemName) > beforeCount, 5000)) {
    Logger.log("Item was not withdrawn");
    return false;
}

// 6. ANIMATION WAITS - Wait for player animations to complete (sheep shearing, mining, etc.)
if (sheep.interact("Shear")) {
    // Wait for animation to start (ensures action was accepted)
    Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 3000);
    // Wait for animation to finish (prevents spam-clicking)
    Sleep.sleepUntil(() -> !Players.getLocal().isAnimating(), 5000);
    Sleep.sleep(500, 1000); // Small delay after animation
}
```

**❌ DON'T do this:**
```java
// Spam-clicking without waiting for animations
while (needMoreWool) {
    sheep.interact("Shear"); // Might click before previous action finishes
    Sleep.sleep(1000); // Fixed delay - inefficient
}

// Fixed delays - inefficient and unreliable
Walking.walk(targetTile);
Sleep.sleep(10000); // Might be too short OR too long

Bank.open();
Sleep.sleep(3000); // Bank might open in 500ms, wasting 2500ms
```

**✅ DO this:**
```java
// Smart waiting with animation checks - human-like and reliable
if (sheep.interact("Shear")) {
    Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 3000); // Wait for action to start
    Sleep.sleepUntil(() -> !Players.getLocal().isAnimating(), 5000); // Wait for completion
}

// Smart waiting - efficient and reliable
Walking.walk(targetTile);
Sleep.sleepUntil(() -> targetArea.contains(Players.getLocal()), 15000);

Bank.open();
Sleep.sleepUntil(() -> Bank.isOpen(), 10000);
```

**Timeout Guidelines:**
- **Navigation**: 15-30 seconds (depends on distance)
- **Interface Opening**: 5-10 seconds
- **Dialogue**: 5 seconds
- **Inventory/Bank Operations**: 3-5 seconds
- **Quest State Changes**: 10 seconds (with fallback check)
- **Animation Waits**: 3-5 seconds (start/finish respectively)

---

## General API Guidelines

### Always Use Sleep.sleepUntil() After Navigation
- Never assume navigation completes instantly
- Always verify the expected state is reached
- Use reasonable timeouts (10-15 seconds for most operations)

### Pattern for Navigation Methods:
```java
// 1. Call the navigation method
if (!NavigationMethod.open()) {
    Logger.log("[ERROR] Navigation failed");
    return false;
}

// 2. Wait for expected state with Sleep.sleepUntil()
if (!Sleep.sleepUntil(() -> ExpectedState.isTrue(), TIMEOUT)) {
    Logger.log("[ERROR] Expected state not reached within timeout");
    return false;
}

// 3. Proceed with operations
Logger.log("[SUCCESS] Ready for operations");
```

---

## Quest Status API ⭐

### `Quests.isFinished()` & `Quests.isStarted()` - Proper Quest Detection

**What they do:**
- `Quests.isFinished(Quest quest)` - Checks if a quest is completely finished
- `Quests.isStarted(Quest quest)` - Checks if a quest is in progress (started but not finished)

**Why use them:**
- ✅ **Reliable** - DreamBot's official quest detection methods
- ✅ **Accurate** - Handles edge cases and special quest states
- ✅ **Future-proof** - Won't break if Jagex changes config values
- ✅ **Clear Intent** - Code is self-documenting

**How to use them:**
```java
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.Quest;

// Check if quest is finished
if (Quests.isFinished(Quest.PIRATES_TREASURE)) {
    Logger.log("Pirate's Treasure is already completed!");
    return true;
}

// Check if quest is started but not finished
if (Quests.isStarted(Quest.PIRATES_TREASURE)) {
    Logger.log("Pirate's Treasure is in progress");
    // Continue with quest logic
} else {
    Logger.log("Pirate's Treasure not started yet");
    // Start the quest
}
```

**❌ DON'T do this (unreliable):**
```java
// Raw config values - can break if Jagex changes them!
int config = PlayerSettings.getConfig(101);
if (config >= 17) {
    return true; // Might be wrong!
}
```

**✅ DO this (best practice):**
```java
// Use official DreamBot Quest API
if (Quests.isFinished(Quest.PIRATES_TREASURE)) {
    return true;
}
```

**Available Quest Constants:**
- `Quest.PIRATES_TREASURE`
- `Quest.RESTLESS_GHOST`
- `Quest.DORICS_QUEST`
- `Quest.COOKS_ASSISTANT`
- `Quest.SHEEP_SHEARER`
- `Quest.ROMEO_AND_JULIET`
- And many more...

**Key Benefits:**
- **Reliability**: Official DreamBot methods handle all edge cases
- **Maintainability**: No need to track config IDs manually
- **Accuracy**: Properly detects quest states even after Jagex updates

---

---

## Our Bot System Integration Strategy ⭐

### How We're Using These APIs in Our Quest Automation System

**Our Architecture:**
- **Dynamic Decision Engine** - Not a traditional tree script, but a state-driven system
- **Smart Decision Nodes** - Continuously evaluate game state and choose next actions
- **Built-in Quest Detection** - Using DreamBot's official quest status methods
- **Intelligent Navigation** - Leveraging DreamBot's automatic pathfinding

**Key Integration Points:**

#### 1. Quest Status Detection
```java
// In our smart decision nodes, we use:
boolean isStarted = Quests.isStarted(FreeQuest.PIRATES_TREASURE);
boolean isFinished = Quests.isFinished(FreeQuest.PIRATES_TREASURE);

// This replaces our old manual config checking:
// int config = PlayerSettings.getConfig(101);
// if (config >= 1) { ... }
```

**Why This Matters:**
- **Reliability**: No more getting stuck because config values changed
- **Simplicity**: Clean, readable code that's easy to debug
- **Future-proof**: Won't break if Jagex updates quest configs

#### 2. Smart Navigation
```java
// Instead of hardcoded paths, we use:
if (!Bank.open()) {
    Logger.log("Failed to open bank");
    return false;
}
Sleep.sleepUntil(() -> Bank.isOpen(), 15000);

// This replaces 60+ lines of manual navigation code
```

**Benefits in Our System:**
- **Adaptive**: Bot works from any starting location
- **Efficient**: Always chooses closest bank/shop/teleport
- **Resilient**: Handles pathfinding failures automatically

#### 3. Dialogue Handling
```java
// Our dialogue system uses:
if (Dialogues.inDialogue()) {
    if (Dialogues.areOptionsAvailable()) {
        String[] options = Dialogues.getOptions();
        // Smart option selection based on quest context
        Dialogues.chooseOption("I'm looking for a quest!");
    } else if (Dialogues.canContinue()) {
        Dialogues.continueDialogue();
    }
}
```

**How This Improves Our Bot:**
- **Context-aware**: Can handle different dialogue scenarios
- **Robust**: Won't get stuck on unexpected dialogue options
- **Human-like**: Natural conversation flow

#### 4. State Synchronization
```java
// We use Sleep.sleepUntil() extensively:
if (!Sleep.sleepUntil(() -> Inventory.contains("Bronze key"), 7000)) {
    Logger.log("Failed to receive bronze key from Leela");
    return false;
}
```

**Why This is Critical:**
- **Prevents Race Conditions**: Bot waits for expected state before proceeding
- **Handles Delays**: Accounts for network lag and game processing time
- **Self-correcting**: Can detect and handle failures gracefully

### Our Bot System Advantages Over Traditional Tree Scripts

#### Traditional Tree Scripts:
- ❌ **Static paths** - Follow rigid sequences regardless of current state
- ❌ **No recovery** - Fail completely if interrupted
- ❌ **Manual tracking** - Require hardcoded config values
- ❌ **Fragile** - Break easily with game updates

#### Our Dynamic System:
- ✅ **State-aware** - Continuously monitors inventory, quest progress, location
- ✅ **Self-correcting** - Adapts to failures and interruptions
- ✅ **Resume capability** - Can pick up mid-quest from any point
- ✅ **Intelligent decisions** - Makes real-time choices based on current conditions

### Implementation Examples in Our Code

#### Smart Decision Node Pattern:
```java
smartDecisionNode = new QuestNode("smart_decision", "Smart quest decision based on current state") {
    @Override
    public ExecutionResult execute() {
        // Use DreamBot's built-in quest status methods
        boolean isStarted = Quests.isStarted(FreeQuest.PIRATES_TREASURE);
        boolean isFinished = Quests.isFinished(FreeQuest.PIRATES_TREASURE);
        
        // Make decisions based on current state
        if (!isStarted) {
            return ExecutionResult.success(startQuestNode, "Start quest with Redbeard Frank");
        } else if (Inventory.contains("Casket")) {
            return ExecutionResult.success(digForTreasureNode, "Open casket to complete quest");
        }
        // ... more intelligent decision logic
    }
};
```

#### Action Node Pattern:
```java
startQuestNode = new ActionNode("start_quest", "Talk to Redbeard Frank to start quest") {
    @Override
    protected boolean performAction() {
        // Use DreamBot's navigation
        if (!Bank.open()) return false;
        Sleep.sleepUntil(() -> Bank.isOpen(), 15000);
        
        // Use DreamBot's dialogue handling
        if (Dialogues.inDialogue()) {
            // Smart dialogue navigation
        }
        
        return true;
    }
};
```

### Future Integration Plans

#### 1. Grand Exchange API
- **Automatic item purchasing** for quest requirements
- **Price monitoring** to optimize costs
- **Supply management** for long quest chains

#### 2. Skill API Integration
- **Level checking** for quest requirements
- **Training automation** when skills are too low
- **Progress tracking** for skill-based quests

#### 3. Advanced Navigation
- **Teleport optimization** using available teleports
- **Multi-location pathfinding** for complex quests
- **Obstacle avoidance** for difficult areas

### Key Takeaways for Our Development

1. **Always use DreamBot's built-in methods** instead of manual implementations
2. **Sleep.sleepUntil() is your friend** - use it for all state changes
3. **Quest status detection** should use `Quests.isStarted()` and `Quests.isFinished()`
4. **Navigation should be automatic** - let DreamBot handle pathfinding
5. **State-driven decisions** are better than rigid sequences
6. **Error handling** should be graceful and self-correcting

---

*Last Updated: 2025-08-15*  
*Next: Document GrandExchange.open() and other navigation APIs* 

================================
. Here is a compiled list of the most useful DreamBot methods for those tasks, organized by category.

Dialogue Handling (Dialogues class)
These methods are essential for navigating conversations with NPCs.

Dialogues.inDialogue()

Use: Checks if a dialogue window is currently open.

Example: if (Dialogues.inDialogue()) { ... }

Dialogues.canContinue()

Use: Checks if a "Click here to continue" prompt is visible.

Example: if (Dialogues.canContinue()) { Dialogues.continueDialogue(); }

Dialogues.getOptions()

Use: Returns an array of strings containing the text of all available dialogue choices.

Example: String[] choices = Dialogues.getOptions();

Dialogues.chooseOption(String text)

Use: Selects a dialogue option that contains the specified text.

Example: Dialogues.chooseOption("I'm looking for a quest!");

Dialogues.getNPCDialogue()

Use: Gets the text the NPC is currently saying.

Example: String npcText = Dialogues.getNPCDialogue();

Movement & Travel (Walking class)
Walking.walk(Tile destination)

Use: The primary method to walk to a specific tile or location.

Example: Walking.walk(new Tile(3222, 3218, 0));

Walking.getRunEnergy()

Use: Returns the player's current run energy as an integer (0-100).

Example: if (Walking.getRunEnergy() > 30) { Walking.setRun(true); }

Walking.setRun(boolean enabled)

Use: Toggles the run setting on or off.

Example: Walking.setRun(true);

Interacting with the World (NPCs & GameObjects classes)
These are used to find and interact with anything in the game world.

NPCs.closest(String name)

Use: Finds the nearest NPC with the given name.

Example: NPC fatherAereck = NPCs.closest("Father Aereck");

GameObjects.closest(String name)

Use: Finds the nearest object (door, ladder, chest, etc.) with the given name.

Example: GameObject ladder = GameObjects.closest("Ladder");

.interact(String action)

Use: This is the universal interaction method for any object or NPC.

Example: fatherAereck.interact("Talk-to"); or ladder.interact("Climb-down");

Inventory & Item Management (Inventory & Equipment classes)
Inventory.contains(String... itemNames)

Use: Checks if your inventory contains one or more specified items.

Example: if (Inventory.contains("Ghostspeak amulet")) { ... }

Inventory.interact(String itemName, String action)

Use: A quick way to interact with an item in your inventory.

Example: Inventory.interact("Energy potion(4)", "Drink");

.useOn(GameObject | NPC | Item)

Use: Performs an item-on-object/NPC interaction. This is often a two-step process.

Example: Item skull = Inventory.get("Ghost's skull"); skull.useOn(GameObjects.closest("Coffin"));

Equipment.contains(String itemName)

Use: Checks if an item is currently equipped.

Example: if (!Equipment.contains("Ghostspeak amulet")) { ... }

Player & Game State (Players & Configs classes)
These are crucial for knowing what to do next.

Players.getLocal()

Use: Gets the local player object, which you can use to find your own tile, animation, etc.

Example: Tile myPosition = Players.getLocal().getTile();

Configs.get(int id)

Use: This is the most important method for questing. It retrieves a game state value.

Example: Config questConfig = Configs.get(107);

.getValue()

Use: Gets the numerical value from a Config object, which tells you the exact stage of a quest.

Example: int questStep = Configs.get(107).getValue();

Utility & Control Flow (Sleep class)
Sleep.sleepUntil(BooleanSupplier condition, long timeout)

Use: Pauses the script until a specific condition is met or a timeout occurs. This is much more reliable than a fixed sleep.




=================selecting dialouges

static boolean	chooseFirstOptionContaining​(@NonNull java.lang.String... options)	
This will go through the provided options in order, and will choose the first available option that contains one of your provided options.

static boolean	inDialogue()	
Checks if you're currently in a dialogue screen.