/**
 * WitchsPotionScript.java
 * 
 * Automated quest script for the Witch's Potion quest in OSRS.
 * This script handles the complete quest from start to finish including
 * item gathering from the Grand Exchange and quest completion.
 * 
 * Quest Overview:
 * 1. Gather required items: Raw beef, Eye of newt, Onion
 * 2. Cook raw beef on fire, then burn the cooked meat
 * 3. Talk to Hetty in Rimmington to start the quest
 * 4. Kill rat in nearby building to get rat's tail
 * 5. Return to Hetty with all ingredients to complete the quest
 * 
 * Quest Details:
 * - Config ID: 101 (3=started, 4=complete)
 * - Location: Rimmington (Hetty's house)
 * - Required items: Raw beef, Eye of newt, Onion, Rat's tail (obtained in quest)
 */

package quest.quests;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.script.AbstractScript;
import quest.core.QuestScript;
import quest.core.QuestDatabase;
import quest.core.QuestEventLogger;
import quest.utils.ItemGatheringUtil;
import quest.utils.ItemGatheringUtil.ItemRequirement;
import quest.utils.GrandExchangeUtil;
import quest.utils.DialogueUtil;
import quest.utils.DialogueUtil.DialogueStep;

/**
 * Witch's Potion Quest Automation
 * 
 * This script fully automates the Witch's Potion quest using our
 * unified item gathering system and Grand Exchange integration.
 */
public class WitchsPotionScript implements QuestScript {
    
    // Quest progress tracking - Witch's Potion uses config 101
    private static final int QUEST_CONFIG_ID = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_IN_PROGRESS = 3;   // Value when quest is started
    private static final int QUEST_COMPLETE = 4;     // Value when quest is completed
    
    // Required quest items
    private static final String RAW_BEEF = "Raw beef";
    private static final String EYE_OF_NEWT = "Eye of newt";
    private static final String ONION = "Onion";
    private static final String COOKED_MEAT = "Cooked meat";
    private static final String BURNT_MEAT = "Burnt meat";
    private static final String RATS_TAIL = "Rat's tail";
    
    // Quest locations
    private static final Area RIMMINGTON_AREA = new Area(2950, 3200, 2970, 3220);
    private static final Area HETTY_HOUSE = new Area(2964, 3204, 2970, 3210);
    private static final Area RAT_BUILDING = new Area(2954, 3200, 2962, 3208);
    private static final String HETTY_NPC_NAME = "Hetty";
    private static final String RAT_NPC_NAME = "Rat";
    
    // Script state management
    private AbstractScript script;
    private QuestDatabase database;
    private QuestEventLogger questLogger;
    private quest.utils.QuestLogger simpleLogger;
    private boolean wasCompleteLastCheck = false;
    
    @Override
    public void initialize(AbstractScript script, QuestDatabase database) {
        this.script = script;
        this.database = database;
        
        // Initialize BOTH logging systems for redundancy
        this.questLogger = new QuestEventLogger(script, "Witch's Potion");
        this.simpleLogger = quest.utils.QuestLogger.getInstance();
        this.simpleLogger.initializeQuest("Witchs_Potion");
        
        logQuest("WitchsPotion DEBUG: Script initialized successfully");
        logQuest("WitchsPotion DEBUG: Quest logger initialized - logs will be saved to quest_logs/");
    }
    
    /**
     * Quest-specific logging method that writes to both console and quest log files
     */
    private void logQuest(String message) {
        // 1. Log to DreamBot console
        if (script != null) {
            script.log(message);
        }
        
        // 2. Also use Logger.log as backup
        Logger.log(message);
        
        // 3. FORCE write to quest log file directly
        if (questLogger != null) {
            questLogger.logConsoleOutput(message);
        }
        
        // 4. Use our simple, reliable logger
        if (simpleLogger != null) {
            simpleLogger.log(message);
        }
    }
    
    @Override
    public String getQuestName() {
        return "Witch's Potion";
    }
    
    @Override
    public String getQuestId() {
        return "WITCHS_POTION";
    }
    
    @Override
    public boolean canStart() {
        // Witch's Potion has no requirements, just check if not already complete
        int configValue = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        boolean canStart = configValue < QUEST_COMPLETE;
        return canStart;
    }
    
    /**
     * Helper method to check if quest is complete
     */
    private boolean isQuestComplete() {
        int config101 = PlayerSettings.getConfig(101);
        boolean complete = config101 >= QUEST_COMPLETE;
        
        // Only log when completion state changes to reduce spam
        if (complete && !wasCompleteLastCheck) {
            logQuest("WitchsPotion: QUEST COMPLETED! Config 101: " + config101);
            wasCompleteLastCheck = true;
        } else if (!complete && wasCompleteLastCheck) {
            wasCompleteLastCheck = false;
        }
        
        return complete;
    }
    
    /**
     * Helper method to check if quest is started
     */
    private boolean isQuestStarted() {
        int config101 = PlayerSettings.getConfig(101);
        
        logQuest("WitchsPotion DEBUG: Config 101 value: " + config101);
        
        boolean started = config101 >= QUEST_IN_PROGRESS;
        logQuest("WitchsPotion DEBUG: Quest started = " + started);
        
        return started;
    }
    
    /**
     * Get current quest step description
     */
    private String getCurrentStep() {
        if (isQuestComplete()) {
            return "Quest completed!";
        }
        
        if (!isQuestStarted()) {
            // Check if we have the ingredients ready
            if (hasAllIngredients()) {
                return "Talk to Hetty in Rimmington to start the quest";
            } else {
                return "Gather and prepare quest ingredients: " + getMissingIngredientsString();
            }
        }
        
        // Quest is started, check if we have rat's tail
        if (!Inventory.contains(RATS_TAIL)) {
            return "Kill rat in Rimmington to get rat's tail";
        } else {
            return "Return to Hetty to complete the quest";
        }
    }
    
    @Override
    public boolean executeCurrentStep() {
        logQuest("WitchsPotion DEBUG: Executing current step...");
        logQuest("WitchsPotion DEBUG: Current config value: " + PlayerSettings.getConfig(QUEST_CONFIG_ID));
        
        if (isQuestComplete()) {
            logQuest("WitchsPotion DEBUG: Quest already complete");
            return true;
        }
        
        if (!isQuestStarted()) {
            // First prepare all ingredients
            if (!hasAllIngredients()) {
                logQuest("WitchsPotion DEBUG: Preparing quest ingredients");
                return prepareIngredients();
            } else {
                logQuest("WitchsPotion DEBUG: Starting quest with Hetty");
                return startQuestInternal();
            }
        }
        
        logQuest("WitchsPotion DEBUG: Quest started, progressing with completion");
        return progressQuest();
    }
    
    @Override
    public boolean isComplete() {
        return isQuestComplete();
    }
    
    @Override
    public int getCurrentProgress() {
        if (isQuestComplete()) {
            return 100;
        } else if (isQuestStarted()) {
            return Inventory.contains(RATS_TAIL) ? 90 : 60;
        } else if (hasAllIngredients()) {
            return 40;
        } else {
            return 10;
        }
    }
    
    @Override
    public String getCurrentStepDescription() {
        return getCurrentStep();
    }
    
    @Override
    public boolean hasRequiredItems() {
        // Always return true since this script can automatically gather items from Grand Exchange
        return true;
    }
    
    @Override
    public String[] getRequiredItems() {
        return new String[]{RAW_BEEF, EYE_OF_NEWT, ONION};
    }
    
    @Override
    public boolean handleDialogue() {
        // Dialogue handling is integrated into quest progression
        return true;
    }
    
    @Override
    public boolean navigateToObjective() {
        return RIMMINGTON_AREA.contains(Players.getLocal()) || 
               Walking.walk(RIMMINGTON_AREA.getRandomTile());
    }
    
    @Override
    public void cleanup() {
        logQuest("WitchsPotion DEBUG: Cleaning up resources");
        
        if (questLogger != null) {
            questLogger.close();
            questLogger = null;
        }
        
        if (simpleLogger != null) {
            simpleLogger.close();
        }
    }
    
    @Override
    public void onQuestStart() {
        logQuest("WitchsPotion DEBUG: Quest started callback");
    }
    
    @Override
    public void onQuestComplete() {
        logQuest("WitchsPotion DEBUG: Quest completed callback");
    }
    
    @Override
    public boolean startQuest() {
        logQuest("WitchsPotion DEBUG: Starting Witch's Potion quest...");
        
        if (!canStart()) {
            logQuest("WitchsPotion DEBUG: Cannot start quest - requirements not met or quest already complete");
            return false;
        }
        
        if (isQuestComplete()) {
            logQuest("WitchsPotion DEBUG: Quest already completed");
            return true;
        }
        
        // First prepare ingredients, then start quest
        if (!hasAllIngredients()) {
            logQuest("WitchsPotion DEBUG: Preparing ingredients first");
            return prepareIngredients();
        } else if (!isQuestStarted()) {
            logQuest("WitchsPotion DEBUG: Starting quest with Hetty");
            return startQuestInternal();
        } else {
            logQuest("WitchsPotion DEBUG: Quest already started, progressing");
            return progressQuest();
        }
    }
    
    /**
     * Prepare all quest ingredients (buy from GE and cook/burn beef)
     */
    private boolean prepareIngredients() {
        logQuest("WitchsPotion DEBUG: === PREPARE INGREDIENTS START ===");
        
        // Step 1: Gather raw materials from Grand Exchange
        if (!hasRawMaterials()) {
            logQuest("WitchsPotion DEBUG: Gathering raw materials from Grand Exchange");
            ItemGatheringUtil.GatheringResult result = gatherRawMaterials();
            
            if (!result.isSuccess()) {
                logQuest("WitchsPotion DEBUG: Failed to gather raw materials");
                return false;
            }
        }
        
        // Step 2: Process raw beef (cook then burn)
        if (!hasBurntMeat()) {
            logQuest("WitchsPotion DEBUG: Processing raw beef");
            return processRawBeef();
        }
        
        logQuest("WitchsPotion DEBUG: All ingredients prepared successfully");
        return true;
    }
    
    /**
     * Gather raw materials from Grand Exchange
     */
    private ItemGatheringUtil.GatheringResult gatherRawMaterials() {
        logQuest("WitchsPotion DEBUG: === GATHER RAW MATERIALS START ===");
        
        // Define requirements with explicit Grand Exchange purchasing
        ItemRequirement[] requirements = {
            new ItemRequirement(RAW_BEEF, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE),
            new ItemRequirement(EYE_OF_NEWT, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE),
            new ItemRequirement(ONION, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE)
        };
        
        // Log what we're trying to gather
        logQuest("WitchsPotion DEBUG: === RAW MATERIAL REQUIREMENTS ===");
        for (ItemRequirement req : requirements) {
            logQuest("WitchsPotion DEBUG: Required item: " + req.getItemName() + " x" + req.getQuantity());
            logQuest("WitchsPotion DEBUG: Current in inventory: " + Inventory.count(req.getItemName()));
        }
        
        // Use unified gathering system
        ItemGatheringUtil.GatheringResult result = ItemGatheringUtil.gatherItems(requirements);
        
        logQuest("WitchsPotion DEBUG: Raw material gathering result: " + (result.isSuccess() ? "SUCCESS" : "FAILED"));
        if (!result.isSuccess()) {
            logQuest("WitchsPotion DEBUG: Gathering failed - " + result.getLastAction());
        }
        
        return result;
    }
    
    /**
     * Process raw beef by cooking it then burning it
     */
    private boolean processRawBeef() {
        logQuest("WitchsPotion DEBUG: === PROCESS RAW BEEF START ===");
        
        if (!Inventory.contains(RAW_BEEF)) {
            logQuest("WitchsPotion DEBUG: No raw beef to process");
            return false;
        }
        
        // Find a fire to cook on
        GameObject fire = GameObjects.closest("Fire");
        if (fire == null) {
            logQuest("WitchsPotion DEBUG: No fire found, creating one");
            if (!createFire()) {
                return false;
            }
            fire = GameObjects.closest("Fire");
        }
        
        if (fire == null) {
            logQuest("WitchsPotion DEBUG: Still no fire available");
            return false;
        }
        
        // Step 1: Cook raw beef
        if (Inventory.contains(RAW_BEEF) && !Inventory.contains(COOKED_MEAT)) {
            logQuest("WitchsPotion DEBUG: Cooking raw beef on fire");
            Item rawBeef = Inventory.get(RAW_BEEF);
            if (rawBeef != null && rawBeef.useOn(fire)) {
                Sleep.sleepUntil(() -> Inventory.contains(COOKED_MEAT), 5000);
            }
        }
        
        // Step 2: Burn cooked meat
        if (Inventory.contains(COOKED_MEAT) && !Inventory.contains(BURNT_MEAT)) {
            logQuest("WitchsPotion DEBUG: Burning cooked meat on fire");
            Item cookedMeat = Inventory.get(COOKED_MEAT);
            if (cookedMeat != null && cookedMeat.useOn(fire)) {
                Sleep.sleepUntil(() -> Inventory.contains(BURNT_MEAT), 5000);
            }
        }
        
        boolean success = Inventory.contains(BURNT_MEAT);
        logQuest("WitchsPotion DEBUG: Raw beef processing result: " + (success ? "SUCCESS" : "FAILED"));
        return success;
    }
    
    /**
     * Create a fire if none exists
     */
    private boolean createFire() {
        logQuest("WitchsPotion DEBUG: Creating fire");
        
        // This is a simplified implementation - in practice you'd need logs and tinderbox
        // For now, assume we can find logs and tinderbox or that there's already a fire nearby
        
        // Try to find existing logs on ground
        GameObject logs = GameObjects.closest("Logs");
        if (logs != null && Inventory.contains("Tinderbox")) {
            Item tinderbox = Inventory.get("Tinderbox");
            if (tinderbox != null && tinderbox.useOn(logs)) {
                Sleep.sleepUntil(() -> GameObjects.closest("Fire") != null, 5000);
                return GameObjects.closest("Fire") != null;
            }
        }
        
        return false;
    }
    
    /**
     * Start the quest by talking to Hetty
     */
    private boolean startQuestInternal() {
        logQuest("WitchsPotion DEBUG: Starting quest with Hetty");
        
        // Navigate to Hetty's house
        if (!HETTY_HOUSE.contains(Players.getLocal())) {
            logQuest("WitchsPotion DEBUG: Walking to Hetty's house");
            if (!Walking.walk(HETTY_HOUSE.getRandomTile())) {
                return false;
            }
            
            if (!Sleep.sleepUntil(() -> HETTY_HOUSE.contains(Players.getLocal()), 10000)) {
                return false;
            }
        }
        
        // Find and talk to Hetty
        NPC hetty = NPCs.closest(HETTY_NPC_NAME);
        if (hetty == null) {
            logQuest("WitchsPotion DEBUG: Hetty not found");
            return false;
        }
        
        logQuest("WitchsPotion DEBUG: Talking to Hetty to start quest");
        if (!hetty.interact("Talk-to")) {
            return false;
        }
        
        // Handle dialogue to start the quest
        if (!Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
            return false;
        }
        
        logQuest("WitchsPotion DEBUG: In dialogue, handling quest start sequence");
        
        // Handle quest start dialogue
        int attempts = 0;
        while (Dialogues.inDialogue() && attempts < 20) {
            attempts++;
            
            if (Dialogues.areOptionsAvailable()) {
                String[] options = Dialogues.getOptions();
                logQuest("WitchsPotion DEBUG: Available dialogue options:");
                for (int i = 0; i < options.length; i++) {
                    logQuest("WitchsPotion DEBUG: Option " + (i+1) + ": " + options[i]);
                }
                
                // Look for quest start option
                boolean foundOption = false;
                for (int i = 0; i < options.length; i++) {
                    if (options[i].toLowerCase().contains("quest") || 
                        options[i].toLowerCase().contains("search")) {
                        logQuest("WitchsPotion DEBUG: Selecting quest option: " + options[i]);
                        Dialogues.chooseOption(i + 1);
                        foundOption = true;
                        break;
                    }
                }
                
                // Look for "Yes" to accept quest
                if (!foundOption) {
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].toLowerCase().contains("yes")) {
                            logQuest("WitchsPotion DEBUG: Selecting 'Yes' to accept quest");
                            Dialogues.chooseOption(i + 1);
                            foundOption = true;
                            break;
                        }
                    }
                }
                
                if (!foundOption) {
                    logQuest("WitchsPotion DEBUG: Selecting first option");
                    Dialogues.chooseOption(1);
                }
                
                Sleep.sleep(800, 1200);
                
            } else if (Dialogues.canContinue()) {
                logQuest("WitchsPotion DEBUG: Continuing dialogue");
                Dialogues.continueDialogue();
                Sleep.sleep(800, 1200);
            } else {
                break;
            }
        }
        
        // Wait for quest state to update
        Sleep.sleep(1000, 2000);
        
        boolean started = isQuestStarted();
        logQuest("WitchsPotion DEBUG: Quest start result: " + started);
        return started;
    }
    
    /**
     * Progress the quest (kill rat for tail and complete)
     */
    private boolean progressQuest() {
        logQuest("WitchsPotion DEBUG: === PROGRESS QUEST START ===");
        
        // Check if we need rat's tail
        if (!Inventory.contains(RATS_TAIL)) {
            logQuest("WitchsPotion DEBUG: Need to get rat's tail");
            return killRatForTail();
        }
        
        // Complete the quest
        logQuest("WitchsPotion DEBUG: Have rat's tail, completing quest");
        return completeQuest();
    }
    
    /**
     * Kill rat to get rat's tail
     */
    private boolean killRatForTail() {
        logQuest("WitchsPotion DEBUG: Killing rat for tail");
        
        // Navigate to rat building
        if (!RAT_BUILDING.contains(Players.getLocal())) {
            logQuest("WitchsPotion DEBUG: Walking to rat building");
            if (!Walking.walk(RAT_BUILDING.getRandomTile())) {
                return false;
            }
            
            if (!Sleep.sleepUntil(() -> RAT_BUILDING.contains(Players.getLocal()), 10000)) {
                return false;
            }
        }
        
        // Find and attack rat
        NPC rat = NPCs.closest(RAT_NPC_NAME);
        if (rat == null) {
            logQuest("WitchsPotion DEBUG: Rat not found");
            return false;
        }
        
        logQuest("WitchsPotion DEBUG: Attacking rat"); 
        if (!rat.interact("Attack")) {
            return false;
        }
        
        // Wait for combat to finish and rat to die
        Sleep.sleepUntil(() -> !Players.getLocal().isInCombat(), 10000);
        Sleep.sleep(1000, 2000);
        
        // Look for rat's tail on ground and pick it up
        if (!Inventory.contains(RATS_TAIL)) {
            // Try to take rat's tail from ground
            GameObject ratsTail = GameObjects.closest("Rat's tail");
            if (ratsTail != null) {
                logQuest("WitchsPotion DEBUG: Taking rat's tail from ground");
                ratsTail.interact("Take");
                Sleep.sleepUntil(() -> Inventory.contains(RATS_TAIL), 5000);
            }
        }
        
        boolean success = Inventory.contains(RATS_TAIL);
        logQuest("WitchsPotion DEBUG: Got rat's tail: " + success);
        return success;
    }
    
    /**
     * Complete the quest by returning to Hetty
     */
    private boolean completeQuest() {
        logQuest("WitchsPotion DEBUG: Completing quest with Hetty");
        
        // Verify we have all required items
        if (!hasAllIngredients() || !Inventory.contains(RATS_TAIL)) {
            logQuest("WitchsPotion DEBUG: Missing required items for completion");
            return false;
        }
        
        // Navigate to Hetty's house
        if (!HETTY_HOUSE.contains(Players.getLocal())) {
            logQuest("WitchsPotion DEBUG: Walking to Hetty for completion");
            if (!Walking.walk(HETTY_HOUSE.getRandomTile())) {
                return false;
            }
            
            if (!Sleep.sleepUntil(() -> HETTY_HOUSE.contains(Players.getLocal()), 10000)) {
                return false;
            }
        }
        
        // Find and talk to Hetty
        NPC hetty = NPCs.closest(HETTY_NPC_NAME);
        if (hetty == null) {
            logQuest("WitchsPotion DEBUG: Hetty not found for completion");
            return false;
        }
        
        logQuest("WitchsPotion DEBUG: Talking to Hetty to complete quest");
        if (!hetty.interact("Talk-to")) {
            return false;
        }
        
        // Handle completion dialogue
        if (!Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
            return false;
        }
        
        logQuest("WitchsPotion DEBUG: In completion dialogue");
        
        // Simple dialogue completion
        int attempts = 0;
        while (Dialogues.inDialogue() && attempts < 15) {
            attempts++;
            
            if (Dialogues.canContinue()) {
                Dialogues.continueDialogue();
                Sleep.sleep(800, 1200);
            } else if (Dialogues.areOptionsAvailable()) {
                // Just select first option for completion
                Dialogues.chooseOption(1);
                Sleep.sleep(800, 1200);
            } else {
                break;
            }
        }
        
        // Check for quest completion
        Sleep.sleep(2000, 3000);
        
        if (Sleep.sleepUntil(this::isQuestComplete, 10000)) {
            logQuest("WitchsPotion: [SUCCESS] QUEST COMPLETED SUCCESSFULLY!");
            return true;
        } else {
            // Manual check
            int config101 = PlayerSettings.getConfig(101);
            if (config101 >= QUEST_COMPLETE) {
                logQuest("WitchsPotion: [SUCCESS] QUEST COMPLETED (Config 101: " + config101 + ")");
                return true;
            } else {
                logQuest("WitchsPotion: [ERROR] Quest not completed after dialogue (Config: " + config101 + ")");
                return false;
            }
        }
    }
    
    /**
     * Check if we have all raw materials
     */
    private boolean hasRawMaterials() {
        return Inventory.contains(RAW_BEEF) && 
               Inventory.contains(EYE_OF_NEWT) && 
               Inventory.contains(ONION);
    }
    
    /**
     * Check if we have burnt meat
     */
    private boolean hasBurntMeat() {
        return Inventory.contains(BURNT_MEAT);
    }
    
    /**
     * Check if we have all processed ingredients
     */
    private boolean hasAllIngredients() {
        return Inventory.contains(BURNT_MEAT) && 
               Inventory.contains(EYE_OF_NEWT) && 
               Inventory.contains(ONION);
    }
    
    /**
     * Get a string describing which ingredients are missing
     */
    private String getMissingIngredientsString() {
        StringBuilder missing = new StringBuilder();
        
        if (!Inventory.contains(BURNT_MEAT) && !Inventory.contains(RAW_BEEF) && !Inventory.contains(COOKED_MEAT)) {
            missing.append("Raw beef (to cook and burn), ");
        }
        if (!Inventory.contains(EYE_OF_NEWT)) {
            missing.append("Eye of newt, ");
        }
        if (!Inventory.contains(ONION)) {
            missing.append("Onion, ");
        }
        
        String result = missing.toString();
        return result.isEmpty() ? "None" : result.substring(0, result.length() - 2);
    }
}