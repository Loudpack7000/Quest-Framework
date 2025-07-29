/**
 * CooksAssistantScript.java
 * 
 * Automated quest script for the Cook's Assistant quest in OSRS.
 * This script handles the complete quest from start to finish including
 * item gathering from the Grand Exchange and quest completion.
 * 
 * Quest Overview:
 * 1. Talk to the Cook in Lumbridge Castle kitchen to start the quest
 * 2. Gather required items: Egg, Bucket of milk, Pot of flour
 * 3. Return to the Cook to complete the quest
 * 
 * Quest Details:
 * - Config ID: 101 (0=not started, 7=started, 8=complete)
 * - Location: Lumbridge Castle kitchen
 * - Required items: All can be purchased from Grand Exchange
 * - No combat required
 */

package quest.quests;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.wrappers.interactive.NPC;
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
 * Cook's Assistant Quest Automation
 * 
 * This script fully automates the Cook's Assistant quest using our
 * unified item gathering system and Grand Exchange integration.
 */
public class CooksAssistantScript implements QuestScript {
    
    // Quest progress tracking - Cook's Assistant uses config 101
    private static final int QUEST_CONFIG_ID = 101;  // Cook's Assistant uses config, not varbit
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_IN_PROGRESS = 1;   // Value when quest is accepted and in progress  
    private static final int QUEST_COMPLETE = 2;      // Value when quest is completed
    
    // Required quest items - all purchasable from Grand Exchange
    private static final String EGG = "Egg";
    private static final String BUCKET_OF_MILK = "Bucket of milk"; 
    private static final String POT_OF_FLOUR = "Pot of flour";
    
    // Quest locations
    private static final Area LUMBRIDGE_CASTLE_KITCHEN = new Area(3205, 3212, 3212, 3217);
    private static final String COOK_NPC_NAME = "Cook";
    
    // Script state management
    private AbstractScript script;
    private QuestDatabase database;
    private QuestEventLogger questLogger; // Add quest-specific logger
    private quest.utils.QuestLogger simpleLogger; // Add simple reliable logger
    private boolean wasCompleteLastCheck = false; // Track completion state to reduce log spam
    
    @Override
    public void initialize(AbstractScript script, QuestDatabase database) {
        this.script = script;
        this.database = database;
        
        // Initialize BOTH logging systems for redundancy
        this.questLogger = new QuestEventLogger(script, "Cook's Assistant");
        this.simpleLogger = quest.utils.QuestLogger.getInstance();
        this.simpleLogger.initializeQuest("Cook_s_Assistant");
        
        logQuest("CooksAssistant DEBUG: Script initialized successfully");
        logQuest("CooksAssistant DEBUG: Quest logger initialized - logs will be saved to quest_logs/");
    }
    
    /**
     * Quest-specific logging method that writes to both console and quest log files
     * This ensures ALL output gets captured, not just our custom logs
     */
    private void logQuest(String message) {
        // 1. Log to DreamBot console (this shows in DreamBot's console)
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
    
    /**
     * Direct file writing as backup to ensure logs are captured
     */
    private void writeDirectToLogFile(String message) {
        try {
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logLine = "[" + timestamp + "] COOKS_ASSISTANT: " + message + "\n";
            
            // Write directly to a quest-specific log file
            String logDir = "quest_logs";
            java.io.File dir = new java.io.File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String timestamp2 = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = logDir + "/QUEST_Cook_s_Assistant_DEBUG_" + timestamp2 + ".log";
            
            // Append to file
            try (java.io.FileWriter fw = new java.io.FileWriter(filename, true);
                 java.io.BufferedWriter bw = new java.io.BufferedWriter(fw)) {
                bw.write(logLine);
                bw.flush();
            }
            
        } catch (Exception e) {
            // Silent fail to prevent infinite loops
        }
    }
    
    @Override
    public String getQuestName() {
        return "Cook's Assistant";
    }
    
    @Override
    public String getQuestId() {
        return "COOKS_ASSISTANT";
    }
    
    @Override
    public boolean canStart() {
        // Cook's Assistant has no requirements, just check if not already complete
        int configValue = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        boolean canStart = configValue < QUEST_COMPLETE;
        return canStart;
    }
    
    /**
     * Helper method to check if quest is complete
     */
    private boolean isQuestComplete() {
        // Check multiple completion indicators
        int config101 = PlayerSettings.getConfig(101);
        int config29 = PlayerSettings.getConfig(29);
        int varbit29 = PlayerSettings.getBitValue(29);
        int config174 = PlayerSettings.getConfig(174);
        
        // FIXED: Cook's Assistant completion values (from logs: Config 29: 2 is completion)
        boolean complete = config101 >= QUEST_COMPLETE || config29 >= 2;
        
        // Only log when completion state changes to reduce spam
        if (complete && !wasCompleteLastCheck) {
            logQuest("CooksAssistant: QUEST COMPLETED! Config 101: " + config101 + ", Config 29: " + config29);
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
        // Try multiple detection methods since we're not sure which one Cook's Assistant uses
        int config101 = PlayerSettings.getConfig(101);
        int config29 = PlayerSettings.getConfig(29);  // Sometimes quests use config instead of varbit
        int varbit29 = PlayerSettings.getBitValue(29);
        int config174 = PlayerSettings.getConfig(174); // Another common quest config
        
        logQuest("CooksAssistant DEBUG: Config 101 value: " + config101);
        logQuest("CooksAssistant DEBUG: Config 29 value: " + config29);
        logQuest("CooksAssistant DEBUG: Varbit 29 value: " + varbit29);
        logQuest("CooksAssistant DEBUG: Config 174 value: " + config174);
        
        // Check if any tracking method shows quest started
        boolean started = config101 >= QUEST_IN_PROGRESS || config29 > 0 || varbit29 > 0 || config174 > 0;
        logQuest("CooksAssistant DEBUG: Quest started = " + started);
        
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
            return "Talk to the Cook in Lumbridge Castle kitchen";
        }
        
        // Check if we have all required items
        if (hasAllQuestItems()) {
            return "Return to the Cook to complete the quest";
        } else {
            return "Gather quest items: " + getMissingItemsString();
        }
    }
    
    @Override
    public boolean executeCurrentStep() {
        logQuest("CooksAssistant DEBUG: Executing current step...");
        logQuest("CooksAssistant DEBUG: Current config value: " + PlayerSettings.getConfig(QUEST_CONFIG_ID));
        
        if (isQuestComplete()) {
            logQuest("CooksAssistant DEBUG: Quest already complete");
            return true;
        }
        
        if (!isQuestStarted()) {
            logQuest("CooksAssistant DEBUG: Quest not started, attempting to start");
            boolean startResult = startQuestInternal();
            logQuest("CooksAssistant DEBUG: Start quest result: " + startResult);
            logQuest("CooksAssistant DEBUG: Post-start config value: " + PlayerSettings.getConfig(QUEST_CONFIG_ID));
            return startResult;
        }
        
        logQuest("CooksAssistant DEBUG: Quest started, proceeding with quest progression");
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
            return hasAllQuestItems() ? 80 : 50;
        } else {
            return 0;
        }
    }
    
    @Override
    public String getCurrentStepDescription() {
        return getCurrentStep();
    }
    
    @Override
    public boolean hasRequiredItems() {
        // Always return true since this script can automatically gather items from Grand Exchange
        logQuest("CooksAssistant DEBUG: hasRequiredItems() called - returning true (auto-gathering enabled)");
        return true;
    }
    
    @Override
    public String[] getRequiredItems() {
        return new String[]{EGG, BUCKET_OF_MILK, POT_OF_FLOUR};
    }
    
    @Override
    public boolean handleDialogue() {
        // Dialogue handling is integrated into quest progression
        return true;
    }
    
    @Override
    public boolean navigateToObjective() {
        return LUMBRIDGE_CASTLE_KITCHEN.contains(Players.getLocal()) || 
               Walking.walk(LUMBRIDGE_CASTLE_KITCHEN.getRandomTile());
    }
    
    @Override
    public void cleanup() {
        logQuest("CooksAssistant DEBUG: Cleaning up resources");
        
        // Close quest logger when cleaning up
        if (questLogger != null) {
            questLogger.close();
            questLogger = null;
        }
        
        // Close simple logger
        if (simpleLogger != null) {
            simpleLogger.close();
        }
    }
    
    @Override
    public void onQuestStart() {
        logQuest("CooksAssistant DEBUG: Quest started callback");
    }
    
    @Override
    public void onQuestComplete() {
        logQuest("CooksAssistant DEBUG: Quest completed callback");
    }
    
    @Override
    public boolean startQuest() {
        logQuest("CooksAssistant DEBUG: Starting Cook's Assistant quest...");
        
        // Check if we can start the quest
        if (!canStart()) {
            logQuest("CooksAssistant DEBUG: Cannot start quest - requirements not met or quest already complete");
            return false;
        }
        
        if (isQuestComplete()) {
            logQuest("CooksAssistant DEBUG: Quest already completed");
            return true;
        }
        
        if (!isQuestStarted()) {
            logQuest("CooksAssistant DEBUG: Quest not started, attempting to start");
            return startQuestInternal();
        } else {
            logQuest("CooksAssistant DEBUG: Quest already started, continuing progression");
            return progressQuest();
        }
    }
    
    /**
     * Internal method to start the quest by talking to the Cook
     */
    private boolean startQuestInternal() {
        logQuest("CooksAssistant DEBUG: Starting quest internal logic");
        
        // Navigate to Lumbridge Castle kitchen
        if (!LUMBRIDGE_CASTLE_KITCHEN.contains(Players.getLocal())) {
            logQuest("CooksAssistant DEBUG: Walking to Lumbridge Castle kitchen");
            if (!Walking.walk(LUMBRIDGE_CASTLE_KITCHEN.getRandomTile())) {
                logQuest("CooksAssistant DEBUG: Failed to initiate walk to kitchen");
                return false;
            }
            
            // Wait for arrival
            if (!Sleep.sleepUntil(() -> LUMBRIDGE_CASTLE_KITCHEN.contains(Players.getLocal()), 10000)) {
                logQuest("CooksAssistant DEBUG: Failed to reach kitchen area");
                return false;
            }
        }
        
        logQuest("CooksAssistant DEBUG: In kitchen area, looking for Cook");
        
        // Find and talk to the Cook
        NPC cook = NPCs.closest(COOK_NPC_NAME);
        if (cook == null) {
            logQuest("CooksAssistant DEBUG: Cook not found in kitchen");
            return false;
        }
        
        logQuest("CooksAssistant DEBUG: Found Cook, attempting to talk");
        if (!cook.interact("Talk-to")) {
            logQuest("CooksAssistant DEBUG: Failed to interact with Cook");
            return false;
        }
        
        // Handle dialogue to start the quest
        if (!Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
            logQuest("CooksAssistant DEBUG: No dialogue opened after talking to Cook");
            return false;
        }
        
        logQuest("CooksAssistant DEBUG: In dialogue, handling quest start sequence");
        
        // Simple direct dialogue handling for Cook's Assistant
        int attempts = 0;
        while (Dialogues.inDialogue() && attempts < 20) {
            attempts++;
            
            if (Dialogues.areOptionsAvailable()) {
                String[] options = Dialogues.getOptions();
                logQuest("CooksAssistant DEBUG: Available dialogue options:");
                for (int i = 0; i < options.length; i++) {
                    logQuest("CooksAssistant DEBUG: Option " + (i+1) + ": " + options[i]);
                }
                
                // Look for "What's wrong?" to start the quest
                boolean foundOption = false;
                for (int i = 0; i < options.length; i++) {
                    if (options[i].toLowerCase().contains("wrong")) {
                        logQuest("CooksAssistant DEBUG: Selecting 'What's wrong?' option");
                        Dialogues.chooseOption(i + 1);
                        foundOption = true;
                        break;
                    }
                }
                
                // If "What's wrong?" not found, look for "Yes" to accept quest
                if (!foundOption) {
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].toLowerCase().contains("yes")) {
                            logQuest("CooksAssistant DEBUG: Selecting 'Yes' option to accept quest");
                            Dialogues.chooseOption(i + 1);
                            foundOption = true;
                            break;
                        }
                    }
                }
                
                // If no specific option found, select first option
                if (!foundOption) {
                    logQuest("CooksAssistant DEBUG: No specific option found, selecting first option");
                    Dialogues.chooseOption(1);
                }
                
                Sleep.sleep(800, 1200);
                
            } else if (Dialogues.canContinue()) {
                logQuest("CooksAssistant DEBUG: Continuing dialogue");
                Dialogues.continueDialogue();
                Sleep.sleep(800, 1200);
            } else {
                logQuest("CooksAssistant DEBUG: No dialogue options available and can't continue");
                break;
            }
        }
        
        logQuest("CooksAssistant DEBUG: Exited dialogue loop after " + attempts + " attempts");
        
        // Wait a bit for quest state to update
        Sleep.sleep(1000, 2000);
        
        // Check quest state with enhanced debugging
        int configValue = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        logQuest("CooksAssistant DEBUG: Final config check - Value: " + configValue + " (Expected >= " + QUEST_IN_PROGRESS + ")");
        
        // Additional state checks for debugging
        logQuest("CooksAssistant DEBUG: Quest journal check...");
        
        // Check if quest was started
        if (isQuestStarted()) {
            logQuest("CooksAssistant DEBUG: Quest successfully started!");
            return true;
        } else {
            logQuest("CooksAssistant DEBUG: Quest not started after dialogue. Current config: " + configValue);
            logQuest("CooksAssistant DEBUG: Trying alternative detection methods...");
            
            // Try alternative quest detection - sometimes the quest state changes with slight delay
            Sleep.sleep(2000, 3000);
            int retryConfigValue = PlayerSettings.getConfig(QUEST_CONFIG_ID);
            logQuest("CooksAssistant DEBUG: Retry config value: " + retryConfigValue);
            
            if (retryConfigValue >= QUEST_IN_PROGRESS) {
                logQuest("CooksAssistant DEBUG: Quest started on retry check!");
                return true;
            }
            
            return false;
        }
    }
    
    /**
     * Progress the quest by gathering items and completing it
     */
    private boolean progressQuest() {
        logQuest("CooksAssistant DEBUG: === PROGRESS QUEST START ===");
        logQuest("CooksAssistant DEBUG: Current location: " + Players.getLocal().getTile());
        logQuest("CooksAssistant DEBUG: Progressing quest...");
        
        // First, ensure we have all required items
        logQuest("CooksAssistant DEBUG: Checking if we have all quest items...");
        if (!hasAllQuestItems()) {
            logQuest("CooksAssistant DEBUG: Missing quest items, gathering them");
            logQuest("CooksAssistant DEBUG: === STARTING ITEM GATHERING ===");
            
            ItemGatheringUtil.GatheringResult result = gatherQuestItems();
            
            logQuest("CooksAssistant DEBUG: === ITEM GATHERING COMPLETE ===");
            logQuest("CooksAssistant DEBUG: Gathering success: " + result.isSuccess());
            logQuest("CooksAssistant DEBUG: Last action: " + result.getLastAction());
            logQuest("CooksAssistant DEBUG: Obtained items: " + result.getObtainedItems());
            logQuest("CooksAssistant DEBUG: Missing items: " + result.getMissingItems());
            
            if (!result.isSuccess()) {
                logQuest("CooksAssistant DEBUG: Failed to gather required items");
                return handleMissingItems(result);
            }
        } else {
            logQuest("CooksAssistant DEBUG: All quest items already in inventory!");
        }
        
        logQuest("CooksAssistant DEBUG: All items gathered, completing quest");
        return completeQuest();
    }
    
    /**
     * Gather all required quest items using our unified gathering system
     */
    private ItemGatheringUtil.GatheringResult gatherQuestItems() {
        logQuest("CooksAssistant DEBUG: === GATHER QUEST ITEMS START ===");
        logQuest("CooksAssistant DEBUG: Gathering quest items with Grand Exchange purchasing");
        logQuest("CooksAssistant DEBUG: Current inventory space: " + Inventory.getEmptySlots());
        
        // Check current inventory state
        logQuest("CooksAssistant DEBUG: Current inventory contents:");
        for (int i = 0; i < 28; i++) {
            if (Inventory.get(i) != null) {
                logQuest("CooksAssistant DEBUG: Slot " + i + ": " + Inventory.get(i).getName());
            }
        }
        
        // Define requirements with explicit Grand Exchange purchasing
        ItemRequirement[] requirements = {
            new ItemRequirement(EGG, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE),
            new ItemRequirement(BUCKET_OF_MILK, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE),
            new ItemRequirement(POT_OF_FLOUR, 1, GrandExchangeUtil.PriceStrategy.AGGRESSIVE)
        };
        
        // Log what we're trying to gather
        logQuest("CooksAssistant DEBUG: === QUEST ITEM REQUIREMENTS ===");
        for (ItemRequirement req : requirements) {
            logQuest("CooksAssistant DEBUG: Required item: " + req.getItemName() + " x" + req.getQuantity());
            logQuest("CooksAssistant DEBUG: Current in inventory: " + Inventory.count(req.getItemName()));
        }
        
        logQuest("CooksAssistant DEBUG: === CALLING ItemGatheringUtil.gatherItems() ===");
        
        // Use unified gathering system
        ItemGatheringUtil.GatheringResult result = ItemGatheringUtil.gatherItems(requirements);
        
        logQuest("CooksAssistant DEBUG: === ItemGatheringUtil.gatherItems() RETURNED ===");
        logQuest("CooksAssistant DEBUG: Item gathering result: " + (result.isSuccess() ? "SUCCESS" : "FAILED"));
        if (!result.isSuccess()) {
            logQuest("CooksAssistant DEBUG: Gathering failed - " + result.getLastAction());
            logQuest("CooksAssistant DEBUG: Missing items: " + result.getMissingItems());
        } else {
            logQuest("CooksAssistant DEBUG: Successfully obtained: " + result.getObtainedItems());
        }
        
        return result;
    }
    
    /**
     * Handle cases where required items are missing
     */
    private boolean handleMissingItems(ItemGatheringUtil.GatheringResult result) {
        logQuest("CooksAssistant DEBUG: Handling missing items");
        logQuest("CooksAssistant DEBUG: Last action: " + result.getLastAction());
        
        // Log current inventory state for debugging
        logQuest("CooksAssistant DEBUG: Current inventory check - Egg: " + Inventory.contains(EGG) + 
                  ", Milk: " + Inventory.contains(BUCKET_OF_MILK) + 
                  ", Flour: " + Inventory.contains(POT_OF_FLOUR));
        
        // Return false to indicate we need to try again
        return false;
    }
    
    /**
     * Complete the quest by returning items to the Cook
     */
    private boolean completeQuest() {
        logQuest("CooksAssistant DEBUG: Attempting to complete quest");
        
        // Verify we have all items
        if (!hasAllQuestItems()) {
            logQuest("CooksAssistant DEBUG: Cannot complete quest - missing required items");
            return false;
        }
        
        // Navigate to kitchen if not already there
        if (!LUMBRIDGE_CASTLE_KITCHEN.contains(Players.getLocal())) {
            logQuest("CooksAssistant DEBUG: Walking to kitchen for quest completion");
            if (!Walking.walk(LUMBRIDGE_CASTLE_KITCHEN.getRandomTile())) {
                return false;
            }
            
            if (!Sleep.sleepUntil(() -> LUMBRIDGE_CASTLE_KITCHEN.contains(Players.getLocal()), 10000)) {
                return false;
            }
        }
        
        // Find and talk to the Cook
        NPC cook = NPCs.closest(COOK_NPC_NAME);
        if (cook == null) {
            logQuest("CooksAssistant DEBUG: Cook not found for quest completion");
            return false;
        }
        
        logQuest("CooksAssistant DEBUG: Talking to Cook to complete quest");
        if (!cook.interact("Talk-to")) {
            return false;
        }
        
        // Handle completion dialogue
        if (!Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
            logQuest("CooksAssistant DEBUG: No dialogue opened for quest completion");
            return false;
        }
        
        logQuest("CooksAssistant DEBUG: In completion dialogue");
        
        // Quest completion dialogue sequence
        DialogueStep[] completionDialogue = {
            new DialogueStep(DialogueStep.StepType.CONTINUE, "Hand in items"),
            new DialogueStep(DialogueStep.StepType.CONTINUE, "Quest completion"),
            new DialogueStep(DialogueStep.StepType.CONTINUE, "Rewards received")
        };
        
        if (DialogueUtil.handleDialogueSequence(completionDialogue)) {
            logQuest("CooksAssistant: Completion dialogue handled successfully");
            
            // Give extra time for quest state to update after dialogue
            Sleep.sleep(2000, 3000);
            
            // Wait for quest completion with longer timeout
            if (Sleep.sleepUntil(this::isQuestComplete, 10000)) {
                logQuest("CooksAssistant: [SUCCESS] QUEST COMPLETED SUCCESSFULLY!");
                return true;
            } else {
                // Check final state manually for debugging
                int config101 = PlayerSettings.getConfig(101);
                int config29 = PlayerSettings.getConfig(29);  
                logQuest("CooksAssistant: Final state check - Config 101: " + config101 + ", Config 29: " + config29);
                
                // If Config 29 is 2, the quest IS complete even if sleepUntil timed out
                if (config29 >= 2) {
                    logQuest("CooksAssistant: [SUCCESS] QUEST ACTUALLY COMPLETED (Config 29: " + config29 + ")");
                    return true;
                } else {
                    logQuest("CooksAssistant: [ERROR] Quest not completed after dialogue");
                    return false;
                }
            }
        } else {
            logQuest("CooksAssistant: Failed to handle completion dialogue");
            return false;
        }
    }
    
    /**
     * Check if we have all required quest items
     */
    private boolean hasAllQuestItems() {
        boolean hasEgg = Inventory.contains(EGG);
        boolean hasMilk = Inventory.contains(BUCKET_OF_MILK);
        boolean hasFlour = Inventory.contains(POT_OF_FLOUR);
        
        logQuest("CooksAssistant DEBUG: Inventory check - Egg: " + hasEgg + 
                  ", Milk: " + hasMilk + ", Flour: " + hasFlour);
        
        return hasEgg && hasMilk && hasFlour;
    }
    
    /**
     * Get a string describing which items are missing
     */
    private String getMissingItemsString() {
        StringBuilder missing = new StringBuilder();
        
        if (!Inventory.contains(EGG)) {
            missing.append("Egg, ");
        }
        if (!Inventory.contains(BUCKET_OF_MILK)) {
            missing.append("Bucket of milk, ");
        }
        if (!Inventory.contains(POT_OF_FLOUR)) {
            missing.append("Pot of flour, ");
        }
        
        String result = missing.toString();
        return result.isEmpty() ? "None" : result.substring(0, result.length() - 2); // Remove trailing ", "
    }
}
