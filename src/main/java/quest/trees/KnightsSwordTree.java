package quest.trees;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.bank.BankMode;
import org.dreambot.api.wrappers.items.Item;

import quest.core.QuestNode;
import quest.core.QuestTree;
import quest.nodes.ActionNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.RunEnergyUtil;

/**
 * The Knight's Sword - Tree-based implementation
 * Uses Quest API completion and config 122 for stage branching (discovered in logs).
 */
public class KnightsSwordTree extends QuestTree {

    // Quest config discovered via logger (Quest API): 122, completion observed at 7
    private static final int CONFIG_ID = 122;
    private static final int COMPLETE_VALUE = 7;

    // Items required
    private static final String REDBERRY_PIE = "Redberry pie";
    private static final String IRON_BAR = "Iron bar"; // need 2
    private static final String PICKAXE = "Bronze pickaxe"; // any pickaxe works; use bronze as cheap default
    private static final String BLURITE_ORE = "Blurite ore";

    // Key locations (from logs and wiki)
    private static final Tile FALADOR_SQUIRE_TILE = new Tile(2978, 3339, 0);
    private static final Tile VARROCK_RELDO_TILE = new Tile(3210, 3494, 0);
    private static final Tile THURGO_TILE = new Tile(3000, 3145, 0);
    private static final Tile TRAPDOOR_TILE = new Tile(3008, 3150, 0);

    // Upstairs navigation for portrait
    private static final Tile CASTLE_LADDER_GROUND = new Tile(2983, 3351, 0);
    private static final Tile CASTLE_DOOR_L1 = new Tile(2991, 3341, 1);
    private static final Tile CASTLE_STAIRS_L1 = new Tile(2984, 3337, 1);
    private static final Tile CASTLE_DOOR_L2 = new Tile(2982, 3337, 2);
    private static final Tile PORTRAIT_CUPBOARD = new Tile(2984, 3336, 2);

    // Ice dungeon waypoints / ladder back out
    private static final Tile ICE_DUNGEON_WAYPOINT_1 = new Tile(3010, 9578, 0);
    private static final Tile ICE_DUNGEON_WAYPOINT_2 = new Tile(3049, 9567, 0);
    private static final Tile ICE_DUNGEON_LADDER = new Tile(3008, 9550, 0);

    private QuestNode smart;
    private QuestNode ensureIronBarsNode;
    private boolean thurgoPieDelivered = false;

    public KnightsSwordTree() {
        super("The Knight's Sword");
    }

    @Override
    protected void buildTree() {
        // Early prep: ensure we have 2x unnoted Iron bars before anything else
        ensureIronBarsNode = new ActionNode("ensure_iron_bars", "Buy and prepare 2x Iron bar (unnoted)") {
            @Override
            protected boolean performAction() {
                // Step 1: If we already have 2 unnoted bars, done
                if (countUnnotedIronBars() >= 2) return true;

                // Step 2: Ensure total bars >= 2 by buying missing at GE
                int totalBars = Inventory.count(IRON_BAR);
                int needed = Math.max(0, 2 - totalBars);
                if (needed > 0) {
                    GrandExchangeUtil.buyItem(IRON_BAR, needed, GrandExchangeUtil.PriceStrategy.CONSERVATIVE);
                    Sleep.sleepUntil(() -> Inventory.count(IRON_BAR) >= totalBars + needed, 30000);
                }

                // Step 3: If unnoted still < 2, use bank to unnote (deposit then withdraw as item)
                if (countUnnotedIronBars() < 2 && Inventory.count(IRON_BAR) >= 2) {
                    if (!Bank.open()) return false;
                    Sleep.sleepUntil(Bank::isOpen, 8000);
                    if (!Bank.isOpen()) return false;
                    // Deposit all Iron bars first to normalize state
                    Bank.depositAll(IRON_BAR);
                    Sleep.sleep(600, 900);
                    // Ensure withdraw mode is ITEM (not noted)
                    if (Bank.getWithdrawMode() != BankMode.ITEM) {
                        Bank.setWithdrawMode(BankMode.ITEM);
                        Sleep.sleep(300, 500);
                    }
                    // Withdraw exactly 2 unnoted Iron bars
                    Bank.withdraw(IRON_BAR, 2);
                    Sleep.sleepUntil(() -> countUnnotedIronBars() >= 2, 5000);
                    Bank.close();
                    Sleep.sleep(400, 700);
                }

                return countUnnotedIronBars() >= 2;
            }
        };

        smart = new QuestNode("smart_decision", "Decide next step for The Knight's Sword") {
            @Override
            public ExecutionResult execute() {
                // Completion check first
                if (Quests.isFinished(FreeQuest.THE_KNIGHTS_SWORD) || PlayerSettings.getConfig(CONFIG_ID) >= COMPLETE_VALUE) {
                    return ExecutionResult.success(new ActionNode("finish", "Mark quest complete") {
                        @Override
                        protected boolean performAction() {
                            setQuestComplete();
                            return true;
                        }
                    }, "Quest complete");
                }
                
                // FINAL HAND-IN: If we have the Blurite sword, return to Squire to complete
                if (Inventory.contains("Blurite sword")) {
                    return ExecutionResult.success(talkToSquireNode(), "Return the Blurite sword to the Squire");
                }
                
                int cfg = PlayerSettings.getConfig(CONFIG_ID);
                boolean started = Quests.isStarted(FreeQuest.THE_KNIGHTS_SWORD) || cfg >= 1;
                
                // Only check iron bars if quest hasn't started yet (very beginning)
                if (!started && countUnnotedIronBars() < 2) {
                    return ExecutionResult.success(ensureIronBarsNode, "Buy and unnote 2x Iron bar early");
                }

                // Do not force baseline items unconditionally; handle per-stage below

                // Branch by config stage (from discovery log: 2→3→4→5→6→7)

                // FINAL HAND-IN: If we have the Blurite sword, return to Squire to complete
                if (Inventory.contains("Blurite sword")) {
                    return ExecutionResult.success(talkToSquireNode(), "Return the Blurite sword to the Squire");
                }
                // If quest not started at all, talk to Squire
                if (!started) {
                    return ExecutionResult.success(talkToSquireNode(), "Start the quest at the Squire");
                }

                // Stage ~1-2: After starting, talk to Reldo about Imcando dwarves, then proceed to Thurgo
                if (cfg <= 2) {
                    // Check if we've already talked to Reldo by looking at quest journal state
                    // If we have the Imcando information, we can skip Reldo
                    boolean hasImcandoInfo = hasImcandoInformation();
                    
                    if (!hasImcandoInfo) {
                        return ExecutionResult.success(talkToReldoNode(), "Consult Reldo in Varrock");
                    }
                    
                    // We have Imcando info, now check pie status
                    if (!thurgoPieDelivered) {
                        if (!Inventory.contains(REDBERRY_PIE)) {
                            return ExecutionResult.success(buyPieNode(), "Buy Redberry pie before visiting Thurgo");
                        }
                        return ExecutionResult.success(talkToThurgoGivePieNode(), "Give redberry pie to Thurgo");
                    }
                    // Pie already delivered, push dialogue progression with Squire if needed
                    return ExecutionResult.success(talkToSquireNode(), "Report back to Squire");
                }

                // Stage 3-4: Interact with Thurgo (give pie, ask to make sword)
                if (cfg == 3) {
                    return ExecutionResult.success(talkToThurgoGivePieNode(), "Give redberry pie to Thurgo");
                }
                if (cfg == 4) {
                    // Return to Squire to learn about the portrait
                    return ExecutionResult.success(talkToSquireNode(), "Report back to Squire");
                }

                // Stage 5: Obtain portrait upstairs
                if (cfg == 5 && !Inventory.contains("Portrait")) {
                    return ExecutionResult.success(obtainPortraitNode(), "Obtain Sir Vyvin's Portrait");
                }

                // After portrait: talk to Thurgo about the portrait (advances to 6)
                if (cfg == 5 && Inventory.contains("Portrait")) {
                    return ExecutionResult.success(talkToThurgoAboutPortraitNode(), "Give portrait to Thurgo");
                }

                // Stage 6: Mine blurite ore, then have Thurgo forge sword
                if (cfg == 6 && !Inventory.contains(BLURITE_ORE)) {
                    // Ensure we have a pickaxe before mining
                    if (!hasAnyPickaxe()) {
                        return ExecutionResult.success(buyPickaxeNode(), "Buy a pickaxe before mining");
                    }
                    return ExecutionResult.success(mineBluriteOreNode(), "Mine blurite ore in ice dungeon");
                }
                if (cfg == 6 && Inventory.contains(BLURITE_ORE)) {
                    // Ensure we have 2 iron bars before forging
                    // This should already be satisfied by early prep, but keep as safety net
                    if (countUnnotedIronBars() < 2) return ExecutionResult.success(ensureIronBarsNode, "Ensure 2x unnoted Iron bar before forging");
                    return ExecutionResult.success(talkToThurgoForgeNode(), "Have Thurgo forge the sword");
                }

                // Final hand-in to Squire (likely cfg becomes 7 on completion)
                return ExecutionResult.success(talkToSquireNode(), "Return to Squire to complete quest");
            }
        };

        this.rootNode = smart;
    }

    private int countUnnotedIronBars() {
        int count = 0;
        for (Item i : org.dreambot.api.methods.container.impl.Inventory.all()) {
            if (i != null && !i.isNoted() && IRON_BAR.equals(i.getName())) {
                count++;
            }
        }
        return count;
    }

    private boolean hasBasicItems() {
        boolean hasBars = Inventory.count(IRON_BAR) >= 2;
        boolean hasPick = Inventory.contains(PICKAXE) || hasAnyPickaxe();
        // Exclude pie from baseline items; handled per-stage
        return hasBars && hasPick;
    }

    private boolean hasAnyPickaxe() {
        String[] picks = new String[]{"Bronze pickaxe", "Iron pickaxe", "Steel pickaxe", "Black pickaxe", "Mithril pickaxe", "Adamant pickaxe", "Rune pickaxe"};
        for (String p : picks) if (Inventory.contains(p)) return true;
        return false;
    }
    
    /**
     * Check if we already have the Imcando dwarf information from Reldo.
     * This prevents the bot from talking to Reldo again after restart.
     */
    private boolean hasImcandoInformation() {
        // Method 1: Check if we have a redberry pie (indicates we know about Thurgo's preference)
        if (Inventory.contains(REDBERRY_PIE)) {
            return true;
        }
        
        // Method 2: Check if we've already progressed past the Reldo stage
        // If config > 2, we've definitely talked to Reldo
        int cfg = PlayerSettings.getConfig(CONFIG_ID);
        if (cfg > 2) {
            return true;
        }
        
        // Method 3: Check if we've already delivered pie to Thurgo
        if (thurgoPieDelivered) {
            return true;
        }
        
        // Method 4: Check if we're near Thurgo (indicates we've progressed past Reldo)
        if (Players.getLocal() != null && Players.getLocal().distance(THURGO_TILE) <= 20) {
            return true;
        }
        
        return false;
    }

    private QuestNode buyRequiredItemsNode() {
        return new ActionNode("buy_items", "Buy 2x Iron bar and a pickaxe") {
            @Override
            protected boolean performAction() {
                if (Inventory.count(IRON_BAR) < 2) {
                    int needed = 2 - Inventory.count(IRON_BAR);
                    GrandExchangeUtil.buyItem(IRON_BAR, needed, GrandExchangeUtil.PriceStrategy.CONSERVATIVE);
                    Sleep.sleepUntil(() -> Inventory.count(IRON_BAR) >= 2, 20000);
                }
                if (!hasAnyPickaxe()) {
                    GrandExchangeUtil.buyItem(PICKAXE, 1, GrandExchangeUtil.PriceStrategy.CONSERVATIVE);
                    Sleep.sleepUntil(() -> hasAnyPickaxe(), 20000);
                }
                return true;
            }
        };
    }

    private QuestNode buyIronBarsNode() {
        return new ActionNode("buy_iron_bars", "Buy 2x Iron bar") {
            @Override
            protected boolean performAction() {
                if (Inventory.count(IRON_BAR) < 2) {
                    int needed = 2 - Inventory.count(IRON_BAR);
                    GrandExchangeUtil.buyItem(IRON_BAR, needed, GrandExchangeUtil.PriceStrategy.CONSERVATIVE);
                    return Sleep.sleepUntil(() -> Inventory.count(IRON_BAR) >= 2, 20000);
                }
                return true;
            }
        };
    }

    private QuestNode buyPickaxeNode() {
        return new ActionNode("buy_pickaxe", "Buy a pickaxe") {
            @Override
            protected boolean performAction() {
                if (!hasAnyPickaxe()) {
                    GrandExchangeUtil.buyItem(PICKAXE, 1, GrandExchangeUtil.PriceStrategy.CONSERVATIVE);
                    return Sleep.sleepUntil(() -> hasAnyPickaxe(), 20000);
                }
                return true;
            }
        };
    }

    private QuestNode buyPieNode() {
        return new ActionNode("buy_pie", "Buy Redberry pie") {
            @Override
            protected boolean performAction() {
                GrandExchangeUtil.buyItem(REDBERRY_PIE, 1, GrandExchangeUtil.PriceStrategy.CONSERVATIVE);
                return Sleep.sleepUntil(() -> Inventory.contains(REDBERRY_PIE), 20000);
            }
        };
    }

    private QuestNode talkToSquireNode() {
        return new TalkToNPCNode("talk_squire", "Squire", FALADOR_SQUIRE_TILE);
    }

    private QuestNode talkToReldoNode() {
        return new ActionNode("talk_reldo", "Talk to Reldo about Imcando dwarves") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before walking to Reldo
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                NPC reldo = NPCs.closest("Reldo");
                if (reldo == null) {
                    if (Players.getLocal().distance(VARROCK_RELDO_TILE) > 6) {
                        // Manage run energy before walking
                        RunEnergyUtil.manageRunEnergy();
                        new WalkToLocationNode("walk_reldo", VARROCK_RELDO_TILE, "Reldo").execute();
                        reldo = NPCs.closest("Reldo");
                        if (reldo == null) return false;
                    } else {
                        return false;
                    }
                }
                if (!reldo.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;

                int startCfg = PlayerSettings.getConfig(CONFIG_ID);
                long start = System.currentTimeMillis();
                while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 15000) {
                    if (Dialogues.areOptionsAvailable()) {
                        String[] opts = Dialogues.getOptions();
                        // Select the exact needed option
                        int idx = indexOfOption(opts, "What do you know about Imcando dwarves?");
                        if (idx == -1) idx = indexOfOption(opts, "Imcando dwarves");
                        if (idx != -1) {
                            Dialogues.chooseOption(idx + 1);
                        } else {
                            // Fallback: continue
                            if (Dialogues.canContinue()) {
                                if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                            }
                        }
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    Sleep.sleep(400, 700);
                }

                // If config advanced, great; otherwise, allow next step to proceed
                int endCfg = PlayerSettings.getConfig(CONFIG_ID);
                return endCfg >= startCfg;
            }
        };
    }

    private QuestNode talkToThurgoGivePieNode() {
        return new ActionNode("thurgo_pie", "Talk to Thurgo and give pie") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before walking to Thurgo
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Walk near Thurgo first for reliability
                if (Players.getLocal().distance(THURGO_TILE) > 6) {
                    // Manage run energy before walking
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_thurgo", THURGO_TILE, "Thurgo").execute();
                }
                NPC thurgo = NPCs.closest("Thurgo");
                if (thurgo == null) return false;
                int pieBefore = Inventory.count(REDBERRY_PIE);
                if (!thurgo.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                long start = System.currentTimeMillis();
                int guard = 0;
                while (Dialogues.inDialogue() && guard++ < 30) {
                    if (Dialogues.areOptionsAvailable()) {
                        // Pick any option mentioning pie or help/sword
                        String[] opts = Dialogues.getOptions();
                        int idx = indexOfOption(opts, "redberry pie", "sword", "help");
                        if (idx != -1) Dialogues.chooseOption(idx + 1);
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    Sleep.sleep(600, 900);
                }
                Sleep.sleep(600, 900);
                boolean pieConsumed = Inventory.count(REDBERRY_PIE) < pieBefore;
                if (pieConsumed) {
                    thurgoPieDelivered = true;
                }
                // Consider success if pie is consumed or config progressed
                int cfg = PlayerSettings.getConfig(CONFIG_ID);
                return pieConsumed || cfg >= 3;
            }
        };
    }

    private QuestNode obtainPortraitNode() {
        return new ActionNode("get_portrait", "Obtain Sir Vyvin's portrait upstairs") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before castle navigation
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Enter castle and go upstairs to floor 1
                if (Players.getLocal().getTile().getZ() == 0) {
                    if (!new InteractWithObjectNode("climb_ladder", "Ladder", "Climb-up", CASTLE_LADDER_GROUND, "Castle ladder").execute().isSuccess()) {
                        return false;
                    }
                }
                // On level 1: open door to staircase area
                new InteractWithObjectNode("open_l1_door", "Door", "Open", CASTLE_DOOR_L1, "Level 1 door").execute();
                // Climb-up to level 2
                if (!new InteractWithObjectNode("climb_stairs", "Staircase", "Climb-up", CASTLE_STAIRS_L1, "Level 1 staircase").execute().isSuccess()) {
                    return false;
                }
                // Open level 2 door to Vyvin's room
                new InteractWithObjectNode("open_l2_door", "Door", "Open", CASTLE_DOOR_L2, "Level 2 door").execute();
                // Open and search cupboard for portrait
                if (!Inventory.contains("Portrait")) {
                    new InteractWithObjectNode("open_cupboard", "Cupboard", "Open", PORTRAIT_CUPBOARD, "Vyvin's cupboard").execute();
                    boolean searched = new InteractWithObjectNode("search_cupboard", "Cupboard", "Search", PORTRAIT_CUPBOARD, "Vyvin's cupboard").execute().isSuccess();
                    if (!searched) return false;
                    Sleep.sleepUntil(() -> Inventory.contains("Portrait"), 5000);
                }
                return Inventory.contains("Portrait");
            }
        };
    }

    private QuestNode talkToThurgoAboutPortraitNode() {
        return new TalkToNPCNode("thurgo_portrait", "Thurgo", THURGO_TILE);
    }

    private QuestNode mineBluriteOreNode() {
        return new ActionNode("mine_blurite", "Mine blurite ore in Asgarnian Ice Dungeon") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before ice dungeon navigation
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Ensure near trapdoor and go down
                if (Players.getLocal().getTile().getZ() == 0) {
                    if (Players.getLocal().distance(TRAPDOOR_TILE) > 5) {
                        // Manage run energy before walking to trapdoor
                        RunEnergyUtil.manageRunEnergy();
                        new WalkToLocationNode("walk_trapdoor", TRAPDOOR_TILE, "Trapdoor").execute();
                    }
                    new InteractWithObjectNode("climb_down_trapdoor", "Trapdoor", "Climb-down", TRAPDOOR_TILE, "Trapdoor").execute();
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 0 && Players.getLocal().getY() > 9000, 6000);
                }

                // Navigate deeper in dungeon
                new WalkToLocationNode("ice_wp1", ICE_DUNGEON_WAYPOINT_1, 5, "Ice cavern waypoint 1").execute();
                new WalkToLocationNode("ice_wp2", ICE_DUNGEON_WAYPOINT_2, 5, "Blurite rocks area").execute();

                // Mine until we have blurite ore with proper wait for inventory change
                int before = Inventory.count(BLURITE_ORE);
                int attempts = 0;
                while (Inventory.count(BLURITE_ORE) <= before && attempts++ < 8) {
                    GameObject rocks = GameObjects.closest("Blurite rocks");
                    if (rocks == null) rocks = GameObjects.closest("Rocks");
                    if (rocks != null && rocks.interact("Mine")) {
                        // Wait for either inventory to increase or animation to finish
                        boolean gained = Sleep.sleepUntil(() -> Inventory.count(BLURITE_ORE) > before, 15000);
                        if (gained) break;
                        // If not gained, small pause then retry
                        Sleep.sleep(600, 900);
                    } else {
                        Sleep.sleep(800, 1200);
                    }
                }

                // Climb back up
                new WalkToLocationNode("exit_wp", ICE_DUNGEON_LADDER, 6, "Exit ladder").execute();
                new InteractWithObjectNode("climb_up_ladder", "Ladder", "Climb-up", ICE_DUNGEON_LADDER, "Exit ladder").execute();
                Sleep.sleep(600, 900);
                return Inventory.count(BLURITE_ORE) > before;
            }
        };
    }

    private QuestNode talkToThurgoForgeNode() {
        return new ActionNode("thurgo_forge", "Have Thurgo forge the replacement sword") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before final Thurgo visit
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                if (Players.getLocal().distance(THURGO_TILE) > 6) {
                    // Manage run energy before walking
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_thurgo_again", THURGO_TILE, "Thurgo").execute();
                }
                NPC thurgo = NPCs.closest("Thurgo");
                if (thurgo == null) return false;
                if (!thurgo.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                int guard = 0;
                while (Dialogues.inDialogue() && guard++ < 30) {
                    if (Dialogues.areOptionsAvailable()) {
                        String[] opts = Dialogues.getOptions();
                        int idx = indexOfOption(opts, "make", "replacement", "sword");
                        if (idx != -1) Dialogues.chooseOption(idx + 1);
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    Sleep.sleep(600, 900);
                }
                Sleep.sleep(800, 1200);
                return true;
            }
        };
    }

    private int indexOfOption(String[] options, String... needles) {
        if (options == null) return -1;
        for (int i = 0; i < options.length; i++) {
            String opt = options[i];
            for (String n : needles) {
                if (opt != null && opt.toLowerCase().contains(n.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean isQuestComplete() {
        return Quests.isFinished(FreeQuest.THE_KNIGHTS_SWORD) || PlayerSettings.getConfig(CONFIG_ID) >= COMPLETE_VALUE || super.isQuestComplete();
    }

    @Override
    public int getQuestProgress() {
        if (isQuestComplete()) return 100;
        int v = PlayerSettings.getConfig(CONFIG_ID);
        // Rough mapping for UI
        if (v <= 1) return 5;    // not started
        if (v == 2) return 15;   // started, early dialogues
        if (v == 3) return 30;   // gave pie
        if (v == 4) return 45;   // asked to make sword
        if (v == 5) return 65;   // getting portrait
        if (v == 6) return 85;   // mining blurite / forging
        return 90;
    }
}
