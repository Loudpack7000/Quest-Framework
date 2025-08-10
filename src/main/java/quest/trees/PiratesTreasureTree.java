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
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.dialogues.Dialogues;

import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.utilities.Sleep;
// Avoid compile-time dependency on Shop by using reflection helpers below
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pirate's Treasure Quest Tree Implementation
 * Based on quest log data from 2025-08-06
 * 
 * CORRECTED Quest Flow:
 * 1. Talk to Redbeard Frank (start quest) - Config 101: 0 → 1 (quest started)
 * 2. Travel to Karamja, buy rum and bananas
 * 3. Smuggle rum back using banana crate method
 * 4. Get White apron, talk to Wydin, search Crate (ID: 2071)
 * 5. Give rum to Redbeard Frank → get key
 * 6. Travel to Blue Moon Inn (Varrock), use key on chest → get Pirate message
 * 7. Travel to Falador garden, dig for treasure → find casket
 * 8. Open casket → quest complete (Config 101: 15 → 17)
 */
public class PiratesTreasureTree extends QuestTree {
    
    // CORRECTED Quest constants from log data analysis
    private static final int QUEST_CONFIG_ID = 101;
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 1;        // CORRECTED: Quest starts at 1, not 15!
    private static final int QUEST_IN_PROGRESS = 15;   // Config was 15 before treasure dig
    private static final int QUEST_COMPLETE = 17;      // Config changed to 17 after casket
    
    // Key locations from log data
    private static final Tile REDBEARD_FRANK_LOCATION = new Tile(3049, 3253, 0);    // Port Sarim
    private static final Tile SEAMAN_LORRIS_LOCATION = new Tile(3027, 3218, 0);     // Port Sarim docks
    private static final Tile KARAMJA_DOCK = new Tile(2956, 3146, 0);               // Karamja arrival
    private static final Tile ZEMBO_LOCATION = new Tile(2927, 3142, 0);             // Karamja shop
    private static final Tile BANANA_TREES_AREA = new Tile(2921, 3158, 0);          // Banana plantation
    private static final Tile LUTHAS_LOCATION = new Tile(2936, 3152, 0);            // Plantation owner
    private static final Tile BANANA_CRATE_LOCATION = new Tile(2942, 3151, 0);      // Crate to fill
    private static final Tile CUSTOMS_OFFICER_LOCATION = new Tile(2954, 3149, 0);   // Karamja customs
    private static final Tile WHITE_APRON_LOCATION = new Tile(3014, 3219, 0);       // Port Sarim food shop
    private static final Tile WYDIN_LOCATION = new Tile(3015, 3204, 0);             // Food shop owner
    private static final Tile RUM_CRATE_LOCATION = new Tile(3011, 3203, 0);         // Crate with rum (ID: 2071)
    private static final Tile BLUE_MOON_INN_UPSTAIRS = new Tile(3219, 3396, 1);     // Varrock chest location
    private static final Tile FALADOR_GARDEN_DIG_SITE = new Tile(2999, 3383, 0);    // Treasure dig location
    
    // Required quest items
    private static final String KARAMJAN_RUM = "Karamjan rum";
    private static final String BANANA = "Banana";
    private static final String WHITE_APRON = "White apron";
    private static final String CHEST_KEY = "Chest key";
    private static final String PIRATE_MESSAGE = "Pirate message";
    private static final String CASKET = "Casket";
    private static final String SPADE = "Spade";
    
    // Special object IDs
    private static final int RUM_CRATE_ID = 2071;
    private static final int PIRATE_CHEST_ID = 2070;
    
    // Nodes
    private ActionNode startQuestNode;
    private ActionNode travelToKaramjaNode;
    private ActionNode getRumAndBananasNode;
    private ActionNode smuggleRumNode;
    private ActionNode returnToPortSarimNode;
    private ActionNode getApronAndJobNode;
    private ActionNode retrieveRumNode;
    private ActionNode exchangeRumForKeyNode;
    private ActionNode getPirateMessageNode;
    private ActionNode digForTreasureNode;
    private QuestNode smartDecisionNode;  // UPDATED: Now uses QuestNode instead of DecisionNode
    // Session flag to ensure we explicitly start the quest with Redbeard Frank
    private boolean hasDialogStartedQuest = false;
    // Track how many bananas were picked from each tree (limit 4 per tree)
    private final Map<String, Integer> bananaTreePickCounts = new HashMap<>();
    
    public PiratesTreasureTree() {
        super("Pirate's Treasure");
    }
    
    @Override
    protected void buildTree() {
        log("Building Pirate's Treasure quest tree...");
        
        // Build all quest nodes
        buildQuestNodes();
        buildSmartDecisionNode();
        
        // Set root node
        rootNode = smartDecisionNode;
        
        log("Pirate's Treasure quest tree built successfully!");
    }
    
    // MODERN SMART DECISION NODE - Uses QuestNode instead of DecisionNode
    private void buildSmartDecisionNode() {
        smartDecisionNode = new QuestNode("smart_decision", "Smart quest decision based on current state") {
            @Override
            public ExecutionResult execute() {
                int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
                logCurrentState(config, Players.getLocal().getTile());

                // Sync the session flag with real quest state if the quest is already started
                if (!hasDialogStartedQuest && config >= QUEST_STARTED) {
                    hasDialogStartedQuest = true;
                }
                
                QuestNode nextStep = null;
                String reason = "";
                
                // Check if quest is actually complete using config values
                if (config >= QUEST_COMPLETE) {
                    log("Quest already completed! Config " + QUEST_CONFIG_ID + " = " + config + " (>= " + QUEST_COMPLETE + ")");
                    log("Quest already completed, but force restart enabled - continuing execution");
                }
                
                // PRIORITY 1: Always ensure we explicitly start with Redbeard Frank before any travel
                if (config < QUEST_STARTED || !hasDialogStartedQuest) {
                    log("Quest not started yet - need to talk to Redbeard Frank first");
                    log("Config " + QUEST_CONFIG_ID + " = " + config + " (< " + QUEST_STARTED + ")");
                    nextStep = startQuestNode;
                    reason = "Start quest with Redbeard Frank";
                }
                // PRIORITY 2: Check quest progress and items (only after quest is started)
                else if (Inventory.contains(CASKET)) {
                    log("Have casket - quest should be complete");
                    nextStep = digForTreasureNode; // Open casket
                    reason = "Open casket to complete quest";
                } else if (Inventory.contains(PIRATE_MESSAGE)) {
                    log("Have pirate message - need to dig for treasure");
                    nextStep = digForTreasureNode;
                    reason = "Dig for treasure at Falador garden";
                } else if (Inventory.contains(CHEST_KEY)) {
                    log("Have chest key - need to get pirate message");
                    nextStep = getPirateMessageNode;
                    reason = "Get pirate message from Blue Moon Inn";
                } else if (hasRumAndNeedToExchange()) {
                    log("Have rum - need to exchange for key");
                    nextStep = exchangeRumForKeyNode;
                    reason = "Exchange rum for chest key";
                } else if (needToRetrieveRum()) {
                    log("Need to retrieve smuggled rum");
                    nextStep = retrieveRumNode;
                    reason = "Retrieve rum from crate";
                } else if (isAtKaramjaAndNeedToReturn()) {
                    log("At Karamja - need to return to Port Sarim");
                    nextStep = returnToPortSarimNode;
                    reason = "Return to Port Sarim";
                } else if (needToSmuggleRum()) {
                    log("Need to smuggle rum using banana crate");
                    nextStep = smuggleRumNode;
                    reason = "Smuggle rum using banana crate";
                } else if (needToGetRumAndBananas()) {
                    log("Need to get rum and bananas");
                    nextStep = getRumAndBananasNode;
                    reason = "Get rum and bananas at Karamja";
                } else if (needToTravelToKaramja()) {
                    log("Quest started, need to travel to Karamja");
                    nextStep = travelToKaramjaNode;
                    reason = "Travel to Karamja";
                } else {
                    log("Fallback - talk to Redbeard Frank");
                    nextStep = startQuestNode;
                    reason = "Fallback: Talk to Redbeard Frank";
                }
                
                log("-> " + reason);
                return ExecutionResult.success(nextStep, reason);
            }
        };
    }
    
    private void buildQuestNodes() {
        // Start quest with Redbeard Frank
        startQuestNode = new ActionNode("start_quest", "Talk to Redbeard Frank to start quest") {
            @Override
            protected boolean performAction() {
                log("Starting Pirate's Treasure quest with Redbeard Frank");
                
                // Walk to Redbeard Frank
                if (Players.getLocal().getTile().distance(REDBEARD_FRANK_LOCATION) > 5) {
                    log("Walking to Redbeard Frank at " + REDBEARD_FRANK_LOCATION);
                    Walking.walk(REDBEARD_FRANK_LOCATION);
                    if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(REDBEARD_FRANK_LOCATION) <= 5, 15000)) {
                        log("Failed to walk to Redbeard Frank");
                        return false;
                    }
                }
                
                // Find and talk to Redbeard Frank
                NPC frank = NPCs.closest("Redbeard Frank");
                if (frank == null) {
                    log("Could not find Redbeard Frank");
                    return false;
                }
                
                if (!frank.interact("Talk-to")) {
                    log("Failed to interact with Redbeard Frank");
                    return false;
                }
                
                // Wait for dialogue to open
                if (!Sleep.sleepUntil(() -> Dialogues.inDialogue(), 7000)) {
                    log("Dialogue did not open with Redbeard Frank");
                    return false;
                }
                
                log("Successfully initiated dialogue with Redbeard Frank");
                
                // Handle quest start dialogue
                try {
                    while (Dialogues.inDialogue()) {
                        if (Dialogues.areOptionsAvailable()) {
                            String[] options = Dialogues.getOptions();
                            log("Frank dialogue options: " + java.util.Arrays.toString(options));
                            
                            // Look for "I'm in search of treasure" option
                            boolean foundTreasureOption = false;
                            for (int i = 0; i < options.length; i++) {
                                if (options[i].contains("treasure") || options[i].contains("search")) {
                                    log("Selecting treasure option: " + options[i]);
                                    Dialogues.chooseOption(i + 1);
                                    foundTreasureOption = true;
                                    break;
                                }
                            }
                            
                            if (!foundTreasureOption) {
                                // If no treasure option, look for "Yes" to start quest
                                for (int i = 0; i < options.length; i++) {
                                    if (options[i].contains("Yes")) {
                                        log("Selecting Yes to start quest: " + options[i]);
                                        Dialogues.chooseOption(i + 1);
                                        break;
                                    }
                                }
                            }
                            
                            Sleep.sleep(1000, 2000);
                        } else if (Dialogues.canContinue()) {
                            log("Continuing dialogue...");
                            if (!Dialogues.spaceToContinue()) {
                                Dialogues.continueDialogue();
                            }
                            Sleep.sleep(1000, 2000);
                        } else {
                            log("Dialogue state unclear, waiting...");
                            Sleep.sleep(1000);
                        }
                    }
                    
                    log("✅ Dialogue with Redbeard Frank completed - quest should be started!");
                    log("Using dialogue completion as trigger to move to next step");
                    hasDialogStartedQuest = true;
                    return true; // Quest started successfully based on dialogue completion
                    
                } catch (Exception e) {
                    log("Exception during Frank dialogue: " + e.getMessage());
                    return false;
                }
            }
        };
        
        // Travel to Karamja
        travelToKaramjaNode = new ActionNode("travel_karamja", "Travel to Karamja") {
            @Override
            protected boolean performAction() {
                log("Traveling to Karamja...");
                
                // Walk to Port Sarim docks
                if (Players.getLocal().getTile().distance(SEAMAN_LORRIS_LOCATION) > 10) {
                    log("Walking to Port Sarim docks...");
                    Walking.walk(SEAMAN_LORRIS_LOCATION);
                    if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(SEAMAN_LORRIS_LOCATION) <= 10, 15000)) {
                        log("Failed to walk to Port Sarim docks");
                        return false;
                    }
                }
                
                // Talk to Seaman Lorris
                NPC lorris = NPCs.closest("Seaman Lorris");
                if (lorris != null) {
                    log("Talking to Seaman Lorris to travel to Karamja...");
                    if (lorris.interact("Talk-to")) {
                        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                        
                        // Handle dialogue to travel to Karamja
                        while (Dialogues.inDialogue()) {
                            if (Dialogues.areOptionsAvailable()) {
                                String[] options = Dialogues.getOptions();
                                log("Lorris dialogue options: " + java.util.Arrays.toString(options));
                                
                                // Look for Karamja travel option
                                for (int i = 0; i < options.length; i++) {
                                    if (options[i].contains("Karamja") || options[i].contains("travel")) {
                                        log("Selecting Karamja travel option: " + options[i]);
                                        Dialogues.chooseOption(i + 1);
                                        break;
                                    }
                                }
                                Sleep.sleep(1000, 2000);
                            } else if (Dialogues.canContinue()) {
                                log("Continuing dialogue...");
                                if (!Dialogues.spaceToContinue()) {
                                    Dialogues.continueDialogue();
                                }
                                Sleep.sleep(1000, 2000);
                            } else {
                                log("Dialogue state unclear, waiting...");
                                Sleep.sleep(1000);
                            }
                        }
                        
                        // Wait for arrival at Karamja
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(KARAMJA_DOCK) < 50, 10000);
                        log("Successfully arrived at Karamja!");
                        return true;
                    }
                }
                
                return false;
            }
        };
        
        // Get rum and bananas
        getRumAndBananasNode = new ActionNode("get_rum_bananas", "Get Karamjan rum and bananas") {
            @Override
            protected boolean performAction() {
                log("Getting rum and bananas...");
                
                // Buy rum from Zembo first
                if (!Inventory.contains(KARAMJAN_RUM)) {
                    log("Need to buy Karamjan rum from Zembo...");
                    
                    // Walk to Zembo if not close
                    if (Players.getLocal().getTile().distance(ZEMBO_LOCATION) > 10) {
                        log("Walking to Zembo at " + ZEMBO_LOCATION);
                        Walking.walk(ZEMBO_LOCATION);
                        if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(ZEMBO_LOCATION) <= 10, 10000)) {
                            log("Failed to walk to Zembo");
                            return false;
                        }
                    }
                    
                    // Find and trade with Zembo
                    NPC zembo = NPCs.closest("Zembo");
                    if (zembo != null) {
                        log("Trading with Zembo for rum...");
                        if (!zembo.interact("Trade")) {
                            log("Failed to interact with Zembo");
                            return false;
                        }
                        // Wait for shop to open, then purchase rum properly
                        if (!Sleep.sleepUntil(() -> isShopOpen(), 5000)) {
                            log("ERROR: Shop did not open when trading with Zembo");
                            return false;
                        }
                        log("Shop opened, attempting to purchase Karamjan rum...");
                        boolean purchased = purchaseFromShop(KARAMJAN_RUM, 1);
                        if (!purchased) {
                            // Sometimes the item may be out of stock or needs a retry
                            Sleep.sleep(500, 800);
                            purchased = purchaseFromShop(KARAMJAN_RUM, 1);
                        }
                        if (!purchased) {
                            log("ERROR: Failed to purchase Karamjan rum from shop");
                            return false;
                        }
                        if (!Sleep.sleepUntil(() -> Inventory.contains(KARAMJAN_RUM), 4000)) {
                            log("ERROR: Rum not found in inventory after purchasing");
                            return false;
                        }
                        log("✅ Successfully purchased Karamjan rum from Zembo");
                    } else {
                        log("Could not find Zembo");
                        return false;
                    }
                }
                
                // Pick bananas (need 10+ for smuggling)
                int bananasNeeded = 10 - Inventory.count(BANANA);
                if (bananasNeeded > 0) {
                    log("Need to pick " + bananasNeeded + " bananas...");

                    // Walk to banana plantation area if not close
                    if (Players.getLocal().getTile().distance(BANANA_TREES_AREA) > 20) {
                        log("Walking to banana plantation at " + BANANA_TREES_AREA);
                        Walking.walk(BANANA_TREES_AREA);
                        if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(BANANA_TREES_AREA) <= 20, 10000)) {
                            log("Failed to walk to banana plantation");
                            return false;
                        }
                    }

                    // Attempt to pick with a limit of 4 bananas per tree before moving to the next
                    int safety = 0;
                    while (Inventory.count(BANANA) < 10 && Inventory.getEmptySlots() > 0 && safety < 200) {
                        GameObject tree = GameObjects.closest(go ->
                            go != null && "Banana tree".equals(go.getName()) &&
                            go.getTile().distance(BANANA_TREES_AREA) <= 25 &&
                            getPickedCountForTree(go) < 4
                        );

                        if (tree == null) {
                            // If no eligible tree found, reset counts to allow another rotation
                            bananaTreePickCounts.clear();
                            tree = GameObjects.closest(go ->
                                go != null && "Banana tree".equals(go.getName()) &&
                                go.getTile().distance(BANANA_TREES_AREA) <= 25
                            );
                        }

                        if (tree == null) {
                            log("No banana trees found in plantation area");
                            return false;
                        }

                        // Move closer if needed
                        if (Players.getLocal().getTile().distance(tree.getTile()) > 5) {
                            Walking.walk(tree);
                            Sleep.sleep(600, 1000);
                        }

                        String key = getTreeKey(tree);
                        int pickedFromThisTree = bananaTreePickCounts.getOrDefault(key, 0);
                        if (pickedFromThisTree >= 4) {
                            safety++;
                            continue;
                        }

                        if (tree.interact("Pick")) {
                            int before = Inventory.count(BANANA);
                            if (Sleep.sleepUntil(() -> Inventory.count(BANANA) > before, 3000)) {
                                bananaTreePickCounts.put(key, pickedFromThisTree + 1);
                            }
                        } else {
                            Sleep.sleep(300, 600);
                        }
                        safety++;
                    }

                    if (Inventory.count(BANANA) < 10) {
                        log("Failed to pick enough bananas. Have: " + Inventory.count(BANANA) + ", Need: 10");
                        return false;
                    }
                }
                
                // Talk to Luthas about employment (required before smuggling)
                if (Inventory.contains(KARAMJAN_RUM) && Inventory.count(BANANA) >= 10) {
                    log("Have rum and bananas - now need to talk to Luthas about employment...");
                    
                    // Walk to Luthas if not close
                    if (Players.getLocal().getTile().distance(LUTHAS_LOCATION) > 10) {
                        log("Walking to Luthas at " + LUTHAS_LOCATION);
                        Walking.walk(LUTHAS_LOCATION);
                        if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(LUTHAS_LOCATION) <= 10, 10000)) {
                            log("Failed to walk to Luthas");
                            return false;
                        }
                    }
                    
                    // Talk to Luthas about employment
                    NPC luthas = NPCs.closest("Luthas");
                    if (luthas != null) {
                        log("Talking to Luthas about employment...");
                        if (luthas.interact("Talk-to")) {
                            Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                            
                            // Handle employment dialogue
                            while (Dialogues.inDialogue()) {
                                if (Dialogues.areOptionsAvailable()) {
                                    String[] options = Dialogues.getOptions();
                                    log("Luthas dialogue options: " + java.util.Arrays.toString(options));
                                    
                                    // Look for employment option
                                    for (int i = 0; i < options.length; i++) {
                                        if (options[i].contains("employment") || options[i].contains("job") || options[i].contains("work")) {
                                            log("Selecting employment option: " + options[i]);
                                            Dialogues.chooseOption(i + 1);
                                            break;
                                        }
                                    }
                                    Sleep.sleep(1000, 2000);
                                } else if (Dialogues.canContinue()) {
                                    log("Continuing dialogue...");
                                    if (!Dialogues.spaceToContinue()) {
                                        Dialogues.continueDialogue();
                                    }
                                    Sleep.sleep(1000, 2000);
                                } else {
                                    log("Dialogue state unclear, waiting...");
                                    Sleep.sleep(1000);
                                }
                            }
                            
                            log("✅ Successfully talked to Luthas about employment!");
                        } else {
                            log("Failed to interact with Luthas");
                            return false;
                        }
                    } else {
                        log("Could not find Luthas");
                        return false;
                    }
                }
                
                log("Successfully got rum, bananas, and employment from Luthas!");
                log("Rum: " + Inventory.contains(KARAMJAN_RUM));
                log("Bananas: " + Inventory.count(BANANA));
                
                return Inventory.contains(KARAMJAN_RUM) && Inventory.count(BANANA) >= 10;
            }
        };
        
        // Smuggle rum using banana crate method
        smuggleRumNode = new ActionNode("smuggle_rum", "Smuggle rum using banana crate") {
            @Override
            protected boolean performAction() {
                log("Smuggling rum using banana crate method...");
                
                // Talk to Luthas about job
                NPC luthas = NPCs.closest("Luthas");
                if (luthas != null) {
                    log("Talking to Luthas about employment...");
                    if (luthas.interact("Talk-to")) {
                        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                        // Handle dialogue about employment
                        Sleep.sleep(2000);
                    }
                }
                
                // Put rum in crate first
                if (Inventory.contains(KARAMJAN_RUM)) {
                    GameObject crate = GameObjects.closest(gameObject -> 
                        gameObject.getName().equals("Crate") && 
                        gameObject.getTile().distance(BANANA_CRATE_LOCATION) < 5);
                    
                    if (crate != null) {
                        log("Putting rum in crate...");
                        Inventory.interact(KARAMJAN_RUM, "Use");
                        Sleep.sleep(1000);
                        crate.interact("Use");
                        Sleep.sleep(2000);
                    }
                }
                
                // Fill crate with bananas
                GameObject crate = GameObjects.closest(gameObject -> 
                    gameObject.getName().equals("Crate") && 
                    gameObject.getTile().distance(BANANA_CRATE_LOCATION) < 5);
                
                if (crate != null) {
                    log("Filling crate with bananas...");
                    if (crate.interact("Fill")) {
                        Sleep.sleep(3000);
                    }
                }
                
                return true;
            }
        };
        
        // Return to Port Sarim
        returnToPortSarimNode = new ActionNode("return_port_sarim", "Return to Port Sarim") {
            @Override
            protected boolean performAction() {
                log("Returning to Port Sarim...");
                
                // Talk to Customs officer
                NPC customs = NPCs.closest("Customs officer");
                if (customs != null) {
                    log("Talking to Customs officer...");
                    if (customs.interact("Talk-to")) {
                        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                        // Handle customs dialogue
                        Sleep.sleep(3000);
                    }
                }
                
                // Cross gangplank back
                GameObject gangplank = GameObjects.closest("Gangplank");
                if (gangplank != null) {
                    log("Crossing gangplank back to Port Sarim...");
                    gangplank.interact("Cross");
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(new Tile(3029, 3217, 0)) < 10, 10000);
                }
                
                return true;
            }
        };
        
        // Get White apron and job at food shop
        getApronAndJobNode = new ActionNode("get_apron_job", "Get White apron and job") {
            @Override
            protected boolean performAction() {
                log("Getting White apron and job at food shop...");
                
                // Get White apron if not wearing it
                if (!Equipment.contains(WHITE_APRON) && !Inventory.contains(WHITE_APRON)) {
                    GameObject apron = GameObjects.closest(WHITE_APRON);
                    if (apron != null) {
                        log("Taking White apron...");
                        apron.interact("Take");
                        Sleep.sleep(2000);
                    }
                }
                
                // Wear apron
                if (Inventory.contains(WHITE_APRON)) {
                    log("Wearing White apron...");
                    Inventory.interact(WHITE_APRON, "Wear");
                    Sleep.sleep(1000);
                }
                
                // Talk to Wydin for job
                NPC wydin = NPCs.closest("Wydin");
                if (wydin != null) {
                    log("Talking to Wydin for job...");
                    if (wydin.interact("Talk-to")) {
                        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                        // Handle job dialogue
                        Sleep.sleep(3000);
                    }
                }
                
                return Equipment.contains(WHITE_APRON) || Inventory.contains(WHITE_APRON);
            }
        };
        
        // Retrieve rum from crate (ID: 2071)
        retrieveRumNode = new ActionNode("retrieve_rum", "Retrieve smuggled rum from crate") {
            @Override
            protected boolean performAction() {
                log("Retrieving smuggled rum from crate (ID: " + RUM_CRATE_ID + ")...");
                
                // First, ensure we have and wear the white apron
                if (!Equipment.contains(WHITE_APRON)) {
                    if (Inventory.contains(WHITE_APRON)) {
                        log("Wearing White apron...");
                        Inventory.interact(WHITE_APRON, "Wear");
                        Sleep.sleep(1000);
                    } else {
                        log("ERROR: No White apron found in inventory or equipment!");
                        return false;
                    }
                }
                
                // Check if we need to talk to Wydin for job access
                // Only talk to Wydin if we haven't already completed the job dialogue
                boolean needToTalkToWydin = true;
                
                // Try to find Wydin and check if we need to talk to him
                NPC wydin = NPCs.closest("Wydin");
                if (wydin != null) {
                    log("Checking if we need to talk to Wydin for job access...");
                    
                    // Try to interact with Wydin to see if dialogue opens
                    if (wydin.interact("Talk-to")) {
                        if (Sleep.sleepUntil(() -> Dialogues.inDialogue(), 3000)) {
                            log("Dialogue opened with Wydin - completing job dialogue...");
                            
                            // Handle job dialogue
                            while (Dialogues.inDialogue()) {
                                if (Dialogues.areOptionsAvailable()) {
                                    String[] options = Dialogues.getOptions();
                                    log("Wydin dialogue options: " + java.util.Arrays.toString(options));
                                    
                                    // Look for job/work option
                                    for (int i = 0; i < options.length; i++) {
                                        if (options[i].contains("job") || options[i].contains("work") || options[i].contains("employment")) {
                                            log("Selecting job option: " + options[i]);
                                            Dialogues.chooseOption(i + 1);
                                            break;
                                        }
                                    }
                                    Sleep.sleep(1000, 2000);
                                } else if (Dialogues.canContinue()) {
                                    log("Continuing dialogue...");
                                    if (!Dialogues.spaceToContinue()) {
                                        Dialogues.continueDialogue();
                                    }
                                    Sleep.sleep(1000, 2000);
                                } else {
                                    log("Dialogue state unclear, waiting...");
                                    Sleep.sleep(1000);
                                }
                            }
                            log("✅ Successfully completed job dialogue with Wydin!");
                        } else {
                            log("No dialogue opened with Wydin - we may already have job access");
                            needToTalkToWydin = false;
                        }
                    } else {
                        log("ERROR: Failed to interact with Wydin!");
                        return false;
                    }
                } else {
                    log("ERROR: Could not find Wydin!");
                    return false;
                }
                
                // Walk to the rum crate location if not close
                if (Players.getLocal().getTile().distance(RUM_CRATE_LOCATION) > 5) {
                    log("Walking to rum crate location at " + RUM_CRATE_LOCATION);
                    Walking.walk(RUM_CRATE_LOCATION);
                    if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(RUM_CRATE_LOCATION) <= 5, 10000)) {
                        log("ERROR: Failed to walk to rum crate location!");
                        return false;
                    }
                }
                
                // Open door to back room
                GameObject door = GameObjects.closest("Door");
                if (door != null && door.getTile().distance(new Tile(3017, 3206, 0)) < 5) {
                    log("Opening door to back room...");
                    if (door.interact("Open")) {
                        Sleep.sleep(2000);
                    } else {
                        log("ERROR: Failed to open door to back room!");
                        return false;
                    }
                } else {
                    log("ERROR: Could not find door to back room!");
                    return false;
                }
                
                // Search the specific crate with rum
                GameObject rumCrate = GameObjects.closest(gameObject -> 
                    gameObject.getName().equals("Crate") && 
                    gameObject.getID() == RUM_CRATE_ID);
                
                if (rumCrate != null) {
                    log("Found rum crate, searching for smuggled rum...");
                    if (rumCrate.interact("Search")) {
                        log("Successfully searched crate, waiting for rum...");
                        if (Sleep.sleepUntil(() -> Inventory.contains(KARAMJAN_RUM), 5000)) {
                            log("✅ Successfully retrieved smuggled rum!");
                            return true;
                        } else {
                            log("ERROR: Rum not found in inventory after searching crate!");
                            return false;
                        }
                    } else {
                        log("ERROR: Failed to interact with rum crate!");
                        return false;
                    }
                } else {
                    log("ERROR: Could not find rum crate (ID: " + RUM_CRATE_ID + ")!");
                    return false;
                }
            }
        };
        
        // Exchange rum for key
        exchangeRumForKeyNode = new ActionNode("exchange_rum", "Exchange rum for chest key") {
            @Override
            protected boolean performAction() {
                log("Exchanging rum for chest key...");
                
                // Walk to Redbeard Frank
                if (Players.getLocal().getTile().distance(REDBEARD_FRANK_LOCATION) > 10) {
                    Walking.walk(REDBEARD_FRANK_LOCATION);
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(REDBEARD_FRANK_LOCATION) < 10, 10000);
                }
                
                // Talk to Redbeard Frank with rum
                NPC redbeard = NPCs.closest("Redbeard Frank");
                if (redbeard != null && Inventory.contains(KARAMJAN_RUM)) {
                    log("Talking to Redbeard Frank with rum...");
                    if (redbeard.interact("Talk-to")) {
                        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                        // Handle dialogue to exchange rum for key
                        Sleep.sleep(3000);
                        Sleep.sleepUntil(() -> Inventory.contains(CHEST_KEY), 5000);
                        return true;
                    }
                }
                
                return false;
            }
        };
        
        // Get pirate message from chest
        getPirateMessageNode = new ActionNode("get_message", "Get pirate message from chest") {
            @Override
            protected boolean performAction() {
                log("Getting pirate message from Blue Moon Inn chest...");
                
                // Travel to Varrock Blue Moon Inn upstairs
                if (Players.getLocal().getTile().distance(BLUE_MOON_INN_UPSTAIRS) > 20) {
                    log("Traveling to Blue Moon Inn...");
                    // Walk to the Blue Moon Inn location
                    Walking.walk(BLUE_MOON_INN_UPSTAIRS);
                    if (!Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(BLUE_MOON_INN_UPSTAIRS) <= 5, 15000)) {
                        log("ERROR: Failed to walk to Blue Moon Inn!");
                        return false;
                    }
                }
                
                // Use key on the specific chest (ID: 2070)
                GameObject chest = GameObjects.closest(gameObject -> 
                    gameObject.getName().equals("Chest") && 
                    gameObject.getID() == PIRATE_CHEST_ID);
                
                if (chest != null && Inventory.contains(CHEST_KEY)) {
                    log("Found pirate chest (ID: " + PIRATE_CHEST_ID + "), using key...");
                    Inventory.interact(CHEST_KEY, "Use");
                    Sleep.sleep(1000);
                    chest.interact("Use");
                    Sleep.sleep(2000);
                    
                    // Open chest
                    if (chest.interact("Open")) {
                        log("Successfully opened chest, waiting for pirate message...");
                        if (Sleep.sleepUntil(() -> Inventory.contains(PIRATE_MESSAGE), 5000)) {
                            log("✅ Successfully retrieved pirate message!");
                            return true;
                        } else {
                            log("ERROR: Pirate message not found in inventory after opening chest!");
                            return false;
                        }
                    } else {
                        log("ERROR: Failed to open chest!");
                        return false;
                    }
                } else {
                    if (chest == null) {
                        log("ERROR: Could not find pirate chest (ID: " + PIRATE_CHEST_ID + ")!");
                    } else {
                        log("ERROR: No chest key in inventory!");
                    }
                    return false;
                }
            }
        };
        
        // Dig for treasure
        digForTreasureNode = new ActionNode("dig_treasure", "Dig for treasure and complete quest") {
            @Override
            protected boolean performAction() {
                log("Digging for treasure at Falador garden...");
                
                // Travel to Falador garden dig site
                if (Players.getLocal().getTile().distance(FALADOR_GARDEN_DIG_SITE) > 20) {
                    log("Traveling to Falador garden...");
                    Walking.walk(FALADOR_GARDEN_DIG_SITE);
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(FALADOR_GARDEN_DIG_SITE) < 10, 15000);
                }
                
                // Get spade if needed
                if (!Inventory.contains(SPADE)) {
                    log("Need to get a spade first...");
                    // Would need to get spade from bank or somewhere
                    return false;
                }
                
                // Dig at the treasure location
                if (Inventory.contains(SPADE)) {
                    log("Digging for treasure...");
                    if (Inventory.interact(SPADE, "Dig")) {
                        Sleep.sleepUntil(() -> Inventory.contains(CASKET), 10000);
                        
                        // Open casket to complete quest
                        if (Inventory.contains(CASKET)) {
                            log("Opening casket to complete quest...");
                            Inventory.interact(CASKET, "Open");
                            Sleep.sleep(3000);
                            return true;
                        }
                    }
                }
                
                return false;
            }
        };
    }
    
    // Helper methods for decision logic
    private boolean hasRumAndNeedToExchange() {
        // Only exchange rum for key when we're at Port Sarim (not Karamja) and have rum but no key
        return Inventory.contains(KARAMJAN_RUM) && 
               !Inventory.contains(CHEST_KEY) &&
               Players.getLocal().getTile().distance(KARAMJA_DOCK) > 100; // Must be at Port Sarim, not Karamja
    }
    
    private boolean needToRetrieveRum() {
        return !Inventory.contains(KARAMJAN_RUM) && !Inventory.contains(CHEST_KEY) && 
               (Equipment.contains(WHITE_APRON) || Inventory.contains(WHITE_APRON));
    }
    
    private boolean isAtKaramjaAndNeedToReturn() {
        return Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100 && 
               !needToGetRumAndBananas() && !needToSmuggleRum();
    }
    
    private boolean needToSmuggleRum() {
        return Inventory.contains(KARAMJAN_RUM) && Inventory.count(BANANA) >= 10 &&
               Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100;
    }
    
    private boolean needToGetRumAndBananas() {
        return (!Inventory.contains(KARAMJAN_RUM) || Inventory.count(BANANA) < 10) &&
               Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100;
    }
    
    private boolean needToTravelToKaramja() {
        // Only travel to Karamja AFTER quest is started via dialogue with Redbeard Frank
        int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        boolean questStarted = config >= QUEST_STARTED || hasDialogStartedQuest;
        return questStarted &&
               !Inventory.contains(KARAMJAN_RUM) &&
               !Inventory.contains(CHEST_KEY) &&
               Players.getLocal().getTile().distance(KARAMJA_DOCK) > 100;
    }

    // ----- Helper methods for Shop reflection to avoid API import issues -----
    private boolean isShopOpen() {
        try {
            Class<?> shopClass = Class.forName("org.dreambot.api.methods.container.impl.shop.Shop");
            java.lang.reflect.Method isOpen = shopClass.getMethod("isOpen");
            Object result = isOpen.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean purchaseFromShop(String itemName, int quantity) {
        try {
            Class<?> shopClass = Class.forName("org.dreambot.api.methods.container.impl.shop.Shop");
            java.lang.reflect.Method purchase = shopClass.getMethod("purchase", String.class, int.class);
            Object result = purchase.invoke(null, itemName, quantity);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    // ----- Helpers for banana trees limiting per tree -----
    private String getTreeKey(GameObject tree) {
        return tree.getTile().getX() + "," + tree.getTile().getY() + "," + tree.getTile().getZ();
    }

    private int getPickedCountForTree(GameObject tree) {
        return bananaTreePickCounts.getOrDefault(getTreeKey(tree), 0);
    }
    
    // Helper methods
    private void logCurrentState(int config, Tile currentTile) {
        log("=== PIRATE'S TREASURE DEBUG ===");
        log("Raw Config " + QUEST_CONFIG_ID + " = " + config);
        log("Quest started check: config >= " + QUEST_STARTED + " = " + (config >= QUEST_STARTED));
        log("Quest finished check: config >= " + QUEST_COMPLETE + " = " + (config >= QUEST_COMPLETE));
        log("Location: " + currentTile);
        log("Has " + KARAMJAN_RUM + ": " + Inventory.contains(KARAMJAN_RUM));
        log("Has " + BANANA + " (" + Inventory.count(BANANA) + "): " + (Inventory.count(BANANA) >= 10));
        log("Has " + WHITE_APRON + ": " + (Equipment.contains(WHITE_APRON) || Inventory.contains(WHITE_APRON)));
        log("Has " + CHEST_KEY + ": " + Inventory.contains(CHEST_KEY));
        log("Has " + PIRATE_MESSAGE + ": " + Inventory.contains(PIRATE_MESSAGE));
        log("Has " + CASKET + ": " + Inventory.contains(CASKET));
        log("Has " + SPADE + ": " + Inventory.contains(SPADE));
        log("===============================");
    }
    
    @Override
    public boolean isQuestComplete() {
        // Use config values for quest completion check
        int currentConfig = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        boolean isFinished = currentConfig >= QUEST_COMPLETE;
        log("DEBUG: Config " + QUEST_CONFIG_ID + " = " + currentConfig + " (>= " + QUEST_COMPLETE + " = " + isFinished + ")");
        
        // Force restart - ignore quest completion status for testing
        // TODO: Remove this line when not testing already completed quests
        log("FORCE RESTART: Ignoring quest completion status for testing");
        return false; // Force restart - always return false to continue quest execution
        
        // return isFinished; // Original line - uncomment when not testing
    }
    
    @Override
    public int getQuestProgress() {
        int config = PlayerSettings.getConfig(QUEST_CONFIG_ID);
        
        if (config >= QUEST_COMPLETE) return 100;
        if (Inventory.contains(PIRATE_MESSAGE)) return 85;
        if (Inventory.contains(CHEST_KEY)) return 70;
        if (Equipment.contains(WHITE_APRON)) return 55;
        if (Inventory.contains(KARAMJAN_RUM)) return 40;
        if (config >= QUEST_STARTED) return 25;
        return 0;
    }
}