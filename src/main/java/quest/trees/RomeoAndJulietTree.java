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
        
        // FIXED: Apothecary dialogue with correct sequence:
        // 1. Select "Talk about something else." (option 2)
        // 2. Then select "Talk about Romeo & Juliet." (option 1)
        talkToApothecary = new TalkToNPCNode("talk_apothecary", "Apothecary", APOTHECARY_LOCATION) {
            @Override
            protected boolean performAction() {
                try {
                    log("Starting Apothecary dialogue sequence for Romeo & Juliet quest...");
                    
                    // Get initial quest config to detect changes
                    int initialConfig = PlayerSettings.getConfig(QUEST_CONFIG);
                    log("Initial quest config (144): " + initialConfig);
                    
                    // Walk to and interact with Apothecary
                    if (!walkToNPCAndTalk("Apothecary", APOTHECARY_LOCATION)) {
                        return false;
                    }
                    
                    // Handle the two-step dialogue sequence
                    return handleApothecaryDialogue(initialConfig);
                    
                } catch (Exception e) {
                    log("Exception in Apothecary dialogue: " + e.getMessage());
                    return false;
                }
            }
            
            private boolean walkToNPCAndTalk(String npcName, Tile location) {
                try {
                    // Walk to location if not close
                    if (Players.getLocal().getTile().distance(location) > 5) {
                        log("Walking to " + npcName + " at " + location);
                        if (!org.dreambot.api.methods.walking.impl.Walking.walk(location)) {
                            log("Failed to walk to " + npcName);
                            return false;
                        }
                        org.dreambot.api.utilities.Sleep.sleepUntil(() -> 
                            Players.getLocal().getTile().distance(location) <= 5, 10000);
                    }
                    
                    // Find and talk to NPC
                    org.dreambot.api.wrappers.interactive.NPC npc = 
                        org.dreambot.api.methods.interactive.NPCs.closest(npcName);
                    if (npc == null) {
                        log("Could not find " + npcName);
                        return false;
                    }
                    
                    log("Talking to " + npcName);
                    if (!npc.interact("Talk-to")) {
                        log("Failed to interact with " + npcName);
                        return false;
                    }
                    
                    // Wait for dialogue to open
                    boolean dialogueOpened = org.dreambot.api.utilities.Sleep.sleepUntil(() -> 
                        org.dreambot.api.methods.dialogues.Dialogues.inDialogue(), 7000);
                    
                    if (!dialogueOpened) {
                        log("Dialogue did not open with " + npcName);
                        return false;
                    }
                    
                    return true;
                } catch (Exception e) {
                    log("Exception in walkToNPCAndTalk: " + e.getMessage());
                    return false;
                }
            }
            
            private boolean handleApothecaryDialogue(int initialConfig) {
                try {
                    // Step 1: Wait for first set of options and select "Talk about something else."
                    log("Waiting for first dialogue options...");
                    boolean optionsAvailable = org.dreambot.api.utilities.Sleep.sleepUntil(() -> 
                        org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable(), 10000);
                    
                    if (!optionsAvailable) {
                        log("No dialogue options available for first step");
                        return false;
                    }
                    
                    String[] options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                    log("First dialogue options: " + java.util.Arrays.toString(options));
                    
                    // Find and select "Talk about something else." (should be option 2)
                    boolean foundOption = false;
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].contains("Talk about something else") || 
                            options[i].contains("something else")) {
                            log("Selecting option " + (i + 1) + ": " + options[i]);
                            if (org.dreambot.api.methods.dialogues.Dialogues.chooseOption(i + 1)) {
                                foundOption = true;
                                break;
                            }
                        }
                    }
                    
                    if (!foundOption) {
                        log("Could not find 'Talk about something else' option, selecting option 2 as fallback");
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(2);
                    }
                    
                    org.dreambot.api.utilities.Sleep.sleep(1000, 2000);
                    
                    // Step 2: Wait for second set of options and select "Talk about Romeo & Juliet."
                    log("Waiting for second dialogue options...");
                    optionsAvailable = org.dreambot.api.utilities.Sleep.sleepUntil(() -> 
                        org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable(), 10000);
                    
                    if (!optionsAvailable) {
                        log("No dialogue options available for second step");
                        return continueRemainingDialogue();
                    }
                    
                    options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                    log("Second dialogue options: " + java.util.Arrays.toString(options));
                    
                    // Find and select "Talk about Romeo & Juliet." (should be option 1)
                    foundOption = false;
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].contains("Romeo") && options[i].contains("Juliet")) {
                            log("Selecting option " + (i + 1) + ": " + options[i]);
                            if (org.dreambot.api.methods.dialogues.Dialogues.chooseOption(i + 1)) {
                                foundOption = true;
                                break;
                            }
                        }
                    }
                    
                    if (!foundOption) {
                        log("Could not find 'Romeo & Juliet' option, selecting option 1 as fallback");
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                    }
                    
                    org.dreambot.api.utilities.Sleep.sleep(1000, 2000);
                    
                    // Step 3: Continue through remaining dialogue and handle cutscene
                    return continueRemainingDialogue();
                    
                } catch (Exception e) {
                    log("Exception in handleApothecaryDialogue: " + e.getMessage());
                    return false;
                }
            }
            
            private boolean continueRemainingDialogue() {
                try {
                    log("Continuing through remaining dialogue...");
                    
                    while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                        if (org.dreambot.api.methods.dialogues.Dialogues.canContinue()) {
                            org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                            org.dreambot.api.utilities.Sleep.sleep(1000, 2000);
                        } else if (org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                            // If more options appear, select the first one
                            log("Additional dialogue options found, selecting first option");
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                            org.dreambot.api.utilities.Sleep.sleep(1000, 2000);
                        } else {
                            log("Dialogue state unclear, waiting...");
                            org.dreambot.api.utilities.Sleep.sleep(1000);
                        }
                    }
                    
                    log("Main dialogue completed, waiting for cutscene...");
                    // Wait for cutscene to complete (mentioned there's a small cutscene)
                    org.dreambot.api.utilities.Sleep.sleep(3000, 5000);
                    
                    // Continue any post-cutscene dialogue
                    if (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                        log("Post-cutscene dialogue detected, continuing...");
                        while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                            if (org.dreambot.api.methods.dialogues.Dialogues.canContinue()) {
                                org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                                org.dreambot.api.utilities.Sleep.sleep(1000, 2000);
                            } else {
                                org.dreambot.api.utilities.Sleep.sleep(1000);
                            }
                        }
                    }
                    
                    log("All Apothecary dialogue completed");
                    
                    // Wait for quest config to update and verify we got the potion
                    org.dreambot.api.utilities.Sleep.sleep(2000, 3000);
                    
                    if (org.dreambot.api.methods.container.impl.Inventory.contains("Cadava potion")) {
                        log("Successfully obtained Cadava potion from Apothecary");
                        return true;
                    } else {
                        log("Warning: Cadava potion not found in inventory after dialogue");
                        return true; // Still return true as dialogue completed
                    }
                    
                } catch (Exception e) {
                    log("Exception in continueRemainingDialogue: " + e.getMessage());
                    return false;
                }
            }
        };
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
                    // Enhanced logic for Father Lawrence transition
                    if (currentTile.distance(FATHER_LAWRENCE_LOCATION) > 3) {
                        nextStep = walkToFatherLawrence;
                        log("-> Walk to Father Lawrence");
                    } else {
                        nextStep = talkToFatherLawrence;
                        log("-> Talk to Father Lawrence (will advance to config 40)");
                    }
                } else if (config == 40) {
                    // Enhanced logic for Cadava berries -> Apothecary transition
                    boolean hasBerries = org.dreambot.api.methods.container.impl.Inventory.contains("Cadava berries");
                    boolean hasPotion = org.dreambot.api.methods.container.impl.Inventory.contains("Cadava potion");
                    
                    log("Config 40 - Berries: " + hasBerries + ", Potion: " + hasPotion);
                    
                    if (!hasBerries && !hasPotion) {
                        // Need to get berries first
                        if (currentTile.distance(CADAVA_BUSH_LOCATION) > 3) {
                            nextStep = walkToCadavaBush;
                            log("-> Walk to Cadava bush");
                        } else {
                            nextStep = pickCadavaBerries;
                            log("-> Pick Cadava berries");
                        }
                    } else if (hasBerries && !hasPotion) {
                        // Have berries, need to go to Apothecary
                        if (currentTile.distance(APOTHECARY_LOCATION) > 3) {
                            nextStep = walkToApothecary;
                            log("-> Walk to Apothecary (have berries, need potion)");
                        } else {
                            nextStep = talkToApothecary;
                            log("-> Talk to Apothecary (have berries, getting potion)");
                        }
                    } else if (hasPotion) {
                        // Already have potion, config should update to 50 soon
                        log("-> Have Cadava potion, waiting for config update to 50...");
                        // Wait a moment for config to update
                        org.dreambot.api.utilities.Sleep.sleep(1000, 2000);
                        int newConfig = PlayerSettings.getConfig(QUEST_CONFIG);
                        if (newConfig == 50) {
                            log("Config updated to 50, proceeding to Juliet");
                            if (currentZ == 0 && currentTile.distance(JULIET_HOUSE_GROUND) > 5) {
                                nextStep = returnToJulietWithPotion;
                                log("-> Return to Juliet's house with potion");
                            } else if (currentZ == 0) {
                                nextStep = climbUpToJulietFinal;
                                log("-> Climb up to Juliet (final)");
                            } else {
                                nextStep = giveJulietPotion;
                                log("-> Give Juliet the potion");
                            }
                        } else {
                            // Config hasn't updated yet, wait
                            nextStep = null;
                            log("-> Waiting for quest config to update from 40 to 50...");
                            return ExecutionResult.success(null, "Waiting for config update");
                        }
                    } else {
                        // Fallback
                        nextStep = walkToCadavaBush;
                        log("-> Fallback: Walk to Cadava bush");
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