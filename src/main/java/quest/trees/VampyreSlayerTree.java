package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.methods.dialogues.Dialogues;
import quest.utils.DialogueUtil;
import org.dreambot.api.methods.container.impl.bank.Bank;
import quest.utils.GrandExchangeUtil;
import quest.utils.GrandExchangeUtil.ItemRequest;
import quest.utils.GrandExchangeUtil.PriceStrategy;

/**
 * Vampyre Slayer Quest Tree Implementation
 * Based on quest event log data from 2025-08-06
 * 
 * Quest Flow:
 * 1. Talk to Morgan (start quest) - Config 178: 0 → 1
 * 2. Talk to Dr. Harlow (get Stake) - Config 178: 1 → 2  
 * 3. Navigate to Draynor Manor basement
 * 4. Open Count Draynor's coffin
 * 5. Fight Count Draynor
 * 6. Return to Morgan (complete quest)
 */
public class VampyreSlayerTree extends QuestTree {
    
    // Quest configuration tracking (from quest log)
    private static final int QUEST_CONFIG_ID = 178;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 1;        // Talked to Morgan
    private static final int QUEST_HAS_ITEMS = 2;      // Got Stake from Dr. Harlow
    private static final int QUEST_DEFEATED_COUNT = 3; // Killed Count Draynor
    private static final int QUEST_COMPLETE = 4;       // Returned to Morgan
    
    // Key locations (from quest log coordinates)
    private static final Tile MORGAN_LOCATION = new Tile(3098, 3268, 0);         // Morgan's house
    private static final Tile DR_HARLOW_LOCATION = new Tile(3223, 3397, 0);      // Blue Moon Inn, Varrock
    private static final Tile MANOR_ENTRANCE = new Tile(3109, 3353, 0);          // Large door entrance
    private static final Tile MANOR_INNER_DOOR = new Tile(3109, 3357, 0);        // Inner door
    private static final Tile MANOR_BASEMENT_STAIRS = new Tile(3107, 3367, 0);   // Stairs to basement
    private static final Tile BASEMENT_LOCATION = new Tile(3077, 9771, 0);       // Basement after stairs
    private static final Tile COUNT_COFFIN_LOCATION = new Tile(3077, 9775, 0);   // Count Draynor's coffin
    
    // Required quest items (from starting inventory + quest log)
    private static final String GARLIC = "Garlic";
    private static final String STAKE = "Stake";
    private static final String HAMMER = "Hammer";
    
    // Quest nodes
    private QuestNode smartDecisionNode;
    private QuestNode walkToMorganNode, startQuestWithMorganNode;
    private QuestNode walkToHarlowNode, getStakeFromHarlowNode;
    private QuestNode walkToManorNode, enterManorNode, navigateToBasementNode;
    private QuestNode openCoffinNode, fightCountDraynorNode;
    private QuestNode returnToMorganNode, completeQuestNode;
    private QuestNode buyMissingItemsNode;
    
    public VampyreSlayerTree() {
        super("Vampyre Slayer");
    }
    
    @Override
    protected void buildTree() {
        createActionNodes();
        createSmartDecisionNode();
        rootNode = smartDecisionNode;
    }
    
    private void createActionNodes() {
        // Quest start nodes
        walkToMorganNode = new WalkToLocationNode("walk_morgan", MORGAN_LOCATION, "Morgan's house");
        
        // Morgan dialogue with explicit acceptance option selection
        startQuestWithMorganNode = new TalkToNPCNode("start_quest_morgan", "Morgan", MORGAN_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Starting Morgan dialogue to begin quest...");
                // Prefer acceptance options; fall back to base behaviour
                if (org.dreambot.api.methods.dialogues.Dialogues
                        .chooseFirstOptionContaining("Yes, I'm up for an adventure", "Yes, I'm up for an adventure.", "I'll help", "I will help")) {
                    log("Selected quest acceptance option with Morgan");
                    return true;
                }
                return super.performAction();
            }
        };
        
        // Item collection nodes
        walkToHarlowNode = new WalkToLocationNode("walk_harlow", DR_HARLOW_LOCATION, "Blue Moon Inn");
        
        getStakeFromHarlowNode = new TalkToNPCNode("get_stake_harlow", "Dr Harlow", DR_HARLOW_LOCATION) {
            @Override
            protected boolean performAction() {
                log("Dr Harlow step: acquire Stake using Beer and correct dialogue");

                if (!Inventory.contains("Beer")) {
                    log("Missing Beer; cannot progress Dr Harlow step");
                    return false;
                }

                int beerBefore = Inventory.count("Beer");
                int stakeBefore = Inventory.count(STAKE);

                // Ensure we open dialogue first
                NPC harlow = NPCs.closest("Dr Harlow");
                if (harlow == null) {
                    log("Could not find Dr Harlow NPC");
                    return false;
                }
                if (!Dialogues.inDialogue()) {
                    if (!harlow.interact("Talk-to")) {
                        log("Failed to talk to Dr Harlow");
                        return false;
                    }
                    Sleep.sleepUntil(Dialogues::inDialogue, 7000);
                }

                // In-dialogue handling: select specific options where applicable
                long dialogueStart = System.currentTimeMillis();
                while (Dialogues.inDialogue() && System.currentTimeMillis() - dialogueStart < 15000) {
                    if (Dialogues.areOptionsAvailable()) {
                        // Prefer the exact phrases to avoid default option 1
                        if (Dialogues.chooseFirstOptionContaining("Morgan needs your help", "buy you a beer")) {
                            log("Selected targeted option with Dr Harlow");
                            Sleep.sleep(600, 900);
                        } else {
                            // Fallback: continue
                            DialogueUtil.continueDialogue(2000);
                        }
                    } else {
                        DialogueUtil.continueDialogue(2000);
                    }
                }

                // Wait to see if stake awarded (sometimes requires a second talk)
                boolean gotStake = Sleep.sleepUntil(() -> Inventory.count(STAKE) > stakeBefore, 4000);
                boolean beerConsumed = Inventory.count("Beer") < beerBefore;

                if (!gotStake) {
                    // Try talking again to consume beer and receive stake
                    if (!Dialogues.inDialogue()) {
                        if (harlow.interact("Talk-to")) {
                            Sleep.sleepUntil(Dialogues::inDialogue, 5000);
                            DialogueUtil.continueDialogue(6000);
                        }
                    }
                    gotStake = Sleep.sleepUntil(() -> Inventory.count(STAKE) > stakeBefore, 5000);
                    beerConsumed = beerConsumed || Inventory.count("Beer") < beerBefore;
                }

                // Last resort: manually use beer on Dr Harlow
                if (!gotStake && Inventory.count("Beer") > 0) {
                    if (Inventory.interact("Beer", "Use")) {
                        Sleep.sleep(300, 600);
                        if (harlow.interact("Use")) {
                            Sleep.sleepUntil(() -> Inventory.count("Beer") < beerBefore || Inventory.count(STAKE) > stakeBefore, 5000);
                            gotStake = Inventory.count(STAKE) > stakeBefore;
                        }
                    }
                }

                log("Dr Harlow outcome: gotStake=" + gotStake + ", beerConsumed=" + beerConsumed);
                return gotStake;
            }
        };
        
        // GE/Bank-backed acquisition for Beer, Garlic, Hammer (batch approach)
        buyMissingItemsNode = new ActionNode("ge_buy_missing", "Buy/Withdraw missing Vampyre Slayer items") {
            @Override
            protected boolean performAction() {
                // 1) Try to withdraw everything missing in one bank session
                boolean needHammer = !Inventory.contains(HAMMER);
                boolean needGarlic = !Inventory.contains(GARLIC);
                boolean needBeer   = !Inventory.contains("Beer");

                if (!needHammer && !needGarlic && !needBeer) {
                    log("All required items already present.");
                    return true;
                }

                if (Bank.open()) {
                    if (needHammer && Bank.contains(HAMMER)) Bank.withdraw(HAMMER, 1);
                    if (needGarlic && Bank.contains(GARLIC)) Bank.withdraw(GARLIC, 1);
                    if (needBeer   && Bank.contains("Beer")) Bank.withdraw("Beer", 1);
                    org.dreambot.api.utilities.Sleep.sleep(600, 900);
                    Bank.close();
                }

                // Re-evaluate after bank
                needHammer = !Inventory.contains(HAMMER);
                needGarlic = !Inventory.contains(GARLIC);
                needBeer   = !Inventory.contains("Beer");

                // 2) Batch-buy remaining items from GE in a single visit
                java.util.List<ItemRequest> requests = new java.util.ArrayList<>();
                if (needHammer) requests.add(new ItemRequest(HAMMER, 1, PriceStrategy.FIXED_500_GP));
                if (needGarlic) requests.add(new ItemRequest(GARLIC, 1, PriceStrategy.FIXED_500_GP));
                if (needBeer)   requests.add(new ItemRequest("Beer", 1, PriceStrategy.FIXED_500_GP));

                if (requests.isEmpty()) {
                    log("All required items obtained after bank.");
                    return true;
                }

                boolean geSuccess = GrandExchangeUtil.buyItems(requests.toArray(new ItemRequest[0]));
                if (!geSuccess) {
                    log("Failed to purchase all missing items from GE");
                }
                return geSuccess;
            }
        };

        // Manor navigation nodes
        walkToManorNode = new WalkToLocationNode("walk_manor", MANOR_ENTRANCE, "Draynor Manor entrance");
        
        enterManorNode = new ActionNode("enter_manor", "Enter Draynor Manor") {
            @Override
            protected boolean performAction() {
                // Step 1: Open large door (entrance)
                GameObject largeDoor = GameObjects.closest("Large door");
                if (largeDoor != null && largeDoor.getTile().equals(MANOR_ENTRANCE)) {
                    log("Opening large door at manor entrance");
                    if (largeDoor.interact("Open")) {
                        Sleep.sleepUntil(() -> !largeDoor.exists() || Players.getLocal().getTile().distance(MANOR_ENTRANCE) < 2, 5000);
                    }
                }
                
                // Step 2: Open inner door
                GameObject innerDoor = GameObjects.closest("Door");
                if (innerDoor != null && innerDoor.getTile().equals(MANOR_INNER_DOOR)) {
                    log("Opening inner door");
                    if (innerDoor.interact("Open")) {
                        Sleep.sleepUntil(() -> !innerDoor.exists() || Players.getLocal().getTile().distance(MANOR_INNER_DOOR) < 2, 5000);
                        return true;
                    }
                }
                
                return isInsideManor();
            }
        };
        
        navigateToBasementNode = new ActionNode("navigate_basement", "Navigate to manor basement") {
            @Override
            protected boolean performAction() {
                // Navigate through manor to basement stairs
                if (!isNearBasementStairs()) {
                    // Walk towards basement stairs area
                    if (!new WalkToLocationNode("temp_walk", MANOR_BASEMENT_STAIRS, "basement stairs").execute().isSuccess()) {
                        return false;
                    }
                }
                
                // Use stairs to go down to basement (multiple stairs from quest log)
                GameObject stairs = GameObjects.closest("Stairs");
                if (stairs != null) {
                    log("Going down stairs to basement");
                    if (stairs.interact("Walk-Down")) {
                        // Wait for teleport to basement (6403 tiles moved according to log)
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 0 && 
                                         Players.getLocal().getTile().getY() > 9000, 10000);
                        
                        if (isInBasement()) {
                            log("Successfully reached basement: " + Players.getLocal().getTile());
                            return true;
                        }
                    }
                }
                
                return isInBasement();
            }
        };
        
        // Combat nodes
        openCoffinNode = new ActionNode("open_coffin", "Open Count Draynor's coffin") {
            @Override
            protected boolean performAction() {
                GameObject coffin = GameObjects.closest("Coffin");
                if (coffin != null && coffin.getTile().distance(COUNT_COFFIN_LOCATION) < 3) {
                    log("Opening Count Draynor's coffin");
                    if (coffin.interact("Open")) {
                        Sleep.sleepUntil(() -> NPCs.closest("Count Draynor") != null, 5000);
                        return NPCs.closest("Count Draynor") != null;
                    }
                }
                
                return NPCs.closest("Count Draynor") != null;
            }
        };
        
        fightCountDraynorNode = new ActionNode("fight_count", "Fight Count Draynor") {
            @Override
            protected boolean performAction() {
                // Ensure we have required combat items
                if (!hasRequiredItems()) {
                    log("Missing required items for combat: Garlic, Stake, Hammer");
                    return false;
                }
                
                NPC countDraynor = NPCs.closest("Count Draynor");
                if (countDraynor != null) {
                    log("Starting combat with Count Draynor");
                    
                    // Do not attempt to wield garlic; just ensure it is present
                    if (Inventory.contains(GARLIC)) {
                        log("Garlic present in inventory for vampyre fight.");
                    }
                    
                    // Attack Count Draynor
                    if (countDraynor.interact("Attack")) {
                        log("Engaging Count Draynor in combat");
                        
                        // Wait for combat to complete (NPC death or player death)
                        boolean combatComplete = Sleep.sleepUntil(() -> {
                            return NPCs.closest("Count Draynor") == null || 
                                   !Players.getLocal().isInCombat();
                        }, 60000); // 60 second combat timeout
                        
                        if (combatComplete && NPCs.closest("Count Draynor") == null) {
                            log("Count Draynor defeated!");
                            return true;
                        } else if (!combatComplete) {
                            log("Combat timeout - continuing to fight");
                            return false; // Retry combat
                        }
                    }
                }
                
                return NPCs.closest("Count Draynor") == null;
            }
        };
        
        // Quest completion nodes
        returnToMorganNode = new WalkToLocationNode("return_morgan", MORGAN_LOCATION, "Return to Morgan");
        
        completeQuestNode = new TalkToNPCNode("complete_quest", "Morgan", MORGAN_LOCATION);
    }
    
    private void createSmartDecisionNode() {
        smartDecisionNode = new QuestNode("vampyre_smart_decision", "Vampyre Slayer Smart Decision") {
            @Override
            public ExecutionResult execute() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
                Tile currentTile = Players.getLocal().getTile();
                
                // Debug logging
                logCurrentState(config, currentTile);
                
                QuestNode nextStep = null;
                String reason = "";
                
                // Priority 1: Quest completion check
                if (config >= QUEST_COMPLETE) {
                    log("-> Quest complete!");
                    setQuestComplete();
                    return ExecutionResult.success(null, "Quest complete");
                }
                
                // Priority 2: Return to Morgan after defeating Count Draynor
                else if (config >= QUEST_DEFEATED_COUNT || 
                        (config >= QUEST_HAS_ITEMS && NPCs.closest("Count Draynor") == null && isInBasement())) {
                    if (currentTile.distance(MORGAN_LOCATION) > 5) {
                        nextStep = returnToMorganNode;
                        reason = "Return to Morgan to complete quest";
                    } else {
                        nextStep = completeQuestNode;
                        reason = "Complete quest with Morgan";
                    }
                }
                
                // Priority 3a: If we have stake but missing garlic/hammer, acquire them
                else if (config >= QUEST_HAS_ITEMS && (!Inventory.contains(HAMMER) || !Inventory.contains(GARLIC))) {
                    nextStep = buyMissingItemsNode;
                    reason = "Buy missing Hammer/Garlic";
                }
                // Priority 3b: Combat phase - have all items, go fight Count Draynor
                else if (config >= QUEST_HAS_ITEMS && hasRequiredItems()) {
                    if (isInBasement()) {
                        NPC countDraynor = NPCs.closest("Count Draynor");
                        if (countDraynor != null) {
                            nextStep = fightCountDraynorNode;
                            reason = "Fight Count Draynor";
                        } else {
                            // Need to open coffin first
                            nextStep = openCoffinNode;
                            reason = "Open Count Draynor's coffin";
                        }
                    } else {
                        // Navigate to basement
                        if (isInsideManor()) {
                            nextStep = navigateToBasementNode;
                            reason = "Navigate to manor basement";
                        } else if (currentTile.distance(MANOR_ENTRANCE) > 5) {
                            nextStep = walkToManorNode;
                            reason = "Walk to Draynor Manor";
                        } else {
                            nextStep = enterManorNode;
                            reason = "Enter Draynor Manor";
                        }
                    }
                }
                
                // Priority 4: Item collection ordering - ensure Beer before Stake; buy any missing misc items first
                else if (config >= QUEST_STARTED && !Inventory.contains(STAKE)) {
                    if (!Inventory.contains("Beer")) {
                        nextStep = buyMissingItemsNode;
                        reason = "Buy Beer (and any other missing items) before talking to Dr Harlow";
                    } else if (currentTile.distance(DR_HARLOW_LOCATION) > 5) {
                        nextStep = walkToHarlowNode;
                        reason = "Walk to Dr. Harlow in Blue Moon Inn";
                    } else {
                        nextStep = getStakeFromHarlowNode;
                        reason = "Get Stake from Dr. Harlow";
                    }
                }
                
                // Priority 5: Quest start phase (ensure basic items first)
                else if (config == QUEST_NOT_STARTED) {
                    if (!Inventory.contains(HAMMER) || !Inventory.contains(GARLIC) || !Inventory.contains("Beer")) {
                        nextStep = buyMissingItemsNode;
                        reason = "Acquire Beer/Garlic/Hammer before starting";
                    } else if (currentTile.distance(MORGAN_LOCATION) > 5) {
                        nextStep = walkToMorganNode;
                        reason = "Walk to Morgan to start quest";
                    } else {
                        nextStep = startQuestWithMorganNode;
                        reason = "Start quest with Morgan";
                    }
                }
                
                // Default: Re-evaluate quest state
                else {
                    log("Unexpected quest state - re-evaluating...");
                    if (currentTile.distance(MORGAN_LOCATION) > 5) {
                        nextStep = walkToMorganNode;
                        reason = "Return to Morgan (fallback)";
                    } else {
                        nextStep = startQuestWithMorganNode;
                        reason = "Talk to Morgan (fallback)";
                    }
                }
                
                log("-> " + reason);
                return ExecutionResult.success(nextStep, reason);
            }
        };
    }
    
    // Helper methods
    private void logCurrentState(int config, Tile currentTile) {
        log("=== VAMPYRE SLAYER DEBUG ===");
        log("Config " + QUEST_CONFIG_ID + " = " + config);
        log("Location: " + currentTile);
        log("Has Garlic: " + Inventory.contains(GARLIC));
        log("Has Stake: " + Inventory.contains(STAKE));
        log("Has Hammer: " + Inventory.contains(HAMMER));
        log("In Basement: " + isInBasement());
        log("In Manor: " + isInsideManor());
        log("Count Draynor present: " + (NPCs.closest("Count Draynor") != null));
        log("========================");
    }
    
    private boolean hasRequiredItems() {
        return Inventory.contains(GARLIC) && 
               Inventory.contains(STAKE) && 
               Inventory.contains(HAMMER);
    }
    
    private boolean isInsideManor() {
        Tile currentTile = Players.getLocal().getTile();
        // Inside manor but not in basement (Z=0, regular Y coordinates)
        return currentTile.getZ() == 0 && 
               currentTile.getY() < 9000 &&
               currentTile.distance(MANOR_ENTRANCE) < 50;
    }
    
    private boolean isInBasement() {
        Tile currentTile = Players.getLocal().getTile();
        // Basement has Y coordinates > 9000 (from quest log: 3077, 9771, 0)
        return currentTile.getZ() == 0 && currentTile.getY() > 9000;
    }
    
    private boolean isNearBasementStairs() {
        return Players.getLocal().getTile().distance(MANOR_BASEMENT_STAIRS) < 5;
    }
    
    @Override
    public boolean isQuestComplete() {
        return PlayerSettings.getConfig(QUEST_CONFIG_ID) >= QUEST_COMPLETE;
    }
    
    @Override
    public int getQuestProgress() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        
        if (config >= QUEST_COMPLETE) return 100;
        if (config >= QUEST_DEFEATED_COUNT) return 80;
        if (config >= QUEST_HAS_ITEMS) return 60;
        if (config >= QUEST_STARTED) return 30;
        return 0;
    }
}
