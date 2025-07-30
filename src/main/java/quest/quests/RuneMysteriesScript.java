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
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Logger;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.script.AbstractScript;
import quest.core.QuestScript;
import quest.core.QuestDatabase;
import quest.core.QuestEventLogger;
import quest.utils.DialogueUtil;
import quest.utils.QuestLogger;
import java.util.Arrays;

public class RuneMysteriesScript implements QuestScript {
    
    private static final String QUEST_NAME = "Rune Mysteries";
    private static final String QUEST_ID = "RUNE_MYSTERIES";
    
    // Quest progress tracking - Rune Mysteries uses config 101 (from discovery log)
    private static final int QUEST_CONFIG_ID = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 1;        // Talked to Duke, have Air talisman
    private static final int QUEST_COMPLETE = 2;       // Quest completed
    
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
            Walking.walk(DUKE_LOCATION);
            boolean arrived = Sleep.sleepUntil(() -> DUKE_HORACIO_AREA.contains(Players.getLocal()), 15000);
            if (!arrived) {
                logQuest("[ERROR] Failed to reach Duke Horacio area within 15 seconds");
                return false;
            }
        }
        
        NPC duke = NPCs.closest("Duke Horacio");
        if (duke != null) {
            logQuest("Found Duke Horacio, attempting interaction");
            if (duke.interact("Talk-to")) {
                boolean inDialogue = Sleep.sleepUntil(() -> Dialogues.inDialogue(), 8000);
                if (!inDialogue) {
                    logQuest("[ERROR] Failed to start dialogue with Duke Horacio");
                    return false;
                }
                
                return handleDukeDialogue();
            } else {
                logQuest("[ERROR] Failed to interact with Duke Horacio");
                return false;
            }
        } else {
            logQuest("[ERROR] Duke Horacio not found");
            return false;
        }
    }
    
    @Override
    public boolean executeCurrentStep() {
        int questProgress = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        logQuest("Current quest progress: Config 101 = " + questProgress);
        
        // Enhanced state detection based on inventory items + config
        String currentStep = determineCurrentQuestStep(questProgress);
        logQuest("Determined current step: " + currentStep);
        
        switch (questProgress) {
            case QUEST_NOT_STARTED:
                logQuest("Quest not started - heading to Duke Horacio");
                return startQuest();
                
            case QUEST_STARTED:
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    logQuest("Have research package - going to Aubury");
                    return deliverPackageToAubury();
                } else if (Inventory.contains(RESEARCH_NOTES)) {
                    logQuest("Have research notes - returning to Sedridor");
                    return returnNotesToSedridor();
                } else if (Inventory.contains(AIR_TALISMAN)) {
                    logQuest("Have air talisman - going to Sedridor for research package");
                    return getResearchPackage();
                } else {
                    logQuest("Quest started but missing items - restarting from Duke");
                    return startQuest();
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
    
    /**
     * Determine current quest step based on config + inventory items
     */
    private String determineCurrentQuestStep(int configValue) {
        if (configValue >= QUEST_COMPLETE) {
            return "QUEST_COMPLETE";
        } else if (configValue >= QUEST_STARTED) {
            if (Inventory.contains(RESEARCH_NOTES)) {
                return "HAVE_RESEARCH_NOTES - Return to Sedridor";
            } else if (Inventory.contains(RESEARCH_PACKAGE)) {
                return "HAVE_RESEARCH_PACKAGE - Go to Aubury";
            } else if (Inventory.contains(AIR_TALISMAN)) {
                return "HAVE_AIR_TALISMAN - Go to Sedridor";
            } else {
                return "QUEST_STARTED - Missing items, restart from Duke";
            }
        } else {
            return "QUEST_NOT_STARTED";
        }
    }
    
    private boolean getResearchPackage() {
        logQuest("Getting research package from Archmage Sedridor");
        
        // Step 1: Navigate to Wizards' Tower area (ground level) - CLEAN WALKING
        if (!WIZARDS_TOWER_AREA.contains(Players.getLocal())) {
            Tile target = new Tile(3104, 3162, 0);
            
            logQuest("Walking to Wizards' Tower (distance: " + 
                   String.format("%.1f", Players.getLocal().getTile().distance(target)) + ")");
            
            boolean walkResult = Walking.walk(target);
            if (!walkResult) {
                logQuest("[ERROR] Walking.walk() returned false - walk initiation failed!");
                return false;
            }
            
            // Enhanced waiting with reduced logging - NO RESTRICTIVE TIMEOUT
            final long startTime = System.currentTimeMillis();
            final int[] checkCounter = {0};
            boolean arrived = Sleep.sleepUntil(() -> {
                checkCounter[0]++;
                Tile currentPos = Players.getLocal().getTile();
                boolean inArea = WIZARDS_TOWER_AREA.contains(Players.getLocal());
                boolean isMoving = Players.getLocal().isMoving();
                boolean hasDestination = Walking.getDestination() != null;
                long elapsed = System.currentTimeMillis() - startTime;
                
                // REDUCED LOGGING: Every 50 checks (~15 seconds) instead of every 10
                if (checkCounter[0] % 50 == 0) {
                    logQuest("=== WALK PROGRESS #" + checkCounter[0] + " ===");
                    logQuest("Time elapsed: " + String.format("%.1f", elapsed / 1000.0) + " seconds");
                    logQuest("Distance remaining: " + String.format("%.1f", currentPos.distance(target)));
                    logQuest("InArea: " + inArea + ", IsMoving: " + isMoving);
                }
                
                // Check if we arrived successfully
                if (inArea) {
                    logQuest("SUCCESS: Player reached Wizards' Tower area!");
                    logQuest("Total walking time: " + String.format("%.1f", elapsed / 1000.0) + " seconds");
                    return true;
                }
                
                // Trust DreamBot's WebWalking - NO INTERNAL RETRY LOGIC
                // Let WebWalking handle pathfinding and temporary pauses naturally
                return false;
                
            }, 600000); // 10-minute maximum timeout (only for extreme cases)
            
            logQuest("=== WALKING COMPLETE ===");
            logQuest("Final result - Arrived: " + arrived);
            
            if (!arrived) {
                logQuest("[ERROR] Failed to reach Wizards' Tower within 10 minutes (likely stuck or disconnected)");
                return false;
            }
        }
        
        // Step 2: Go down to basement using staircase interaction (not Walking.walk)
        if (Players.getLocal().getZ() == 0) { // If on ground floor (Z=0), need to go down
            logQuest("Going down to basement via staircase");
            
            // Find the ladder to go down
            GameObject staircase = GameObjects.closest(obj -> 
                obj != null && obj.getName().equals("Ladder") && 
                obj.hasAction("Climb-down") && obj.distance() < 10);
            
            if (staircase == null) {
                logQuest("[ERROR] Ladder not found for going down to basement");
                return false;
            }
            
            logQuest("Found ladder at " + staircase.getTile() + ", interacting with Climb-down");
            if (!staircase.interact("Climb-down")) {
                logQuest("[ERROR] Failed to interact with ladder");
                return false;
            }
            
            // Multi-stage waiting for ladder transition
            boolean transitionCompleted = Sleep.sleepUntil(() -> {
                Tile currentPos = Players.getLocal().getTile();
                int currentZ = Players.getLocal().getZ();
                boolean isMoving = Players.getLocal().isMoving();
                
                // Check if we've been teleported to basement (Y coordinate changes significantly)
                boolean inBasement = currentPos.getY() > 9000; // Basement coordinates typically have Y > 9000
                
                // Log progress occasionally  
                if (System.currentTimeMillis() % 2000 < 100) { // Every ~2 seconds
                    logQuest("Staircase transition: Pos=" + currentPos + ", Z=" + currentZ + 
                           ", InBasement=" + inBasement + ", IsMoving=" + isMoving);
                }
                
                return inBasement && !isMoving; // Must be in basement coordinates AND stopped moving
            }, 15000); // 15-second timeout for staircase transition
            
            if (!transitionCompleted) {
                logQuest("[ERROR] Failed to complete staircase transition to basement within 15 seconds");
                return false;
            }
            logQuest("Successfully transitioned to basement");
        }
        
        // Step 3: Verify Sedridor is visible and interactable
        logQuest("Looking for Archmage Sedridor in basement");
        boolean sedridorFound = Sleep.sleepUntil(() -> {
            NPC sedridor = NPCs.closest("Archmage Sedridor");
            boolean found = sedridor != null && sedridor.isOnScreen();
            
            if (System.currentTimeMillis() % 2000 < 100) { // Every ~2 seconds
                logQuest("Sedridor search: Found=" + (sedridor != null) + 
                       ", OnScreen=" + (sedridor != null && sedridor.isOnScreen()));
            }
            
            return found;
        }, 10000); // 10-second timeout for NPC to appear
        
        if (!sedridorFound) {
            logQuest("[ERROR] Archmage Sedridor not found or not visible after basement transition");
            return false;
        }
        
        NPC sedridor = NPCs.closest("Archmage Sedridor");
        if (sedridor != null) {
            logQuest("Found Archmage Sedridor at " + sedridor.getTile() + ", attempting interaction");
            if (sedridor.interact("Talk-to")) {
                boolean inDialogue = Sleep.sleepUntil(() -> Dialogues.inDialogue(), 8000);
                if (!inDialogue) {
                    logQuest("[ERROR] Failed to start dialogue with Archmage Sedridor");
                    return false;
                }
                
                if (handleSedridorDialogue()) {
                    boolean receivedPackage = Sleep.sleepUntil(() -> Inventory.contains(RESEARCH_PACKAGE), 10000);
                    if (receivedPackage) {
                        logQuest("[SUCCESS] Received research package from Sedridor");
                        return true;
                    } else {
                        logQuest("[ERROR] Failed to receive research package within 10 seconds");
                        return false;
                    }
                } else {
                    logQuest("[ERROR] Failed to handle dialogue with Sedridor");
                    return false;
                }
            } else {
                logQuest("[ERROR] Failed to interact with Archmage Sedridor");
                return false;
            }
        } else {
            logQuest("[ERROR] Archmage Sedridor not found");
            return false;
        }
    }
    
    private boolean deliverPackageToAubury() {
        logQuest("Delivering research package to Aubury");
        
        if (!AUBURY_SHOP_AREA.contains(Players.getLocal())) {
            logQuest("Walking to Aubury in Varrock East");
            Walking.walk(AUBURY_LOCATION);
            boolean arrived = Sleep.sleepUntil(() -> AUBURY_SHOP_AREA.contains(Players.getLocal()), 25000);
            if (!arrived) {
                logQuest("[ERROR] Failed to reach Aubury area within 25 seconds");
                return false;
            }
        }
        
        NPC aubury = NPCs.closest("Aubury");
        if (aubury != null) {
            logQuest("Found Aubury, attempting interaction");
            if (aubury.interact("Talk-to")) {
                boolean inDialogue = Sleep.sleepUntil(() -> Dialogues.inDialogue(), 8000);
                if (!inDialogue) {
                    logQuest("[ERROR] Failed to start dialogue with Aubury");
                    return false;
                }
                
                if (handleAuburyDialogue()) {
                    boolean receivedNotes = Sleep.sleepUntil(() -> Inventory.contains(RESEARCH_NOTES), 10000);
                    if (receivedNotes) {
                        logQuest("[SUCCESS] Received research notes from Aubury");
                        return true;
                    } else {
                        logQuest("[ERROR] Failed to receive research notes within 10 seconds");
                        return false;
                    }
                } else {
                    logQuest("[ERROR] Failed to handle dialogue with Aubury");
                    return false;
                }
            } else {
                logQuest("[ERROR] Failed to interact with Aubury");
                return false;
            }
        } else {
            logQuest("[ERROR] Aubury not found");
            return false;
        }
    }
    
    private boolean returnNotesToSedridor() {
        logQuest("Returning research notes to Archmage Sedridor");
        
        // Step 1: Navigate to Wizards' Tower area (ground level) - CLEAN WALKING  
        if (!WIZARDS_TOWER_AREA.contains(Players.getLocal())) {
            Tile target = new Tile(3104, 3162, 0);
            
            logQuest("Walking back to Wizards' Tower (distance: " + 
                   String.format("%.1f", Players.getLocal().getTile().distance(target)) + ")");
            
            boolean walkResult = Walking.walk(target);
            if (!walkResult) {
                logQuest("[ERROR] Return Walking.walk() returned false - walk initiation failed!");
                return false;
            }
            
            // Wait 1 second to see if walking actually started
            Sleep.sleep(1000);
            logQuest("1 second after return walk - Position: " + Players.getLocal().getTile() + 
                   ", IsMoving: " + Players.getLocal().isMoving());
            
            // Enhanced waiting with detailed debugging every check
            final long startTime = System.currentTimeMillis();
            final int[] checkCounter = {0};
            boolean arrived = Sleep.sleepUntil(() -> {
                checkCounter[0]++;
                Tile currentPos = Players.getLocal().getTile();
                boolean inArea = WIZARDS_TOWER_AREA.contains(Players.getLocal());
                boolean isMoving = Players.getLocal().isMoving();
                boolean hasDestination = Walking.getDestination() != null;
                long elapsed = System.currentTimeMillis() - startTime;
                
                // REDUCED LOGGING: Every 50 checks (~15 seconds) instead of every 10
                if (checkCounter[0] % 50 == 0) {
                    logQuest("=== RETURN WALK PROGRESS #" + checkCounter[0] + " ===");
                    logQuest("Time elapsed: " + String.format("%.1f", elapsed / 1000.0) + " seconds");
                    logQuest("Distance remaining: " + String.format("%.1f", currentPos.distance(target)));
                    logQuest("InArea: " + inArea + ", IsMoving: " + isMoving);
                }
                
                // Check if we arrived successfully
                if (inArea) {
                    logQuest("SUCCESS: Player returned to Wizards' Tower area!");
                    logQuest("Total return walking time: " + String.format("%.1f", elapsed / 1000.0) + " seconds");
                    return true;
                }
                
                // Trust DreamBot's WebWalking - NO INTERNAL RETRY LOGIC
                // Let WebWalking handle pathfinding and temporary pauses naturally
                return false;
                
            }, 600000); // 10-minute maximum timeout (only for extreme cases)
            
            logQuest("=== RETURN WALKING COMPLETE ===");
            logQuest("Final return result - Arrived: " + arrived);
            
            if (!arrived) {
                logQuest("[ERROR] Failed to return to Wizards' Tower within 10 minutes (likely stuck or disconnected)");
                return false;
            }
        }
        
        // Step 2: Go down to basement using staircase interaction (not Walking.walk)
        if (Players.getLocal().getZ() == 0) { // If on ground floor (Z=0), need to go down
            logQuest("Going back down to basement via staircase");
            
            // Find the ladder to go down
            GameObject staircase = GameObjects.closest(obj -> 
                obj != null && obj.getName().equals("Ladder") && 
                obj.hasAction("Climb-down") && obj.distance() < 10);
            
            if (staircase == null) {
                logQuest("[ERROR] Ladder not found for going down to basement");
                return false;
            }
            
            logQuest("Found ladder at " + staircase.getTile() + ", interacting with Climb-down");
            if (!staircase.interact("Climb-down")) {
                logQuest("[ERROR] Failed to interact with ladder");
                return false;
            }
            
            // Multi-stage waiting for staircase transition
            boolean transitionCompleted = Sleep.sleepUntil(() -> {
                Tile currentPos = Players.getLocal().getTile();
                int currentZ = Players.getLocal().getZ();
                boolean isMoving = Players.getLocal().isMoving();
                
                // Check if we've been teleported to basement (Y coordinate changes significantly)
                boolean inBasement = currentPos.getY() > 9000; // Basement coordinates typically have Y > 9000
                
                // Log progress occasionally  
                if (System.currentTimeMillis() % 2000 < 100) { // Every ~2 seconds
                    logQuest("Return staircase transition: Pos=" + currentPos + ", Z=" + currentZ + 
                           ", InBasement=" + inBasement + ", IsMoving=" + isMoving);
                }
                
                return inBasement && !isMoving; // Must be in basement coordinates AND stopped moving
            }, 15000); // 15-second timeout for staircase transition
            
            if (!transitionCompleted) {
                logQuest("[ERROR] Failed to complete return staircase transition to basement within 15 seconds");
                return false;
            }
            logQuest("Successfully transitioned back to basement");
        }
        
        // Step 3: Verify Sedridor is visible and interactable
        logQuest("Looking for Archmage Sedridor in basement for quest completion");
        boolean sedridorFound = Sleep.sleepUntil(() -> {
            NPC sedridor = NPCs.closest("Archmage Sedridor");
            boolean found = sedridor != null && sedridor.isOnScreen();
            
            if (System.currentTimeMillis() % 2000 < 100) { // Every ~2 seconds
                logQuest("Final Sedridor search: Found=" + (sedridor != null) + 
                       ", OnScreen=" + (sedridor != null && sedridor.isOnScreen()));
            }
            
            return found;
        }, 10000); // 10-second timeout for NPC to appear
        
        if (!sedridorFound) {
            logQuest("[ERROR] Archmage Sedridor not found or not visible after return basement transition");
            return false;
        }
        
        NPC sedridor = NPCs.closest("Archmage Sedridor");
        if (sedridor != null) {
            logQuest("Found Archmage Sedridor at " + sedridor.getTile() + ", completing quest");
            if (sedridor.interact("Talk-to")) {
                boolean inDialogue = Sleep.sleepUntil(() -> Dialogues.inDialogue(), 8000);
                if (!inDialogue) {
                    logQuest("[ERROR] Failed to start dialogue with Archmage Sedridor");
                    return false;
                }
                
                if (handleFinalSedridorDialogue()) {
                    boolean questCompleted = Sleep.sleepUntil(() -> isComplete(), 12000);
                    
                                    // Manual fallback check if sleepUntil times out
                if (!questCompleted) {
                    Sleep.sleep(2000, 3000);
                    int finalConfig = PlayerSettings.getConfig(QUEST_CONFIG_ID);
                    if (finalConfig >= QUEST_COMPLETE) {
                        logQuest("[SUCCESS] Quest completed! Final config 101: " + finalConfig);
                        return true;
                    } else {
                        logQuest("[ERROR] Quest not completed after dialogue. Config 101: " + finalConfig);
                        return false;
                    }
                }
                    
                    return isComplete();
                } else {
                    logQuest("[ERROR] Failed to handle final dialogue with Sedridor");
                    return false;
                }
            } else {
                logQuest("[ERROR] Failed to interact with Archmage Sedridor");
                return false;
            }
        } else {
            logQuest("[ERROR] Archmage Sedridor not found for quest completion");
            return false;
        }
    }
    
    @Override
    public int getCurrentProgress() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        switch (config) {
            case QUEST_NOT_STARTED:
                return 0;
            case QUEST_STARTED:
                if (Inventory.contains(RESEARCH_NOTES)) {
                    return 75; // Almost complete
                } else if (Inventory.contains(RESEARCH_PACKAGE)) {
                    return 50; // Halfway
                } else {
                    return 25; // Just started
                }
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
            case QUEST_STARTED:
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    return "Deliver research package to Aubury in Varrock East";
                } else if (Inventory.contains(RESEARCH_NOTES)) {
                    return "Return research notes to Sedridor in Wizards' Tower";
                } else if (Inventory.contains(AIR_TALISMAN)) {
                    return "Take air talisman to Sedridor in Wizards' Tower";
                } else {
                    return "Quest started but missing items - restart from Duke Horacio";
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
            logQuest("[ERROR] Not in dialogue when handleDialogue() called");
            return false;
        }
        
        // Determine what we expect to receive based on current quest state
        String expectedItem = determineExpectedDialogueOutcome();
        logQuest("Dialogue context: Expecting " + expectedItem);
        
        int attempts = 0;
        int maxAttempts = 20; // Prevent infinite loops
        
        while (Dialogues.inDialogue() && attempts < maxAttempts) {
            attempts++;
            logQuest("Dialogue attempt " + attempts + "/20");
            
            String[] options = Dialogues.getOptions();
            String dialogueText = Dialogues.getNPCDialogue();
            
            if (options != null && options.length > 0) {
                logQuest("Dialogue options available: " + Arrays.toString(options));
                
                // Context-aware option selection
                boolean optionSelected = false;
                for (int i = 0; i < options.length; i++) {
                    String option = options[i].toLowerCase();
                    
                    // Quest-related options
                    if (option.contains("quest") || option.contains("have you any quests")) {
                        logQuest("Selecting quest option: " + options[i]);
                        if (Dialogues.chooseOption(options[i])) {
                            Sleep.sleepUntil(() -> !Arrays.equals(options, Dialogues.getOptions()), 3000);
                            optionSelected = true;
                            break;
                        }
                    }
                    // Research/talisman related options (for Sedridor)
                    else if (option.contains("research") || option.contains("talisman") || 
                             option.contains("package") || option.contains("brought you")) {
                        logQuest("Selecting research/talisman option: " + options[i]);
                        if (Dialogues.chooseOption(options[i])) {
                            Sleep.sleepUntil(() -> !Arrays.equals(options, Dialogues.getOptions()), 3000);
                            optionSelected = true;
                            break;
                        }
                    }
                    // Notes related options (for completing quest)
                    else if (option.contains("notes") || option.contains("finished") || 
                             option.contains("completed")) {
                        logQuest("Selecting notes/completion option: " + options[i]);
                        if (Dialogues.chooseOption(options[i])) {
                            Sleep.sleepUntil(() -> !Arrays.equals(options, Dialogues.getOptions()), 3000);
                            optionSelected = true;
                            break;
                        }
                    }
                    // Generic yes options
                    else if (option.contains("yes")) {
                        logQuest("Selecting 'Yes' option: " + options[i]);
                        if (Dialogues.chooseOption(options[i])) {
                            Sleep.sleepUntil(() -> !Arrays.equals(options, Dialogues.getOptions()), 3000);
                            optionSelected = true;
                            break;
                        }
                    }
                }
                
                // If no specific option found, select the first one
                if (!optionSelected) {
                    logQuest("No specific option found, selecting first option: " + options[0]);
                    if (Dialogues.chooseOption(options[0])) {
                        Sleep.sleepUntil(() -> !Arrays.equals(options, Dialogues.getOptions()), 3000);
                    }
                }
            } else if (dialogueText != null && !dialogueText.isEmpty()) {
                // Continue dialogue by clicking to advance
                logQuest("Continuing dialogue: " + dialogueText);
                Dialogues.continueDialogue();
                Sleep.sleepUntil(() -> !dialogueText.equals(Dialogues.getNPCDialogue()), 3000);
            } else {
                logQuest("No dialogue options or text, attempting to continue");
                Dialogues.continueDialogue();
                Sleep.sleep(1000, 2000);
            }
            
            // Context-aware success checking
            if (checkDialogueSuccess(expectedItem)) {
                return true;
            }
            
            // Short delay between dialogue attempts
            Sleep.sleep(500, 1000);
        }
        
        if (attempts >= maxAttempts) {
            logQuest("[ERROR] Dialogue handling exceeded maximum attempts (20)");
            return false;
        }
        
        // Final success check
        return checkDialogueSuccess(expectedItem);
    }
    
    /**
     * Handle dialogue with Sedridor to get research package
     */
    private boolean handleSedridorDialogue() {
        logQuest("Handling Sedridor dialogue for research package");
        
        // First, continue through initial NPC dialogue and select the talisman option
        boolean selectedTalismanOption = DialogueUtil.selectDialogueOption("talisman", 15000);
        if (!selectedTalismanOption) {
            // Try alternative keywords if "talisman" doesn't work
            selectedTalismanOption = DialogueUtil.selectDialogueOption("Duke", 5000);
        }
        if (!selectedTalismanOption) {
            selectedTalismanOption = DialogueUtil.selectDialogueOption("research", 5000);  
        }
        if (!selectedTalismanOption) {
            // Try the specific "Okay, here you are" option for giving the talisman
            selectedTalismanOption = DialogueUtil.selectDialogueOption("Okay, here you are", 5000);
        }
        
        if (!selectedTalismanOption) {
            logQuest("[ERROR] Could not find dialogue option for research package request");
            return false;
        }
        
        // Continue through any remaining dialogue
        DialogueUtil.continueDialogue(10000);
        
        logQuest("Sedridor dialogue completed successfully");
        return true;
    }
    
    /**
     * Handle dialogue with Duke Horacio to start the quest
     */
    private boolean handleDukeDialogue() {
        logQuest("Handling Duke Horacio dialogue for quest start");
        
        // Select the "Yes" option to start the quest
        boolean selectedYesOption = DialogueUtil.selectDialogueOption("yes", 10000);
        if (!selectedYesOption) {
            logQuest("[ERROR] Could not find 'Yes' option to start the quest");
            return false;
        }
        
        // Continue through any remaining dialogue
        DialogueUtil.continueDialogue(10000);
        
        logQuest("Duke Horacio dialogue completed successfully");
        return true;
    }

    /**
     * Handle dialogue with Aubury to get research notes
     */
    private boolean handleAuburyDialogue() {
        logQuest("Handling Aubury dialogue for research notes");

        // Select option related to research package
        boolean selectedPackageOption = DialogueUtil.selectDialogueOption("package", 10000);
        if (!selectedPackageOption) {
            selectedPackageOption = DialogueUtil.selectDialogueOption("research", 5000);
        }
        if (!selectedPackageOption) {
            // Try the specific "Okay, here you are" option for giving the package
            selectedPackageOption = DialogueUtil.selectDialogueOption("Okay, here you are", 5000);
        }
        if (!selectedPackageOption) {
            selectedPackageOption = DialogueUtil.selectDialogueOption("yes", 5000);
        }
        
        if (!selectedPackageOption) {
            logQuest("[ERROR] Could not find dialogue option for research package delivery");
            return false;
        }

        // Continue through any remaining dialogue  
        DialogueUtil.continueDialogue(10000);

        logQuest("Aubury dialogue completed successfully");
        return true;
    }
    
    /**
     * Handle final dialogue with Sedridor to complete the quest
     */
    private boolean handleFinalSedridorDialogue() {
        logQuest("Handling final Sedridor dialogue for quest completion");

        // Select option related to research notes completion
        boolean selectedNotesOption = DialogueUtil.selectDialogueOption("notes", 10000);
        if (!selectedNotesOption) {
            selectedNotesOption = DialogueUtil.selectDialogueOption("research", 5000);
        }
        if (!selectedNotesOption) {
            // Try the specific "Okay, here you are" option for giving the notes
            selectedNotesOption = DialogueUtil.selectDialogueOption("Okay, here you are", 5000);
        }
        if (!selectedNotesOption) {
            selectedNotesOption = DialogueUtil.selectDialogueOption("yes", 5000);
        }
        
        if (!selectedNotesOption) {
            logQuest("[ERROR] Could not find dialogue option for quest completion");
            return false;
        }

        // Continue through any remaining dialogue
        DialogueUtil.continueDialogue(10000);

        logQuest("Final Sedridor dialogue completed successfully");
        return true;
    }
    
    /**
     * Determine what we expect to receive from dialogue based on current quest state
     */
    private String determineExpectedDialogueOutcome() {
        int questProgress = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        
        if (questProgress == QUEST_NOT_STARTED) {
            return "Air talisman";
        } else if (questProgress == QUEST_STARTED) {
            if (Inventory.contains(RESEARCH_NOTES)) {
                return "Quest completion";
            } else if (Inventory.contains(RESEARCH_PACKAGE)) {
                return "Research notes";
            } else if (Inventory.contains(AIR_TALISMAN)) {
                return "Research package";
            }
        }
        
        return "Unknown";
    }
    
    /**
     * Check if dialogue was successful based on expected outcome
     */
    private boolean checkDialogueSuccess(String expectedItem) {
        switch (expectedItem) {
            case "Air talisman":
                if (Inventory.contains(AIR_TALISMAN)) {
                    logQuest("[SUCCESS] Received Air talisman - quest started!");
                    return true;
                }
                break;
                
            case "Research package":
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    logQuest("[SUCCESS] Received Research package from Sedridor!");
                    return true;
                }
                break;
                
            case "Research notes":
                if (Inventory.contains(RESEARCH_NOTES)) {
                    logQuest("[SUCCESS] Received Research notes from Aubury!");
                    return true;
                }
                break;
                
            case "Quest completion":
                if (isComplete()) {
                    logQuest("[SUCCESS] Quest completed!");
                    return true;
                }
                break;
        }
        
        return false;
    }
    
    @Override
    public boolean navigateToObjective() {
        int questProgress = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        
        switch (questProgress) {
            case QUEST_NOT_STARTED:
                logQuest("Navigating to Duke Horacio");
                Walking.walk(DUKE_LOCATION);
                return Sleep.sleepUntil(() -> DUKE_HORACIO_AREA.contains(Players.getLocal()), 15000);
                
            case QUEST_STARTED:
                if (Inventory.contains(RESEARCH_PACKAGE)) {
                    logQuest("Navigating to Aubury with research package");
                    Walking.walk(AUBURY_LOCATION);
                    return Sleep.sleepUntil(() -> AUBURY_SHOP_AREA.contains(Players.getLocal()), 25000);
                } else if (Inventory.contains(RESEARCH_NOTES)) {
                    logQuest("Navigating back to Wizards' Tower with research notes");
                    Walking.walk(new Tile(3104, 3162, 0));
                    return Sleep.sleepUntil(() -> WIZARDS_TOWER_AREA.contains(Players.getLocal()), 20000);
                } else if (Inventory.contains(AIR_TALISMAN)) {
                    logQuest("Navigating to Wizards' Tower with air talisman");
                    Walking.walk(new Tile(3104, 3162, 0));
                    return Sleep.sleepUntil(() -> WIZARDS_TOWER_AREA.contains(Players.getLocal()), 20000);
                } else {
                    logQuest("Quest started but missing items - navigating to Duke Horacio");
                    Walking.walk(DUKE_LOCATION);
                    return Sleep.sleepUntil(() -> DUKE_HORACIO_AREA.contains(Players.getLocal()), 15000);
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