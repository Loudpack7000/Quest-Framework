package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.DecisionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.methods.settings.PlayerSettings;

/**
 * X Marks the Spot quest tree implementation
 * A treasure hunting quest that requires digging at 4 specific locations to find an Ancient casket
 * Config 101 tracks quest progress: 0 (not started) -> multiple stages -> complete
 */
public class XMarksTheSpotTree extends QuestTree {
    
    // Quest progress config ID
    private static final int QUEST_CONFIG = 101;
    
    // Quest locations for the treasure hunt
    private static final Tile VEOS_LOCATION = new Tile(3054, 3245, 0); // Port Sarim docks
    private static final Tile DIG_LOCATION_1 = new Tile(3229, 3209, 0); // First dig location  
    private static final Tile DIG_LOCATION_2 = new Tile(3203, 3212, 0); // Second dig location
    private static final Tile DIG_LOCATION_3 = new Tile(3108, 3264, 0); // Third dig location
    private static final Tile DIG_LOCATION_4 = new Tile(3077, 3260, 0); // Fourth dig location (Ancient casket)
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode talkToVeosStart;
    private QuestNode walkAndDigLocation1;
    private QuestNode walkAndDigLocation2;
    private QuestNode walkAndDigLocation3;
    private QuestNode walkAndDigLocation4;
    private QuestNode openAncientCasket;
    private QuestNode returnToVeos;
    private QuestNode questCompleteNode;
    
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
        // Smart decision node - determines next step based on quest state and inventory
        smartDecisionNode = new DecisionNode("xmarks_decision", "Determine X Marks the Spot next step") {
            @Override
            protected String makeDecision() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG);
                log("Quest config " + QUEST_CONFIG + " = " + config);
                
                // Check if quest is complete (based on config or absence of casket in world)
                if (config >= 5) { // Assuming config 5+ means complete
                    log("Quest is complete! Config = " + config);
                    setQuestComplete();
                    return "complete";
                }
                
                // Check if we have Ancient casket - quest nearly complete
                if (Inventory.contains("Ancient casket")) {
                    log("Have Ancient casket - returning to Veos");
                    return "return_to_veos";
                }
                
                // Check if casket is available to open
                GameObject casket = GameObjects.closest("Ancient casket");
                if (casket != null) {
                    log("Ancient casket found in world - opening it");
                    return "open_casket";
                }
                
                // Quest progression logic based on config and what we have
                if (config == 0) {
                    log("Quest not started - talking to Veos");
                    return "start_quest";
                }
                
                // If quest started, check treasure scroll status and dig accordingly
                if (Inventory.contains("Treasure scroll")) {
                    // We have scroll, need to dig based on quest progress
                    // This is simplified - in reality we'd track which location we're on
                    log("Have treasure scroll - going to dig locations in sequence");
                    return "dig_sequence";
                }
                
                log("Quest in progress but no clear next step - defaulting to dig sequence");
                return "dig_sequence";
            }
        };

        // Talk to Veos to start the quest
        talkToVeosStart = new TalkToNPCNode("talk_veos_start", "Veos", VEOS_LOCATION,
            new String[]{"I'm looking for a quest.", "Nothing."}, "I'm looking for a quest.", null) {
            @Override
            protected boolean performAction() {
                log("Starting X Marks the Spot quest with Veos");
                boolean success = super.performAction();
                if (success) {
                    // Wait for treasure scroll to appear
                    boolean receivedScroll = Sleep.sleepUntil(() -> 
                        Inventory.contains("Treasure scroll"), 10000);
                    if (receivedScroll) {
                        log("Successfully received Treasure scroll from Veos");
                        return true;
                    } else {
                        log("Failed to receive Treasure scroll within 10 seconds");
                        return false;
                    }
                }
                return success;
            }
        };

        // Walk to and dig at location 1
        walkAndDigLocation1 = new ActionNode("walk_dig_1", "Walk to and dig at first location") {
            @Override
            protected boolean performAction() {
                log("Going to first dig location: " + DIG_LOCATION_1);
                
                // Walk to location if not there
                if (DIG_LOCATION_1.distance() > 5) {
                    log("Walking to dig location 1");
                    org.dreambot.api.methods.walking.impl.Walking.walk(DIG_LOCATION_1);
                    Sleep.sleepUntil(() -> DIG_LOCATION_1.distance() <= 5, 15000);
                }
                
                // Read scroll if we have it
                if (Inventory.contains("Treasure scroll")) {
                    log("Reading treasure scroll");
                    Inventory.interact("Treasure scroll", "Read");
                    Sleep.sleep(2000, 3000);
                }
                
                // Dig with spade
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                log("Digging at location 1");
                boolean success = Inventory.interact("Spade", "Dig");
                Sleep.sleep(3000, 4000); // Wait for dig animation
                return success;
            }
        };

        // Walk to and dig at location 2
        walkAndDigLocation2 = new ActionNode("walk_dig_2", "Walk to and dig at second location") {
            @Override
            protected boolean performAction() {
                log("Going to second dig location: " + DIG_LOCATION_2);
                
                // Walk to location if not there
                if (DIG_LOCATION_2.distance() > 5) {
                    log("Walking to dig location 2");
                    org.dreambot.api.methods.walking.impl.Walking.walk(DIG_LOCATION_2);
                    Sleep.sleepUntil(() -> DIG_LOCATION_2.distance() <= 5, 15000);
                }
                
                // Read scroll if we have it
                if (Inventory.contains("Treasure scroll")) {
                    log("Reading treasure scroll");
                    Inventory.interact("Treasure scroll", "Read");
                    Sleep.sleep(2000, 3000);
                }
                
                // Dig with spade
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                log("Digging at location 2");
                boolean success = Inventory.interact("Spade", "Dig");
                Sleep.sleep(3000, 4000); // Wait for dig animation
                return success;
            }
        };

        // Walk to and dig at location 3
        walkAndDigLocation3 = new ActionNode("walk_dig_3", "Walk to and dig at third location") {
            @Override
            protected boolean performAction() {
                log("Going to third dig location: " + DIG_LOCATION_3);
                
                // Walk to location if not there
                if (DIG_LOCATION_3.distance() > 5) {
                    log("Walking to dig location 3");
                    org.dreambot.api.methods.walking.impl.Walking.walk(DIG_LOCATION_3);
                    Sleep.sleepUntil(() -> DIG_LOCATION_3.distance() <= 5, 15000);
                }
                
                // Dig with spade (no scroll reading needed for location 3)
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                log("Digging at location 3");
                boolean success = Inventory.interact("Spade", "Dig");
                Sleep.sleep(3000, 4000); // Wait for dig animation
                return success;
            }
        };

        // Walk to and dig at location 4 (final location - Ancient casket)
        walkAndDigLocation4 = new ActionNode("walk_dig_4", "Walk to and dig at fourth location (Ancient casket)") {
            @Override
            protected boolean performAction() {
                log("Going to fourth dig location for Ancient casket: " + DIG_LOCATION_4);
                
                // Walk to location if not there
                if (DIG_LOCATION_4.distance() > 5) {
                    log("Walking to dig location 4");
                    org.dreambot.api.methods.walking.impl.Walking.walk(DIG_LOCATION_4);
                    Sleep.sleepUntil(() -> DIG_LOCATION_4.distance() <= 5, 15000);
                }
                
                // Dig with spade
                if (!Inventory.contains("Spade")) {
                    log("ERROR: No spade found in inventory!");
                    return false;
                }
                
                log("Digging at final location for Ancient casket");
                boolean success = Inventory.interact("Spade", "Dig");
                
                if (success) {
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

        // Return to Veos to complete the quest
        returnToVeos = new TalkToNPCNode("return_veos", "Veos", VEOS_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Returning to Veos to complete quest");
                boolean success = super.performAction();
                if (success) {
                    // Wait for quest completion
                    Sleep.sleepUntil(() -> 
                        PlayerSettings.getConfig(QUEST_CONFIG) >= 5, 10000);
                    log("Quest completion dialogue finished");
                }
                return success;
            }
        };

        // Quest complete node
        questCompleteNode = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                log("X Marks the Spot quest completed successfully!");
                setQuestComplete();
                return true;
            }
        };

        // Chain action nodes together for dig sequence
        ((ActionNode)walkAndDigLocation1).setNextNode(walkAndDigLocation2);
        ((ActionNode)walkAndDigLocation2).setNextNode(walkAndDigLocation3);
        ((ActionNode)walkAndDigLocation3).setNextNode(walkAndDigLocation4);
        
        // Set up branches for the smart decision node
        DecisionNode decisionNode = (DecisionNode) smartDecisionNode;
        decisionNode.addBranch("complete", questCompleteNode);
        decisionNode.addBranch("start_quest", talkToVeosStart);
        decisionNode.addBranch("dig_sequence", walkAndDigLocation1);
        decisionNode.addBranch("open_casket", openAncientCasket);
        decisionNode.addBranch("return_to_veos", returnToVeos);
        decisionNode.setDefaultBranch(talkToVeosStart); // Default fallback
    }
}
