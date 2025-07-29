# AI Quest Framework v7.3 - Universal Utilities Architecture

## Overview
The AI Quest Framework v7.3 introduces a comprehensive universal utilities system designed for scalable quest automation across multiple OSRS quests. This architecture provides reusable components for dialogue handling, Grand Exchange automation, and centralized quest data management.

## Architecture Components

### 1. DialogueUtil - Universal Dialogue Management
**Location:** `quest.utils.DialogueUtil`

#### Key Features:
- **DialogueStep Pattern**: Structured approach to handling complex dialogue sequences
- **Multiple Dialogue Types**: Continue, Option Selection, Text Input support
- **Timeout Management**: Configurable timeouts for each dialogue step
- **Emergency Escape**: Built-in safety mechanisms for stuck dialogue states

#### Usage Examples:
```java
// Simple dialogue continuation
DialogueUtil.continueDialogue();

// Select specific dialogue option
DialogueUtil.selectDialogueOption("Yes, certainly.");

// Complex dialogue sequence
List<DialogueStep> questStart = Arrays.asList(
    DialogueStep.continueDialogue("Initial greeting"),
    DialogueStep.selectOption("Accept quest", "Yes, I'll help"),
    DialogueStep.continueDialogue("Final instructions")
);
DialogueUtil.handleDialogueSequence(questStart);
```

#### DialogueStep Factory Methods:
- `DialogueStep.continueDialogue(description)` - For continuing dialogue
- `DialogueStep.selectOption(optionText, description)` - For selecting options
- `DialogueStep.waitForOptions(description)` - For waiting for dialogue options

### 2. GrandExchangeUtil - Intelligent Trading System
**Location:** `quest.utils.GrandExchangeUtil`

#### Key Features:
- **Smart Price Strategies**: Multiple pricing approaches (Conservative 5%, Moderate 10%, Aggressive 15%, Instant 50%)
- **Retry Logic**: Automatic price increases on failed orders
- **Batch Purchasing**: Support for buying multiple items in sequence
- **Cost Calculation**: Pre-purchase cost estimation and affordability checks

#### Price Strategies:
- `CONSERVATIVE(5%)` - Small price increases for common items
- `MODERATE(10%)` - Standard pricing for most situations
- `AGGRESSIVE(15%)` - Higher prices for rare/urgent items  
- `INSTANT(50%)` - Maximum speed regardless of cost

#### Usage Examples:
```java
// Buy single item with default strategy
GrandExchangeUtil.buyItem("Egg", 1);

// Buy with specific strategy and timeout
GrandExchangeUtil.buyItem("Dragon bones", 100, PriceStrategy.AGGRESSIVE, 600000);

// Buy multiple items
ItemRequest[] items = {
    new ItemRequest("Hammer", 1, PriceStrategy.CONSERVATIVE),
    new ItemRequest("Chisel", 1, PriceStrategy.CONSERVATIVE)
};
GrandExchangeUtil.buyItems(items);

// Check affordability before purchasing
if (GrandExchangeUtil.hasEnoughCoins(items)) {
    GrandExchangeUtil.buyItems(items);
}
```

#### ItemRequest Class:
```java
// Simple request
new ItemRequest("Item name", quantity)

// With strategy
new ItemRequest("Item name", quantity, PriceStrategy.MODERATE)

// Full control
new ItemRequest("Item name", quantity, PriceStrategy.AGGRESSIVE, 300000)
```

### 3. QuestData - Centralized Quest Information
**Location:** `quest.utils.QuestData`

#### Key Features:
- **Quest Dialogue Storage**: Pre-defined dialogue sequences for each quest scenario
- **Item Requirements**: Required items with preferred purchasing strategies
- **NPC Information**: Location and interaction data for quest NPCs
- **Cost Calculation**: Total quest cost estimation

#### Quest Data Structure:
Each quest contains:
- **Dialogue Sequences**: Mapped by scenario (start_quest, complete_quest, etc.)
- **Required Items**: List of ItemRequest objects with purchasing strategies
- **NPC Locations**: Coordinate and area information for quest NPCs

#### Usage Examples:
```java
// Get quest dialogue for specific scenario
List<DialogueStep> startSequence = QuestData.getDialogueSequence("Cook's Assistant", "start_quest");
DialogueUtil.handleDialogueSequence(startSequence);

// Get required items for quest
List<ItemRequest> items = QuestData.getRequiredItems("Imp Catcher");
GrandExchangeUtil.buyItems(items.toArray(new ItemRequest[0]));

// Get NPC information
NPCInfo cook = QuestData.getNPCInfo("Cook's Assistant", "Cook");
// cook.getX(), cook.getY(), cook.getZ(), cook.getLocation()

// Check quest affordability
boolean canAfford = QuestData.canAffordQuest("Vampire Slayer");
int totalCost = QuestData.calculateQuestCost("Vampire Slayer");
```

#### Pre-configured Quests:
1. **Cook's Assistant**
   - Dialogue: start_quest, complete_quest
   - Items: Egg, Bucket of milk, Pot of flour (Conservative pricing)
   - NPCs: Cook (Lumbridge Castle kitchen)

2. **Imp Catcher**  
   - Dialogue: start_quest, complete_quest
   - Items: Red/Yellow/Black/White beads (Moderate pricing)
   - NPCs: Wizard Mizgog (Wizards' Tower)

3. **Vampire Slayer**
   - Dialogue: start_quest, get_garlic
   - Items: Hammer, Beer (Conservative pricing)
   - NPCs: Morgan (Draynor Village), Dr. Harlow (Blue Moon Inn)

## Integration with Existing Framework

### Quest Script Integration:
```java
public class MyQuestScript implements QuestScript {
    @Override
    public boolean executeQuest() {
        // Buy required items
        List<ItemRequest> items = QuestData.getRequiredItems(getQuestName());
        if (!GrandExchangeUtil.buyItems(items.toArray(new ItemRequest[0]))) {
            return false;
        }
        
        // Handle start dialogue
        List<DialogueStep> startDialogue = QuestData.getDialogueSequence(getQuestName(), "start_quest");
        if (!DialogueUtil.handleDialogueSequence(startDialogue)) {
            return false;
        }
        
        // Quest-specific logic here...
        
        return true;
    }
}
```

### GUI Integration:
The utilities integrate seamlessly with the existing 3-tab GUI:
- **Discovery Tab**: Continue to use for config/varbit discovery
- **Automation Tab**: Enhanced with universal utilities for reliable quest execution
- **Database Tab**: Displays quest information including costs and requirements

## Best Practices

### 1. Dialogue Handling:
- Always use descriptive names for DialogueStep descriptions
- Include timeout adjustments for slow dialogue responses
- Test dialogue sequences manually before automation

### 2. Grand Exchange Usage:
- Use Conservative strategy for common, cheap items
- Use Moderate strategy for most standard purchases
- Reserve Aggressive/Instant for rare or time-critical items
- Always check affordability before starting quest automation

### 3. Quest Data Management:
- Add new quests using the established patterns
- Keep dialogue sequences simple and reliable
- Update item strategies based on market conditions
- Document NPC locations accurately

## Future Expansion

### Adding New Quests:
1. Add dialogue sequences to QuestData.initializeNewQuest()
2. Define required items with appropriate pricing strategies
3. Add NPC information with accurate coordinates
4. Create QuestScript implementation using universal utilities
5. Test thoroughly and update documentation

### Utility Extensions:
- **BankUtil**: Universal banking operations
- **InventoryUtil**: Smart inventory management
- **CombatUtil**: Universal combat handling for combat quests
- **SkillUtil**: Training utilities for skill requirements

## Technical Notes

### Dependencies:
- All utilities require DreamBot API client.jar
- JSON.jar for potential data serialization (future use)
- Existing quest framework components (QuestEventLogger, QuestDatabase, etc.)

### Performance Considerations:
- DialogueUtil includes intelligent timeout management
- GrandExchangeUtil implements retry logic with exponential price increases
- QuestData uses static initialization for fast access
- All utilities designed for minimal memory footprint

### Error Handling:
- Comprehensive try-catch blocks in all critical operations
- Graceful degradation when APIs are unavailable
- Detailed logging for debugging automation issues
- Emergency escape mechanisms for stuck states

## Conclusion

The AI Quest Framework v7.3 universal utilities provide a robust foundation for scalable OSRS quest automation. By centralizing common operations like dialogue handling, Grand Exchange trading, and quest data management, the framework enables rapid development of new quest scripts while maintaining reliability and consistency across all implementations.

The modular architecture ensures that each utility can be used independently or in combination, providing maximum flexibility for both simple and complex quest automation scenarios.
