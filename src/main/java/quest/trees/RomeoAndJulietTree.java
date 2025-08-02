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

/**
 * Romeo and Juliet Quest Tree
 * SIMPLIFIED LOGIC: Use location + config to determine next step
 * Config 144 tracks quest progress: 0 -> 10 -> 20 -> 30 -> 40 -> 50 -> 60 (complete)
 */
public class RomeoAndJulietTree extends QuestTree {
    
    // Quest progress config ID
    private static final int QUEST_CONFIG = 144;
    
    // Key locations from quest log
    private static final Tile ROMEO_LOCATION_1 = new Tile(3219, 3427, 0); // Initial Romeo location
    private static final Tile ROMEO_LOCATION_2 = new Tile(3209, 3423, 0); // Romeo's second location
    private static final Tile JULIET_HOUSE_GROUND = new Tile(3156, 3435, 0); // Ground floor of Juliet's house
    private static final Tile JULIET_LOCATION = new Tile(3158, 3425, 1); // Juliet upstairs
    private static final Tile FATHER_LAWRENCE_LOCATION = new Tile(3254, 3487, 0); // Father Lawrence
    private static final Tile CADAVA_BUSH_LOCATION = new Tile(3271, 3366, 0); // Cadava berries
    private static final Tile APOTHECARY_LOCATION = new Tile(3197, 3406, 0); // Apothecary
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode talkToRomeoInitial;
    private QuestNode walkToJulietHouse;
    private QuestNode climbUpToJuliet;
    private QuestNode talkToJuliet;
    private QuestNode climbDownFromJuliet;
    private QuestNode returnToRomeo;
    private QuestNode walkToFatherLawrence;
    private QuestNode talkToFatherLawrence;
    private QuestNode walkToCadavaBush;
    private QuestNode pickCadavaBerries;
    private QuestNode walkToApothecary;
    private QuestNode talkToApothecary;
    private QuestNode returnToJulietWithPotion;
    private QuestNode climbUpToJulietFinal;
    private QuestNode giveJulietPotion;
    private QuestNode climbDownFinal;
    private QuestNode finalReturnToRomeo;
    private QuestNode questComplete;
    
    public RomeoAndJulietTree() {
        super("Romeo and Juliet");
    }
    
    @Override
    protected void buildTree() {
        // Build all the nodes
        createNodes();
        
        // Set the root node to our smart decision node
        rootNode = smartDecisionNode;
    }
    
    private void createNodes() {
        // Step 1: Talk to Romeo initially (config 0 -> 10)
        talkToRomeoInitial = new TalkToNPCNode("talk_romeo_initial", "Romeo", ROMEO_LOCATION_1,
            new String[]{"Yes.", "No."}, "Yes", null);
        // Step 2: Go to Juliet
        walkToJulietHouse = new WalkToLocationNode("walk_juliet_house", JULIET_HOUSE_GROUND, "Juliet's house");
        climbUpToJuliet = new InteractWithObjectNode("climb_up_juliet", "Staircase", "Climb-up", 
            new Tile(3156, 3435, 0), "Juliet's house stairs");
        talkToJuliet = new TalkToNPCNode("talk_juliet", "Juliet", JULIET_LOCATION);
        climbDownFromJuliet = new InteractWithObjectNode("climb_down_juliet", "Staircase", "Climb-down",
            new Tile(3156, 3435, 1), "Juliet's house stairs");
        // Step 3: Return to Romeo with message (config 10 -> 20 -> 30) - FIXED DIALOGUE
        returnToRomeo = new TalkToNPCNode("return_romeo", "Romeo", ROMEO_LOCATION_2,
            new String[]{"I have a message from Juliet.", "I'm just passing by."}, 
            "I have a message from Juliet", null);
        // Step 4: Go to Father Lawrence (config 30 -> 40)
        walkToFatherLawrence = new WalkToLocationNode("walk_father_lawrence", FATHER_LAWRENCE_LOCATION, "Father Lawrence");
        talkToFatherLawrence = new TalkToNPCNode("talk_father_lawrence", "Father Lawrence", FATHER_LAWRENCE_LOCATION);
        // Step 5: Get Cadava berries and go to Apothecary (config 40 -> 50)
        walkToCadavaBush = new WalkToLocationNode("walk_cadava_bush", CADAVA_BUSH_LOCATION, "Cadava bush");
        pickCadavaBerries = new InteractWithObjectNode("pick_cadava_berries", "Cadava bush", "Pick-from",
            CADAVA_BUSH_LOCATION, "Cadava bush");
        walkToApothecary = new WalkToLocationNode("walk_apothecary", APOTHECARY_LOCATION, "Apothecary");
        talkToApothecary = new TalkToNPCNode("talk_apothecary", "Apothecary", APOTHECARY_LOCATION);
        // Step 6: Return to Juliet with potion (config 50 -> 60)
        returnToJulietWithPotion = new WalkToLocationNode("return_juliet_potion", JULIET_HOUSE_GROUND, "Juliet's house for potion");
        climbUpToJulietFinal = new InteractWithObjectNode("climb_up_juliet_final", "Staircase", "Climb-up",
            new Tile(3156, 3435, 0), "Juliet's house stairs final");
        giveJulietPotion = new TalkToNPCNode("give_juliet_potion", "Juliet", JULIET_LOCATION) {
            @Override
            protected boolean performAction() {
                // Enhanced dialogue handling for cutscene
                try {
                    log("Giving Juliet the Cadava potion with enhanced dialogue handling...");
                    
                    // Call the parent method to handle basic NPC interaction
                    boolean success = super.performAction();
                    
                    if (success) {
                        // Additional wait for cutscene completion
                        log("Waiting for cutscene to complete...");
                        org.dreambot.api.utilities.Sleep.sleep(3000, 5000);
                        
                        // Verify quest progression by checking if potion was consumed
                        if (!org.dreambot.api.methods.container.impl.Inventory.contains("Cadava potion")) {
                            log("Successfully gave Juliet the potion - cutscene completed");
                        }
                    }
                    
                    return success;
                } catch (Exception e) {
                    log("Exception in enhanced Juliet dialogue: " + e.getMessage());
                    return super.performAction(); // Fallback to basic handling
                }
            }
        };
        climbDownFinal = new InteractWithObjectNode("climb_down_final", "Staircase", "Climb-down",
            new Tile(3156, 3435, 1), "Juliet's house stairs final");
        // Step 7: Final return to Romeo (quest complete)
        finalReturnToRomeo = new TalkToNPCNode("final_return_romeo", "Romeo", ROMEO_LOCATION_2);
        // Quest completion node
        questComplete = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                log("Romeo and Juliet quest completed!");
                return true;
            }
        };
        // SMART DECISION NODE - uses config + location to determine next step
        smartDecisionNode = new QuestNode("smart_decision", "Smart Quest Decision") {
            @Override
            public ExecutionResult execute() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG);
                Tile currentTile = Players.getLocal().getTile();
                int currentZ = currentTile.getZ();
                log("Config 144 = " + config + ", Location: " + currentTile + " (Z=" + currentZ + ")");
                QuestNode nextStep = null;
                if (config == 0) {
                    nextStep = talkToRomeoInitial;
                    log("-> Talk to Romeo (quest not started)");
                } else if (config == 10) {
                    if (currentZ == 0 && currentTile.distance(JULIET_HOUSE_GROUND) > 5) {
                        nextStep = walkToJulietHouse;
                        log("-> Walk to Juliet's house");
                    } else if (currentZ == 0 && currentTile.distance(JULIET_HOUSE_GROUND) <= 5) {
                        nextStep = climbUpToJuliet;
                        log("-> Climb up to Juliet");
                    } else if (currentZ == 1) {
                        nextStep = talkToJuliet;
                        log("-> Talk to Juliet");
                    }
                } else if (config == 20) {
                    if (currentZ == 1) {
                        nextStep = climbDownFromJuliet;
                        log("-> Climb down from Juliet");
                    } else {
                        nextStep = returnToRomeo;
                        log("-> Return to Romeo");
                    }
                } else if (config == 30) {
                    // FIXED: Add location-based logic for Father Lawrence
                    if (currentTile.distance(FATHER_LAWRENCE_LOCATION) > 3) {
                        nextStep = walkToFatherLawrence;
                        log("-> Walk to Father Lawrence");
                    } else {
                        nextStep = talkToFatherLawrence;
                        log("-> Talk to Father Lawrence");
                    }
                } else if (config == 40) {
                    if (!org.dreambot.api.methods.container.impl.Inventory.contains("Cadava berries")) {
                        if (currentTile.distance(CADAVA_BUSH_LOCATION) > 3) {
                            nextStep = walkToCadavaBush;
                            log("-> Walk to Cadava bush");
                        } else {
                            nextStep = pickCadavaBerries;
                            log("-> Pick Cadava berries");
                        }
                    } else {
                        // New logic: Walk to Apothecary if more than 3 tiles away, talk if within 3 tiles
                        if (currentTile.distance(APOTHECARY_LOCATION) > 3) {
                            nextStep = walkToApothecary;
                            log("-> Walk to Apothecary (more than 3 tiles away)");
                        } else {
                            nextStep = talkToApothecary;
                            log("-> Talk to Apothecary (within 3 tiles)");
                        }
                    }
                } else if (config == 50) {
                    if (currentZ == 0 && currentTile.distance(JULIET_HOUSE_GROUND) > 5) {
                        nextStep = returnToJulietWithPotion;
                        log("-> Return to Juliet's house with potion");
                    } else if (currentZ == 0) {
                        nextStep = climbUpToJulietFinal;
                        log("-> Climb up to Juliet (final)");
                    } else if (currentZ == 1) {
                        nextStep = giveJulietPotion;
                        log("-> Give Juliet the potion");
                    }
                } else if (config == 60) {
                    // After giving Juliet the potion, need to return to Romeo to complete quest
                    if (currentZ == 1) {
                        nextStep = climbDownFinal;
                        log("-> Climb down after giving potion to Juliet");
                    } else if (currentZ == 0 && currentTile.distance(ROMEO_LOCATION_2) > 3) {
                        nextStep = finalReturnToRomeo;
                        log("-> Walk to Romeo to complete quest");
                    } else if (currentZ == 0 && currentTile.distance(ROMEO_LOCATION_2) <= 3) {
                        nextStep = finalReturnToRomeo;
                        log("-> Talk to Romeo to complete quest");
                    } else {
                        // Only mark complete after talking to Romeo
                        nextStep = questComplete;
                        log("-> Quest complete!");
                    }
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
            case 10: return 15;  // Talked to Romeo
            case 20: return 30;  // Talked to Juliet
            case 30: return 45;  // Returned to Romeo
            case 40: return 60;  // Talked to Father Lawrence
            case 50: return 80;  // Got potion from Apothecary
            case 60: return 100; // Quest complete
            default: 
                // Handle any intermediate values
                return Math.min(100, (configValue * 100) / 60);
        }
    }
}