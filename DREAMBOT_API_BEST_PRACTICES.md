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
```

**❌ DON'T do this:**
```java
// Fixed delays - inefficient and unreliable
Walking.walk(targetTile);
Sleep.sleep(10000); // Might be too short OR too long

Bank.open();
Sleep.sleep(3000); // Bank might open in 500ms, wasting 2500ms
```

**✅ DO this:**
```java
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

*Last Updated: 2025-07-28*  
*Next: Document GrandExchange.open() and other navigation APIs* 



