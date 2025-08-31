package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.GrandExchangeUtil.PriceStrategy;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;

/**
 * Witch's Potion Quest Tree
 * Automated quest using decision tree structure with Grand Exchange integration
 * Quest involves making a potion with burnt meat, eye of newt, and onion
 * Config 101 tracks progress: 0 (not started) -> 21 (started) -> 22 (complete)
 */
public class WitchsPotionTree extends QuestTree {
    
    // Quest progress config - Witch's Potion uses config 108 (from discovery logs)
    private static final int QUEST_CONFIG = 108;  // FIXED: Was 101, now 108 (discovered)
    private static final int QUEST_NOT_STARTED = 17;      // FIXED: Was 0, now 17 (discovered)
    private static final int QUEST_STARTED = 0;           // FIXED: Was 21, now 0 (discovered)
    private static final int QUEST_COMPLETE = 0;          // FIXED: Was 22, now 0 (discovered)
    
    // NEW: Additional quest tracking using discovered varbits
    private static final int QUEST_VARBIT_275 = 275;      // Discovered: tracks quest progress
    private static final int QUEST_VARBIT_276 = 276;      // Discovered: tracks quest progress
    
    // Required quest items
    private static final String EYE_OF_NEWT = "Eye of newt";
    private static final String ONION = "Onion";
    private static final String RAW_BEEF = "Raw beef";
    private static final String BURNT_MEAT = "Burnt meat";
    private static final String RAT_TAIL = "Rat's tail";
    // Do NOT require tinderbox or logs; we will use Hetty's fireplace exclusively
    private static final String[] REQUIRED_ITEMS = {EYE_OF_NEWT, ONION, RAW_BEEF};
    
    // Quest locations
    private static final Area HETTY_HOUSE_AREA = new Area(2965, 3202, 2973, 3210, 0);
    private static final Tile HETTY_LOCATION = new Tile(2969, 3207, 0);
    private static final Area GRAND_EXCHANGE_AREA = new Area(3161, 3477, 3168, 3490, 0);
    private static final Tile GRAND_EXCHANGE_LOCATION = new Tile(3165, 3486, 0);
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode walkToGrandExchange;
    private QuestNode purchaseItemsNode;
    private QuestNode walkToHetty;
    private QuestNode talkToHettyStart;
    private QuestNode walkToHettyForCooking;
    private QuestNode cookRawBeef;
    private QuestNode walkToHettyForCompletion;
    private QuestNode talkToHettyComplete;
    private QuestNode drinkFromCauldron;
    private QuestNode questCompleteNode;
    private QuestNode walkToRatHuntArea;
    private QuestNode obtainRatTailNode;
    // Tracks that we handed items to Hetty and should proceed to the cauldron (avoid GE trips)
    private volatile boolean completionPhase = false;
    
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
        // Step 1: Walk to Grand Exchange
        walkToGrandExchange = new WalkToLocationNode("walk_to_ge", GRAND_EXCHANGE_LOCATION, "Grand Exchange");
        
        // Step 2: Purchase required items from Grand Exchange
        purchaseItemsNode = new ActionNode("purchase_items", "Purchase quest items from Grand Exchange") {
            @Override
            public boolean shouldSkip() {
                // Only run this node when we're actually at the GE
                return !GRAND_EXCHANGE_AREA.contains(Players.getLocal());
            }
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
                    
                    // Purchase missing items using GrandExchangeUtil
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
        
        // Step 3: Walk to Hetty's house (fluid movement)
        walkToHetty = new WalkToLocationNode("walk_to_hetty", HETTY_LOCATION, "Hetty's house");

    // Optional: Area near Hetty's house where rats commonly spawn
    final Area RAT_HUNT_AREA = new Area(2958, 3200, 2980, 3218, 0);
    final Tile RAT_HUNT_LOCATION = new Tile(2969, 3212, 0);
    walkToRatHuntArea = new WalkToLocationNode("walk_to_rats", RAT_HUNT_LOCATION, "Rat hunting area near Hetty");
        
        // Step 4: Talk to Hetty to start quest
        talkToHettyStart = new TalkToNPCNode("talk_hetty_start", "Hetty", HETTY_LOCATION,
            new String[]{"I am in search of a quest.", "Yes."}, "I am in search of a quest.", null) {
            @Override
            protected boolean performAction() {
                log("Talking to Hetty to start quest");
                
                int configBefore = PlayerSettings.getConfig(QUEST_CONFIG);
                int varbit275Before = PlayerSettings.getBitValue(QUEST_VARBIT_275);
                int varbit276Before = PlayerSettings.getBitValue(QUEST_VARBIT_276);
                log("Before Hetty interaction - Config 108: " + configBefore + ", Varbit 275: " + varbit275Before + ", Varbit 276: " + varbit276Before);
                
                boolean success = super.performAction();
                
                if (success) {
                    Sleep.sleep(2000, 3000);
                    
                    int configAfter = PlayerSettings.getConfig(QUEST_CONFIG);
                    int varbit275After = PlayerSettings.getBitValue(QUEST_VARBIT_275);
                    int varbit276After = PlayerSettings.getBitValue(QUEST_VARBIT_276);
                    log("After Hetty interaction - Config 108: " + configAfter + ", Varbit 275: " + varbit275After + ", Varbit 276: " + varbit276After);
                    
                    // Check if quest started using discovered values
                    if (configAfter != configBefore || varbit275After != varbit275Before || varbit276After != varbit276Before) {
                        log("Quest started successfully - values changed!");
                        return true;
                    }
                }
                
                return success;
            }
        };
        
        // Step 5: Walk to Hetty's house for cooking (fluid movement)
        walkToHettyForCooking = new WalkToLocationNode("walk_to_hetty_cooking", HETTY_LOCATION, "Hetty's house for cooking");
        
    // Step 6: Cook raw beef on fireplace to make burnt meat (two-step process)
        cookRawBeef = new ActionNode("cook_raw_beef", "Cook raw beef on fireplace to make burnt meat") {
            @Override
            protected boolean performAction() {
                log("Cooking raw beef on fireplace to make burnt meat");
                
                // Look for fireplace in Hetty's house (we always use Hetty's fireplace)
                GameObject fireplace = GameObjects.closest("Fireplace");
                if (fireplace == null || fireplace.distance() > 10) {
                    log("No fireplace nearby for cooking");
                    return false;
                }
                
                log("Found fireplace at distance: " + fireplace.distance());
                
                // Step 1: Cook raw beef to cooked meat
                if (Inventory.contains(RAW_BEEF)) {
                    log("Step 1: Cooking raw beef to cooked meat");
                    if (Inventory.interact(RAW_BEEF, "Use")) {
                        Sleep.sleep(1000, 2000);
                        if (fireplace.interact("Use")) {
                            log("Used raw beef on fireplace, waiting for cooked meat");
                            // Wait for cooked meat to appear
                            boolean meatCooked = Sleep.sleepUntil(() -> 
                                Inventory.contains("Cooked meat"), 10000);
                            
                            if (meatCooked) {
                                log("Successfully cooked raw beef into cooked meat");
                                
                                // Step 2: Cook cooked meat to burnt meat
                                log("Step 2: Cooking cooked meat to burnt meat");
                                if (Inventory.interact("Cooked meat", "Use")) {
                                    Sleep.sleep(1000, 2000);
                                    if (fireplace.interact("Use")) {
                                        log("Used cooked meat on fireplace, waiting for burnt meat");
                                        // Wait for burnt meat to appear
                                        boolean meatBurnt = Sleep.sleepUntil(() -> 
                                            Inventory.contains(BURNT_MEAT), 10000);
                                        
                                        if (meatBurnt) {
                                            log("Successfully cooked cooked meat into burnt meat");
                                            return true;
                                        } else {
                                            log("Failed to burn cooked meat within 10 seconds");
                                            return false;
                                        }
                                    } else {
                                        log("Failed to use cooked meat on fireplace");
                                        return false;
                                    }
                                } else {
                                    log("Failed to interact with cooked meat");
                                    return false;
                                }
                            } else {
                                log("Failed to cook raw beef within 10 seconds");
                                return false;
                            }
                        } else {
                            log("Failed to use raw beef on fireplace");
                            return false;
                        }
                    } else {
                        log("Failed to interact with raw beef");
                        return false;
                    }
                }
                
                log("Failed to complete meat cooking process");
                return false;
            }
        };
        
    // Step 7: Walk to Hetty's house for completion (fluid movement)
        walkToHettyForCompletion = new WalkToLocationNode("walk_to_hetty_complete", HETTY_LOCATION, "Hetty's house for completion");
        
        // Step 9: Talk to Hetty with all ingredients
        talkToHettyComplete = new TalkToNPCNode("talk_hetty_complete", "Hetty", HETTY_LOCATION,
            new String[]{"I have the ingredients.", "Yes."}, "I have the ingredients.", null) {
            @Override
            protected boolean performAction() {
                log("Handing ingredients to Hetty");
                boolean ok = super.performAction();
                if (ok) {
                    // After handing in items, enter completion phase regardless of inventory
                    completionPhase = true;
                    log("Entered completion phase (proceed to drink from cauldron)");
                }
                return ok;
            }
        };

        // Step 8: Obtain Rat's tail by killing a rat after starting the quest
        obtainRatTailNode = new ActionNode("obtain_rat_tail", "Kill a rat and pick up Rat's tail") {
            @Override
            public boolean shouldSkip() {
                // Skip if we already have the tail
                if (Inventory.contains(RAT_TAIL)) return true;
                // Skip if quest not started (rats don't drop tail before quest start)
                boolean isStarted = Quests.isStarted(FreeQuest.WITCHS_POTION);
                if (!isStarted) return true;
                return false;
            }

            @Override
            protected boolean performAction() {
                log("Acquiring Rat's tail by killing a rat");

                // If already obtained, we're done
                if (Inventory.contains(RAT_TAIL)) return true;

                // Find nearest rat
                NPC rat = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase("Rat") && !n.isInCombat());
                if (rat == null) {
                    log("No rat nearby; moving slightly towards hunt area to find one");
                    // Light nudge towards hunt area; WalkToLocationNode handles main movement
                    new WalkToLocationNode("nudge_to_rats", RAT_HUNT_LOCATION, "Find rats").execute();
                    Sleep.sleep(600, 1000);
                    rat = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equalsIgnoreCase("Rat") && !n.isInCombat());
                }

                if (rat == null) {
                    log("Still no rat in reach");
                    return false;
                }

                log("Attacking rat at " + rat.getTile());
                final NPC targetRat = rat; // make effectively final for lambda
                if (targetRat.interact("Attack")) {
                    // Wait until rat dies or we stop animating
                    Sleep.sleepUntil(() -> !targetRat.exists() || !Players.getLocal().isInCombat(), 10000);
                } else {
                    log("Failed to attack rat");
                    return false;
                }

                // Wait for tail drop and take it
                GroundItem tail = null;
                if (Sleep.sleepUntil(() -> GroundItems.closest(RAT_TAIL) != null, 8000)) {
                    tail = GroundItems.closest(RAT_TAIL);
                }
                if (tail != null && tail.interact("Take")) {
                    boolean picked = Sleep.sleepUntil(() -> Inventory.contains(RAT_TAIL), 8000);
                    log(picked ? "Picked up Rat's tail" : "Failed to pick up Rat's tail");
                    return picked;
                }

                // If the ground item isn't visible yet, wait a bit more then scan and attempt pickup
                Sleep.sleep(1000, 1500);
                tail = GroundItems.closest(RAT_TAIL);
                if (tail != null && tail.interact("Take")) {
                    return Sleep.sleepUntil(() -> Inventory.contains(RAT_TAIL), 8000);
                }

                log("Rat's tail not found after kill");
                return false;
            }
        };
        
        // Step 10: Drink from cauldron to complete quest
    drinkFromCauldron = new InteractWithObjectNode("drink_cauldron", "Cauldron", "Drink-from",
            HETTY_LOCATION, "Cauldron in Hetty's house") {
            @Override
            protected boolean performAction() {
                log("Drinking from cauldron to complete quest");
                
                int configBefore = PlayerSettings.getConfig(QUEST_CONFIG);
                int varbit275Before = PlayerSettings.getBitValue(QUEST_VARBIT_275);
                int varbit276Before = PlayerSettings.getBitValue(QUEST_VARBIT_276);
                log("Before cauldron interaction - Config 108: " + configBefore + ", Varbit 275: " + varbit275Before + ", Varbit 276: " + varbit276Before);
                
                boolean success = super.performAction();
                
                if (success) {
                    Sleep.sleep(3000, 5000);
                    
                    int configAfter = PlayerSettings.getConfig(QUEST_CONFIG);
                    int varbit275After = PlayerSettings.getBitValue(QUEST_VARBIT_275);
                    int varbit276After = PlayerSettings.getBitValue(QUEST_VARBIT_276);
                    log("After cauldron interaction - Config 108: " + configAfter + ", Varbit 275: " + varbit275After + ", Varbit 276: " + varbit276After);
                    
                    // Check if quest completed using discovered values
                    if (configAfter == QUEST_COMPLETE && varbit275After == 0 && varbit276After == 0) {
                        log("Quest completed!");
                        setQuestComplete();
                        completionPhase = false;
                        return true;
                    }
                    // Fallback: if Quest API says finished after drinking, finalize
                    if (Quests.isFinished(FreeQuest.WITCHS_POTION)) {
                        setQuestComplete();
                        completionPhase = false;
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
        
        // Smart decision node - determines next step based on quest state and inventory
        smartDecisionNode = new QuestNode("witchs_potion_decision", "Determine Witch's Potion next step") {
            @Override
            public ExecutionResult execute() {
                // Prefer DreamBot quest API for reliable status
                boolean isFinished = Quests.isFinished(FreeQuest.WITCHS_POTION);
                boolean isStarted = Quests.isStarted(FreeQuest.WITCHS_POTION);

                // Keep discovered values for logging/debug only
                int config = PlayerSettings.getConfig(QUEST_CONFIG);
                int varbit275 = PlayerSettings.getBitValue(QUEST_VARBIT_275);
                int varbit276 = PlayerSettings.getBitValue(QUEST_VARBIT_276);

                Tile currentTile = Players.getLocal().getTile();
                log("Quest Progress - finished=" + isFinished + ", started=" + isStarted +
                    ", Config108=" + config + ", V275=" + varbit275 + ", V276=" + varbit276 + ", Loc=" + currentTile);
                
                QuestNode nextStep = null;
                
                // Check if quest is complete using DreamBot API only (avoid false-positives from config/varbits)
                if (isFinished) {
                    log("Quest is complete according to Quest API");
                    setQuestComplete();
                    nextStep = questCompleteNode;
                    log("-> Quest complete!");
                } else if (completionPhase) {
                    // We're in completion phase: prioritize drinking from the cauldron; NEVER go to GE here
                    if (HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                        log("Completion phase active at Hetty - drinking from cauldron");
                        nextStep = drinkFromCauldron;
                        log("-> Drink from cauldron");
                    } else {
                        log("Completion phase active - walking back to Hetty to drink");
                        nextStep = walkToHettyForCompletion;
                        log("-> Walk to Hetty for completion");
                    }
                    return ExecutionResult.success(nextStep, "Completion phase step");
                }
                // Check inventory state
                boolean hasEyeOfNewt = Inventory.contains(EYE_OF_NEWT);
                boolean hasOnion = Inventory.contains(ONION);
                boolean hasBurntMeat = Inventory.contains(BURNT_MEAT);
                boolean hasCookedMeat = Inventory.contains("Cooked meat");
                boolean hasRawBeef = Inventory.contains(RAW_BEEF);
                boolean hasRatTail = Inventory.contains(RAT_TAIL);
                
                log("Inventory check - Eye of newt: " + hasEyeOfNewt + ", Onion: " + hasOnion + 
                    ", Burnt meat: " + hasBurntMeat + ", Cooked meat: " + hasCookedMeat + 
                    ", Raw beef: " + hasRawBeef + ", Rat's tail: " + hasRatTail);
                
                // Handle any quest state (including unknown config values) using discovered values
                if (isStarted) {
                    // Quest started or in progress - check what we need to do
                    if (hasEyeOfNewt && hasOnion && hasBurntMeat && hasRatTail) {
                        // Check if we're at Hetty's house
                        if (HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                            log("Have all final ingredients and at Hetty - completing quest");
                            nextStep = talkToHettyComplete;
                            log("-> Talk to Hetty to complete quest");
                        } else {
                            log("Have all final ingredients - going to Hetty");
                            nextStep = walkToHettyForCompletion;
                            log("-> Walk to Hetty for completion");
                        }
                    } else if (!hasRatTail && hasEyeOfNewt && hasOnion) {
                        // We need the rat tail and quest must be started; go get it
                        if (RAT_HUNT_AREA.contains(Players.getLocal())) {
                            log("Missing Rat's tail; obtaining by killing a rat");
                            nextStep = obtainRatTailNode;
                            log("-> Kill a rat for Rat's tail");
                        } else {
                            log("Missing Rat's tail; walking to rat area near Hetty");
                            nextStep = walkToRatHuntArea;
                            log("-> Walk to rats");
                        }
                    } else if (hasEyeOfNewt && hasOnion && hasCookedMeat) {
                        // Have cooked meat - need to burn it at Hetty's fireplace to make burnt meat
                        if (HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                            log("Have cooked meat and at Hetty - burning meat to make burnt meat");
                            nextStep = cookRawBeef; // This node handles both burning and cooking via fireplace
                            log("-> Burn cooked meat to make burnt meat");
                        } else {
                            log("Have cooked meat - going to Hetty to burn it");
                            nextStep = walkToHettyForCooking;
                            log("-> Walk to Hetty for burning meat");
                        }
                    } else if (hasEyeOfNewt && hasOnion && hasRawBeef) {
                        // Have raw ingredients - need to cook beef at Hetty's fireplace
                        if (HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                            log("Have raw ingredients and at Hetty - cooking beef at fireplace");
                            nextStep = cookRawBeef;
                            log("-> Cook beef at fireplace");
                        } else {
                            log("Have raw ingredients - going to Hetty to cook");
                            nextStep = walkToHettyForCooking;
                            log("-> Walk to Hetty for cooking");
                        }
                    } else {
                        log("Quest in progress but missing items - going to GE");
                        // Check if we need to walk to Grand Exchange first
                        if (completionPhase) {
                            // Do not go to GE in completion phase; head back to Hetty
                            nextStep = walkToHettyForCompletion;
                            log("-> Completion phase - IGNORE GE; return to Hetty");
                        } else if (!GRAND_EXCHANGE_AREA.contains(Players.getLocal())) {
                            nextStep = walkToGrandExchange;
                            log("-> Walk to Grand Exchange");
                        } else {
                            nextStep = purchaseItemsNode;
                            log("-> Purchase missing items from GE");
                        }
                    }
                } else /* not started */ {
                    // Quest not started
                    if (hasAllRequiredItems()) {
                        // Check if we're at Hetty's house
                        if (HETTY_HOUSE_AREA.contains(Players.getLocal())) {
                            log("Quest not started, have all items, and at Hetty - starting quest");
                            nextStep = talkToHettyStart;
                            log("-> Talk to Hetty to start quest");
                        } else {
                            log("Quest not started but have all items - going to Hetty to start");
                            nextStep = walkToHetty;
                            log("-> Walk to Hetty to start quest");
                        }
                    } else {
                        log("Quest not started and missing items - going to GE");
                        // Check if we need to walk to Grand Exchange first
                        if (!GRAND_EXCHANGE_AREA.contains(Players.getLocal())) {
                            nextStep = walkToGrandExchange;
                            log("-> Walk to Grand Exchange");
                        } else {
                            nextStep = purchaseItemsNode;
                            log("-> Purchase missing items from GE");
                        }
                    }
                }
                
                if (nextStep != null) {
                    return ExecutionResult.success(nextStep, "Moving to next step");
                } else {
                    return ExecutionResult.failure("No next step determined");
                }
            }
        };
        
        // Chain action nodes together for fluid movement
        ((ActionNode)walkToGrandExchange).setNextNode(purchaseItemsNode);
        ((ActionNode)purchaseItemsNode).setNextNode(null); // Return to decision node
        ((ActionNode)walkToHetty).setNextNode(null); // Decision node will determine next step
        ((ActionNode)walkToHettyForCooking).setNextNode(null); // Decision node will determine next step
        ((ActionNode)walkToHettyForCompletion).setNextNode(null); // Decision node will determine next step
    ((ActionNode)cookRawBeef).setNextNode(null); // Return to decision node
        ((ActionNode)talkToHettyStart).setNextNode(null); // Return to decision node
        ((ActionNode)talkToHettyComplete).setNextNode(drinkFromCauldron);
        ((ActionNode)drinkFromCauldron).setNextNode(questCompleteNode);
    ((ActionNode)walkToRatHuntArea).setNextNode(obtainRatTailNode);
    ((ActionNode)obtainRatTailNode).setNextNode(null);
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
    boolean isFinished = Quests.isFinished(FreeQuest.WITCHS_POTION);
    boolean isStarted = Quests.isStarted(FreeQuest.WITCHS_POTION);
    log("Quest Progress Check - finished=" + isFinished + ", started=" + isStarted);
    if (isFinished) return 100;
    if (isStarted) {
            boolean hasBurntMeat = Inventory.contains(BURNT_MEAT);
            boolean hasEyeOfNewt = Inventory.contains(EYE_OF_NEWT);
            boolean hasOnion = Inventory.contains(ONION);
            boolean hasRatTail = Inventory.contains(RAT_TAIL);
            
            if (hasBurntMeat && hasEyeOfNewt && hasOnion && hasRatTail) {
                return 90; // Ready to complete
            } else if (hasEyeOfNewt && hasOnion && Inventory.contains(RAW_BEEF)) {
                return 60; // Have raw materials
            } else {
                return 30; // Started but missing items
            }
        }
        
    // Not started
    return hasAllRequiredItems() ? 10 : 0;
    }
}