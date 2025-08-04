package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.decisions.QuestProgressDecisionNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.methods.interactive.NPCs;

/**
 * The Restless Ghost Quest Tree
 * Config 107 tracks quest progress: 0 -> 1 -> 2 -> 3 -> 4 -> 5 (complete)
 */
public class RestlessGhostTree extends QuestTree {
    
    // Quest progress config ID
    private static final int QUEST_CONFIG = 107;
    
    // Key locations from quest log
    private static final Tile FATHER_AERECK_LOCATION = new Tile(3242, 3208, 0); // Lumbridge Church
    private static final Tile FATHER_URHNEY_LOCATION = new Tile(3150, 3175, 0); // Lumbridge Swamp
    private static final Tile URHNEY_DOOR_LOCATION = new Tile(3147, 3172, 0); // Door to Father Urhney's house
    private static final Tile GRAVEYARD_COFFIN_LOCATION = new Tile(3249, 3192, 0); // Coffin in graveyard
    private static final Tile RESTLESS_GHOST_LOCATION = new Tile(3250, 3195, 0); // Ghost location
    private static final Tile WIZARD_TOWER_ENTRANCE = new Tile(3104, 3162, 0); // Wizard Tower entrance
    private static final Tile WIZARD_TOWER_BASEMENT = new Tile(3103, 9576, 0); // Basement ladder
    private static final Tile ALTAR_LOCATION = new Tile(3120, 9566, 0); // Altar with skull
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode talkToFatherAereck;
    private QuestNode walkToSwamp;
    private QuestNode openUrhneysHouse;
    private QuestNode talkToFatherUrhney;
    private QuestNode equipGhostspeakAmulet;
    private QuestNode walkToGraveyard;
    private QuestNode openCoffin;
    private QuestNode talkToRestlessGhost;
    private QuestNode walkToWizardTower;
    private QuestNode climbDownLadder;
    private QuestNode searchAltar;
    private QuestNode climbUpLadder;
    private QuestNode returnToGraveyard;
    private QuestNode useSkullOnCoffin;
    private QuestNode questComplete;
    
    public RestlessGhostTree() {
        super("The Restless Ghost");
    }
    
    @Override
    protected void buildTree() {
        // Build all the nodes
        createNodes();
        
        // Set the root node to our smart decision node
        rootNode = smartDecisionNode;
    }
    
    private void createNodes() {
        // Step 1: Talk to Father Aereck (config 0 -> 1)
        talkToFatherAereck = new TalkToNPCNode("talk_father_aereck", "Father Aereck", FATHER_AERECK_LOCATION,
            new String[]{"I'm looking for a quest!", "Nothing."}, "I'm looking for a quest!", null);
        
        // Step 2: Go to Father Urhney (config 1 -> 2)
        walkToSwamp = new WalkToLocationNode("walk_to_swamp", URHNEY_DOOR_LOCATION, "Father Urhney's house");
        openUrhneysHouse = new InteractWithObjectNode("open_urhney_door", "Door", "Open", 
            URHNEY_DOOR_LOCATION, "Door to Father Urhney's house");
        
        // Custom TalkToNPCNode for Father Urhney with multi-step dialogue
        talkToFatherUrhney = new TalkToNPCNode("talk_father_urhney", "Father Urhney", FATHER_URHNEY_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Starting Father Urhney dialogue sequence...");
                
                // Walk to NPC and initiate dialogue
                if (!walkToNPCAndTalk("Father Urhney", FATHER_URHNEY_LOCATION)) {
                    return false;
                }
                
                try {
                    // First dialogue step: "Father Aereck sent me to talk to you."
                    log("Waiting for first dialogue options...");
                    if (!Sleep.sleepUntil(() -> org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable(), 10000)) {
                        log("No dialogue options available for first step");
                        return false;
                    }
                    
                    String[] options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                    log("First dialogue options: " + java.util.Arrays.toString(options));
                    
                    // Select "Father Aereck sent me to talk to you."
                    boolean foundFirstOption = false;
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].contains("Father Aereck sent me to talk to you")) {
                            log("Selecting first option: " + options[i]);
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(i + 1);
                            foundFirstOption = true;
                            break;
                        }
                    }
                    
                    if (!foundFirstOption) {
                        log("Could not find 'Father Aereck sent me to talk to you' option, selecting option 1 as fallback");
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                    }
                    
                    Sleep.sleep(2000, 3000);
                    
                    // Second dialogue step: "He's got a ghost haunting his graveyard."
                    log("Waiting for second dialogue options...");
                    if (!Sleep.sleepUntil(() -> org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable(), 10000)) {
                        log("No dialogue options available for second step");
                        return false;
                    }
                    
                    options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                    log("Second dialogue options: " + java.util.Arrays.toString(options));
                    
                    // Select "He's got a ghost haunting his graveyard."
                    boolean foundSecondOption = false;
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].contains("He's got a ghost haunting his graveyard") || 
                            options[i].contains("ghost haunting")) {
                            log("Selecting second option: " + options[i]);
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(i + 1);
                            foundSecondOption = true;
                            break;
                        }
                    }
                    
                    if (!foundSecondOption) {
                        log("Could not find 'ghost haunting' option, selecting option 1 as fallback");
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                    }
                    
                    Sleep.sleep(2000, 3000);
                    
                } catch (Exception e) {
                    log("Error in Father Urhney dialogue: " + e.getMessage());
                    return false;
                }
                
                // Continue through any remaining dialogue
                try {
                    log("Continuing through remaining dialogue...");
                    int dialogueSteps = 0;
                    while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                        if (dialogueSteps++ > 20) {
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
                    
                    log("Father Urhney dialogue completed");
                    
                    // Check if we received the Ghostspeak amulet
                    Sleep.sleep(2000, 3000);
                    if (org.dreambot.api.methods.container.impl.Inventory.contains("Ghostspeak amulet")) {
                        log("Successfully obtained Ghostspeak amulet from Father Urhney");
                        return true;
                    } else {
                        log("Warning: Ghostspeak amulet not found in inventory after dialogue");
                        return true; // Still consider it successful as the dialogue completed
                    }
                    
                } catch (Exception e) {
                    log("Error in Father Urhney dialogue continuation: " + e.getMessage());
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
                    NPC npc = NPCs.closest(npcName);
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
        };
        
        // Step 3: Equip Ghostspeak amulet and talk to ghost (config 2 -> 3)
        equipGhostspeakAmulet = new ActionNode("equip_ghostspeak", "Equip Ghostspeak amulet") {
            @Override
            protected boolean performAction() {
                if (Equipment.contains("Ghostspeak amulet")) {
                    log("Ghostspeak amulet already equipped");
                    return true;
                }
                
                if (!Inventory.contains("Ghostspeak amulet")) {
                    log("ERROR: Ghostspeak amulet not found in inventory!");
                    return false;
                }
                
                log("Equipping Ghostspeak amulet...");
                return Inventory.interact("Ghostspeak amulet", "Wear");
            }
        };
        
        walkToGraveyard = new WalkToLocationNode("walk_to_graveyard", GRAVEYARD_COFFIN_LOCATION, "Graveyard coffin");
        openCoffin = new InteractWithObjectNode("open_coffin", "Coffin", "Open", 
            GRAVEYARD_COFFIN_LOCATION, "Coffin in graveyard");
            
        // Custom TalkToNPCNode for Restless Ghost with specific dialogue
        talkToRestlessGhost = new TalkToNPCNode("talk_restless_ghost", "Restless ghost", RESTLESS_GHOST_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Starting Restless Ghost dialogue...");
                
                // Walk to NPC and initiate dialogue
                if (!walkToNPCAndTalk("Restless ghost", RESTLESS_GHOST_LOCATION)) {
                    return false;
                }
                
                try {
                    // Wait for dialogue options and select "Yep, now tell me what the problem is."
                    log("Waiting for ghost dialogue options...");
                    if (!Sleep.sleepUntil(() -> org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable(), 10000)) {
                        // If no options, just continue dialogue
                        log("No options available, continuing dialogue...");
                        return continueDialogue();
                    }
                    
                    String[] options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                    log("Ghost dialogue options: " + java.util.Arrays.toString(options));
                    
                    // Select "Yep, now tell me what the problem is."
                    boolean foundOption = false;
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].contains("Yep, now tell me what the problem is") ||
                            options[i].contains("tell me what the problem")) {
                            log("Selecting ghost option: " + options[i]);
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(i + 1);
                            foundOption = true;
                            break;
                        }
                    }
                    
                    if (!foundOption) {
                        log("Could not find expected ghost dialogue option, selecting first option");
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                    }
                    
                    Sleep.sleep(2000, 3000);
                    
                    // Continue through remaining dialogue
                    return continueDialogue();
                    
                } catch (Exception e) {
                    log("Error in Restless Ghost dialogue: " + e.getMessage());
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
                    NPC npc = NPCs.closest(npcName);
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
                    log("Continuing through ghost dialogue...");
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
                    
                    log("Ghost dialogue completed");
                    return true;
                    
                } catch (Exception e) {
                    log("Error continuing ghost dialogue: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Step 4: Get skull from Wizard Tower (config 3 -> 4)
        walkToWizardTower = new WalkToLocationNode("walk_to_wizard_tower", WIZARD_TOWER_ENTRANCE, "Wizard Tower");
        climbDownLadder = new InteractWithObjectNode("climb_down_ladder", "Ladder", "Climb-down",
            WIZARD_TOWER_ENTRANCE, "Wizard Tower ladder");
        searchAltar = new InteractWithObjectNode("search_altar", "Altar", "Search",
            ALTAR_LOCATION, "Altar in basement") {
            @Override
            protected boolean performAction() {
                log("Searching altar for Ghost's skull...");
                boolean result = super.performAction();
                if (result) {
                    // Wait a moment for the skull to appear in inventory
                    org.dreambot.api.utilities.Sleep.sleep(1000, 2000);
                    if (Inventory.contains("Ghost's skull")) {
                        log("Successfully obtained Ghost's skull");
                    } else {
                        log("Warning: Ghost's skull not found in inventory after searching altar");
                    }
                }
                return result;
            }
        };
        climbUpLadder = new InteractWithObjectNode("climb_up_ladder", "Ladder", "Climb-up",
            WIZARD_TOWER_BASEMENT, "Wizard Tower basement ladder");
        
        // Step 5: Return to graveyard and complete quest (config 4 -> 5)
        returnToGraveyard = new WalkToLocationNode("return_to_graveyard", GRAVEYARD_COFFIN_LOCATION, "Return to graveyard");
        useSkullOnCoffin = new ActionNode("use_skull_on_coffin", "Use Ghost's skull on coffin") {
            @Override
            protected boolean performAction() {
                if (!Inventory.contains("Ghost's skull")) {
                    log("ERROR: Ghost's skull not found in inventory!");
                    return false;
                }
                
                log("Using Ghost's skull on coffin...");
                
                // First try to use the skull
                if (!Inventory.interact("Ghost's skull", "Use")) {
                    log("Failed to select Ghost's skull for use");
                    return false;
                }
                
                // Wait a moment and then click on the coffin
                org.dreambot.api.utilities.Sleep.sleep(500, 1000);
                
                org.dreambot.api.wrappers.interactive.GameObject coffin = 
                    org.dreambot.api.methods.interactive.GameObjects.closest("Coffin");
                if (coffin == null) {
                    log("Could not find coffin to use skull on");
                    return false;
                }
                
                if (!coffin.interact()) {
                    log("Failed to use skull on coffin");
                    return false;
                }
                
                // Wait for quest completion
                org.dreambot.api.utilities.Sleep.sleep(2000, 3000);
                log("Used Ghost's skull on coffin - quest should be complete");
                return true;
            }
        };
        
        // Quest completion node
        questComplete = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                log("The Restless Ghost quest completed!");
                return true;
            }
        };
        
        // SMART DECISION NODE - uses config + inventory to determine next step
        smartDecisionNode = new QuestNode("smart_decision", "Smart Quest Decision") {
            @Override
            public ExecutionResult execute() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG);
                Tile currentTile = Players.getLocal().getTile();
                log("Config 107 = " + config + ", Location: " + currentTile);
                
                QuestNode nextStep = null;
                
                if (config == 0) {
                    nextStep = talkToFatherAereck;
                    log("-> Talk to Father Aereck (quest not started)");
                    
                } else if (config == 1) {
                    // Need to go to Father Urhney
                    if (currentTile.distance(URHNEY_DOOR_LOCATION) > 5) {
                        nextStep = walkToSwamp;
                        log("-> Walk to Father Urhney's house");
                    } else if (currentTile.distance(FATHER_URHNEY_LOCATION) > 3) {
                        nextStep = openUrhneysHouse;
                        log("-> Open door to Father Urhney's house");
                    } else {
                        nextStep = talkToFatherUrhney;
                        log("-> Talk to Father Urhney");
                    }
                    
                } else if (config == 2) {
                    // Need to equip amulet and talk to ghost
                    if (!Equipment.contains("Ghostspeak amulet")) {
                        nextStep = equipGhostspeakAmulet;
                        log("-> Equip Ghostspeak amulet");
                    } else if (currentTile.distance(GRAVEYARD_COFFIN_LOCATION) > 5) {
                        nextStep = walkToGraveyard;
                        log("-> Walk to graveyard");
                    } else {
                        // Check if coffin is open, if not open it first
                        nextStep = openCoffin;
                        log("-> Open coffin");
                        // After opening coffin, will talk to ghost in next iteration
                    }
                    
                } else if (config == 3) {
                    // Talk to ghost if we haven't yet, otherwise go to Wizard Tower
                    if (currentTile.distance(RESTLESS_GHOST_LOCATION) <= 5) {
                        nextStep = talkToRestlessGhost;
                        log("-> Talk to Restless ghost");
                    } else if (currentTile.distance(WIZARD_TOWER_ENTRANCE) > 5) {
                        nextStep = walkToWizardTower;
                        log("-> Walk to Wizard Tower");
                    } else if (currentTile.getZ() == 0) {
                        nextStep = climbDownLadder;
                        log("-> Climb down to basement");
                    } else {
                        nextStep = searchAltar;
                        log("-> Search altar for skull");
                    }
                    
                } else if (config == 4) {
                    // Have skull, need to use it on coffin
                    if (currentTile.getZ() == 3) {
                        nextStep = climbUpLadder;
                        log("-> Climb up from basement");
                    } else if (currentTile.distance(GRAVEYARD_COFFIN_LOCATION) > 5) {
                        nextStep = returnToGraveyard;
                        log("-> Return to graveyard");
                    } else {
                        nextStep = useSkullOnCoffin;
                        log("-> Use Ghost's skull on coffin");
                    }
                    
                } else if (config == 5) {
                    nextStep = questComplete;
                    log("-> Quest complete!");
                    
                } else {
                    log("ERROR: Unknown config value: " + config);
                    return ExecutionResult.failure("Unknown config value: " + config);
                }
                
                if (nextStep != null) {
                    return ExecutionResult.success(nextStep, "Next step determined: " + nextStep.getDescription());
                } else {
                    log("ERROR: Could not determine next step");
                    return ExecutionResult.failure("Could not determine next step for config " + config);
                }
            }
        };
    }
    
    @Override
    public int getQuestProgress() {
        int configValue = PlayerSettings.getConfig(QUEST_CONFIG);
        
        // Convert config value to percentage
        switch (configValue) {
            case 0: return 0;    // Not started
            case 1: return 20;   // Talked to Father Aereck
            case 2: return 40;   // Got Ghostspeak amulet
            case 3: return 60;   // Talked to ghost
            case 4: return 80;   // Got skull
            case 5: return 100;  // Quest complete
            default: 
                // Handle any intermediate values
                return Math.min(100, (configValue * 100) / 5);
        }
    }
}
