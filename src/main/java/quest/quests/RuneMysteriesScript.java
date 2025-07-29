/**
 * RuneMysteriesScript.java
 * 
 * Automated quest script for the Rune Mysteries quest in OSRS.
 * This script handles the complete quest from start to finish.
 * 
 * Quest Overview:
 * 1. Talk to Duke Horacio in Lumbridge Castle to start
 * 2. Talk to Archmage Sedridor in Wizards' Tower to get Research package
 * 3. Deliver package to Aubury in Varrock East to get Research notes
 * 4. Return Research notes to Sedridor to complete quest
 * 
 * Quest Details:
 * - Config ID: 101 (0=not started, 1=in progress, 2=complete)
 * - No items required to start (Air talisman given during quest)
 * - No combat required
 */

package quest.quests;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.script.AbstractScript;
import quest.core.QuestScript;
import quest.core.QuestDatabase;
import quest.core.QuestEventLogger;
import quest.utils.DialogueUtil;

public class RuneMysteriesScript implements QuestScript {
    
    private static final String QUEST_NAME = "Rune Mysteries";
    private static final String QUEST_ID = "RUNE_MYSTERIES";
    
    // Quest progress tracking - Rune Mysteries uses config 101
    private static final int QUEST_CONFIG_ID = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_IN_PROGRESS = 1;
    private static final int QUEST_COMPLETE = 2;
    
    // Quest locations
    private static final Area DUKE_HORACIO_AREA = new Area(3207, 3221, 3213, 3227, 1);
    private static final Area WIZARDS_TOWER_AREA = new Area(3101, 3159, 3107, 3166, 0);
    private static final Area AUBURY_SHOP_AREA = new Area(3250, 3400, 3256, 3406, 0);
    
    // Specific coordinates from discovery log
    private static final Tile DUKE_LOCATION = new Tile(3210, 3224, 1);
    private static final Tile SEDRIDOR_LOCATION = new Tile(3102, 9570, 0);
    private static final Tile AUBURY_LOCATION = new Tile(3253, 3403, 0);
    
    // Quest items
    private static final String AIR_TALISMAN = "Air talisman";
    private static final String RESEARCH_PACKAGE = "Research package";
    private static final String RESEARCH_NOTES = "Research notes";
    
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
        this.questLogger = new QuestEventLogger(script, QUEST_NAME);
        this.simpleLogger = quest.utils.QuestLogger.getInstance();
        this.simpleLogger.initializeQuest("Rune_Mysteries");
        
        logQuest("RuneMysteriesScript: Script initialized successfully");
        logQuest("RuneMysteriesScript: Quest logger initialized - logs will be saved to quest_logs/");
    }
    
    /**
     * Quest-specific logging method that writes to both console and quest log files
     */
    private void logQuest(String message) {
        if (script != null) {
            script.log(message);
        }
        Logger.log(message);
        
        if (simpleLogger != null) {
            simpleLogger.log(message);
        }
    }
    
    @Override
    public String getQuestId() {
        return QUEST_ID;
    }
    
    @Override
    public String getQuestName() {
        return QUEST_NAME;
    }
    
    @Override
    public boolean canStart() {
        int questProgress = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        return questProgress == QUEST_NOT_STARTED;
    }
    
    @Override
    public boolean startQuest() {
        logQuest("Starting Rune Mysteries quest with Duke Horacio");
        
        if (!DUKE_HORACIO_AREA.contains(Players.getLocal())) {
            logQuest("Walking to Duke Horacio in Lumbridge Castle");
            if (!Walking.walk(DUKE_LOCATION)) {
                return false;
            }
            Sleep.sleepUntil(() -> DUKE_HORACIO_AREA.contains(Players.getLocal()), 10000);
        }
        
        NPC duke = NPCs.closest("Duke Horacio");
        if (duke != null && duke.interact("Talk-to")) {
            logQuest("Talking to Duke Horacio");
            Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
            
            return handleDialogue();
        }
        
        return false;
    }
    
    @Override
    public boolean executeCurrentStep() {
        int questProgress = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        logQuest("Current quest progress: Config 101 = " + questProgress);
        
        switch (questProgress) {
            case QUEST_NOT_STARTED:
                logQuest("Quest not started - heading to Duke Horacio");
                return startQuest();
                
            case QUEST_IN_PROGRESS:
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    logQuest("Have research package - going to Aubury");
                    return deliverPackageToAubury();
                } else if (Inventory.contains(RESEARCH_NOTES)) {
                    logQuest("Have research notes - returning to Sedridor");
                    return returnNotesToSedridor();
                } else {
                    logQuest("Need to get research package from Sedridor");
                    return getResearchPackage();
                }
                
            default:
                if (isComplete()) {
                    logQuest("Rune Mysteries quest completed!");
                    return true;
                } else {
                    logQuest("Unknown quest state: Config 101 = " + questProgress);
                    return false;
                }
        }
    }
    
    private boolean getResearchPackage() {
        logQuest("Getting research package from Archmage Sedridor");
        
        if (!WIZARDS_TOWER_AREA.contains(Players.getLocal())) {
            logQuest("Walking to Wizards' Tower");
            if (!Walking.walk(new Tile(3104, 3162, 0))) {
                return false;
            }
            Sleep.sleepUntil(() -> WIZARDS_TOWER_AREA.contains(Players.getLocal()), 15000);
        }
        
        // Go down into the tower basement
        if (Players.getLocal().getZ() != 0) {
            logQuest("Going down to basement");
            if (Walking.walk(new Tile(3105, 3162, 0))) {
                Sleep.sleepUntil(() -> Players.getLocal().getZ() == 0, 8000);
            }
        }
        
        NPC sedridor = NPCs.closest("Archmage Sedridor");
        if (sedridor != null && sedridor.interact("Talk-to")) {
            logQuest("Talking to Archmage Sedridor");
            Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
            
            if (handleDialogue()) {
                Sleep.sleepUntil(() -> Inventory.contains(RESEARCH_PACKAGE), 8000);
                
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    logQuest("[SUCCESS] Received research package from Sedridor");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean deliverPackageToAubury() {
        logQuest("Delivering research package to Aubury");
        
        if (!AUBURY_SHOP_AREA.contains(Players.getLocal())) {
            logQuest("Walking to Aubury in Varrock East");
            if (!Walking.walk(AUBURY_LOCATION)) {
                return false;
            }
            Sleep.sleepUntil(() -> AUBURY_SHOP_AREA.contains(Players.getLocal()), 20000);
        }
        
        NPC aubury = NPCs.closest("Aubury");
        if (aubury != null && aubury.interact("Talk-to")) {
            logQuest("Talking to Aubury");
            Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
            
            if (handleDialogue()) {
                Sleep.sleepUntil(() -> Inventory.contains(RESEARCH_NOTES), 8000);
                
                if (Inventory.contains(RESEARCH_NOTES)) {
                    logQuest("[SUCCESS] Received research notes from Aubury");
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private boolean returnNotesToSedridor() {
        logQuest("Returning research notes to Archmage Sedridor");
        
        if (!WIZARDS_TOWER_AREA.contains(Players.getLocal())) {
            logQuest("Walking back to Wizards' Tower");
            if (!Walking.walk(new Tile(3104, 3162, 0))) {
                return false;
            }
            Sleep.sleepUntil(() -> WIZARDS_TOWER_AREA.contains(Players.getLocal()), 15000);
        }
        
        // Go back down to basement
        if (Players.getLocal().getZ() != 0) {
            if (Walking.walk(new Tile(3105, 3162, 0))) {
                Sleep.sleepUntil(() -> Players.getLocal().getZ() == 0, 8000);
            }
        }
        
        NPC sedridor = NPCs.closest("Archmage Sedridor");
        if (sedridor != null && sedridor.interact("Talk-to")) {
            logQuest("Completing quest with Archmage Sedridor");
            Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
            
            if (handleDialogue()) {
                Sleep.sleepUntil(() -> isComplete(), 10000);
                
                // Manual fallback check
                if (!isComplete()) {
                    Sleep.sleep(2000, 3000);
                    int finalConfig = PlayerSettings.getConfig(QUEST_CONFIG_ID);
                    if (finalConfig >= QUEST_COMPLETE) {
                        logQuest("[SUCCESS] Quest completed! Final config 101: " + finalConfig);
                        return true;
                    }
                }
                
                return isComplete();
            }
        }
        
        return false;
    }
    
    @Override
    public int getCurrentProgress() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        switch (config) {
            case QUEST_NOT_STARTED:
                return 0;
            case QUEST_IN_PROGRESS:
                return 50;
            case QUEST_COMPLETE:
                return 100;
            default:
                return 0;
        }
    }
    
    @Override
    public boolean isComplete() {
        int config101 = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        boolean isComplete = config101 >= QUEST_COMPLETE;
        
        if (isComplete && !wasCompleteLastCheck) {
            logQuest("[SUCCESS] Quest completed! Config 101: " + config101);
            wasCompleteLastCheck = true;
        }
        
        return isComplete;
    }
    
    @Override
    public String getCurrentStepDescription() {
        int questProgress = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        
        switch (questProgress) {
            case QUEST_NOT_STARTED:
                return "Talk to Duke Horacio in Lumbridge Castle to start quest";
            case QUEST_IN_PROGRESS:
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    return "Deliver research package to Aubury in Varrock East";
                } else if (Inventory.contains(RESEARCH_NOTES)) {
                    return "Return research notes to Sedridor in Wizards' Tower";
                } else {
                    return "Get research package from Sedridor in Wizards' Tower";
                }
            case QUEST_COMPLETE:
                return "Quest completed!";
            default:
                return "Unknown quest state: Config 101 = " + questProgress;
        }
    }
    
    @Override
    public boolean hasRequiredItems() {
        // No items required to start this quest
        return true;
    }
    
    @Override
    public String[] getRequiredItems() {
        // No items required to start this quest
        return new String[0];
    }
    
    @Override
    public boolean handleDialogue() {
        if (!Dialogues.inDialogue()) {
            return false;
        }
        
        // Use the generic dialogue handler which should work for most cases
        return DialogueUtil.handleDialogueSequence();
    }
    
    @Override
    public boolean navigateToObjective() {
        int questProgress = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        
        switch (questProgress) {
            case QUEST_NOT_STARTED:
                return Walking.walk(DUKE_LOCATION);
            case QUEST_IN_PROGRESS:
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    return Walking.walk(AUBURY_LOCATION);
                } else if (Inventory.contains(RESEARCH_NOTES)) {
                    return Walking.walk(new Tile(3104, 3162, 0));
                } else {
                    return Walking.walk(new Tile(3104, 3162, 0));
                }
            default:
                return true;
        }
    }
    
    @Override
    public void cleanup() {
        logQuest("RuneMysteriesScript: Cleaning up resources");
        if (questLogger != null) {
            // Any cleanup for quest logger if needed
        }
    }
    
    @Override
    public void onQuestStart() {
        logQuest("[SUCCESS] Rune Mysteries quest started!");
    }
    
    @Override
    public void onQuestComplete() {
        logQuest("[SUCCESS] Rune Mysteries quest completed successfully!");
        if (simpleLogger != null) {
            simpleLogger.success("Rune Mysteries quest completed!");
        }
    }
} 