package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.DecisionNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.GrandExchangeUtil.PriceStrategy;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.utilities.Sleep;

/**
 * Cook's Assistant Quest Tree
 * Automated quest using decision tree structure with Grand Exchange integration
 * Config 29 tracks quest progress: 0 (not started) -> 1+ (started/in progress) -> 2 (complete)
 */
public class CooksAssistantTree extends QuestTree {
    
    // Quest progress config - Cook's Assistant uses config 29
    private static final int QUEST_CONFIG = 29;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 1;        // Talked to Cook, quest started
    private static final int QUEST_COMPLETE = 2;       // Quest completed
    
    // Required quest items - exact names for Grand Exchange
    private static final String EGG = "Egg";
    private static final String BUCKET_OF_MILK = "Bucket of milk";
    private static final String POT_OF_FLOUR = "Pot of flour";
    private static final String[] REQUIRED_ITEMS = {EGG, BUCKET_OF_MILK, POT_OF_FLOUR};
    
    // Quest locations
    private static final Area LUMBRIDGE_CASTLE_KITCHEN = new Area(3205, 3212, 3212, 3217, 0);
    private static final Tile COOK_LOCATION = new Tile(3207, 3214, 0);
    private static final Area GRAND_EXCHANGE_AREA = new Area(3161, 3477, 3168, 3490, 0);
    private static final Tile GRAND_EXCHANGE_LOCATION = new Tile(3165, 3486, 0);
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode purchaseItemsNode;
    private QuestNode walkToKitchen;
    private QuestNode talkToCookStartComplete;
    private QuestNode questCompleteNode;
    
    public CooksAssistantTree() {
        super("Cook's Assistant");
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
        smartDecisionNode = new DecisionNode("cooks_assistant_decision", "Determine Cook's Assistant next step") {
            @Override
            protected String makeDecision() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG);
                log("Quest config " + QUEST_CONFIG + " = " + config);
                
                // Check if quest is complete
                if (config >= QUEST_COMPLETE) {
                    log("Quest is complete! Config=" + config);
                    setQuestComplete();
                    return "complete";
                }
                
                // Check if we have all required items
                boolean hasAllItems = hasAllQuestItems();
                log("Has all quest items: " + hasAllItems);
                
                if (config == QUEST_NOT_STARTED) {
                    // Quest not started
                    if (hasAllItems) {
                        log("Quest not started but have all items - going to Cook");
                        return "ready_to_start";
                    } else {
                        log("Quest not started and missing items - going to GE");
                        return "need_items";
                    }
                }
                
                if (config >= QUEST_STARTED) {
                    // Quest started
                    if (hasAllItems) {
                        log("Quest started and have all items - completing quest");
                        return "ready_to_complete";
                    } else {
                        log("Quest started but missing items - going to GE");
                        return "need_items";
                    }
                }
                
                // Fallback logic
                log("Unknown quest state: config=" + config);
                return hasAllItems ? "ready_to_start" : "need_items";
            }
        };
        
        // Set up branches for the smart decision node
        DecisionNode decisionNode = (DecisionNode) smartDecisionNode;
        decisionNode.addBranch("complete", questCompleteNode);
        decisionNode.addBranch("need_items", purchaseItemsNode);
        decisionNode.addBranch("ready_to_start", walkToKitchen);
        decisionNode.addBranch("ready_to_complete", walkToKitchen);
        decisionNode.setDefaultBranch(purchaseItemsNode); // Default: get items
        
        // Step 1: Purchase required items from Grand Exchange
        purchaseItemsNode = new ActionNode("purchase_items", "Purchase quest items from Grand Exchange") {
            @Override
            protected boolean performAction() {
                log("Purchasing required items from Grand Exchange");
                
                try {
                    // Check what we're missing
                    String[] missingItems = getMissingItems();
                    if (missingItems.length == 0) {
                        log("Already have all required items");
                        return true;
                    }
                    
                    log("Missing items: " + String.join(", ", missingItems));
                    
                    // Navigate to Grand Exchange if not there
                    if (!GRAND_EXCHANGE_AREA.contains(Players.getLocal())) {
                        log("Walking to Grand Exchange");
                        if (!org.dreambot.api.methods.walking.impl.Walking.walk(GRAND_EXCHANGE_LOCATION)) {
                            log("Failed to initiate walk to Grand Exchange");
                            return false;
                        }
                        
                        boolean arrived = Sleep.sleepUntil(() -> GRAND_EXCHANGE_AREA.contains(Players.getLocal()), 30000);
                        if (!arrived) {
                            log("Failed to reach Grand Exchange");
                            return false;
                        }
                    }
                    
                    // Use GrandExchangeUtil to buy missing items
                    boolean success = true;
                    for (String item : missingItems) {
                        log("Purchasing: " + item);
                        if (!GrandExchangeUtil.buyItem(item, 1, GrandExchangeUtil.PriceStrategy.MODERATE)) {
                            log("Failed to purchase: " + item);
                            success = false;
                        }
                    }
                    
                    if (success) {
                        log("Successfully purchased all missing items");
                        // Wait a moment for items to appear in inventory
                        Sleep.sleepUntil(() -> hasAllQuestItems(), 15000);
                        return hasAllQuestItems();
                    } else {
                        log("Failed to purchase some items from Grand Exchange");
                        return false;
                    }
                    
                } catch (Exception e) {
                    log("Exception while purchasing items: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Step 2: Walk to Lumbridge Castle kitchen
        walkToKitchen = new WalkToLocationNode("walk_to_kitchen", COOK_LOCATION, "Lumbridge Castle kitchen");
        
        // Step 3: Talk to Cook to start or complete quest
        talkToCookStartComplete = new TalkToNPCNode("talk_cook_start_complete", "Cook", COOK_LOCATION,
            new String[]{"What's wrong?", "Yes.", "I'm always happy to help a cook in distress."}, "What's wrong?", null) {
            @Override
            protected boolean performAction() {
                log("Talking to Cook to start/complete quest");
                
                // Check current quest state
                int configBefore = PlayerSettings.getConfig(QUEST_CONFIG);
                log("Before Cook interaction - Config: " + configBefore);
                
                boolean success = super.performAction();
                
                if (success) {
                    // Wait for quest state to change
                    Sleep.sleep(2000, 3000);
                    
                    int configAfter = PlayerSettings.getConfig(QUEST_CONFIG);
                    log("After Cook interaction - Config: " + configAfter);
                    
                    // Check if quest completed
                    if (configAfter >= QUEST_COMPLETE) {
                        log("Quest completed!");
                        setQuestComplete();
                        return true;
                    }
                    
                    // Check if quest started or progressed
                    if (configAfter > configBefore) {
                        log("Quest progression detected");
                        return true;
                    }
                    
                    // If quest was already started and we have items, consider it successful
                    if (configBefore >= QUEST_STARTED && hasAllQuestItems()) {
                        log("Quest was in progress and we delivered items");
                        // Check again after a longer delay
                        Sleep.sleep(3000, 5000);
                        int finalConfig = PlayerSettings.getConfig(QUEST_CONFIG);
                        if (finalConfig >= QUEST_COMPLETE) {
                            log("Quest completed after delay!");
                            setQuestComplete();
                            return true;
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
                log("Cook's Assistant quest completed successfully!");
                setQuestComplete();
                return true;
            }
        };
        
        // Chain action nodes together
        ((ActionNode)purchaseItemsNode).setNextNode(null); // Return to decision node
        ((ActionNode)walkToKitchen).setNextNode(talkToCookStartComplete);
        ((ActionNode)talkToCookStartComplete).setNextNode(questCompleteNode);
    }
    
    /**
     * Check if we have all required quest items
     */
    private boolean hasAllQuestItems() {
        for (String item : REQUIRED_ITEMS) {
            if (!Inventory.contains(item)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Get list of missing required items
     */
    private String[] getMissingItems() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String item : REQUIRED_ITEMS) {
            if (!Inventory.contains(item)) {
                missing.add(item);
            }
        }
        return missing.toArray(new String[0]);
    }
    
    @Override
    public int getQuestProgress() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG);
        
        // Check if completed
        if (config >= QUEST_COMPLETE) {
            return 100;
        }
        
        // Check if started
        if (config >= QUEST_STARTED) {
            return hasAllQuestItems() ? 75 : 50;
        }
        
        // Not started
        return hasAllQuestItems() ? 25 : 0;
    }
}