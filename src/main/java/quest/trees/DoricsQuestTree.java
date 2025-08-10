package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;

import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.utils.GrandExchangeUtil;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.grandexchange.GrandExchange;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.utilities.Sleep;

/**
 * Doric's Quest Tree Implementation
 * Based on quest log data from 2025-08-06
 * 
 * Quest Requirements:
 * - 4x Copper ore
 * - 6x Clay  
 * - 2x Iron ore
 * 
 * Quest Flow:
 * 1. Collect required items (GE/Bank)
 * 2. Talk to Doric at (2951, 3451, 0)
 * 3. Select "I wanted to use your anvils."
 * 4. Quest config 101: 14 → 15 (completion)
 */
public class DoricsQuestTree extends QuestTree {
    
    // Quest constants from log data
    private static final int QUEST_CONFIG_ID = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 14;  // From log: config was 14 before completion
    private static final int QUEST_COMPLETE = 15; // From log: config changed to 15
    
    // Item constants from log data
    private static final String COPPER_ORE = "Copper ore";
    private static final int COPPER_ORE_NEEDED = 4;
    private static final String CLAY = "Clay";
    private static final int CLAY_NEEDED = 6;
    private static final String IRON_ORE = "Iron ore";
    private static final int IRON_ORE_NEEDED = 2;
    
    // Location constants from log data
    private static final Tile DORIC_LOCATION = new Tile(2951, 3451, 0);
    
    // Nodes
    private ActionNode buyItemsAtGE;
    private ActionNode collectItemsFromBank;
    private TalkToNPCNode talkToDoric;
    private QuestNode smartDecisionNode;
    
    public DoricsQuestTree() {
        super("Doric's Quest");
    }
    
    @Override
    protected void buildTree() {
        log("Building Doric's Quest tree...");
        
        // Build decision and action nodes
        buildItemCollectionNodes();
        buildQuestCompletionNodes();
        buildSmartDecisionNode();
        
        // Set root node
        rootNode = smartDecisionNode;
        
        log("Doric's Quest tree built successfully!");
    }
    
    private void buildSmartDecisionNode() {
        smartDecisionNode = new QuestNode("smart_decision", "Smart quest decision based on current state") {
            @Override
            public ExecutionResult execute() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
                logCurrentState(config, Players.getLocal().getTile());
                
                QuestNode nextStep = null;
                String reason = "";
                
                // Check if quest is already completed, but respect force restart
                if (config >= QUEST_COMPLETE) {
                    log("Quest already completed, but force restart enabled - continuing execution");
                }
                
                if (hasAllRequiredItems()) {
                    log("Have all required items, proceeding to Doric");
                    nextStep = talkToDoric;
                    reason = "Talk to Doric with required items";
                } else {
                    // Check if we need to buy items or collect from bank
                    if (Bank.contains(COPPER_ORE) || Bank.contains(CLAY) || Bank.contains(IRON_ORE)) {
                        log("Items available in bank, collecting them");
                        nextStep = collectItemsFromBank;
                        reason = "Collect quest items from bank";
                    } else {
                        log("Missing required items, need to buy them");
                        nextStep = buyItemsAtGE;
                        reason = "Buy required items at Grand Exchange";
                    }
                }
                
                log("-> " + reason);
                return ExecutionResult.success(nextStep, reason);
            }
        };
    }
    
    private void buildItemCollectionNodes() {
        // Item purchase node
        buyItemsAtGE = new ActionNode("buy_items_ge", "Buy quest items at Grand Exchange") {
            @Override
            protected boolean performAction() {
                log("Starting item collection for Doric's Quest");
                
                // Use our existing GrandExchangeUtil for buying items
                log("Buying " + COPPER_ORE_NEEDED + "x " + COPPER_ORE + "...");
                if (!GrandExchangeUtil.buyItem(COPPER_ORE, COPPER_ORE_NEEDED, GrandExchangeUtil.PriceStrategy.CONSERVATIVE)) {
                    log("Failed to buy " + COPPER_ORE);
                    return false;
                }
                
                log("Buying " + CLAY_NEEDED + "x " + CLAY + "...");
                if (!GrandExchangeUtil.buyItem(CLAY, CLAY_NEEDED, GrandExchangeUtil.PriceStrategy.CONSERVATIVE)) {
                    log("Failed to buy " + CLAY);
                    return false;
                }
                
                log("Buying " + IRON_ORE_NEEDED + "x " + IRON_ORE + "...");
                if (!GrandExchangeUtil.buyItem(IRON_ORE, IRON_ORE_NEEDED, GrandExchangeUtil.PriceStrategy.CONSERVATIVE)) {
                    log("Failed to buy " + IRON_ORE);
                    return false;
                }
                
                log("All items purchased successfully!");
                return true;
            }
        };
        
        // Item collection from bank node
        collectItemsFromBank = new ActionNode("collect_from_bank", "Collect quest items from bank") {
            @Override
            protected boolean performAction() {
                log("Collecting quest items from bank");
                
                // Close GE interface if open
                if (GrandExchange.isOpen()) {
                    log("Closing Grand Exchange interface");
                    GrandExchange.close();
                    Sleep.sleepUntil(() -> !GrandExchange.isOpen(), 3000);
                }
                
                // Open bank
                if (!Bank.isOpen()) {
                    log("Opening bank");
                    if (!Bank.open()) {
                        log("Failed to open bank");
                        return false;
                    }
                    Sleep.sleepUntil(Bank::isOpen, 5000);
                }
                
                if (!Bank.isOpen()) {
                    log("Bank failed to open");
                    return false;
                }
                
                // Withdraw required items
                log("Withdrawing " + COPPER_ORE_NEEDED + "x " + COPPER_ORE);
                if (!Bank.withdraw(COPPER_ORE, COPPER_ORE_NEEDED)) {
                    log("Failed to withdraw " + COPPER_ORE);
                    return false;
                }
                Sleep.sleep(500, 1000);
                
                log("Withdrawing " + CLAY_NEEDED + "x " + CLAY);
                if (!Bank.withdraw(CLAY, CLAY_NEEDED)) {
                    log("Failed to withdraw " + CLAY);
                    return false;
                }
                Sleep.sleep(500, 1000);
                
                log("Withdrawing " + IRON_ORE_NEEDED + "x " + IRON_ORE);
                if (!Bank.withdraw(IRON_ORE, IRON_ORE_NEEDED)) {
                    log("Failed to withdraw " + IRON_ORE);
                    return false;
                }
                Sleep.sleep(500, 1000);
                
                // Close bank
                Bank.close();
                Sleep.sleepUntil(() -> !Bank.isOpen(), 3000);
                
                log("Successfully collected all quest items from bank");
                return hasAllRequiredItems();
            }
        };
    }
    
    private void buildQuestCompletionNodes() {
        // Talk to Doric node - based on log data
        talkToDoric = new TalkToNPCNode("talk_to_doric", "Doric", DORIC_LOCATION);
    }
    
    // Helper methods
    private void logCurrentState(int config, Tile currentTile) {
        log("=== DORIC'S QUEST DEBUG ===");
        log("Config " + QUEST_CONFIG_ID + " = " + config);
        log("Location: " + currentTile);
        log("Has " + COPPER_ORE + " (" + COPPER_ORE_NEEDED + " needed): " + Inventory.count(COPPER_ORE));
        log("Has " + CLAY + " (" + CLAY_NEEDED + " needed): " + Inventory.count(CLAY));
        log("Has " + IRON_ORE + " (" + IRON_ORE_NEEDED + " needed): " + Inventory.count(IRON_ORE));
        log("All items ready: " + hasAllRequiredItems());
        log("========================");
    }
    
    private boolean hasAllRequiredItems() {
        return Inventory.count(COPPER_ORE) >= COPPER_ORE_NEEDED &&
               Inventory.count(CLAY) >= CLAY_NEEDED &&
               Inventory.count(IRON_ORE) >= IRON_ORE_NEEDED;
    }
    
    @Override
    public boolean isQuestComplete() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        boolean complete = config >= QUEST_COMPLETE;
        // Force restart - ignore quest completion status for testing
        return false;
    }
    
    @Override
    public int getQuestProgress() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        
        if (config >= QUEST_COMPLETE) return 100;
        if (hasAllRequiredItems()) return 75;
        if (config >= QUEST_STARTED) return 50;
        return 0;
    }
}
