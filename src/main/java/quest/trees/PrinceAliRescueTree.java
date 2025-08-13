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
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.items.Item;

import quest.core.QuestNode;
import quest.core.QuestTree;
import quest.nodes.ActionNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.UseItemOnNPCNode;
import quest.nodes.actions.UseItemOnObjectNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.ItemGatheringUtil;
import quest.utils.QuestLogger;

/**
 * Prince Ali Rescue - Tree implementation based on discovery log.
 * Prefers Quest API; uses config 273 from QuestEventLogger as fallback progress.
 */
public class PrinceAliRescueTree extends QuestTree {

    // Quest config discovered in logger
    private static final int CONFIG_ID = 273; // stages vary; completion via API

    // Key tiles
    private static final Tile HASSAN_TILE = new Tile(3302, 3160, 0);
    private static final Tile OSMAN_TILE = new Tile(3289, 3181, 0);
    private static final Tile NED_TILE = new Tile(3098, 3258, 0);
    private static final Tile AGGIE_TILE = new Tile(3087, 3259, 0);
    private static final Tile LEELA_TILE = new Tile(3113, 3260, 0);
    private static final Tile LADY_KELI_TILE = new Tile(3128, 3246, 0);
    private static final Tile PRISON_GATE_TILE = new Tile(3123, 3244, 0);

    // Items
    private static final String WIG = "Wig";
    private static final String YELLOW_WIG = "Yellow wig";
    private static final String YELLOW_DYE = "Yellow dye";
    private static final String PASTE = "Skin paste";
    private static final String ROPE = "Rope";
    private static final String BEER = "Beer";
    private static final String PINK_SKIRT = "Pink skirt";
    private static final String BRONZE_KEY = "Bronze key";
    private static final String BALL_OF_WOOL = "Ball of wool";
    private static final String REDBERRIES = "Redberries";
    private static final String ASHES = "Ashes";
    private static final String BUCKET_OF_WATER = "Bucket of water";
    private static final String SOFT_CLAY = "Soft clay";
    private static final String BRONZE_BAR = "Bronze bar";
    private static final String KEY_PRINT = "Key print"; // soft clay imprint item after talking to Keli
    private static final String KEY_MOULD = "Key mould"; // some clients may use this name

    private QuestNode smart;
    // Global flags to avoid re-instantiation loops
    private static volatile boolean OSMAN_DONE = false;
    private static volatile boolean LEELA_DONE = false;
    private static volatile long LAST_OSMAN_TALK = 0L;
    private static volatile long LAST_LEELA_TALK = 0L;
    // Session flags to enforce dialogue order
    // After handing the imprint + bronze bar to Osman, we should NOT try to re-acquire them.
    // Track this explicitly so the decision logic prefers collecting the forged key from Leela.
    private static volatile boolean AWAITING_LEELA_KEY = false;
    private static volatile long LAST_IMPRINT_DELIVERY_MS = 0L;
    // Retry/cooldown for Leela collection attempts (resumable-safe)
    private static volatile long LAST_LEELA_COLLECTION_ATTEMPT_MS = 0L;
    private static volatile int LEELA_COLLECTION_ATTEMPTS = 0;
    // Guard management
    private static volatile boolean JOE_HANDLED = false;
    private boolean spokeOsman = false;
    private boolean spokeLeela = false;
    private int osmanAttempts = 0;
    private int leelaAttempts = 0;
    private long lastOsmanTalkMs = 0L;
    private long lastLeelaTalkMs = 0L;
    private boolean triedOsmanKeyPickup = false;

    public PrinceAliRescueTree() {
        super("Prince Ali Rescue");
    // Reset static progress flags for a fresh run of the tree
    OSMAN_DONE = false;
    LEELA_DONE = false;
    AWAITING_LEELA_KEY = false;
    LAST_IMPRINT_DELIVERY_MS = 0L;
    LAST_LEELA_COLLECTION_ATTEMPT_MS = 0L;
    LEELA_COLLECTION_ATTEMPTS = 0;
    spokeOsman = false;
    spokeLeela = false;
    osmanAttempts = 0;
    leelaAttempts = 0;
    lastOsmanTalkMs = 0L;
    lastLeelaTalkMs = 0L;
    triedOsmanKeyPickup = false;
    }

    @Override
    protected void buildTree() {
    smart = new QuestNode("smart_par", "Decide next step for Prince Ali Rescue") {
            @Override
            public ExecutionResult execute() {
        // Snapshot state for decisions
        boolean hasKey = hasBronzeKey();
        boolean hasPrint = hasKeyPrint();
        boolean hasBar = Inventory.contains(BRONZE_BAR);
        boolean keliReady = hasKeliPrereqs();
        QuestLogger.getInstance().log("STATE: key=" + hasKey + ", print=" + hasPrint + ", bar=" + hasBar + ", keliReady=" + keliReady);
                // Completion using API
                if (Quests.isFinished(FreeQuest.PRINCE_ALI_RESCUE)) {
                    return ExecutionResult.success(new ActionNode("finish", "Mark quest complete") {
                        @Override
                        protected boolean performAction() { setQuestComplete(); return true; }
                    }, "Quest complete");
                }

                // Always start the quest first before gathering items
                if (!Quests.isStarted(FreeQuest.PRINCE_ALI_RESCUE)) {
                    return ExecutionResult.success(new TalkToNPCNode("hassan", "Chancellor Hassan", HASSAN_TILE), "Start with Hassan");
                }

                // STATE-DRIVEN OBJECTIVES (resumable at any point)

                // Derive delivery/awaiting state snapshots
                boolean awaitingLeelaKey = AWAITING_LEELA_KEY;
                long sinceDeliveryMs = LAST_IMPRINT_DELIVERY_MS == 0L ? Long.MAX_VALUE : (System.currentTimeMillis() - LAST_IMPRINT_DELIVERY_MS);

                // 1) If we already have the bronze key, follow prison sequence:
                //    - Get Joe drunk (3 beers)
                //    - Tie Keli with rope
                //    - Unlock door and free Prince Ali
                if (hasKey) {
                    QuestLogger.getInstance().log("Proceed: Have Bronze key -> handle prison sequence (Joe -> Keli -> Door -> Ali)");
                    if (!JOE_HANDLED) {
                        return ExecutionResult.success(getJoeDrunkNode(), "Get Joe drunk with beers");
                    }
                    if (Inventory.contains(ROPE)) {
                        return ExecutionResult.success(tieKeliNode(), "Walk and tie up Lady Keli");
                    }
                    return ExecutionResult.success(unlockAndFreePrinceNode(), "Unlock and free the prince");
                }

                // 1a) Resumable-safe: if we have no print, no bar, and no key, try collecting from Leela first
                // (even if we didn't run the Osman delivery in this session). Cooldown and limited retries prevent loops.
                if (!hasKey && !hasPrint && !hasBar) {
                    long now = System.currentTimeMillis();
                    boolean cooled = (now - LAST_LEELA_COLLECTION_ATTEMPT_MS) > 15000; // 15s cooldown
                    if (LEELA_COLLECTION_ATTEMPTS < 3 && cooled) {
                        QuestLogger.getInstance().log("Proceed: No print/bar; attempting Leela key collection (resumable-safe)");
                        AWAITING_LEELA_KEY = true; // prevent any GE buys while we try to collect
                        return ExecutionResult.success(collectKeyFromLeelaNode(), "Collect forged key from Leela");
                    }
                }

                // 1b) If we already delivered the imprint + bar to Osman, do NOT try to re-acquire them.
                // Prefer collecting the forged key from Leela.
                if (!hasKey && (awaitingLeelaKey || OSMAN_DONE || sinceDeliveryMs < 60000)) {
                    QuestLogger.getInstance().log("Proceed: Awaiting forged key -> collect from Leela");
                    return ExecutionResult.success(collectKeyFromLeelaNode(), "Collect forged key from Leela");
                }

                // 2) If we have a key print and a bronze bar, deliver them to Osman to obtain the bronze key
                if (hasPrint && hasBar) {
                    QuestLogger.getInstance().log("Proceed: Deliver imprint + bar to Osman");
                    return ExecutionResult.success(deliverImprintToOsmanNode(), "Deliver imprint to Osman for bronze key");
                }

                // 3) If we lack the key print, go to Keli and get the imprint (requires disguise + soft clay)
                if (!hasPrint) {
                    if (!keliReady) {
                        QuestLogger.getInstance().log("Prepare items: missing Keli prerequisites");
                        return ExecutionResult.success(prepareItemsNode(), "Prepare disguise items and tools");
                    }
                    if (Players.getLocal().distance(LADY_KELI_TILE) > 8) {
                        QuestLogger.getInstance().log("Walk: heading to Lady Keli");
                        return ExecutionResult.success(new WalkToLocationNode("walk_keli", LADY_KELI_TILE, "Lady Keli"), "Walk to Lady Keli");
                    }
                    QuestLogger.getInstance().log("Proceed: Talk to Keli to make key imprint");
                    return ExecutionResult.success(talkToKeliForKeyImprintNode(), "Charm Keli and make key imprint");
                }

                // 4) If we have a key print but are missing a Bronze bar, acquire it and then deliver to Osman
                if (hasPrint && !hasBar) {
                    QuestLogger.getInstance().log("Acquire: Need Bronze bar before delivering imprint");
                    return ExecutionResult.success(ensureBronzeBarNode(), "Obtain Bronze bar for key forging");
                }

                // 5) If imprint delivered (print and bar no longer in inv) but no key yet, collect key from Leela
                if (!hasPrint && !hasBar && !hasKey && (awaitingLeelaKey || OSMAN_DONE)) {
                    QuestLogger.getInstance().log("Collect: Get forged key from Leela (post-delivery)");
                    return ExecutionResult.success(collectKeyFromLeelaNode(), "Collect forged key from Leela");
                }

                // Default: deliver imprint to Osman if present; otherwise prepare items
                if (hasPrint) {
                    QuestLogger.getInstance().log("Fallback: Deliver imprint to Osman");
                    return ExecutionResult.success(deliverImprintToOsmanNode(), "Deliver imprint to Osman for bronze key");
                }
                QuestLogger.getInstance().log("Fallback: Prepare items");
                return ExecutionResult.success(prepareItemsNode(), "Prepare disguise items and tools");
            }
        };

        this.rootNode = smart;
    }

    private QuestNode ensureBronzeBarNode() {
        return new ActionNode("ensure_bronze_bar", "Buy a Bronze bar if missing") {
            @Override
            protected boolean performAction() {
                // If we've already handed the imprint + bar to Osman, don't try to re-buy
                if (AWAITING_LEELA_KEY || OSMAN_DONE) {
                    return true;
                }
                if (Inventory.contains(BRONZE_BAR)) return true;
                GrandExchangeUtil.buyItems(new GrandExchangeUtil.ItemRequest(BRONZE_BAR, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                Sleep.sleep(600, 1000);
                return Inventory.contains(BRONZE_BAR);
            }
        };
    }

    // Note: We only interact with Osman via deliverImprintToOsmanNode()

    private QuestNode talkToLeelaNode() {
        return new ActionNode("talk_leela_wrap", "Talk to Leela and set flag") {
            @Override
            protected boolean performAction() {
                // Cooldown to avoid loops
                if (System.currentTimeMillis() - lastLeelaTalkMs < 8000) {
                    spokeLeela = true;
                    LEELA_DONE = true; // treat as completed to advance
                    return true;
                }
                leelaAttempts++;
                new WalkToLocationNode("walk_leela", LEELA_TILE, "Leela").execute();
                NPC leela = NPCs.closest("Leela");
                if (leela == null || !leela.interact("Talk-to")) {
                    Sleep.sleep(400, 800);
                    if (leelaAttempts >= 3) { spokeLeela = true; LEELA_DONE = true; return true; }
                    return false;
                }
                if (Sleep.sleepUntil(Dialogues::inDialogue, 7000)) {
                    // Mark as done once dialogue opens
                    spokeLeela = true;
                    LEELA_DONE = true;
                    lastLeelaTalkMs = System.currentTimeMillis();
                    long start = System.currentTimeMillis();
                    while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 15000) {
                        if (Dialogues.canContinue()) {
                            if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                        } else if (Dialogues.areOptionsAvailable()) {
                            Dialogues.chooseOption(1); // take first sensible option
                        }
                        Sleep.sleep(350, 600);
                    }
                } else {
                    if (leelaAttempts >= 3) { spokeLeela = true; LEELA_DONE = true; return true; }
                    return false;
                }
                Sleep.sleep(300, 600);
                // Mark Leela step complete so the smart node can advance
                LEELA_DONE = true;
                return true;
            }
        };
    }

    private boolean hasAllRequiredItemsPreBreakIn() {
    boolean hasWig = hasDisguiseWig();
    boolean hasPasteOrIngredients = Inventory.contains(PASTE)
        || (Inventory.contains(REDBERRIES) && Inventory.contains(ASHES) && Inventory.contains("Pot of flour") && Inventory.contains(BUCKET_OF_WATER));
    boolean hasRope = Inventory.contains(ROPE);
    boolean hasSkirt = Inventory.contains(PINK_SKIRT);
    boolean hasBeerQty = Inventory.count(BEER) >= 3;
    boolean hasClay = Inventory.contains(SOFT_CLAY);
    boolean hasBronzeBar = Inventory.contains(BRONZE_BAR);
    return hasWig && hasPasteOrIngredients && hasRope && hasSkirt && hasBeerQty && hasClay && hasBronzeBar;
    }

    private boolean hasDisguiseWig() {
    // Only consider the disguise complete when the final Yellow wig is present
    return Inventory.contains(YELLOW_WIG);
    }

    private boolean hasKeyPrint() {
        return Inventory.contains(KEY_PRINT) || Inventory.contains(KEY_MOULD);
    }

    private boolean hasBronzeKey() {
        return Inventory.contains(BRONZE_KEY);
    }

    private boolean hasKeliPrereqs() {
        // Minimal prerequisite to create an imprint is Soft clay; disguise is useful later but not required to talk to Keli
        return Inventory.contains(SOFT_CLAY);
    }

    private QuestNode prepareItemsNode() {
        return new ActionNode("prepare_items", "Acquire/craft disguise items and tools") {
            @Override
            protected boolean performAction() {
                // If we've already delivered imprint + bar, skip item prep and go collect the key
                if (AWAITING_LEELA_KEY || OSMAN_DONE) {
                    return true;
                }
                // Build batch purchase list and keep GE open during all purchases
                java.util.List<GrandExchangeUtil.ItemRequest> list = new java.util.ArrayList<>();
                // Beer: ensure 3 total
                int needBeer = Math.max(0, 3 - Inventory.count(BEER));
                if (needBeer > 0) list.add(new GrandExchangeUtil.ItemRequest(BEER, needBeer, GrandExchangeUtil.PriceStrategy.FIXED_500_GP));
                // Ball of wool: ensure 3 to craft wig
                int needWool = 0;
                if (!Inventory.contains(WIG) && !Inventory.contains(YELLOW_WIG)) {
                    needWool = Math.max(0, 3 - Inventory.count(BALL_OF_WOOL));
                    if (needWool > 0) list.add(new GrandExchangeUtil.ItemRequest(BALL_OF_WOOL, needWool, GrandExchangeUtil.PriceStrategy.INSTANT));
                }
                if (!Inventory.contains(PINK_SKIRT)) list.add(new GrandExchangeUtil.ItemRequest(PINK_SKIRT, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                if (!Inventory.contains(ROPE)) list.add(new GrandExchangeUtil.ItemRequest(ROPE, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                if (!Inventory.contains(SOFT_CLAY)) list.add(new GrandExchangeUtil.ItemRequest(SOFT_CLAY, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                // Only buy dye if we still need a Yellow wig AND we have a plain wig to dye
                if (!Inventory.contains(YELLOW_WIG) && !Inventory.contains(YELLOW_DYE) && Inventory.contains(WIG)) {
                    list.add(new GrandExchangeUtil.ItemRequest(YELLOW_DYE, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                }
                if (!Inventory.contains(BRONZE_BAR)) list.add(new GrandExchangeUtil.ItemRequest(BRONZE_BAR, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                // Paste ingredients if we don't already have the paste
                if (!Inventory.contains(PASTE)) {
                    if (!Inventory.contains(REDBERRIES)) list.add(new GrandExchangeUtil.ItemRequest(REDBERRIES, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                    if (!Inventory.contains(ASHES)) list.add(new GrandExchangeUtil.ItemRequest(ASHES, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                    if (!Inventory.contains("Pot of flour")) list.add(new GrandExchangeUtil.ItemRequest("Pot of flour", 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                    if (!Inventory.contains(BUCKET_OF_WATER)) list.add(new GrandExchangeUtil.ItemRequest(BUCKET_OF_WATER, 1, GrandExchangeUtil.PriceStrategy.INSTANT));
                }

                if (!list.isEmpty()) {
                    boolean boughtAll = GrandExchangeUtil.buyItems(list.toArray(new GrandExchangeUtil.ItemRequest[0]));
                    if (!boughtAll) {
                        // If we didn't get everything yet, wait briefly and retry on next tick
                        Sleep.sleep(800, 1400);
                        return false;
                    }
                    Sleep.sleep(800, 1400);
                }

                // Unnote required items (Beer x3 and Ball of wool x3) if they arrived noted
                ItemGatheringUtil.ensureUnnotedInInventory(BEER, 3);
                if (needWool > 0) {
                    ItemGatheringUtil.ensureUnnotedInInventory(BALL_OF_WOOL, 3);
                }

                // Wig: craft via Ned if missing
                if (!Inventory.contains(WIG) && !Inventory.contains(YELLOW_WIG)) {
                    if (Players.getLocal().distance(NED_TILE) > 8) {
                        new WalkToLocationNode("walk_ned", NED_TILE, "Ned").execute();
                    }
                    NPC ned = NPCs.closest("Ned");
                    if (ned != null && ned.interact("Talk-to")) {
                        if (Sleep.sleepUntil(Dialogues::inDialogue, 7000)) {
                            long start = System.currentTimeMillis();
                            while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 20000) {
                                if (Dialogues.areOptionsAvailable()) {
                                    String[] opts = Dialogues.getOptions();
                                    // Prefer exact path from manual recording, else choose the first option
                                    int idx = indexOfOption(opts,
                                        "Could you make other things apart from rope?",
                                        "other things apart from rope",
                                        "How about some sort of wig?",
                                        "some sort of wig",
                                        "I have them here. Please make me a wig.",
                                        "I have them here",
                                        "make me a wig",
                                        "wig");
                                    if (idx == -1) idx = 0; // default to FIRST option
                                    Dialogues.chooseOption(idx + 1);
                                } else if (Dialogues.canContinue()) {
                                    if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                                }
                                Sleep.sleep(350, 600);
                            }
                        }
                    }
                    Sleep.sleepUntil(() -> Inventory.contains(WIG), 8000);
                }

                // Paste: ask Aggie to make it if missing
                if (!Inventory.contains(PASTE)) {
                    if (Players.getLocal().distance(AGGIE_TILE) > 8) {
                        new WalkToLocationNode("walk_aggie", AGGIE_TILE, "Aggie").execute();
                    }
                    NPC aggie = NPCs.closest("Aggie");
                    if (aggie != null && aggie.interact("Talk-to")) {
                        if (Sleep.sleepUntil(Dialogues::inDialogue, 7000)) {
                            long start = System.currentTimeMillis();
                            while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 20000) {
                                if (Dialogues.areOptionsAvailable()) {
                                    String[] opts = Dialogues.getOptions();
                                    // Follow recorded path: Ask for skin paste, then accept making it
                                    int idx = indexOfOption(opts,
                                        "Can you make skin paste?",
                                        "skin paste",
                                        "Yes please. Mix me some skin paste.",
                                        "Yes please");
                                    if (idx == -1) idx = 0; // default to FIRST option
                                    Dialogues.chooseOption(idx + 1);
                                } else if (Dialogues.canContinue()) {
                                    if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                                }
                                Sleep.sleep(350, 600);
                            }
                            Sleep.sleep(500, 800);
                        }
                    }
                    Sleep.sleepUntil(() -> Inventory.contains(PASTE), 8000);
                }

                // Dye the wig if we have dye and plain wig
                if (Inventory.contains(YELLOW_DYE) && Inventory.contains(WIG) && !Inventory.contains(YELLOW_WIG)) {
                    Item dye = Inventory.get(YELLOW_DYE);
                    Item wig = Inventory.get(WIG);
                    if (dye != null && wig != null) {
                        if (dye.useOn(wig)) {
                            // Wait until the Yellow wig is present (avoid racing on dye consumption)
                            Sleep.sleepUntil(() -> Inventory.contains(YELLOW_WIG), 7000);
                        }
                    }
                }
                return true;
            }
        };
    }

    private QuestNode deliverImprintToOsmanNode() {
        return new ActionNode("deliver_imprint", "Give Osman the key print and bronze bar") {
            @Override
            protected boolean performAction() {
                // Walk to Osman
                new WalkToLocationNode("walk_osman_deliver", OSMAN_TILE, "Osman").execute();
                NPC osman = NPCs.closest("Osman");
                if (osman == null) return false;
                // Ensure we have required items before talking
                if (!hasKeyPrint() || !Inventory.contains(BRONZE_BAR)) return true; // nothing to do
                if (!osman.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                long start = System.currentTimeMillis();
                int guard = 0;
                while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 20000 && guard++ < 50) {
                    if (Dialogues.areOptionsAvailable()) {
                        // Choose options likely to hand over items / progress
                        Dialogues.chooseOption(1);
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    Sleep.sleep(350, 600);
                }
                // After delivery, Osman arranges the key via Leela; advance without waiting for key in inventory
                OSMAN_DONE = true;
                AWAITING_LEELA_KEY = true;
                LAST_IMPRINT_DELIVERY_MS = System.currentTimeMillis();
                return true;
            }
        };
    }

    private QuestNode collectKeyFromLeelaNode() {
        return new ActionNode("collect_key_leela", "Collect the bronze key from Leela") {
            @Override
            protected boolean performAction() {
                // Mark attempt and hold item-gathering while we try to collect
                AWAITING_LEELA_KEY = true;
                LAST_LEELA_COLLECTION_ATTEMPT_MS = System.currentTimeMillis();
                LEELA_COLLECTION_ATTEMPTS++;

                new WalkToLocationNode("walk_leela_getkey", LEELA_TILE, "Leela").execute();
                NPC leela = NPCs.closest("Leela");
                if (leela == null) return false;
                if (!leela.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                long start = System.currentTimeMillis();
                while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 20000) {
                    if (Dialogues.areOptionsAvailable()) {
                        // Quick guide requires selecting option 2 to receive the duplicate key
                        Dialogues.chooseOption(2);
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    Sleep.sleep(350, 600);
                }
                // Wait briefly for the key to be added
                boolean gotKey = Sleep.sleepUntil(() -> Inventory.contains(BRONZE_KEY), 7000);
                if (gotKey) {
                    LEELA_DONE = true;
                    AWAITING_LEELA_KEY = false;
                    // Reset attempts after success
                    LEELA_COLLECTION_ATTEMPTS = 0;
                    LAST_LEELA_COLLECTION_ATTEMPT_MS = 0L;
                    QuestLogger.getInstance().log("SUCCESS: Received Bronze key from Leela");
                } else {
                    // If we've tried a few times, let fallback logic proceed on later loops
                    if (LEELA_COLLECTION_ATTEMPTS >= 3) {
                        AWAITING_LEELA_KEY = false;
                        QuestLogger.getInstance().log("WARNING: Leela key collection failed after retries; allowing fallback flow");
                    } else {
                        QuestLogger.getInstance().log("Retry: Leela key collection attempt did not yield key; will retry after cooldown");
                    }
                }
                return true;
            }
        };
    }

    private QuestNode talkToKeliForKeyImprintNode() {
        return new ActionNode("keli_key", "Talk to Keli and get key imprint") {
            @Override
            protected boolean performAction() {
                NPC keli = NPCs.closest("Lady Keli");
                if (keli == null) {
                    new WalkToLocationNode("walk_keli2", LADY_KELI_TILE, "Lady Keli").execute();
                    keli = NPCs.closest("Lady Keli");
                    if (keli == null) return false;
                }
                if (!keli.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                long start = System.currentTimeMillis();
                int guard = 0;
                while (Dialogues.inDialogue() && (System.currentTimeMillis() - start < 20000) && guard++ < 40) {
                    if (Dialogues.areOptionsAvailable()) {
                        String[] opts = Dialogues.getOptions();
                        // Choose flattering/conversation options leading to seeing/touching key
                        int idx = indexOfOption(opts, "I've heard of you", "famous", "escape", "key", "touch the key");
                        if (idx == -1) idx = 1;
                        Dialogues.chooseOption(idx + 1);
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    Sleep.sleep(500, 800);
                }
                // If imprint succeeded and Osman provided key, Bronze key may be in inventory now; otherwise Leela/Osman path handles it later
                Sleep.sleep(800, 1200);
                return true;
            }
        };
    }

    private QuestNode unlockAndFreePrinceNode() {
        return new ActionNode("unlock_and_free", "Unlock gate and free Prince Ali") {
            @Override
            protected boolean performAction() {
                // Use key on gate
                boolean used = new UseItemOnObjectNode("use_key_gate", BRONZE_KEY, "Prison Gate").execute().isSuccess();
                Sleep.sleep(400, 800);
                // Talk to Prince Ali
                NPC prince = NPCs.closest("Prince Ali");
                if (prince == null) {
                    new WalkToLocationNode("walk_gate", PRISON_GATE_TILE, "Prison Gate").execute();
                    prince = NPCs.closest("Prince Ali");
                }
                if (prince != null && prince.interact("Talk-to")) {
                    Sleep.sleepUntil(Dialogues::inDialogue, 7000);
                    long start = System.currentTimeMillis();
                    while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 15000) {
                        if (Dialogues.canContinue()) {
                            if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                        } else if (Dialogues.areOptionsAvailable()) {
                            Dialogues.chooseOption(1);
                        }
                        Sleep.sleep(500, 800);
                    }
                }
                // After freeing, return to Hassan to complete
                new TalkToNPCNode("return_hassan", "Chancellor Hassan", HASSAN_TILE).execute();
                // Wait for quest completion and mark
                Sleep.sleepUntil(() -> Quests.isFinished(FreeQuest.PRINCE_ALI_RESCUE), 10000);
                if (Quests.isFinished(FreeQuest.PRINCE_ALI_RESCUE)) {
                    setQuestComplete();
                }
                return true;
            }
        };
    }

    // Walk to Lady Keli first, then use rope on her
    private QuestNode tieKeliNode() {
        return new ActionNode("tie_keli", "Walk to Lady Keli and tie her with rope") {
            @Override
            protected boolean performAction() {
                if (Players.getLocal().distance(LADY_KELI_TILE) > 6) {
                    new WalkToLocationNode("walk_to_keli_for_rope", LADY_KELI_TILE, "Lady Keli").execute();
                    Sleep.sleep(300, 600);
                }
                NPC keli = NPCs.closest("Lady Keli");
                if (keli == null) {
                    new WalkToLocationNode("walk_to_keli_retry", LADY_KELI_TILE, "Lady Keli").execute();
                    keli = NPCs.closest("Lady Keli");
                }
                if (keli == null) return false;
                // Use rope on Lady Keli
                return new UseItemOnNPCNode("use_rope_on_keli", ROPE, "Lady Keli").execute().isSuccess();
            }
        };
    }

    // Get Joe drunk with 3 beers so he leaves the post
    private QuestNode getJoeDrunkNode() {
        return new ActionNode("joe_beers", "Give Joe 3 beers to get him drunk") {
            @Override
            protected boolean performAction() {
                // Ensure we are at the prison area first
                if (Players.getLocal().distance(LADY_KELI_TILE) > 12) {
                    new WalkToLocationNode("walk_to_prison_area", LADY_KELI_TILE, "Lady Keli").execute();
                    Sleep.sleep(300, 600);
                }
                NPC joe = NPCs.closest("Joe");
                if (joe == null) {
                    // Joe is inside the prison area; walking to the gate helps respawn/path
                    new WalkToLocationNode("walk_to_gate_for_joe", PRISON_GATE_TILE, "Prison Gate").execute();
                    joe = NPCs.closest("Joe");
                }
                if (joe == null) return false;
                // Talk to Joe and hand over beers; typically multiple interactions until he passes out
                int interactions = 0;
                while (interactions < 3) {
                    if (!joe.interact("Talk-to")) break;
                    if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) break;
                    long start = System.currentTimeMillis();
                    while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 15000) {
                        if (Dialogues.areOptionsAvailable()) {
                            // Usually accept or give beer options show; pick first sensible
                            Dialogues.chooseOption(1);
                        } else if (Dialogues.canContinue()) {
                            if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                        }
                        Sleep.sleep(350, 600);
                    }
                    interactions++;
                    Sleep.sleep(300, 600);
                }
                // Mark Joe handled so we proceed with Keli
                JOE_HANDLED = true;
                return true;
            }
        };
    }

    private int indexOfOption(String[] options, String... needles) {
        if (options == null) return -1;
        for (int i = 0; i < options.length; i++) {
            String opt = options[i];
            if (opt == null) continue;
            for (String n : needles) {
                if (opt.toLowerCase().contains(n.toLowerCase())) return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isQuestComplete() {
        return Quests.isFinished(FreeQuest.PRINCE_ALI_RESCUE) || super.isQuestComplete();
    }

    @Override
    public int getQuestProgress() {
        if (isQuestComplete()) return 100;
        int v = PlayerSettings.getConfig(CONFIG_ID);
        if (v <= 1) return 5;  // early
        if (v <= 10) return 25; // setup
        if (v <= 20) return 50; // disguise
        if (v <= 30) return 75; // break-in
        return 85;              // freeing prince
    }
}
