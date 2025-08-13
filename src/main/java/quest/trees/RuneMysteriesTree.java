package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.DecisionNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.container.impl.Inventory;

/**
 * Rune Mysteries Quest Tree
 * Automated quest using decision tree structure
 * Config 101 tracks quest progress: 0 (not started) -> 10 (started) -> 20 (got package) -> 30 (got notes) -> 40 (complete)
 */
public class RuneMysteriesTree extends QuestTree {
    
    // Quest progress config ID
    private static final int QUEST_CONFIG = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 10;        // Talked to Duke, have Air talisman
    private static final int QUEST_GOT_PACKAGE = 20;    // Got research package from Sedridor
    private static final int QUEST_GOT_NOTES = 30;      // Got research notes from Aubury
    private static final int QUEST_COMPLETE = 40;       // Quest completed
    
    // Key locations from original script
    private static final Tile DUKE_LOCATION = new Tile(3210, 3224, 1);
    private static final Tile WIZARDS_TOWER_ENTRANCE = new Tile(3104, 3162, 0);
    private static final Tile SEDRIDOR_LOCATION = new Tile(3102, 9570, 0);
    private static final Tile AUBURY_LOCATION = new Tile(3253, 3403, 0);
    
    // Quest items
    private static final String AIR_TALISMAN = "Air talisman";
    private static final String RESEARCH_PACKAGE = "Research package";
    private static final String RESEARCH_NOTES = "Research notes";
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode talkToDukeStart;
    private QuestNode walkToWizardsTower;
    private QuestNode goDownToBasement;
    private QuestNode talkToSedridorForPackage;
    private QuestNode walkToAubury;
    private QuestNode talkToAuburyForNotes;
    private QuestNode returnToWizardsTower;
    private QuestNode goDownToBasementFinal;
    private QuestNode talkToSedridorComplete;
    private QuestNode questCompleteNode;
    
    public RuneMysteriesTree() {
        super("Rune Mysteries");
    }
    
    @Override
    protected void buildTree() {
        // Build all the nodes
        createNodes();
        
        // Set the root node to our smart decision node
        rootNode = smartDecisionNode;
    }
    
    private void createNodes() {
        // Smart decision node - determines next step based on quest state and inventory
        smartDecisionNode = new DecisionNode("rune_mysteries_decision", "Determine Rune Mysteries next step") {
            @Override
            protected String makeDecision() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG);
                log("Quest config " + QUEST_CONFIG + " = " + config);
                
                if (config >= QUEST_COMPLETE) {
                    log("Quest is complete! Config = " + config);
                    setQuestComplete();
                    return "complete";
                }
                
                if (config == QUEST_NOT_STARTED) {
                    log("Quest not started - going to Duke Horacio");
                    return "not_started";
                }
                
                if (config == QUEST_STARTED) {
                    log("Quest started - have Air talisman, going to Sedridor");
                    return "has_talisman";
                }
                
                if (config == QUEST_GOT_PACKAGE) {
                    // Check if we're already at Aubury's location
                    if (Players.getLocal().getTile().distance(AUBURY_LOCATION) <= 3) {
                        log("Have research package (config=" + config + ") and at Aubury - talking to Aubury");
                        return "talk_to_aubury";
                    } else {
                        log("Have research package (config=" + config + ") - going to Aubury");
                        return "has_package";
                    }
                }
                
                if (config == QUEST_GOT_NOTES) {
                    log("Have research notes (config=" + config + ") - returning to Sedridor");
                    return "has_notes";
                }
                
                // Fallback logic based on inventory items if config doesn't match expected values
                if (Inventory.contains(RESEARCH_NOTES)) {
                    log("Fallback: Have research notes - returning to Sedridor");
                    return "has_notes";
                } else if (Inventory.contains(RESEARCH_PACKAGE)) {
                    // Check if we're already at Aubury's location
                    if (Players.getLocal().getTile().distance(AUBURY_LOCATION) <= 3) {
                        log("Fallback: Have research package and at Aubury - talking to Aubury");
                        return "talk_to_aubury";
                    } else {
                        log("Fallback: Have research package - going to Aubury");
                        return "has_package";
                    }
                } else if (Inventory.contains(AIR_TALISMAN)) {
                    log("Fallback: Have air talisman - going to Sedridor");
                    return "has_talisman";
                } else {
                    log("Fallback: Quest started but missing items - restarting from Duke");
                    return "not_started";
                }
            }
        };
        

        
        // Step 1: Talk to Duke Horacio to start quest
        talkToDukeStart = new TalkToNPCNode("talk_duke_start", "Duke Horacio", DUKE_LOCATION,
            new String[]{"Have you any quests for me?", "Yes.", "No."}, "Have you any quests for me?", null) {
            @Override
            protected boolean performAction() {
                log("Starting Rune Mysteries quest with Duke Horacio");
                boolean success = super.performAction();
                if (success) {
                    // Wait for air talisman to appear
                    boolean receivedTalisman = org.dreambot.api.utilities.Sleep.sleepUntil(() -> 
                        Inventory.contains(AIR_TALISMAN), 10000);
                    if (receivedTalisman) {
                        log("Successfully received Air talisman from Duke Horacio");
                        return true;
                    } else {
                        log("Failed to receive Air talisman within 10 seconds");
                        return false;
                    }
                }
                return success;
            }
        };
        
        // Step 2: Walk to Wizards' Tower
        walkToWizardsTower = new WalkToLocationNode("walk_wizards_tower", WIZARDS_TOWER_ENTRANCE, "Wizards' Tower");
        
        // Step 3: Go down to basement via ladder (simple, reliable)
        goDownToBasement = new InteractWithObjectNode("go_down_basement", "Ladder", "Climb-down",
            WIZARDS_TOWER_ENTRANCE, "Ladder to basement") {
            @Override
            protected boolean performAction() {
                log("Going down to basement via ladder (simple)");
                // Already in basement?
                if (Players.getLocal().getTile().getY() > 9000) return true;

                // Walk to the tower entrance/ladder area
                new quest.nodes.actions.WalkToLocationNode(
                    "walk_to_basement_ladder",
                    WIZARDS_TOWER_ENTRANCE,
                    6,
                    "Wizards' Tower ladder area"
                ).execute();

                // Interact with the ladder directly and wait for basement Y plane
                org.dreambot.api.wrappers.interactive.GameObject ladder =
                    org.dreambot.api.methods.interactive.GameObjects.closest("Ladder");
                if (ladder == null || !ladder.interact("Climb-down")) {
                    log("Could not interact with Ladder");
                    return false;
                }
                boolean inBasement = org.dreambot.api.utilities.Sleep.sleepUntil(
                    () -> Players.getLocal().getTile().getY() > 9000,
                    15000
                );
                if (!inBasement) log("Basement transition timed out");
                return inBasement;
            }
        };
        
        // Step 4: Talk to Sedridor to get research package
        talkToSedridorForPackage = new TalkToNPCNode("talk_sedridor_package", "Archmage Sedridor", SEDRIDOR_LOCATION,
            new String[]{"I have brought you this talisman", "talisman", "Duke", "research"}, "I have brought you this talisman", null) {
            @Override
            protected boolean performAction() {
                log("Getting research package from Archmage Sedridor");
                boolean success = super.performAction();
                if (success) {
                    // Wait for research package to appear
                    boolean receivedPackage = org.dreambot.api.utilities.Sleep.sleepUntil(() -> 
                        Inventory.contains(RESEARCH_PACKAGE), 10000);
                    if (receivedPackage) {
                        log("Successfully received research package from Sedridor");
                        return true;
                    } else {
                        log("Failed to receive research package within 10 seconds");
                        return false;
                    }
                }
                return success;
            }
        };
        
        // Step 5: Walk to Aubury in Varrock
        walkToAubury = new WalkToLocationNode("walk_aubury", AUBURY_LOCATION, "Aubury in Varrock East");
        
        // Step 6: Talk to Aubury to exchange package for notes
        talkToAuburyForNotes = new ActionNode("talk_aubury_notes", "Talk to Aubury to exchange package for notes") {
            @Override
            protected boolean performAction() {
                log("Exchanging research package for notes with Aubury");

                // Ensure Aubury NPC is present
                org.dreambot.api.wrappers.interactive.NPC aubury = org.dreambot.api.methods.interactive.NPCs.closest("Aubury");
                if (aubury == null) {
                    log("Could not find Aubury nearby");
                    return false;
                }

                // Interact to start dialogue
                if (!aubury.interact("Talk-to")) {
                    log("Failed to interact with Aubury");
                    return false;
                }

                // Wait for dialogue to start
                boolean dialogueStarted = org.dreambot.api.utilities.Sleep.sleepUntil(
                    org.dreambot.api.methods.dialogues.Dialogues::inDialogue, 7000);
                if (!dialogueStarted) {
                    log("Failed to start dialogue with Aubury");
                    return false;
                }

                // Actively select the option to hand over the package
                long start = System.currentTimeMillis();
                boolean optionChosen = false;
                while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()
                        && System.currentTimeMillis() - start < 20000) {
                    if (org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                        String[] options = org.dreambot.api.methods.dialogues.Dialogues.getOptions();
                        int chosenIdx = -1;
                        if (options != null) {
                            for (int i = 0; i < options.length; i++) {
                                String opt = options[i] == null ? "" : options[i].toLowerCase();
                                if (opt.contains("package") || opt.contains("sedridor") || opt.contains("research")) {
                                    chosenIdx = i;
                                    break;
                                }
                            }
                        }
                        if (chosenIdx == -1) {
                            // Default to first option to progress
                            chosenIdx = 0;
                        }
                        org.dreambot.api.methods.dialogues.Dialogues.chooseOption(chosenIdx + 1);
                        optionChosen = true;
                        org.dreambot.api.utilities.Sleep.sleep(400, 700);
                    } else if (org.dreambot.api.methods.dialogues.Dialogues.canContinue()) {
                        if (!org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue()) {
                            org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                        }
                        org.dreambot.api.utilities.Sleep.sleep(350, 650);
                    } else {
                        org.dreambot.api.utilities.Sleep.sleep(250, 450);
                    }

                    // Early exit if we already received the notes
                    if (Inventory.contains(RESEARCH_NOTES)) break;
                }

                // Wait up to 10s for notes to appear after dialogue interaction
                boolean receivedNotes = org.dreambot.api.utilities.Sleep.sleepUntil(
                    () -> Inventory.contains(RESEARCH_NOTES)
                        || org.dreambot.api.methods.settings.PlayerSettings.getConfig(QUEST_CONFIG) >= QUEST_GOT_NOTES,
                    10000);

                if (receivedNotes || Inventory.contains(RESEARCH_NOTES)) {
                    log("Successfully received research notes from Aubury");
                    return true;
                }

                // If we didn't receive notes but we did choose an option, allow fallback continue for a short time
                if (optionChosen) {
                    long fallbackStart = System.currentTimeMillis();
                    while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()
                            && System.currentTimeMillis() - fallbackStart < 5000) {
                        if (org.dreambot.api.methods.dialogues.Dialogues.canContinue()) {
                            if (!org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue()) {
                                org.dreambot.api.methods.dialogues.Dialogues.continueDialogue();
                            }
                        } else if (org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                            org.dreambot.api.methods.dialogues.Dialogues.chooseOption(1);
                        }
                        org.dreambot.api.utilities.Sleep.sleep(300, 500);
                        if (Inventory.contains(RESEARCH_NOTES)) break;
                    }
                }

                boolean finalCheck = Inventory.contains(RESEARCH_NOTES)
                    || org.dreambot.api.methods.settings.PlayerSettings.getConfig(QUEST_CONFIG) >= QUEST_GOT_NOTES;
                if (!finalCheck) {
                    log("Failed to receive research notes from Aubury");
                }
                return finalCheck;
            }
        };
        
        // Step 7: Return to Wizards' Tower
        returnToWizardsTower = new WalkToLocationNode("return_wizards_tower", WIZARDS_TOWER_ENTRANCE, "Wizards' Tower (return)");
        
        // Step 8: Go down to basement again (simple, reliable)
        goDownToBasementFinal = new InteractWithObjectNode("go_down_basement_final", "Ladder", "Climb-down",
            WIZARDS_TOWER_ENTRANCE, "Ladder to basement (final)") {
            @Override
            protected boolean performAction() {
                log("Climbing down to basement to turn in notes (simple)");
                // Already in basement?
                if (Players.getLocal().getTile().getY() > 9000) return true;

                // Walk to the ladder area
                new quest.nodes.actions.WalkToLocationNode(
                    "walk_to_basement_ladder_final",
                    WIZARDS_TOWER_ENTRANCE,
                    6,
                    "Wizards' Tower ladder area"
                ).execute();

                // Interact with the ladder and wait for basement plane
                org.dreambot.api.wrappers.interactive.GameObject ladder =
                    org.dreambot.api.methods.interactive.GameObjects.closest("Ladder");
                if (ladder == null || !ladder.interact("Climb-down")) {
                    log("Could not interact with Ladder for final descent");
                    return false;
                }
                boolean inBasement = org.dreambot.api.utilities.Sleep.sleepUntil(
                    () -> Players.getLocal().getTile().getY() > 9000,
                    15000
                );
                if (!inBasement) log("Final basement transition timed out");
                return inBasement;
            }
        };
        
        // Step 9: Talk to Sedridor to complete quest
        talkToSedridorComplete = new TalkToNPCNode("talk_sedridor_complete", "Archmage Sedridor", SEDRIDOR_LOCATION,
            new String[]{"I have brought you the research notes", "notes", "research", "completed"}, "I have brought you the research notes", null) {
            @Override
            protected boolean performAction() {
                log("Completing quest with Sedridor");
                boolean success = super.performAction();
                if (success) {
                    // Wait for quest completion
                    boolean questCompleted = org.dreambot.api.utilities.Sleep.sleepUntil(() -> 
                        PlayerSettings.getConfig(QUEST_CONFIG) >= QUEST_COMPLETE, 12000);
                    if (questCompleted) {
                        log("Quest completed successfully!");
                        setQuestComplete();
                        return true;
                    } else {
                        // Manual fallback check
                        org.dreambot.api.utilities.Sleep.sleep(2000, 3000);
                        int finalConfig = PlayerSettings.getConfig(QUEST_CONFIG);
                        if (finalConfig >= QUEST_COMPLETE) {
                            log("Quest completed! Final config: " + finalConfig);
                            setQuestComplete();
                            return true;
                        } else {
                            log("Quest not completed after dialogue. Config: " + finalConfig);
                            return false;
                        }
                    }
                }
                return success;
            }
        };
        
        // Quest complete node
        questCompleteNode = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                log("Rune Mysteries quest completed successfully!");
                setQuestComplete();
                return true;
            }
        };
        
        // Chain action nodes together where appropriate (only ActionNodes have setNextNode)
        ((ActionNode)walkToWizardsTower).setNextNode(goDownToBasement);
        ((ActionNode)goDownToBasement).setNextNode(talkToSedridorForPackage);
        ((ActionNode)walkToAubury).setNextNode(talkToAuburyForNotes);
        ((ActionNode)returnToWizardsTower).setNextNode(goDownToBasementFinal);
        ((ActionNode)goDownToBasementFinal).setNextNode(talkToSedridorComplete);
        ((ActionNode)talkToSedridorComplete).setNextNode(questCompleteNode);
        
        // Set up branches for the smart decision node (after all nodes are created)
        DecisionNode decisionNode = (DecisionNode) smartDecisionNode;
        decisionNode.addBranch("complete", questCompleteNode);
        decisionNode.addBranch("not_started", talkToDukeStart);
        decisionNode.addBranch("has_notes", returnToWizardsTower);
        decisionNode.addBranch("has_package", walkToAubury);
        decisionNode.addBranch("talk_to_aubury", talkToAuburyForNotes);
        decisionNode.addBranch("has_talisman", walkToWizardsTower);
        decisionNode.addBranch("started_no_items", talkToDukeStart);
        decisionNode.setDefaultBranch(talkToDukeStart); // Default fallback
    }
}