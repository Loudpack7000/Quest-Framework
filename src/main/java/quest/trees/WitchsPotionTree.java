package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.DecisionNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.GrandExchangeUtil.PriceStrategy;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;

/**
 * Witch's Potion Quest Tree
 * Automated quest using decision tree structure with Grand Exchange integration
 * Quest involves making a potion with burnt meat, eye of newt, and onion
 * Config 101 tracks progress: 0 (not started) -> 21 (started) -> 22 (complete)
 */
public class WitchsPotionTree extends QuestTree {
    
    // Quest progress config - Witch's Potion uses config 101 (from discovery logs)
    private static final int QUEST_CONFIG = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 21;       // From logs: config 101 = 21 when started
    private static final int QUEST_COMPLETE = 22;      // From logs: config 101 = 22 when complete
    
    // Required quest items
    private static final String EYE_OF_NEWT = "Eye of newt";
    private static final String ONION = "Onion";
    private static final String RAW_BEEF = "Raw beef";
    private static final String BURNT_MEAT = "Burnt meat";
    private static final String TINDERBOX = "Tinderbox";
    private static final String LOGS = "Logs";
    private static final String[] REQUIRED_ITEMS = {EYE_OF_NEWT, ONION, RAW_BEEF, TINDERBOX, LOGS};
    
    // Quest locations
    private static final Area HETTY_HOUSE_AREA = new Area(2965, 3202, 2973, 3210, 0);
    private static final Tile HETTY_LOCATION = new Tile(2969, 3207, 0);
    private static final Area GRAND_EXCHANGE_AREA = new Area(3161, 3477, 3168, 3490, 0);
    private static final Tile GRAND_EXCHANGE_LOCATION = new Tile(3165, 3486, 0);
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode purchaseItemsNode;
    private QuestNode walkToHetty;
    private QuestNode talkToHettyStart;
    private QuestNode makeFire;
    private QuestNode cookRawBeef;
    private QuestNode talkToHettyComplete;
    private QuestNode drinkFromCauldron;
    private QuestNode questCompleteNode;
    
    public WitchsPotionTree() {
        super("Witch's Potion");
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
        smartDecisionNode = new DecisionNode("witchs_potion_decision", "Determine Witch's Potion next step") {
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
                
                // Check inventory state
                boolean hasEyeOfNewt = Inventory.contains(EYE_OF_NEWT);
                boolean hasOnion = Inventory.contains(ONION);
                boolean hasBurntMeat = Inventory.contains(BURNT_MEAT);
                boolean hasRawBeef = Inventory.contains(RAW_BEEF);
                boolean hasTinderbox = Inventory.contains(TINDERBOX);
                boolean hasLogs = Inventory.contains(LOGS);
                
                log("Inventory check - Eye of newt: " + hasEyeOfNewt + ", Onion: " + hasOnion + 
                    ", Burnt meat: " + hasBurntMeat + ", Raw beef: " + hasRawBeef + 
                    ", Tinderbox: " + hasTinderbox + ", Logs: " + hasLogs);
                

                
                if (config >= QUEST_STARTED) {
                    // Quest started - check what we need to do
                    if (hasEyeOfNewt && hasOnion && hasBurntMeat) {
                        // Check if we're at Hetty's house
                        if (HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                            log("Have all final ingredients and at Hetty - completing quest");
                            return "talk_to_complete";
                        } else {
                            log("Have all final ingredients - going to Hetty");
                            return "ready_to_complete";
                        }
                    } else if (hasEyeOfNewt && hasOnion && hasRawBeef && hasTinderbox && hasLogs) {
                        log("Have raw ingredients - need to cook beef");
                        return "need_to_cook";
                    } else {
                        log("Quest started but missing items - going to GE");
                        return "need_items";
                    }
                }
                
                if (config == QUEST_NOT_STARTED) {
                    // Quest not started
                    if (hasAllRequiredItems()) {
                        // Check if we're at Hetty's house
                        if (HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                            log("Quest not started, have all items, and at Hetty - starting quest");
                            return "talk_to_start";
                        } else {
                            log("Quest not started but have all items - going to Hetty to start");
                            return "ready_to_start";
                        }
                    } else {
                        log("Quest not started and missing items - going to GE");
                        return "need_items";
                    }
                }
                
                // Fallback logic
                log("Unknown quest state: config=" + config);
                return "need_items";
            }
        };
        
        // Set up branches for the smart decision node
        DecisionNode decisionNode = (DecisionNode) smartDecisionNode;
        decisionNode.addBranch("complete", questCompleteNode);
        decisionNode.addBranch("need_items", purchaseItemsNode);
        decisionNode.addBranch("ready_to_start", walkToHetty);
        decisionNode.addBranch("need_to_cook", makeFire);
        decisionNode.addBranch("ready_to_complete", walkToHetty);
        decisionNode.addBranch("talk_to_start", talkToHettyStart);
        decisionNode.addBranch("talk_to_complete", talkToHettyComplete);
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
                    
                    // Purchase missing items
                    boolean success = true;
                    for (String item : missingItems) {
                        log("Purchasing: " + item);
                        if (!GrandExchangeUtil.buyItem(item, 1, PriceStrategy.MODERATE)) {
                            log("Failed to purchase: " + item);
                            success = false;
                        }
                    }
                    
                    if (success) {
                        log("Successfully purchased all missing items");
                        Sleep.sleepUntil(() -> hasAllRequiredItems(), 15000);
                        return hasAllRequiredItems();
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
        
        // Step 2: Walk to Hetty's house
        walkToHetty = new WalkToLocationNode("walk_to_hetty", HETTY_LOCATION, "Hetty's house");
        
        // Step 3: Talk to Hetty to start quest
        talkToHettyStart = new TalkToNPCNode("talk_hetty_start", "Hetty", HETTY_LOCATION,
            new String[]{"I am in search of a quest.", "Yes."}, "I am in search of a quest.", null) {
            @Override
            protected boolean performAction() {
                log("Talking to Hetty to start quest");
                
                int configBefore = PlayerSettings.getConfig(QUEST_CONFIG);
                log("Before Hetty interaction - Config: " + configBefore);
                
                boolean success = super.performAction();
                
                if (success) {
                    Sleep.sleep(2000, 3000);
                    
                    int configAfter = PlayerSettings.getConfig(QUEST_CONFIG);
                    log("After Hetty interaction - Config: " + configAfter);
                    
                    if (configAfter > configBefore) {
                        log("Quest started successfully");
                        return true;
                    }
                }
                
                return success;
            }
        };
        
        // Step 4: Make fire using tinderbox and logs
        makeFire = new ActionNode("make_fire", "Make fire for cooking") {
            @Override
            protected boolean performAction() {
                log("Making fire with tinderbox and logs");
                
                if (!HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                    log("Not at Hetty's house, walking there first");
                    if (!org.dreambot.api.methods.walking.impl.Walking.walk(HETTY_LOCATION)) {
                        log("Failed to walk to Hetty's house");
                        return false;
                    }
                    Sleep.sleepUntil(() -> HETTY_HOUSE_AREA.contains(Players.getLocal()), 15000);
                }
                
                // Use tinderbox on logs to make fire
                if (!Inventory.contains(TINDERBOX) || !Inventory.contains(LOGS)) {
                    log("Missing tinderbox or logs for making fire");
                    return false;
                }
                
                // Interact tinderbox with logs
                if (Inventory.interact(TINDERBOX, "Use")) {
                    Sleep.sleep(1000, 2000);
                    if (Inventory.interact(LOGS, "Use")) {
                        log("Used tinderbox on logs, waiting for fire");
                        // Wait for fire to appear
                        boolean fireCreated = Sleep.sleepUntil(() -> {
                            GameObject fire = GameObjects.closest("Fire");
                            return fire != null && fire.distance() < 10;
                        }, 10000);
                        
                        if (fireCreated) {
                            log("Successfully created fire");
                            return true;
                        } else {
                            log("Failed to create fire within 10 seconds");
                            return false;
                        }
                    }
                }
                
                log("Failed to use tinderbox on logs");
                return false;
            }
        };
        
        // Step 5: Cook raw beef on fire to make burnt meat
        cookRawBeef = new ActionNode("cook_raw_beef", "Cook raw beef on fire") {
            @Override
            protected boolean performAction() {
                log("Cooking raw beef on fire to make burnt meat");
                
                if (!Inventory.contains(RAW_BEEF)) {
                    log("No raw beef to cook");
                    return false;
                }
                
                GameObject fire = GameObjects.closest("Fire");
                if (fire == null || fire.distance() > 10) {
                    log("No fire nearby for cooking");
                    return false;
                }
                
                // Use raw beef on fire
                if (Inventory.interact(RAW_BEEF, "Use")) {
                    Sleep.sleep(1000, 2000);
                    if (fire.interact("Use")) {
                        log("Used raw beef on fire, waiting for cooking");
                        // Wait for burnt meat to appear
                        boolean meatCooked = Sleep.sleepUntil(() -> 
                            Inventory.contains(BURNT_MEAT), 10000);
                        
                        if (meatCooked) {
                            log("Successfully cooked raw beef into burnt meat");
                            return true;
                        } else {
                            log("Failed to cook raw beef within 10 seconds");
                            return false;
                        }
                    }
                }
                
                log("Failed to use raw beef on fire");
                return false;
            }
        };
        
        // Step 6: Talk to Hetty with all ingredients
        talkToHettyComplete = new TalkToNPCNode("talk_hetty_complete", "Hetty", HETTY_LOCATION,
            new String[]{"I have the ingredients.", "Yes."}, "I have the ingredients.", null);
        
        // Step 7: Drink from cauldron to complete quest
        drinkFromCauldron = new InteractWithObjectNode("drink_cauldron", "Cauldron", "Drink-from",
            HETTY_LOCATION, "Cauldron in Hetty's house") {
            @Override
            protected boolean performAction() {
                log("Drinking from cauldron to complete quest");
                
                int configBefore = PlayerSettings.getConfig(QUEST_CONFIG);
                log("Before cauldron interaction - Config: " + configBefore);
                
                boolean success = super.performAction();
                
                if (success) {
                    Sleep.sleep(3000, 5000);
                    
                    int configAfter = PlayerSettings.getConfig(QUEST_CONFIG);
                    log("After cauldron interaction - Config: " + configAfter);
                    
                    if (configAfter >= QUEST_COMPLETE) {
                        log("Quest completed!");
                        setQuestComplete();
                        return true;
                    }
                }
                
                return success;
            }
        };
        
        // Quest complete node
        questCompleteNode = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                log("Witch's Potion quest completed successfully!");
                setQuestComplete();
                return true;
            }
        };
        
        // Chain action nodes together
        ((ActionNode)purchaseItemsNode).setNextNode(null); // Return to decision node
        ((ActionNode)walkToHetty).setNextNode(null); // Decision node will determine next step
        ((ActionNode)makeFire).setNextNode(cookRawBeef);
        ((ActionNode)cookRawBeef).setNextNode(null); // Return to decision node
        ((ActionNode)talkToHettyStart).setNextNode(null); // Return to decision node
        ((ActionNode)talkToHettyComplete).setNextNode(drinkFromCauldron);
        ((ActionNode)drinkFromCauldron).setNextNode(questCompleteNode);
    }
    

    
    /**
     * Check if we have all required quest items
     */
    private boolean hasAllRequiredItems() {
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
            boolean hasBurntMeat = Inventory.contains(BURNT_MEAT);
            boolean hasEyeOfNewt = Inventory.contains(EYE_OF_NEWT);
            boolean hasOnion = Inventory.contains(ONION);
            
            if (hasBurntMeat && hasEyeOfNewt && hasOnion) {
                return 90; // Ready to complete
            } else if (hasAllRequiredItems()) {
                return 60; // Have raw materials
            } else {
                return 30; // Started but missing items
            }
        }
        
        // Not started
        return hasAllRequiredItems() ? 10 : 0;
    }
}