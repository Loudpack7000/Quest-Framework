package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.DecisionNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.widgets.WidgetChild;

/**
 * Sheep Shearer Quest Tree
 * Automated quest using decision tree structure
 * Config 179 tracks quest progress: 0 (not started) -> 1 (started) -> 21 (complete)
 * Config 101 also involved in quest tracking (6 -> 7)
 */
public class SheepShearerTree extends QuestTree {
    
    // Quest progress configs - Sheep Shearer uses config 179 and 101
    private static final int QUEST_CONFIG_PRIMARY = 179;
    private static final int QUEST_CONFIG_SECONDARY = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 1;
    private static final int QUEST_COMPLETE = 21;
    
    // Required wool count
    private static final int REQUIRED_WOOL = 20;
    
    // Quest locations - based on log file coordinates
    private static final Tile FRED_LOCATION = new Tile(3188, 3272, 0);
    private static final Area SHEEP_FIELD = new Area(3197, 3256, 3210, 3279, 0); // Inside the fence after entering gate
    private static final Tile GATE_LOCATION = new Tile(3211, 3261, 0);
    
    // Quest nodes
    private DecisionNode smartDecisionNode;
    private QuestNode walkToFred;
    private QuestNode shearSheepNode;
    private QuestNode walkToSpinningWheel;
    private QuestNode walkBackToFred;
    private QuestNode questCompleteNode;
    
    public SheepShearerTree() {
        super("Sheep Shearer");
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
        smartDecisionNode = new DecisionNode("sheep_shearer_decision", "Determine Sheep Shearer next step") {
            @Override
            protected String makeDecision() {
                int config179 = PlayerSettings.getConfig(QUEST_CONFIG_PRIMARY);
                int config101 = PlayerSettings.getConfig(QUEST_CONFIG_SECONDARY);
                log("Quest config " + QUEST_CONFIG_PRIMARY + " = " + config179);
                log("Quest config " + QUEST_CONFIG_SECONDARY + " = " + config101);

                // Check if quest is complete
                if (config179 >= QUEST_COMPLETE) {
                    log("Quest is complete! Config179=" + config179);
                    setQuestComplete();
                    return "complete";
                }

                // **NEW CHECK**: If spinning interface is open, don't make new decisions
                if (Widgets.isVisible(270)) {
                    log("Currently spinning - waiting for interface to close");
                    return "spin_wool"; // Stay in spinning state
                }
                
                // **NEW CHECK**: If player is animating (spinning), wait
                if (Players.getLocal().isAnimating()) {
                    log("Player is animating (likely spinning) - waiting");
                    return "spin_wool";
                }

                // Count wool and balls of wool
                int woolCount = Inventory.count("Wool");
                int ballOfWoolCount = Inventory.count("Ball of wool");
                int totalWool = woolCount + ballOfWoolCount;

                log("Current wool: " + woolCount + ", balls of wool: " + ballOfWoolCount + ", total: " + totalWool);

                if (config179 == QUEST_NOT_STARTED) {
                    log("Quest not started - going to talk to Fred");
                    return "start_quest";
                }

                if (config179 >= QUEST_STARTED) {
                    // Quest has been started
                    if (ballOfWoolCount >= REQUIRED_WOOL) {
                        log("Have " + REQUIRED_WOOL + " balls of wool - completing quest");
                        return "complete_quest";
                    } else if (woolCount >= (REQUIRED_WOOL - ballOfWoolCount)) {
                        log("Have enough wool to spin - going to spinning wheel");
                        return "spin_wool";
                    } else {
                        log("Need more wool - going to shear sheep");
                        return "shear_sheep";
                    }
                }

                log("Unknown quest state - defaulting to start");
                return "start_quest";
            }
        };
        
        // Walk to Fred the Farmer and talk to start quest
        walkToFred = new ActionNode("walk_and_talk_fred_start", "Walk to Fred and start quest") {
            @Override
            protected boolean performAction() {
                try {
                    log("Walking to Fred to start quest");
                    
                    // If more than 6 tiles away, walk to him
                    if (FRED_LOCATION.distance() > 6) {
                        log("Fred is far away (" + FRED_LOCATION.distance() + " tiles) - walking to him");
                        Walking.walk(FRED_LOCATION);
                        Sleep.sleepUntil(() -> FRED_LOCATION.distance() <= 6, 15000);
                        
                        // Double-check we're actually close enough
                        if (FRED_LOCATION.distance() > 6) {
                            log("Still too far from Fred (" + FRED_LOCATION.distance() + " tiles) - trying again");
                            Walking.walk(FRED_LOCATION);
                            Sleep.sleepUntil(() -> FRED_LOCATION.distance() <= 6, 10000);
                        }
                    }
                    
                    // Verify we're close enough before attempting to talk
                    double currentDistance = FRED_LOCATION.distance();
                    log("Current distance to Fred: " + currentDistance + " tiles");
                    
                    if (currentDistance > 8) {
                        log("Still too far from Fred (" + currentDistance + " tiles) - cannot start quest yet");
                        return false; // Will retry this action
                    }
                    
                    // Now talk to Fred
                    log("Close enough to Fred (" + currentDistance + " tiles) - talking to start quest");
                    NPC fred = NPCs.closest("Fred the Farmer");
                    if (fred != null && fred.canReach()) {
                        log("Found Fred at: " + fred.getTile() + " (distance: " + fred.getTile().distance() + " tiles)");
                        int configBefore = PlayerSettings.getConfig(QUEST_CONFIG_PRIMARY);
                        log("Before Fred interaction - Config179: " + configBefore);
                        
                        if (fred.interact("Talk-to")) {
                            // Handle dialogue
                            Sleep.sleepUntil(() -> org.dreambot.api.methods.dialogues.Dialogues.inDialogue(), 5000);
                            
                            // Use spacebar to continue through initial dialogue
                            while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue() && 
                                   !org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                                org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue();
                                Sleep.sleep(1000, 2000);
                            }
                            
                            // Select "I'm looking for a quest."
                            if (org.dreambot.api.methods.dialogues.Dialogues.chooseOption("I'm looking for a quest.")) {
                                log("Selected quest option");
                                Sleep.sleep(1000, 2000);
                            }
                            
                            // Use spacebar to continue after first option
                            while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue() && 
                                   !org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                                org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue();
                                Sleep.sleep(1000, 2000);
                            }
                            
                            // Select "Yes."
                            if (org.dreambot.api.methods.dialogues.Dialogues.chooseOption("Yes.")) {
                                log("Accepted quest");
                                Sleep.sleep(2000, 3000);
                            }
                            
                            // Use spacebar to finish dialogue
                            while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                                org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue();
                                Sleep.sleep(1000, 2000);
                            }
                            
                            int configAfter = PlayerSettings.getConfig(QUEST_CONFIG_PRIMARY);
                            log("After Fred interaction - Config179: " + configAfter);
                            
                            return configAfter > configBefore;
                        }
                    } else {
                        log("Could not find Fred the Farmer or he is not reachable");
                        return false;
                    }
                    
                    return false;
                } catch (Exception e) {
                    log("Exception while talking to Fred: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Shear sheep node
        shearSheepNode = new ActionNode("shear_sheep", "Shear sheep until we have enough wool") {
            @Override
            protected boolean performAction() {
                try {
                    log("Starting sheep shearing process");

                    // Only do gate logic if we're not already in the sheep field
                    if (!SHEEP_FIELD.contains(Players.getLocal())) {
                        log("Not in sheep field - need to enter through gate");
                        
                        // If not near the gate, walk to it
                        if (GATE_LOCATION.distance() > 5) {
                            log("Walking to gate location: " + GATE_LOCATION);
                            Walking.walk(GATE_LOCATION);
                            Sleep.sleepUntil(() -> GATE_LOCATION.distance() < 5, 10000);
                        }

                        log("At gate - current position: " + Players.getLocal().getTile());
                        
                        // Try to find a gate to open first
                        GameObject gate = GameObjects.closest("Gate");
                        if (gate != null) {
                            log("Found gate at: " + gate.getTile());
                            // Try to open the gate (in case it's closed)
                            if (gate.interact("Open")) {
                                log("Opened gate, waiting briefly...");
                                Sleep.sleep(1000, 2000);
                            } else {
                                log("Gate might already be open or interaction failed");
                            }
                        }
                        
                        // Whether gate was opened or already open, walk to sheep field center
                        Tile sheepFieldCenter = new Tile(3203, 3267, 0); // Center of sheep field
                        log("Walking through gate to sheep field center: " + sheepFieldCenter);
                        Walking.walk(sheepFieldCenter);
                        Sleep.sleepUntil(() -> SHEEP_FIELD.contains(Players.getLocal()), 10000);
                        
                        // Final check - if still not in sheep field, something is wrong
                        if (!SHEEP_FIELD.contains(Players.getLocal())) {
                            log("Failed to enter sheep field after walking - current position: " + Players.getLocal().getTile());
                            return false;
                        }
                        
                        log("Successfully entered sheep field at: " + Players.getLocal().getTile());
                    } else {
                        log("Already in sheep field at: " + Players.getLocal().getTile() + " - starting to shear directly");
                    }

                    int targetWool = REQUIRED_WOOL - Inventory.count("Ball of wool");
                    int attempts = 0;
                    int maxAttempts = 50;

                    while (Inventory.count("Wool") < targetWool && attempts < maxAttempts) {
                        NPC sheep = NPCs.closest("Sheep");
                        if (sheep != null && sheep.canReach()) {
                            log("Attempting to shear sheep at: " + sheep.getTile());
                            if (sheep.interact("Shear")) {
                                log("Clicked sheep - waiting for animation...");
                                // Wait for animation to start (or timeout after 3 seconds)
                                boolean animationStarted = Sleep.sleepUntil(() -> Players.getLocal().isAnimating(), 3000);
                                if (animationStarted) {
                                    log("Animation started - waiting for completion...");
                                    // Wait for animation to finish
                                    Sleep.sleepUntil(() -> !Players.getLocal().isAnimating(), 5000);
                                    log("Animation finished");
                                    // No extra delay here - immediately try next sheep
                                } else {
                                    log("No animation detected - waiting briefly before retry...");
                                    Sleep.sleep(500, 800); // Short delay only if animation didn't start
                                }
                            } else {
                                log("Failed to interact with sheep - retrying in a moment");
                                Sleep.sleep(500, 800); // Short delay only if interaction failed
                            }
                        } else {
                            log("No reachable sheep found - waiting...");
                            Sleep.sleep(1000, 1500); // Reduced wait time when no sheep found
                        }

                        attempts++;
                        
                        // Log progress every 5 attempts to reduce spam
                        if (attempts % 5 == 0) {
                            log("Shearing progress: " + Inventory.count("Wool") + "/" + targetWool + " wool (attempt " + attempts + "/" + maxAttempts + ")");
                        }

                        if (Inventory.isFull()) {
                            log("Inventory full with " + Inventory.count("Wool") + " wool");
                            break;
                        }
                    }

                    int woolCount = Inventory.count("Wool");
                    log("Finished shearing - collected " + woolCount + " wool");
                    return woolCount > 0;

                } catch (Exception e) {
                    log("Exception while shearing sheep: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Walk to spinning wheel and spin wool
        walkToSpinningWheel = new ActionNode("walk_and_spin_wool", "Walk to spinning wheel and spin wool") {
            @Override
            protected boolean performAction() {
                try {
                    log("Going to spinning wheel to spin wool");
                    
                    Tile spinningWheelBuilding = new Tile(3204, 3229, 0);
                    Tile spinningWheelTile = new Tile(3209, 3212, 1); // Actual spinning wheel upstairs
                    
                    // Walk to spinning wheel building if not there
                    if (spinningWheelBuilding.distance() > 10) {
                        log("Walking to spinning wheel building");
                        Walking.walk(spinningWheelBuilding);
                        Sleep.sleepUntil(() -> spinningWheelBuilding.distance() <= 10, 15000);
                    }
                    
                    // Make sure we're upstairs - only check this if not already spinning
                    if (Players.getLocal().getZ() == 0 && !Widgets.isVisible(270) && !Players.getLocal().isAnimating()) {
                        log("Looking for staircase to climb up");
                        GameObject staircase = GameObjects.closest("Staircase");
                        if (staircase != null && staircase.canReach()) {
                            log("Climbing up staircase");
                            if (staircase.interact("Climb-up")) {
                                Sleep.sleepUntil(() -> Players.getLocal().getZ() > 0, 10000);
                            }
                        }
                        
                        if (Players.getLocal().getZ() == 0) {
                            log("Failed to get upstairs");
                            return false;
                        }
                    } else if (Players.getLocal().getZ() > 0) {
                        log("Already upstairs at Z level: " + Players.getLocal().getZ());
                    }
                    
                    // Walk to spinning wheel tile after going upstairs - only if not already spinning
                    if (spinningWheelTile.distance() > 4 && !Widgets.isVisible(270) && !Players.getLocal().isAnimating()) {
                        log("Walking to spinning wheel upstairs at: " + spinningWheelTile);
                        Walking.walk(spinningWheelTile);
                        Sleep.sleepUntil(() -> spinningWheelTile.distance() <= 4, 8000);
                    }
                    
                    int woolCount = Inventory.count("Wool");
                    if (woolCount == 0) {
                        log("No wool to spin");
                        return true;
                    }
                    
                    log("Found " + woolCount + " wool to spin");
                    
                    // Find spinning wheel
                    GameObject spinningWheel = GameObjects.closest("Spinning wheel");
                    if (spinningWheel != null && spinningWheel.canReach()) {
                        log("Using spinning wheel");
                        if (spinningWheel.interact("Spin")) {
                            // Wait for spinning interface to appear
                            Sleep.sleepUntil(() -> Widgets.isVisible(270), 5000);
                            
                            if (Widgets.isVisible(270)) {
                                log("Spinning interface opened - selecting wool to spin");
                                
                                // Select the wool item (Ball of Wool option)
                                WidgetChild woolOption = Widgets.getChildWidget(270, 14);
                                if (woolOption != null && woolOption.isVisible()) {
                                    log("Clicking on wool option");
                                    woolOption.interact();
                                    Sleep.sleep(1000, 2000);
                                    
                                    // Press Space to confirm and start spinning
                                    WidgetChild spaceButton = Widgets.getChildWidget(270, 0);
                                    if (spaceButton != null && spaceButton.isVisible()) {
                                        log("Pressing space to start spinning");
                                        spaceButton.interact();
                                        
                                        // **IMPROVED**: Wait for interface to close AND spinning to start
                                        log("Waiting for spinning to start...");
                                        Sleep.sleepUntil(() -> !Widgets.isVisible(270), 5000);
                                        
                                        // Wait for spinning to complete - more patient approach
                                        log("Spinning started - waiting for completion (this may take 30+ seconds)...");
                                        int originalWoolCount = Inventory.count("Wool");
                                        
                                        // Wait up to 60 seconds for all wool to be spun
                                        Sleep.sleepUntil(() -> {
                                            int currentWool = Inventory.count("Wool");
                                            if (currentWool < originalWoolCount) {
                                                log("Progress: " + (originalWoolCount - currentWool) + "/" + originalWoolCount + " wool spun");
                                            }
                                            return currentWool == 0;
                                        }, 60000);
                                        
                                        log("Finished spinning wool - " + Inventory.count("Ball of wool") + " balls of wool created");
                                        return true;
                                    } else {
                                        log("Could not find space button");
                                    }
                                } else {
                                    log("Could not find wool option in interface");
                                }
                            } else {
                                log("Spinning interface did not open");
                            }
                        }
                    } else {
                        log("Could not find or reach spinning wheel");
                    }
                    
                    return false;
                } catch (Exception e) {
                    log("Exception while spinning wool: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Walk back to Fred and complete quest
        walkBackToFred = new ActionNode("walk_and_talk_fred_complete", "Walk to Fred and complete quest") {
            @Override
            protected boolean performAction() {
                try {
                    log("Going to Fred to complete quest");
                    
                    // If more than 6 tiles away, walk to him
                    if (FRED_LOCATION.distance() > 6) {
                        log("Fred is far away (" + FRED_LOCATION.distance() + " tiles) - walking to him");
                        Walking.walk(FRED_LOCATION);
                        Sleep.sleepUntil(() -> FRED_LOCATION.distance() <= 6, 15000);
                        
                        // Double-check we're actually close enough
                        if (FRED_LOCATION.distance() > 6) {
                            log("Still too far from Fred (" + FRED_LOCATION.distance() + " tiles) - trying again");
                            Walking.walk(FRED_LOCATION);
                            Sleep.sleepUntil(() -> FRED_LOCATION.distance() <= 6, 10000);
                        }
                    }
                    
                    // Verify we're close enough before attempting to talk
                    double currentDistance = FRED_LOCATION.distance();
                    log("Current distance to Fred: " + currentDistance + " tiles");
                    
                    if (currentDistance > 8) {
                        log("Still too far from Fred (" + currentDistance + " tiles) - cannot complete quest yet");
                        return false; // Will retry this action
                    }
                    
                    // Now talk to Fred to complete quest
                    log("Close enough to Fred (" + currentDistance + " tiles) - talking to complete quest");
                    NPC fred = NPCs.closest("Fred the Farmer");
                    if (fred != null && fred.canReach()) {
                        log("Found Fred at: " + fred.getTile() + " (distance: " + fred.getTile().distance() + " tiles)");
                        int configBefore = PlayerSettings.getConfig(QUEST_CONFIG_PRIMARY);
                        log("Before Fred completion - Config179: " + configBefore);
                        
                        if (fred.interact("Talk-to")) {
                            // Handle dialogue
                            Sleep.sleepUntil(() -> org.dreambot.api.methods.dialogues.Dialogues.inDialogue(), 5000);
                            
                            // Use spacebar to continue through dialogue until options appear
                            while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue() && 
                                   !org.dreambot.api.methods.dialogues.Dialogues.areOptionsAvailable()) {
                                org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue();
                                Sleep.sleep(1000, 2000);
                            }
                            
                            // Select completion dialogue if available
                            if (org.dreambot.api.methods.dialogues.Dialogues.chooseOption("I need to talk to you about shearing these sheep!")) {
                                log("Selected completion dialogue");
                                Sleep.sleep(2000, 3000);
                            }
                            
                            // Use spacebar to continue through remaining dialogue
                            while (org.dreambot.api.methods.dialogues.Dialogues.inDialogue()) {
                                org.dreambot.api.methods.dialogues.Dialogues.spaceToContinue();
                                Sleep.sleep(1000, 2000);
                            }
                            
                            Sleep.sleep(3000, 5000); // Extra wait for quest completion
                            
                            int configAfter = PlayerSettings.getConfig(QUEST_CONFIG_PRIMARY);
                            log("After Fred completion - Config179: " + configAfter);
                            
                            if (configAfter >= QUEST_COMPLETE) {
                                log("Quest completed successfully!");
                                setQuestComplete();
                                return true;
                            }
                        }
                    } else {
                        log("Could not find Fred the Farmer or he is not reachable");
                        return false;
                    }
                    
                    return false;
                } catch (Exception e) {
                    log("Exception while completing quest with Fred: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Quest complete node
        questCompleteNode = new ActionNode("quest_complete", "Quest completed successfully") {
            @Override
            protected boolean performAction() {
                log("Sheep Shearer quest has been completed!");
                setQuestComplete();
                return true;
            }
        };
        
        // Connect the decision node branches
        smartDecisionNode.addBranch("start_quest", walkToFred);
        smartDecisionNode.addBranch("shear_sheep", shearSheepNode);
        smartDecisionNode.addBranch("spin_wool", walkToSpinningWheel);
        smartDecisionNode.addBranch("complete_quest", walkBackToFred);
        smartDecisionNode.addBranch("complete", questCompleteNode);
    }
    
    @Override
    public int getQuestProgress() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG_PRIMARY);
        
        // Check if completed
        if (config >= QUEST_COMPLETE) {
            return 100;
        }
        
        // Check if started
        if (config >= QUEST_STARTED) {
            int ballOfWoolCount = Inventory.count("Ball of wool");
            if (ballOfWoolCount >= REQUIRED_WOOL) {
                return 90; // Ready to complete
            } else {
                return 25 + (ballOfWoolCount * 3); // Progress based on wool collected
            }
        }
        
        // Not started
        return 0;
    }
}
