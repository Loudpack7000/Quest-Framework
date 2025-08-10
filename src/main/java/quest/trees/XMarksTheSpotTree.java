package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;

/**
 * X Marks the Spot quest tree implementation
 * A treasure hunting quest that requires digging at 4 specific locations to find an Ancient casket
 * Config 101 tracks quest progress: 11 (not started) -> 12 (complete)
 */
public class XMarksTheSpotTree extends QuestTree {
    
    // Quest progress config ID
    private static final int QUEST_CONFIG = 101;
    
    // Quest locations for the treasure hunt - based on actual log coordinates
    private static final Tile VEOS_LOCATION = new Tile(3228, 3242, 0); // Port Sarim docks
    private static final Tile VEOS_FINAL_LOCATION = new Tile(3053, 3249, 0); // Veos location after Ancient casket
    private static final Tile DIG_LOCATION_1 = new Tile(3229, 3209, 0); // First dig location
    private static final Tile DIG_LOCATION_2 = new Tile(3203, 3212, 0); // Second dig location
    private static final Tile DIG_LOCATION_3 = new Tile(3108, 3264, 0); // Third dig location
    private static final Tile DIG_LOCATION_4 = new Tile(3077, 3260, 0); // Fourth dig location
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode talkToVeosStart;
    private QuestNode readTreasureScroll;
    private QuestNode walkToDigLocation1;
    private QuestNode walkToDigLocation2;
    private QuestNode walkToDigLocation3;
    private QuestNode walkToDigLocation4;
    private QuestNode digAtLocation1;
    private QuestNode digAtLocation2;
    private QuestNode digAtLocation3;
    private QuestNode digAtLocation4;
    private QuestNode openAncientCasket;
    private QuestNode useAncientCasketOnVeos;
    private QuestNode returnToVeos;
    private QuestNode questComplete;
    
    public XMarksTheSpotTree() {
        super("X Marks the Spot");
    }
    
    @Override
    protected void buildTree() {
        // Build all the nodes
        createNodes();
        
        // Set the root node to our smart decision node
        rootNode = smartDecisionNode;
    }
    
    private void createNodes() {
        // Talk to Veos to start the quest
        talkToVeosStart = new TalkToNPCNode("talk_veos_start", "Veos", VEOS_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Starting X Marks the Spot quest with Veos");
                
                // Walk to NPC and initiate dialogue
                if (!walkToNPCAndTalk("Veos", VEOS_LOCATION)) {
                    return false;
                }
                
                try {
                    // Wait for dialogue options and select "I'm looking for a quest."
                    log("Waiting for Veos dialogue options...");
                    if (!Sleep.sleepUntil(() -> org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable(), 10000)) {
                        log("No dialogue options available, continuing dialogue...");
                        return continueDialogue();
                    }
                    
                    String[] options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                    log("Veos dialogue options: " + java.util.Arrays.toString(options));
                    
                    // Select "I'm looking for a quest."
                    boolean foundOption = false;
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].contains("I'm looking for a quest") ||
                            options[i].contains("looking for a quest")) {
                            log("Selecting Veos option: " + options[i]);
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(i + 1);
                            foundOption = true;
                            break;
                        }
                    }
                    
                    if (!foundOption) {
                        log("Could not find 'I'm looking for a quest' option, selecting first option");
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                    }
                    
                    Sleep.sleep(2000, 3000);
                    
                    // Continue through remaining dialogue
                    return continueDialogue();
                    
                } catch (Exception e) {
                    log("Error in Veos dialogue: " + e.getMessage());
                    return false;
                }
            }
            
            private boolean walkToNPCAndTalk(String npcName, Tile location) {
                try {
                    // Walk to NPC location
                    if (Players.getLocal().getTile().distance(location) > 5) {
                        log("Walking to " + npcName + " at " + location);
                        org.dreambot.api.methods.walking.impl.Walking.walk(location);
                        
                        if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(location) <= 5, 15000)) {
                            log("Failed to walk to NPC location");
                            return false;
                        }
                    }
                    
                    // Find and talk to NPC
                    org.dreambot.api.wrappers.interactive.NPC npc = org.dreambot.api.methods.interactive.NPCs.closest(npcName);
                    if (npc == null) {
                        log("Could not find NPC: " + npcName);
                        return false;
                    }
                    
                    if (!npc.interact("Talk-to")) {
                        log("Failed to interact with " + npcName);
                        return false;
                    }
                    
                    // Wait for dialogue to open
                    if (!Sleep.sleepUntil(() -> org.dreambot.api.methods.dialogues.Dialogues.inDialogue(), 7000)) {
                        log("Dialogue did not open with " + npcName);
                        return false;
                    }
                    
                    return true;
                    
                } catch (Exception e) {
                    log("Error walking to and talking to NPC: " + e.getMessage());
                    return false;
                }
            }
            
            private boolean continueDialogue() {
                try {
                    log("Continuing through Veos dialogue...");
                    int dialogueSteps = 0;
                    while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                        if (dialogueSteps++ > 15) {
                            log("Too many dialogue steps, breaking...");
                            break;
                        }
                        
                        if (org.dreambot.api.methods.dialogues.Dialogues.canContinue()) {
                            log("Continuing dialogue...");
                            org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                        } else if (org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                            log("Additional dialogue options found, selecting first option");
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                        } else {
                            log("Dialogue state unclear, waiting...");
                            Sleep.sleep(1000, 2000);
                        }
                        Sleep.sleep(1500, 2500);
                    }
                    
                    log("Veos dialogue completed");
                    
                    // Check if we received the Treasure scroll
                    Sleep.sleep(2000, 3000);
                    if (Inventory.contains("Treasure scroll")) {
                        log("Successfully obtained Treasure scroll from Veos");
                        return true;
                    } else {
                        log("Warning: Treasure scroll not found in inventory after dialogue");
                        return true; // Still consider it successful as the dialogue completed
                    }
                    
                } catch (Exception e) {
                    log("Error continuing Veos dialogue: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Read the Treasure scroll to determine the next dig location
        readTreasureScroll = new ActionNode("read_scroll", "Read Treasure scroll to determine next dig location") {
            @Override
            protected boolean performAction() {
                log("Reading Treasure scroll to determine next dig location...");
                if (!Inventory.contains("Treasure scroll")) {
                    log("ERROR: Treasure scroll not found in inventory!");
                    return false;
                }
                
                Inventory.interact("Treasure scroll", "Read");
                Sleep.sleep(2000, 3000); // Wait for scroll reading animation
                log("Treasure scroll read successfully");
                
                return true;
            }
        };
        
        // Walk to dig locations using WalkToLocationNode for fluid movement
        walkToDigLocation1 = new WalkToLocationNode("walk_dig_1", DIG_LOCATION_1, "First dig location");
        walkToDigLocation2 = new WalkToLocationNode("walk_dig_2", DIG_LOCATION_2, "Second dig location");
        walkToDigLocation3 = new WalkToLocationNode("walk_dig_3", DIG_LOCATION_3, "Third dig location");
        walkToDigLocation4 = new WalkToLocationNode("walk_dig_4", DIG_LOCATION_4, "Fourth dig location");
        
        // Dig at locations
        digAtLocation1 = new ActionNode("dig_1", "Dig at first location") {
            @Override
            protected boolean performAction() {
                log("Digging at first location");
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                boolean success = Inventory.interact("Spade", "Dig");
                Sleep.sleep(3000, 4000); // Wait for dig animation
                
                if (success) {
                    log("Successfully dug at location 1");
                }
                
                return success;
            }
        };
        
        digAtLocation2 = new ActionNode("dig_2", "Dig at second location") {
            @Override
            protected boolean performAction() {
                log("Digging at second location");
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                boolean success = Inventory.interact("Spade", "Dig");
                Sleep.sleep(3000, 4000); // Wait for dig animation
                
                if (success) {
                    log("Successfully dug at location 2");
                }
                
                return success;
            }
        };
        
        digAtLocation3 = new ActionNode("dig_3", "Dig at third location") {
            @Override
            protected boolean performAction() {
                log("Digging at third location");
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                // Check what we have before digging
                boolean hadMysteriousOrb = Inventory.contains("Mysterious orb");
                
                boolean success = Inventory.interact("Spade", "Dig");
                Sleep.sleep(3000, 4000); // Wait for dig animation
                
                if (success) {
                    log("Successfully dug at location 3");
                    
                    // Wait for new item to appear and check what we got
                    Sleep.sleep(2000, 3000);
                    
                    if (Inventory.contains(item -> item.getID() == 23070)) {
                        log("Received new treasure scroll (ID 23070) from digging at location 3!");
                    } else if (!hadMysteriousOrb && Inventory.contains("Mysterious orb")) {
                        log("Received Mysterious orb from digging at location 3!");
                    } else {
                        log("Warning: Expected to receive item from digging at location 3");
                    }
                }
                
                return success;
            }
        };
        
        digAtLocation4 = new ActionNode("dig_4", "Dig at fourth location") {
            @Override
            protected boolean performAction() {
                log("Digging at fourth location for Ancient casket");
                
                // Ensure we're at the exact dig location before digging
                if (DIG_LOCATION_4.distance() > 0) {
                    log("Not at exact dig location 4, walking to " + DIG_LOCATION_4);
                    org.dreambot.api.methods.walking.impl.Walking.walk(DIG_LOCATION_4);
                    
                    // Wait until we reach the exact tile
                    if (!Sleep.sleepUntil(() -> DIG_LOCATION_4.distance() == 0, 15000)) {
                        log("Failed to reach exact dig location 4");
                        return false;
                    }
                    log("Reached exact dig location 4: " + DIG_LOCATION_4);
                }
                
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                boolean success = Inventory.interact("Spade", "Dig");
                Sleep.sleep(3000, 4000); // Wait for dig animation
                
                if (success) {
                    log("Successfully dug at location 4 - Ancient casket should appear");
                    // Wait for Ancient casket to appear
                    Sleep.sleep(3000, 4000);
                    GameObject casket = GameObjects.closest("Ancient casket");
                    if (casket != null) {
                        log("Ancient casket found after digging!");
                    } else {
                        log("Warning: Ancient casket not found after digging");
                    }
                }
                
                return success;
            }
        };
        
        // Open the Ancient casket
        openAncientCasket = new ActionNode("open_casket", "Open Ancient casket") {
            @Override
            protected boolean performAction() {
                GameObject casket = GameObjects.closest("Ancient casket");
                if (casket == null) {
                    log("ERROR: Ancient casket not found!");
                    return false;
                }
                
                log("Opening Ancient casket...");
                boolean success = casket.interact("Open");
                
                if (success) {
                    // Wait for casket item to be added to inventory
                    Sleep.sleepUntil(() -> Inventory.contains("Ancient casket"), 10000);
                    log("Successfully opened Ancient casket");
                }
                
                return success;
            }
        };
        
        // Use Ancient casket on Veos (appears after opening the casket)
        useAncientCasketOnVeos = new ActionNode("use_casket_on_veos", "Use Ancient casket on Veos") {
            @Override
            protected boolean performAction() {
                log("Looking for Ancient casket -> Veos object...");
                GameObject casketToVeos = GameObjects.closest("Ancient casket -> Veos");
                if (casketToVeos == null) {
                    log("ERROR: Ancient casket -> Veos object not found!");
                    return false;
                }
                
                log("Using Ancient casket on Veos...");
                boolean success = casketToVeos.interact("Use");
                
                if (success) {
                    // Wait for quest completion - config should be 12 when complete
                    Sleep.sleepUntil(() -> PlayerSettings.getConfig(QUEST_CONFIG) == 12, 10000);
                    log("Successfully used Ancient casket on Veos - quest should be complete");
                }
                
                return success;
            }
        };
        
        // Return to Veos to complete the quest
        returnToVeos = new TalkToNPCNode("return_veos", "Veos", VEOS_FINAL_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Returning to Veos to complete quest");
                boolean success = super.performAction();
                if (success) {
                    // Wait for quest completion - config should be 12 when complete
                    Sleep.sleepUntil(() -> 
                        PlayerSettings.getConfig(QUEST_CONFIG) == 12, 10000);
                    log("Quest completion dialogue finished");
                }
                return success;
            }
        };
        
        // Quest complete node
        questComplete = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                log("X Marks the Spot quest completed successfully!");
                setQuestComplete();
                return true;
            }
        };
        
        // SMART DECISION NODE - uses config + inventory to determine next step
        smartDecisionNode = new QuestNode("smart_decision", "Smart Quest Decision") {
            @Override
            public ExecutionResult execute() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG);
                Tile currentTile = Players.getLocal().getTile();
                log("Config 101 = " + config + ", Location: " + currentTile);
                
                QuestNode nextStep = null;
                
                // Check if quest is complete
                if (config == 12) {
                    nextStep = questComplete;
                    log("-> Quest complete!");
                }
                // Check if we have Ancient casket - quest nearly complete
                else if (Inventory.contains("Ancient casket")) {
                    nextStep = returnToVeos;
                    log("-> Return to Veos with Ancient casket");
                }
                // Check if "Ancient casket -> Veos" object is available (after opening casket)
                else if (GameObjects.closest("Ancient casket -> Veos") != null) {
                    nextStep = useAncientCasketOnVeos;
                    log("-> Use Ancient casket on Veos");
                }
                // Check if casket is available to open
                else if (GameObjects.closest("Ancient casket") != null) {
                    nextStep = openAncientCasket;
                    log("-> Open Ancient casket");
                }
                // PRIORITY 1: Check inventory items first to determine correct location
                else if (Inventory.contains("Mysterious orb")) {
                    if (currentTile.distance(DIG_LOCATION_3) <= 3) {
                        nextStep = digAtLocation3;
                        log("-> Dig at third location (have Mysterious orb, already there)");
                    } else {
                        nextStep = walkToDigLocation3;
                        log("-> Walk to third dig location (have Mysterious orb)");
                    }
                }
                else if (Inventory.contains(item -> item.getID() == 23070)) {
                    if (currentTile.distance(DIG_LOCATION_4) <= 3) {
                        nextStep = digAtLocation4;
                        log("-> Dig at fourth location (have treasure scroll ID 23070, already there)");
                    } else {
                        nextStep = walkToDigLocation4;
                        log("-> Walk to fourth dig location (have treasure scroll ID 23070)");
                    }
                }
                else if (Inventory.contains(item -> item.getID() == 23067)) {
                    if (currentTile.distance(DIG_LOCATION_1) <= 3) {
                        nextStep = digAtLocation1;
                        log("-> Dig at first location (have treasure scroll ID 23067, already there)");
                    } else {
                        nextStep = walkToDigLocation1;
                        log("-> Walk to first dig location (have treasure scroll ID 23067)");
                    }
                }
                else if (Inventory.contains(item -> item.getID() == 23068)) {
                    if (currentTile.distance(DIG_LOCATION_2) <= 3) {
                        nextStep = digAtLocation2;
                        log("-> Dig at second location (have treasure scroll ID 23068, already there)");
                    } else {
                        nextStep = walkToDigLocation2;
                        log("-> Walk to second dig location (have treasure scroll ID 23068)");
                    }
                }
                // PRIORITY 2: Generic treasure scroll - need to read it
                else if (Inventory.contains("Treasure scroll")) {
                    // Only read if we're not already walking to a location
                    if (!Players.getLocal().isMoving()) {
                        nextStep = readTreasureScroll;
                        log("-> Read treasure scroll to determine next location");
                    } else {
                        // Still moving, wait for arrival
                        log("-> Still walking, waiting for arrival...");
                        return ExecutionResult.success(null, "Walking in progress");
                    }
                }
                // No treasure scroll - need to start quest
                else {
                    nextStep = talkToVeosStart;
                    log("-> Start quest with Veos (no treasure scroll)");
                }
                
                if (nextStep != null) {
                    return ExecutionResult.success(nextStep, "Moving to next step");
                } else {
                    return ExecutionResult.failure("No next step determined");
                }
            }
        };
    }
    
    @Override
    public int getQuestProgress() {
        int configValue = PlayerSettings.getConfig(QUEST_CONFIG);
        
        // Convert config value to percentage based on actual log data
        switch (configValue) {
            case 11: return 0;    // Not started
            case 12: return 100;  // Quest complete
            default: 
                // Handle any intermediate values
                return Math.min(100, Math.max(0, (configValue - 11) * 100));
        }
    }
}
