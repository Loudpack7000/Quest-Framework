package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;

import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.wrappers.interactive.GameObject;

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
    private static final Tile WIZARD_TOWER_BASEMENT = new Tile(3103, 9576, 0); // Basement ladder (correct coordinates)
    private static final Tile ALTAR_LOCATION = new Tile(3120, 9566, 0); // Altar with skull (in basement)
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode talkToFatherAereck;
    private QuestNode walkToSwamp;
    private QuestNode openUrhneysHouse;
	    private QuestNode walkInsideUrhneysHouse;
    private QuestNode talkToFatherUrhney;
    private QuestNode equipGhostspeakAmulet;
    private QuestNode walkToGraveyard;
    private QuestNode openCoffin;
    private QuestNode talkToRestlessGhost;

    private QuestNode walkToWizardTower;
    private QuestNode climbDownLadder;
    private QuestNode walkToAltar;
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
        // Step 1: Talk to Father Aereck (config 0 -> 1) with robust option selection
        talkToFatherAereck = new TalkToNPCNode("talk_father_aereck", "Father Aereck", FATHER_AERECK_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Starting Father Aereck dialogue to start quest...");

                // Walk to and talk to Father Aereck
                NPC npc = NPCs.closest("Father Aereck");
                if (npc == null || Players.getLocal().getTile().distance(FATHER_AERECK_LOCATION) > 6) {
                    new WalkToLocationNode("walk_aereck", FATHER_AERECK_LOCATION, "Father Aereck").execute();
                    npc = NPCs.closest("Father Aereck");
                }
                if (npc == null || !npc.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(org.dreambot.api.methods.dialogues.Dialogues::inDialogue, 7000)) return false;

                long start = System.currentTimeMillis();
                int guard = 0;
                while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue() && System.currentTimeMillis() - start < 20000 && guard++ < 50) {
                    if (org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                        String[] opts = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                        // Prefer options that start/accept the quest
                        int idx = indexOfOption(opts,
                                "I'm looking for a quest",
                                "looking for a quest",
                                "I want to help",
                                "I'll help",
                                "Ok, I will help",
                                "Yes",
                                "yes");
                        if (idx == -1) idx = 0;
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(idx + 1);
                    } else if (org.dreambot.api.methods.dialogues.Dialogues.canContinue()) {
                        if (!org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue()) org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                    }
                    Sleep.sleep(300, 600);
                }

                // Wait briefly for quest config to move to stage 1
                boolean started = Sleep.sleepUntil(() -> PlayerSettings.getConfig(QUEST_CONFIG) >= 1, 8000);
                if (!started) {
                    // Final check after a short delay
                    Sleep.sleep(1000, 1500);
                }
                return PlayerSettings.getConfig(QUEST_CONFIG) >= 1;
            }

            private int indexOfOption(String[] options, String... needles) {
                if (options == null) return -1;
                for (int i = 0; i < options.length; i++) {
                    String opt = options[i] == null ? "" : options[i].toLowerCase();
                    for (String n : needles) {
                        if (n != null && opt.contains(n.toLowerCase())) return i;
                    }
                }
                return -1;
            }
        };
        
        // Step 2: Go to Father Urhney (config 1 -> 2)
        walkToSwamp = new WalkToLocationNode("walk_to_swamp", URHNEY_DOOR_LOCATION, "Father Urhney's house");
	        openUrhneysHouse = new InteractWithObjectNode("open_urhney_door", "Door", "Open", 
	            URHNEY_DOOR_LOCATION, "Door to Father Urhney's house");

	        // After opening the door, walk inside to Father Urhney
	        walkInsideUrhneysHouse = new WalkToLocationNode(
	            "walk_inside_urhney_house",
	            FATHER_URHNEY_LOCATION,
	            "Inside Father Urhney's house"
	        );
        
        // Custom TalkToNPCNode for Father Urhney with robust multi-step dialogue to obtain Ghostspeak amulet
        talkToFatherUrhney = new TalkToNPCNode("talk_father_urhney", "Father Urhney", FATHER_URHNEY_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Starting Father Urhney dialogue sequence...");
                
                // Walk to NPC and initiate dialogue
                if (!walkToNPCAndTalk("Father Urhney", FATHER_URHNEY_LOCATION)) {
                    return false;
                }
                
                try {
                    long start = System.currentTimeMillis();
                    int guard = 0;
                    while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue() && System.currentTimeMillis() - start < 25000 && guard++ < 60) {
                        if (org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                            String[] options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                            log("Urhney options: " + java.util.Arrays.toString(options));
                            // Prefer the two-step path to amulet
                            int idx = indexOfOption(options,
                                    "Father Aereck sent me to talk to you",
                                    "Aereck sent me",
                                    "He's got a ghost haunting his graveyard",
                                    "ghost haunting",
                                    "ghost",
                                    "graveyard");
                            if (idx == -1) idx = 0;
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(idx + 1);
                        } else if (org.dreambot.api.methods.dialogues.Dialogues.canContinue()) {
                            if (!org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue()) org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                        }
                        Sleep.sleep(350, 650);
                        // Early exit if we already have the amulet
                        if (Inventory.contains("Ghostspeak amulet")) break;
                    }
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
                    boolean gotAmulet = Sleep.sleepUntil(() -> Inventory.contains("Ghostspeak amulet"), 8000);
                    if (gotAmulet || Inventory.contains("Ghostspeak amulet")) {
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

            private int indexOfOption(String[] options, String... needles) {
                if (options == null) return -1;
                for (int i = 0; i < options.length; i++) {
                    String opt = options[i] == null ? "" : options[i].toLowerCase();
                    for (String n : needles) {
                        if (n != null && opt.contains(n.toLowerCase())) return i;
                    }
                }
                return -1;
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
        
        // Custom coffin interaction that ONLY opens the coffin (no searching)
        openCoffin = new ActionNode("open_coffin", "Open coffin to make ghost appear") {
            @Override
            protected boolean performAction() {
                log("Opening coffin to make ghost appear...");
                
                GameObject coffin = GameObjects.closest("Coffin");
                if (coffin == null) {
                    log("ERROR: Coffin not found!");
                    return false;
                }
                
                // Only open the coffin - do NOT search it
                if (coffin.hasAction("Open")) {
                    log("Opening coffin...");
                    if (!coffin.interact("Open")) {
                        log("Failed to open coffin.");
                        return false;
                    }
                    Sleep.sleep(2000, 3000);
                    log("Coffin opened successfully - ghost should now appear");
                    return true;
                } else {
                    log("Coffin is already open or doesn't need opening");
                    return true;
                }
            }
        };
            
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
        walkToAltar = new WalkToLocationNode("walk_to_altar", ALTAR_LOCATION, "Altar in basement");
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
            WIZARD_TOWER_BASEMENT, "Wizard Tower basement ladder") {
            @Override
            protected boolean performAction() {
                log("Climbing up from Wizard Tower basement...");
                Tile beforeTile = Players.getLocal().getTile();
                log("Starting position: " + beforeTile);
                
                boolean result = super.performAction();
                if (result) {
                    // Wait a moment for the player to reach the ground floor
                    org.dreambot.api.utilities.Sleep.sleep(2000, 3000);
                    Tile afterTile = Players.getLocal().getTile();
                    log("After climbing position: " + afterTile);
                    
                    // Check if we successfully moved from basement to surface
                    if (beforeTile.getY() >= 9500 && afterTile.getY() < 9500) {
                        log("SUCCESS: Successfully climbed up from basement to surface");
                        log("Transition: " + beforeTile + " -> " + afterTile);
                    } else if (afterTile.getY() >= 9500) {
                        log("WARNING: Still in basement after climbing up");
                        log("Position: " + afterTile);
                    } else {
                        log("INFO: Climbed up successfully, now on surface");
                        log("Position: " + afterTile);
                    }
                } else {
                    log("ERROR: Failed to climb up ladder");
                }
                return result;
            }
        };
        
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
                
                // Step 1: First open the coffin if it's not already open
                org.dreambot.api.wrappers.interactive.GameObject coffin = 
                    org.dreambot.api.methods.interactive.GameObjects.closest("Coffin");
                if (coffin == null) {
                    log("Could not find coffin");
                    return false;
                }
                
                // Check if coffin needs to be opened first
                if (coffin.hasAction("Open")) {
                    log("Opening coffin first...");
                    if (!coffin.interact("Open")) {
                        log("Failed to open coffin");
                        return false;
                    }
                    
                    // Wait for coffin to open
                    org.dreambot.api.utilities.Sleep.sleep(2000, 3000);
                } else {
                    log("Coffin is already open or doesn't need opening");
                }
                
                // Step 2: Now use the skull on the coffin
                log("Using Ghost's skull on coffin...");
                if (!Inventory.interact("Ghost's skull", "Use")) {
                    log("Failed to select Ghost's skull for use");
                    return false;
                }
                
                // Wait a moment and then click on the coffin
                org.dreambot.api.utilities.Sleep.sleep(500, 1000);
                
                // Find the coffin again (it might have changed state)
                coffin = org.dreambot.api.methods.interactive.GameObjects.closest("Coffin");
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
                boolean hasSkull = Inventory.contains("Ghost's skull");
                log("=== SMART DECISION DEBUG ===");
                log("Config 107 = " + config + ", Location: " + currentTile);
                log("Has Ghost's skull: " + hasSkull);
                log("Distance to altar: " + currentTile.distance(ALTAR_LOCATION));
                log("Distance to wizard tower: " + currentTile.distance(WIZARD_TOWER_ENTRANCE));
                log("Distance to graveyard: " + currentTile.distance(GRAVEYARD_COFFIN_LOCATION));
                log("Z-level: " + currentTile.getZ());
                log("Y-coordinate: " + currentTile.getY() + " (Basement: Y>=9500, Surface: Y<9500)");
                log("In basement: " + (currentTile.getY() >= 9500));
                
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
	                // If the door can be opened, open it; otherwise walk inside to Father Urhney
	                GameObject door = GameObjects.closest(obj ->
	                    obj != null &&
	                    "Door".equals(obj.getName()) &&
	                    obj.getTile().equals(URHNEY_DOOR_LOCATION) &&
	                    obj.hasAction("Open")
	                );
	                if (door != null) {
	                    nextStep = openUrhneysHouse;
	                    log("-> Open door to Father Urhney's house");
	                } else {
	                    nextStep = walkInsideUrhneysHouse;
	                    log("-> Walk inside to Father Urhney");
	                }
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
                        // Check if the ghost is visible - if so, talk to it
                        NPC ghost = NPCs.closest("Restless ghost");
                        if (ghost != null && ghost.exists()) {
                            nextStep = talkToRestlessGhost;
                            log("-> Talk to Restless ghost (ghost is visible)");
                        } else {
                            // Ghost not visible yet, need to open/search coffin first
                            nextStep = openCoffin;
                            log("-> Open coffin (ghost not visible yet)");
                        }
                    }
                    
                } else if (config == 3) {
                    // After talking to the ghost, proceed to Wizard Tower to get the skull
                    if (Inventory.contains("Ghost's skull")) {
                        // Already have the skull, go back to graveyard
                        if (currentTile.getY() >= 9500) {
                            nextStep = climbUpLadder;
                            log("-> Climb up from basement (have skull)");
                        } else if (currentTile.distance(GRAVEYARD_COFFIN_LOCATION) > 5) {
                            nextStep = returnToGraveyard;
                            log("-> Return to graveyard (have skull, on surface)");
                        } else {
                            nextStep = useSkullOnCoffin;
                            log("-> Use Ghost's skull on coffin");
                        }
                    } else if (currentTile.getY() >= 9500) {
                        // In basement, navigate to altar and search
                        if (currentTile.distance(ALTAR_LOCATION) <= 5) {
                            nextStep = searchAltar;
                            log("-> Search altar for skull (close to altar)");
                        } else {
                            nextStep = walkToAltar;
                            log("-> Walk to altar in basement (distance: " + currentTile.distance(ALTAR_LOCATION) + ")");
                        }
                    } else if (currentTile.distance(WIZARD_TOWER_ENTRANCE) > 5) {
                        nextStep = walkToWizardTower;
                        log("-> Walk to Wizard Tower");
                    } else {
                        nextStep = climbDownLadder;
                        log("-> Climb down to basement");
                    }
                    
                } else if (config == 4) {
                    // Have skull, need to use it on coffin
                    if (currentTile.getY() >= 9500) {
                        // We're in the basement - climb up
                        nextStep = climbUpLadder;
                        log("-> Climb up from basement");
                    } else {
                        // We're on the surface - go to graveyard
                        if (currentTile.distance(GRAVEYARD_COFFIN_LOCATION) > 5) {
                            nextStep = returnToGraveyard;
                            log("-> Return to graveyard (on surface)");
                        } else {
                            nextStep = useSkullOnCoffin;
                            log("-> Use Ghost's skull on coffin");
                        }
                    }
                    
                } else if (config == 5) {
                    nextStep = questComplete;
                    log("-> Quest complete!");
                    
                } else {
                    log("ERROR: Unknown config value: " + config);
                    return ExecutionResult.failure("Unknown config value: " + config);
                }
                
                // SAFETY CHECK: If no next step determined, try to recover based on current state
                if (nextStep == null) {
                    log("WARNING: No next step determined, attempting recovery...");
                    log("RECOVERY DEBUG: Config=" + config + ", HasSkull=" + hasSkull + ", Y=" + currentTile.getY());
                    
                    // Recovery logic based on inventory and location
                    if (hasSkull) {
                        if (currentTile.getY() >= 9500) {
                            // Have skull, in basement - climb up
                            nextStep = climbUpLadder;
                            log("-> RECOVERY: Climb up from basement (have skull)");
                        } else {
                            // Have skull, on surface - go to graveyard
                            if (currentTile.distance(GRAVEYARD_COFFIN_LOCATION) > 10) {
                                nextStep = returnToGraveyard;
                                log("-> RECOVERY: Return to graveyard (have skull, on surface, distance=" + currentTile.distance(GRAVEYARD_COFFIN_LOCATION) + ")");
                            } else {
                                nextStep = useSkullOnCoffin;
                                log("-> RECOVERY: Use skull on coffin (at graveyard)");
                            }
                        }
                    } else {
                        // No skull - figure out what we need to do based on config and location
                        if (currentTile.getY() >= 9500) {
                            // In basement without skull - search altar
                            if (currentTile.distance(ALTAR_LOCATION) <= 10) {
                                nextStep = searchAltar;
                                log("-> RECOVERY: Search altar for skull (in basement)");
                            } else {
                                nextStep = walkToAltar;
                                log("-> RECOVERY: Walk to altar (in basement)");
                            }
                        } else {
                            // On surface without skull - need to go to wizard tower
                            if (currentTile.distance(WIZARD_TOWER_ENTRANCE) > 10) {
                                nextStep = walkToWizardTower;
                                log("-> RECOVERY: Walk to Wizard Tower (no skull)");
                            } else {
                                nextStep = climbDownLadder;
                                log("-> RECOVERY: Climb down to basement (no skull)");
                            }
                        }
                    }
                    
                    if (nextStep != null) {
                        log("RECOVERY SUCCESS: Selected recovery step: " + nextStep.getDescription());
                    } else {
                        log("RECOVERY FAILED: Could not determine recovery step");
                    }
                }
                
                if (nextStep != null) {
                    log("=== DECISION MADE ===");
                    log("Selected step: " + nextStep.getDescription());
                    log("=====================");
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
