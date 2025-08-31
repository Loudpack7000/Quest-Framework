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
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;

import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.utilities.Sleep;
// Avoid compile-time dependency on Shop by using reflection helpers below
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import quest.utils.RunEnergyUtil;

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
    private static final Tile KARAMJA_DOCK = new Tile(2956, 3146, 0);               // Karamja arrival dock
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
    // Track how many bananas were picked from each tree (limit 4 per tree)
    private final Map<String, Integer> bananaTreePickCounts = new HashMap<>();
    // Session flag to indicate we've successfully smuggled rum into the crate
    private boolean hasSmuggledRum = false;
    
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
                // Use DreamBot's built-in quest status methods instead of manual config checking
                boolean isStarted = Quests.isStarted(FreeQuest.PIRATES_TREASURE);
                boolean isFinished = Quests.isFinished(FreeQuest.PIRATES_TREASURE);
                
                log("=== PIRATE'S TREASURE DEBUG ===");
                log("Quest started: " + isStarted);
                log("Quest finished: " + isFinished);
                log("Location: " + Players.getLocal().getTile());
                log("Has " + KARAMJAN_RUM + ": " + Inventory.contains(KARAMJAN_RUM));
                log("Has " + BANANA + " (" + Inventory.count(BANANA) + "): " + (Inventory.count(BANANA) >= 10));
                log("Has " + WHITE_APRON + ": " + (Equipment.contains(WHITE_APRON) || Inventory.contains(WHITE_APRON)));
                log("Has " + CHEST_KEY + ": " + Inventory.contains(CHEST_KEY));
                log("Has " + PIRATE_MESSAGE + ": " + Inventory.contains(PIRATE_MESSAGE));
                log("Has " + CASKET + ": " + Inventory.contains(CASKET));
                log("Has " + SPADE + ": " + Inventory.contains(SPADE));
                log("===============================");
                
                QuestNode nextStep = null;
                String reason = "";
                
                // PRIORITY 1: Check if quest is finished
                if (isFinished) {
                    log("Quest is already finished!");
                    nextStep = digForTreasureNode; // Open casket if we have it
                    reason = "Quest finished - open casket if available";
                }
                // PRIORITY 2: Check if quest is started - if not, start with Redbeard Frank
                else if (!isStarted) {
                    log("Quest not started yet - need to talk to Redbeard Frank first");
                    nextStep = startQuestNode;
                    reason = "Start quest with Redbeard Frank";
                }
                // PRIORITY 3: Check quest progress and items (only after quest is started)
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
                } 
                // CRITICAL FIX: If we're at Port Sarim and already have rum, go exchange for key
                else if (Inventory.contains(KARAMJAN_RUM) && Players.getLocal().getTile().distance(KARAMJA_DOCK) > 100) {
                    log("Already have rum at Port Sarim - can skip to exchange for key");
                    // Reset smuggling flag since we're past that phase
                    hasSmuggledRum = false;
                    nextStep = exchangeRumForKeyNode;
                    reason = "Skip to exchange rum for key (already have rum)";
                }
                // NEW ORDER: If we haven't gone to Karamja yet, go there BEFORE apron/job logic
                else if (needToTravelToKaramja()) {
                    log("Quest started, need to travel to Karamja");
                    nextStep = travelToKaramjaNode;
                    reason = "Travel to Karamja";
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
                    log("Need to smuggle rum using banana crate (hasSmuggledRum=" + hasSmuggledRum + ")");
                    nextStep = smuggleRumNode;
                    reason = "Smuggle rum using banana crate";
                } else if (hasSmuggledRum && Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100) {
                    log("Smuggling completed, need to return to Port Sarim");
                    nextStep = returnToPortSarimNode;
                    reason = "Return to Port Sarim after smuggling";
                } else if (needToGetRumAndBananas()) {
                    log("Need to get rum and bananas");
                    nextStep = getRumAndBananasNode;
                    reason = "Get rum and bananas at Karamja";
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
                
                // Check and manage run energy before starting
                int currentEnergy = (int) org.dreambot.api.methods.walking.impl.Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    log("Low on run energy (" + currentEnergy + "%) - checking for energy potions...");
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        log("No energy potions found - attempting to restock from bank...");
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    // Drink a potion if we have one
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Walk to Redbeard Frank
                if (Players.getLocal().getTile().distance(REDBEARD_FRANK_LOCATION) > 8) {
                    log("Walking to Redbeard Frank at " + REDBEARD_FRANK_LOCATION);
                    // Manage run energy before walking
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_redbeard", REDBEARD_FRANK_LOCATION, "Redbeard Frank").execute();
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
                if (Players.getLocal().getTile().distance(SEAMAN_LORRIS_LOCATION) > 8) {
                    log("Walking to Port Sarim docks...");
                    // Manage run energy before walking
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_docks", SEAMAN_LORRIS_LOCATION, "Port Sarim docks").execute();
                }
                
                // Talk to Seaman Lorris - use Pay-fare action directly
                NPC lorris = NPCs.closest("Seaman Lorris");
                if (lorris != null) {
                    log("Paying fare to Seaman Lorris to travel to Karamja...");
                    if (lorris.interact("Pay-fare")) {
                        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                        
                        // Handle dialogue to travel to Karamja
                        while (Dialogues.inDialogue()) {
                            if (Dialogues.areOptionsAvailable()) {
                                String[] options = Dialogues.getOptions();
                                log("Lorris dialogue options: " + java.util.Arrays.toString(options));
                                
                                // Look for "Yes please" option to confirm travel
                                for (int i = 0; i < options.length; i++) {
                                    if (options[i].contains("Yes") || options[i].contains("please")) {
                                        log("Selecting yes to travel: " + options[i]);
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
                        
                        // CRITICAL: Wait for ship animation and arrival at Karamja
                        log("Waiting for ship travel to complete...");
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(KARAMJA_DOCK) < 50, 20000);
                        
                        // IMPORTANT: Cross the gangplank to actually get off the ship
                        GameObject gangplank = GameObjects.closest("Gangplank");
                        if (gangplank != null) {
                            log("Crossing gangplank to disembark at Karamja...");
                            if (gangplank.interact("Cross")) {
                                Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(KARAMJA_DOCK) < 10, 8000);
                                log("✅ Successfully arrived at Karamja!");
                                return true;
                            }
                        } else {
                            log("WARN: No gangplank found, but may have arrived successfully");
                            return Players.getLocal().getTile().distance(KARAMJA_DOCK) < 50;
                        }
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
                    if (Players.getLocal().getTile().distance(ZEMBO_LOCATION) > 8) {
                        log("Walking to Zembo at " + ZEMBO_LOCATION);
                        // Manage run energy before walking
                        RunEnergyUtil.manageRunEnergy();
                        new WalkToLocationNode("walk_zembo", ZEMBO_LOCATION, "Zembo").execute();
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
                        
                        // CRITICAL: Wait for shop interface to be fully ready
                        Sleep.sleep(500, 800);
                        
                        // Try to purchase rum with retry logic
                        boolean purchased = false;
                        int attempts = 0;
                        while (!purchased && attempts < 3) {
                            attempts++;
                            log("Purchase attempt " + attempts + " for Karamjan rum...");
                            
                            purchased = purchaseFromShop(KARAMJAN_RUM, 1);
                            if (!purchased) {
                                log("Purchase attempt " + attempts + " failed, waiting before retry...");
                                Sleep.sleep(800, 1200);
                            }
                        }
                        
                        if (!purchased) {
                            log("ERROR: Failed to purchase Karamjan rum after " + attempts + " attempts");
                            return false;
                        }
                        
                        // CRITICAL: Wait for rum to actually appear in inventory
                        log("Waiting for rum to appear in inventory...");
                        if (!Sleep.sleepUntil(() -> Inventory.contains(KARAMJAN_RUM), 8000)) {
                            log("ERROR: Rum not found in inventory after purchasing - checking if we have enough coins");
                            // Check if we have enough coins
                            if (Inventory.count("Coins") < 30) {
                                log("ERROR: Not enough coins! Need 30, have: " + Inventory.count("Coins"));
                                return false;
                            }
                            log("ERROR: Purchase may have failed despite shop interaction");
                            return false;
                        }
                        
                        log("✅ Successfully purchased Karamjan rum from Zembo! Quantity: " + Inventory.count(KARAMJAN_RUM));
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
                    if (Players.getLocal().getTile().distance(BANANA_TREES_AREA) > 8) {
                        log("Walking to banana plantation at " + BANANA_TREES_AREA);
                        new WalkToLocationNode("walk_banana_plantation", BANANA_TREES_AREA, "Banana plantation").execute();
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
                        if (Players.getLocal().getTile().distance(tree.getTile()) > 8) {
                            new WalkToLocationNode("walk_banana_tree", tree.getTile(), "Banana tree").execute();
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
                    if (Players.getLocal().getTile().distance(LUTHAS_LOCATION) > 8) {
                        log("Walking to Luthas at " + LUTHAS_LOCATION);
                        new WalkToLocationNode("walk_luthas", LUTHAS_LOCATION, "Luthas").execute();
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
                
                // Talk to Luthas about job first
                NPC luthas = NPCs.closest("Luthas");
                if (luthas != null) {
                    log("Talking to Luthas about employment...");
                    if (luthas.interact("Talk-to")) {
                        if (Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000)) {
                            // Handle dialogue about employment
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
                            log("✅ Successfully completed employment dialogue with Luthas!");
                        }
                    }
                }
                
                // STEP 1: Put rum in crate first (CRITICAL - must complete before bananas)
                if (Inventory.contains(KARAMJAN_RUM)) {
                    log("STEP 1: Putting rum in crate...");

                    // CRITICAL FIX: Walk to crate location first
                    if (Players.getLocal().getTile().distance(BANANA_CRATE_LOCATION) > 8) {
                        log("Walking to banana crate location at " + BANANA_CRATE_LOCATION);
                        new WalkToLocationNode("walk_to_crate", BANANA_CRATE_LOCATION, "Banana crate").execute();
                    }

                    GameObject crate = GameObjects.closest(gameObject ->
                        gameObject.getName().equals("Crate") &&
                        gameObject.getTile().distance(BANANA_CRATE_LOCATION) < 5);

                    if (crate != null) {
                        log("Using rum on crate...");
                        Inventory.interact(KARAMJAN_RUM, "Use");
                        Sleep.sleep(500, 800);
                        crate.interact("Use");

                        // CRITICAL: Wait for rum to actually be placed in crate
                        if (Sleep.sleepUntil(() -> !Inventory.contains(KARAMJAN_RUM), 5000)) {
                            log("✅ Rum successfully placed in crate!");
                        } else {
                            log("ERROR: Rum was not placed in crate!");
                            return false;
                        }
                    } else {
                        log("ERROR: Could not find crate for rum!");
                        return false;
                    }
                } else {
                    log("ERROR: No rum found in inventory for smuggling!");
                    return false;
                }
                
                // STEP 2: Fill crate with bananas (only after rum is confirmed placed)
                log("STEP 2: Filling crate with bananas...");

                // Ensure we're still close to the crate location for banana filling
                if (Players.getLocal().getTile().distance(BANANA_CRATE_LOCATION) > 8) {
                    log("Walking back to banana crate location at " + BANANA_CRATE_LOCATION);
                    new WalkToLocationNode("walk_to_crate_bananas", BANANA_CRATE_LOCATION, "Banana crate").execute();
                }

                GameObject crate = GameObjects.closest(gameObject ->
                    gameObject.getName().equals("Crate") &&
                    gameObject.getTile().distance(BANANA_CRATE_LOCATION) < 5);

                if (crate != null) {
                    log("Filling crate with bananas...");
                    if (crate.interact("Fill")) {
                        // CRITICAL: Wait for bananas to actually be placed
                        int bananasBefore = Inventory.count(BANANA);
                        if (Sleep.sleepUntil(() -> Inventory.count(BANANA) < bananasBefore, 5000)) {
                            log("✅ Bananas successfully placed in crate!");
                            
                            // Wait a bit more for the fill action to complete
                            Sleep.sleep(1000, 2000);
                            
                            // Verify we have successfully smuggled
                            if (!Inventory.contains(KARAMJAN_RUM) && Inventory.count(BANANA) < 10) {
                                hasSmuggledRum = true;
                                log("✅ SUCCESS: Smuggling complete! Rum and bananas are in crate.");
                                
                                // STEP 3: Talk to Luthas again to get paid and complete the job
                                log("STEP 3: Talking to Luthas to get paid...");
                                if (luthas != null && luthas.interact("Talk-to")) {
                                    if (Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000)) {
                                        // Handle payment dialogue
                                        while (Dialogues.inDialogue()) {
                                            if (Dialogues.areOptionsAvailable()) {
                                                String[] options = Dialogues.getOptions();
                                                log("Luthas payment options: " + java.util.Arrays.toString(options));
                                                
                                                // Look for payment/complete job option
                                                for (int i = 0; i < options.length; i++) {
                                                    if (options[i].contains("payment") || options[i].contains("paid") || options[i].contains("finished") || options[i].contains("complete")) {
                                                        log("Selecting payment option: " + options[i]);
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
                                        log("✅ Successfully completed payment dialogue with Luthas!");
                                    }
                                }
                                
                                return true;
                            } else {
                                log("ERROR: Smuggling verification failed - rum or bananas still in inventory!");
                                return false;
                            }
                        } else {
                            log("ERROR: Failed to place bananas in crate!");
                            return false;
                        }
                    } else {
                        log("ERROR: Failed to interact with crate for bananas!");
                        return false;
                    }
                } else {
                    log("ERROR: Could not find crate for bananas!");
                    return false;
                }
            }
        };
        
        // Return to Port Sarim
        returnToPortSarimNode = new ActionNode("return_port_sarim", "Return to Port Sarim") {
            @Override
            protected boolean performAction() {
                log("Returning to Port Sarim...");

                // Walk to customs officer location first
                if (Players.getLocal().getTile().distance(CUSTOMS_OFFICER_LOCATION) > 8) {
                    log("Walking to customs officer at " + CUSTOMS_OFFICER_LOCATION);
                    new WalkToLocationNode("walk_customs", CUSTOMS_OFFICER_LOCATION, "Customs officer").execute();
                }
                
                // Pay fare to customs officer with proper dialogue handling
                NPC customs = NPCs.closest("Customs officer");
                if (customs != null) {
                    log("Paying fare to Customs officer to sail back...");
                    if (customs.interact("Pay-Fare")) {
                        Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000);
                        
                        // Handle the multi-step customs dialogue
                        while (Dialogues.inDialogue()) {
                            if (Dialogues.areOptionsAvailable()) {
                                String[] options = Dialogues.getOptions();
                                log("Customs dialogue options: " + java.util.Arrays.toString(options));
                                
                                // Handle different dialogue stages
                                boolean foundOption = false;
                                for (int i = 0; i < options.length; i++) {
                                    String opt = options[i];
                                    // Stage 1: "Can I journey on this ship?"
                                    if (opt.contains("journey") || opt.contains("ship")) {
                                        log("Selecting journey option: " + opt);
                                        Dialogues.chooseOption(i + 1);
                                        foundOption = true;
                                        break;
                                    }
                                    // Stage 2: "Search away, I have nothing to hide."
                                    else if (opt.contains("Search away") || opt.contains("nothing to hide")) {
                                        log("Selecting search option: " + opt);
                                        Dialogues.chooseOption(i + 1);
                                        foundOption = true;
                                        break;
                                    }
                                    // Stage 3: "Ok."
                                    else if (opt.equals("Ok.")) {
                                        log("Selecting Ok: " + opt);
                                        Dialogues.chooseOption(i + 1);
                                        foundOption = true;
                                        break;
                                    }
                                }
                                
                                if (!foundOption) {
                                    log("No specific option found, selecting first option");
                                    Dialogues.chooseOption(1);
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
                        
                        log("✅ Customs dialogue completed, waiting for ship travel...");
                    }
                }

                // After customs dialogue, cross the gangplank
                GameObject gangplank = GameObjects.closest("Gangplank");
                if (gangplank != null) {
                    log("Crossing gangplank to return to Port Sarim...");
                    if (gangplank.interact("Cross")) {
                        log("Successfully crossed gangplank, waiting for arrival...");
                    }
                } else {
                    log("WARN: No gangplank found for return journey");
                }

                // Wait for arrival at Port Sarim with better detection
                Tile portSarimDock = new Tile(3029, 3217, 0);
                boolean arrived = Sleep.sleepUntil(() -> {
                    Tile currentTile = Players.getLocal().getTile();
                    return currentTile.distance(portSarimDock) < 20 && currentTile.getZ() == 0;
                }, 15000);
                
                if (arrived) {
                    log("✅ Successfully returned to Port Sarim!");
                } else {
                    log("WARN: Did not detect arrival to Port Sarim within timeout");
                    // Check if we're actually there anyway
                    arrived = Players.getLocal().getTile().distance(portSarimDock) < 30;
                }
                return arrived;
            }
        };
        
        // Get White apron and job at food shop
        getApronAndJobNode = new ActionNode("get_apron_job", "Get White apron and job") {
            @Override
            protected boolean performAction() {
                log("Getting White apron and job at food shop...");
                
                // CRITICAL FIX: Check if we already have apron and job access
                if (Equipment.contains(WHITE_APRON) || Inventory.contains(WHITE_APRON)) {
                    log("✅ Already have White apron - checking if we need job access...");
                    
                    // Test if we already have job access by trying to open the door
                    GameObject door = GameObjects.closest("Door");
                    if (door != null && door.getTile().distance(new Tile(3017, 3206, 0)) < 5) {
                        log("Testing if we already have job access...");
                        if (door.interact("Open")) {
                            Sleep.sleep(1000, 1500);
                            log("✅ Door opened successfully - we already have job access! Skipping Wydin dialogue.");
                            return true;
                        } else {
                            log("Door interaction failed - we need to talk to Wydin for job access");
                        }
                    }
                }
                
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
                
                // Talk to Wydin for job (only if we don't already have access)
                NPC wydin = NPCs.closest("Wydin");
                if (wydin != null) {
                    log("Talking to Wydin for job access...");
                    if (wydin.interact("Talk-to")) {
                        if (Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000)) {
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
                        }
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
                
                // CRITICAL FIX: Check if we already have the rum (skip if we do)
                if (Inventory.contains(KARAMJAN_RUM)) {
                    log("✅ Already have Karamjan rum - skipping retrieval!");
                    return true;
                }
                
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
                
                // CRITICAL FIX: Skip Wydin dialogue if we already have job access
                // Try to access the crate first to see if we already have job access
                GameObject door = GameObjects.closest("Door");
                if (door != null && door.getTile().distance(new Tile(3017, 3206, 0)) < 5) {
                    log("Testing if we already have job access by trying to open door...");
                    if (door.interact("Open")) {
                        Sleep.sleep(1000, 1500);
                        // If door opened, we already have job access
                        log("✅ Door opened successfully - we already have job access, skipping Wydin dialogue!");
                    } else {
                        log("Door interaction failed - we may need to talk to Wydin");
                        // Only talk to Wydin if door interaction failed
                        NPC wydin = NPCs.closest("Wydin");
                        if (wydin != null) {
                            log("Talking to Wydin for job access...");
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
                                }
                            }
                        }
                    }
                }
                
                // Walk to the rum crate location if not close
                if (Players.getLocal().getTile().distance(RUM_CRATE_LOCATION) > 8) {
                    log("Walking to rum crate location at " + RUM_CRATE_LOCATION);
                    new WalkToLocationNode("walk_rum_crate", RUM_CRATE_LOCATION, "Rum crate location").execute();
                }
                
                // Open door to back room (reuse door variable from earlier check)
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
                if (Players.getLocal().getTile().distance(REDBEARD_FRANK_LOCATION) > 8) {
                    new WalkToLocationNode("walk_redbeard_exchange", REDBEARD_FRANK_LOCATION, "Redbeard Frank").execute();
                }
                
                // Talk to Redbeard Frank with rum
                NPC redbeard = NPCs.closest("Redbeard Frank");
                if (redbeard != null && Inventory.contains(KARAMJAN_RUM)) {
                    log("Talking to Redbeard Frank with rum...");
                    if (redbeard.interact("Talk-to")) {
                        if (Sleep.sleepUntil(() -> Dialogues.inDialogue(), 5000)) {
                            log("✅ Dialogue opened with Redbeard Frank - handling rum exchange...");
                            
                            // Handle dialogue to exchange rum for key
                            long startTime = System.currentTimeMillis();
                            boolean dialogueHandled = false;
                            
                            while (Dialogues.inDialogue() && System.currentTimeMillis() - startTime < 15000) {
                                if (Dialogues.areOptionsAvailable()) {
                                    String[] options = Dialogues.getOptions();
                                    log("Redbeard dialogue options: " + java.util.Arrays.toString(options));
                                    
                                    // Look for options related to rum, treasure, or giving items
                                    boolean foundOption = false;
                                    for (int i = 0; i < options.length; i++) {
                                        String option = options[i].toLowerCase();
                                        if (option.contains("rum") || option.contains("treasure") || 
                                            option.contains("give") || option.contains("here") || 
                                            option.contains("yes") || option.contains("okay")) {
                                            log("Selecting option: " + options[i]);
                                            Dialogues.chooseOption(i + 1);
                                            foundOption = true;
                                            Sleep.sleep(1000, 2000);
                                            break;
                                        }
                                    }
                                    
                                    if (!foundOption) {
                                        log("No specific option found, selecting first option");
                                        Dialogues.chooseOption(1);
                                        Sleep.sleep(1000, 2000);
                                    }
                                    
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
                                
                                // Check if we received the chest key
                                if (Inventory.contains(CHEST_KEY)) {
                                    log("✅ Successfully received chest key from Redbeard Frank!");
                                    dialogueHandled = true;
                                    break;
                                }
                            }
                            
                            if (!dialogueHandled) {
                                log("WARN: Dialogue timeout or incomplete - checking if we got the key anyway");
                            }
                            
                            // Final verification that we got the key
                            if (Sleep.sleepUntil(() -> Inventory.contains(CHEST_KEY), 5000)) {
                                log("✅ SUCCESS: Chest key confirmed in inventory!");
                                return true;
                            } else {
                                log("ERROR: Failed to receive chest key from Redbeard Frank!");
                                return false;
                            }
                        } else {
                            log("ERROR: Dialogue did not open with Redbeard Frank!");
                            return false;
                        }
                    } else {
                        log("ERROR: Failed to interact with Redbeard Frank!");
                        return false;
                    }
                } else {
                    if (redbeard == null) {
                        log("ERROR: Could not find Redbeard Frank!");
                    } else {
                        log("ERROR: No Karamjan rum in inventory!");
                    }
                    return false;
                }
            }
        };
        
        // Get pirate message from chest
        getPirateMessageNode = new ActionNode("get_message", "Get pirate message from chest") {
            @Override
            protected boolean performAction() {
                log("Getting pirate message from Blue Moon Inn chest...");
                
                // Travel near Blue Moon Inn (ground floor) first
                Tile innGround = new Tile(BLUE_MOON_INN_UPSTAIRS.getX(), BLUE_MOON_INN_UPSTAIRS.getY(), 0);
                if (Players.getLocal().getTile().distance(innGround) > 8) {
                    log("Traveling to Blue Moon Inn (ground floor)...");
                    new WalkToLocationNode("walk_blue_moon_ground", innGround, "Blue Moon Inn ground floor").execute();
                }

                // If we are not upstairs yet, get inside and climb up first
                if (Players.getLocal().getZ() != 1) {
                    // Open the inn door if we're just outside
                    GameObject door = GameObjects.closest(go -> go != null && "Door".equals(go.getName()) && go.hasAction("Open") && go.getTile().distance(innGround) <= 6);
                    if (door != null) {
                        log("Opening Blue Moon Inn door if closed...");
                        door.interact("Open");
                        Sleep.sleep(600, 1000);
                    }

                    // Find stairs/staircase/ladder within the inn vicinity and climb up
                    GameObject stairs = GameObjects.closest(go -> go != null && ("Stairs".equals(go.getName()) || "Staircase".equals(go.getName()) || "Ladder".equals(go.getName())) && go.hasAction("Climb-up") && go.getTile().distance(innGround) <= 12);
                    if (stairs != null) {
                        if (Players.getLocal().getTile().distance(stairs.getTile()) > 5) {
                            new WalkToLocationNode("walk_to_stairs", stairs.getTile(), "Blue Moon Inn stairs").execute();
                        }
                        log("Climbing upstairs in Blue Moon Inn...");
                        if (stairs.interact("Climb-up")) {
                            if (!Sleep.sleepUntil(() -> Players.getLocal().getZ() == 1, 6000)) {
                                log("ERROR: Failed to change to upstairs level after climbing");
                                return false; // Avoid pathing to upstairs tile while still on ground
                            }
                        } else {
                            log("ERROR: Failed to interact with stairs");
                            return false;
                        }
                    } else {
                        log("ERROR: Could not find stairs to go upstairs. Will retry from ground floor.");
                        return false; // Guard: don't attempt to path to upstairs tile from ground
                    }
                }

                // Now we are upstairs; ensure we are close to the upstairs chest tile only on level 1
                if (Players.getLocal().getZ() == 1 && Players.getLocal().getTile().distance(BLUE_MOON_INN_UPSTAIRS) > 6) {
                    new WalkToLocationNode("walk_blue_moon_upstairs", BLUE_MOON_INN_UPSTAIRS, "Blue Moon Inn upstairs").execute();
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
                if (Players.getLocal().getTile().distance(FALADOR_GARDEN_DIG_SITE) > 8) {
                    log("Traveling to Falador garden...");
                    // Manage run energy before walking
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_falador_garden", FALADOR_GARDEN_DIG_SITE, "Falador garden").execute();
                }

                // Ensure we are exactly on the dig tile before digging to avoid premature digs en route
                if (!Players.getLocal().getTile().equals(FALADOR_GARDEN_DIG_SITE)) {
                    if (Players.getLocal().getTile().distance(FALADOR_GARDEN_DIG_SITE) > 1) {
                        log("Not exactly on the dig tile yet; walking precisely to (" + FALADOR_GARDEN_DIG_SITE.getX() + ", " + FALADOR_GARDEN_DIG_SITE.getY() + ", " + FALADOR_GARDEN_DIG_SITE.getZ() + ")");
                        new WalkToLocationNode("walk_exact_dig_tile", FALADOR_GARDEN_DIG_SITE, "Exact dig tile").execute();
                        // After walking, re-check exact position next loop
                        return false;
                    }
                }

                // Make sure we have and have read the pirate message before digging
                if (!Inventory.contains(PIRATE_MESSAGE)) {
                    log("ERROR: Pirate message not in inventory; cannot dig yet.");
                    return false;
                }
                // Read the message if it's not already been read; reading is safe even if already read
                log("Reading Pirate message before digging...");
                if (Inventory.interact(PIRATE_MESSAGE, "Read")) {
                    Sleep.sleep(600, 1000);
                }
                
                // Get spade if needed
                if (!Inventory.contains(SPADE)) {
                    log("No spade found - attempting to withdraw from nearest bank...");
                    boolean bankOpen = Bank.isOpen() || Bank.open();
                    if (!bankOpen) {
                        // Fallback: try interacting with a nearby banker or bank booth
                        NPC banker = NPCs.closest("Banker");
                        if (banker != null && banker.interact("Bank")) {
                            Sleep.sleepUntil(Bank::isOpen, 5000);
                        } else {
                            GameObject bankBooth = GameObjects.closest(go -> go != null && ("Bank booth".equals(go.getName()) || "Bank".equals(go.getName())) && go.hasAction("Bank"));
                            if (bankBooth != null) {
                                bankBooth.interact("Bank");
                                Sleep.sleepUntil(Bank::isOpen, 5000);
                            }
                        }
                    }

                    if (!Bank.isOpen()) {
                        log("ERROR: Could not open bank to get a Spade");
                        return false;
                    }

                    if (!Bank.contains(SPADE)) {
                        log("ERROR: Bank does not contain a Spade");
                        Bank.close();
                        return false;
                    }
                    if (!Bank.withdraw(SPADE, 1)) {
                        log("ERROR: Failed to withdraw Spade from bank");
                        Bank.close();
                        return false;
                    }
                    Sleep.sleepUntil(() -> Inventory.contains(SPADE), 4000);
                    Bank.close();
                }
                
                // Dig at the treasure location
                // Final guard: only dig if exactly on the target tile
                if (Inventory.contains(SPADE) && Players.getLocal().getTile().equals(FALADOR_GARDEN_DIG_SITE)) {
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
        // Only retrieve rum at Port Sarim (not Karamja) after smuggling phase
        return Players.getLocal().getTile().distance(KARAMJA_DOCK) > 100 &&
               !Inventory.contains(KARAMJAN_RUM) && !Inventory.contains(CHEST_KEY) &&
               (Equipment.contains(WHITE_APRON) || Inventory.contains(WHITE_APRON));
    }
    
    private boolean isAtKaramjaAndNeedToReturn() {
        return Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100 && 
               !needToGetRumAndBananas() && !needToSmuggleRum();
    }
    
    private boolean needToSmuggleRum() {
        // Only need to smuggle if we haven't smuggled yet AND we have the items AND we're on Karamja
        // CRITICAL FIX: Don't try to smuggle if we already completed smuggling (even if items are gone)
        return !hasSmuggledRum && Inventory.contains(KARAMJAN_RUM) && Inventory.count(BANANA) >= 10 &&
               Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100;
    }
    
    private boolean needToGetRumAndBananas() {
        // On Karamja: buy rum if not owned and collect bananas until 10
        return Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100 &&
               (!Inventory.contains(KARAMJAN_RUM) || Inventory.count(BANANA) < 10);
    }
    
    private boolean needToTravelToKaramja() {
        // Travel when quest started, we're not on Karamja, and we don't yet have rum/key/message/casket
        boolean questStarted = Quests.isStarted(FreeQuest.PIRATES_TREASURE);
        boolean onKaramja = Players.getLocal().getTile().distance(KARAMJA_DOCK) < 100;
        boolean haveProgressItems = Inventory.contains(KARAMJAN_RUM) || Inventory.contains(CHEST_KEY) ||
                                     Inventory.contains(PIRATE_MESSAGE) || Inventory.contains(CASKET);
        return questStarted && !onKaramja && !haveProgressItems;
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