package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.DecisionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.actions.EquipItemsNode;
import quest.nodes.actions.UseItemOnObjectNode;
import quest.nodes.actions.SelectDialogueOptionNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.GrandExchangeUtil.ItemRequest;
import quest.utils.GrandExchangeUtil.PriceStrategy;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;

public class BlackKnightsFortressTree extends QuestTree {

    public BlackKnightsFortressTree() { super("Black Knights' Fortress"); }

    // Correct quest varplayer for Black Knights' Fortress (shared by logger map)
    private static final int CONFIG_BKF = 130;

    private static final String IRON_CHAIN = "Iron chainbody";
    private static final String BRONZE_MED = "Bronze med helm";
    private static final String CABBAGE = "Cabbage";

    // Key tiles from discovery log (approx)
    private static final Tile FALADOR_CASTLE_3F = new Tile(2960, 3336, 2);
    private static final Tile FORTRESS_STURDY_DOOR = new Tile(3016, 3514, 0);
    private static final Tile GUARD_DOOR = new Tile(3020, 3515, 0);
    private static final Tile GRILL_TILE = new Tile(3026, 3507, 0);
    private static final Tile LADDER_GROUND = new Tile(3021, 3510, 0);
    private static final Tile STURDY_DOOR_F1 = new Tile(3025, 3511, 1);
    private static final Tile PUSH_WALL_F1 = new Tile(3030, 3509, 1);
    private static final Tile HOLE_TILE = new Tile(3032, 3507, 1); // approx; adjust if needed

    private QuestNode decide;

    @Override
    protected void buildTree() {
        decide = new DecisionNode("bkf_decide", "BKF decide") {
            @Override
            protected String makeDecision() {
                // Use authoritative quest API first
                if (Quests.isFinished(FreeQuest.BLACK_KNIGHTS_FORTRESS)) return "complete";

                // If not started, acquire/equip items first, then start quest
                if (!Quests.isStarted(FreeQuest.BLACK_KNIGHTS_FORTRESS)) {
                    if (!hasAllItemsBase()) return "buy";
                    if (!isEquipped()) return "equip";
                    if (!near(FALADOR_CASTLE_3F, 6)) return "walk_amik";
                    return "talk_amik";
                }

                // If options available at guard door, assert entry option
                if (Dialogues.areOptionsAvailable()) {
                    return "guard_dialog";
                }

                // If still have cabbage, perform full sabotage path with explicit routing
                if (Inventory.contains(CABBAGE)) {
                    // PRE-SABOTAGE: ensure we have disguise + cabbage before entering
                    if (!hasAllItemsBase()) return "buy";
                    if (!isEquipped()) return "equip";
                    // Approach fortress and enter side door
                    if (!near(FORTRESS_STURDY_DOOR, 12)) return "walk_fortress";
                    if (!near(FORTRESS_STURDY_DOOR, 4)) return "walk_sturdy_ground";
                    if (!insideAfterSturdy()) return "open_sturdy";

                    // Go to guard door, open, handle dialogue
                    if (!near(GUARD_DOOR, 6)) return "walk_guard";
                    if (!insideAfterGuardDoor()) return "open_guard";
                    if (Dialogues.areOptionsAvailable()) return "guard_dialog";

                    // Walk to and listen at grill
                    if (!near(GRILL_TILE, 4)) return "walk_grill";
                    if (!listened()) return "listen";

                    // Climb to F1 and push wall
                    if (!near(LADDER_GROUND, 4)) return "walk_ladder";
                    if (!wallPushedF1()) return "climb_f1";

                    // Use cabbage on hole
                    if (!holeVisible()) return "walk_hole";
                    return "use_cabbage";
                }

                // POST-SABOTAGE: no cabbage → go turn in to Sir Amik
                if (!near(FALADOR_CASTLE_3F, 10)) return "walk_amik";
                return "talk_amik";
            }
        };

        // Purchases
        QuestNode geBuy = new quest.nodes.ActionNode("bkf_ge_buy", "Buy BKF items") {
            @Override protected boolean performAction() {
                ItemRequest[] items = new ItemRequest[] {
                    new ItemRequest(IRON_CHAIN, 1, PriceStrategy.FIXED_500_GP),
                    new ItemRequest(BRONZE_MED, 1, PriceStrategy.FIXED_500_GP),
                    new ItemRequest(CABBAGE, 1, PriceStrategy.FIXED_500_GP)
                };
                boolean ok = GrandExchangeUtil.buyItems(items);
                return ok && hasAllItemsBase();
            }
        };

        // Equip disguise
        EquipItemsNode equip = new EquipItemsNode("bkf_equip", BRONZE_MED, IRON_CHAIN);

        // Start and end
        WalkToLocationNode walkAmik = new WalkToLocationNode("bkf_walk_amik", FALADOR_CASTLE_3F, "Falador Castle 3F") {};
        TalkToNPCNode talkAmik = new TalkToNPCNode("bkf_talk_amik", "Sir Amik Varze", FALADOR_CASTLE_3F);

        // Fortress approach and interactions
        WalkToLocationNode walkFortress = new WalkToLocationNode("bkf_walk_fortress", FORTRESS_STURDY_DOOR, "Black Knights' Fortress") {};
        WalkToLocationNode walkSturdyGround = new WalkToLocationNode("bkf_walk_sturdy_ground", FORTRESS_STURDY_DOOR, "Fortress side door") {};
        WalkToLocationNode walkGuard = new WalkToLocationNode("bkf_walk_guard", GUARD_DOOR, "Guard door") {};
        WalkToLocationNode walkLadder = new WalkToLocationNode("bkf_walk_ladder", LADDER_GROUND, "Ground ladder") {};
        WalkToLocationNode walkHole = new WalkToLocationNode("bkf_walk_hole", HOLE_TILE, "Hole") {};
        
        InteractWithObjectNode openSturdy = new InteractWithObjectNode("bkf_open_sturdy", "Sturdy door", "Open", FORTRESS_STURDY_DOOR, "Fortress side door");
        InteractWithObjectNode openGuardDoor = new InteractWithObjectNode("bkf_open_guard", "Door", "Open", GUARD_DOOR, "Guard door");
        SelectDialogueOptionNode assertEntry = new SelectDialogueOptionNode("bkf_dialog_guard", "I don't care. I'm going in anyway.");

        // Listen at grill
        WalkToLocationNode walkGrill = new WalkToLocationNode("bkf_walk_grill", GRILL_TILE, "Grill room") {};
        InteractWithObjectNode listenGrill = new InteractWithObjectNode("bkf_listen_grill", "Grill", "Listen-at", GRILL_TILE, "Grill");

        // Climb to F1 and push wall
        InteractWithObjectNode climbUpGround = new InteractWithObjectNode("bkf_ladder_up_g", "Ladder", "Climb-up", LADDER_GROUND, "Ground ladder");
        InteractWithObjectNode openSturdyF1 = new InteractWithObjectNode("bkf_open_sturdy_f1", "Sturdy door", "Open", STURDY_DOOR_F1, "F1 door");
        InteractWithObjectNode pushWallF1 = new InteractWithObjectNode("bkf_push_wall_f1", "Wall", "Push", PUSH_WALL_F1, "Hidden wall F1");

        // Use cabbage on hole
        UseItemOnObjectNode useCabbage = new UseItemOnObjectNode("bkf_use_cabbage", CABBAGE, "Hole");

        // Wire decisions
        DecisionNode d = (DecisionNode) decide;
        d.addBranch("buy", geBuy)
         .addBranch("equip", equip)
         .addBranch("walk_fortress", walkFortress)
         .addBranch("walk_sturdy_ground", walkSturdyGround)
         .addBranch("open_sturdy", openSturdy)
         .addBranch("walk_guard", walkGuard)
         .addBranch("open_guard", openGuardDoor)
         .addBranch("guard_dialog", assertEntry)
         .addBranch("walk_grill", walkGrill)
         .addBranch("listen", listenGrill)
         .addBranch("walk_ladder", walkLadder)
         .addBranch("climb_f1", climbUpGround)
         .addBranch("open_sturdy_f1", openSturdyF1)
         .addBranch("push_wall_f1", pushWallF1)
         .addBranch("walk_hole", walkHole)
         .addBranch("use_cabbage", useCabbage)
         .addBranch("walk_amik", walkAmik)
         .addBranch("talk_amik", talkAmik)
         .addBranch("complete", talkAmik);

        // Light chaining where dialogue immediately follows door
        openGuardDoor.setNextNode(assertEntry);
        walkGrill.setNextNode(listenGrill);
        climbUpGround.setNextNode(openSturdyF1);
        openSturdyF1.setNextNode(pushWallF1);

        rootNode = decide;
    }

    private boolean hasAllItemsBase() {
        boolean haveHelm = Equipment.contains(BRONZE_MED) || Inventory.contains(BRONZE_MED);
        boolean haveChain = Equipment.contains(IRON_CHAIN) || Inventory.contains(IRON_CHAIN);
        boolean haveCabbage = Inventory.contains(CABBAGE);
        return haveHelm && haveChain && haveCabbage;
    }

    private boolean isEquipped() {
        return Equipment.contains(BRONZE_MED) && Equipment.contains(IRON_CHAIN);
    }

    private boolean insideAfterSturdy() {
        return Players.getLocal() != null && Players.getLocal().getTile().distance(FORTRESS_STURDY_DOOR) <= 6;
    }

    private boolean insideAfterGuardDoor() {
        return Players.getLocal() != null && Players.getLocal().getTile().distance(LADDER_GROUND) <= 10;
    }

    private boolean listened() {
        // If we're close to grill tile, we likely did or will do listen; fall back to cabbage presence
        return false; // always perform listen step once; lightweight and safe
    }

    private boolean wallPushedF1() {
        // Require being near the hidden wall position on F1
        return Players.getLocal() != null && Players.getLocal().getTile().getZ() >= 1 &&
               Players.getLocal().getTile().distance(PUSH_WALL_F1) <= 6;
    }

    private boolean holeVisible() {
        GameObject hole = GameObjects.closest("Hole");
        return hole != null && (Players.getLocal() == null || hole.getTile().distance(Players.getLocal()) <= 10);
    }

    private boolean near(Tile t, int r) {
        return Players.getLocal() != null && Players.getLocal().getTile().distance(t) <= r;
    }

    private int getProgressValue() {
        return PlayerSettings.getConfig(CONFIG_BKF);
    }

    @Override
    public int getQuestProgress() {
        // Prefer authoritative quest API first
        if (Quests.isFinished(FreeQuest.BLACK_KNIGHTS_FORTRESS)) return 100;
        if (Quests.isStarted(FreeQuest.BLACK_KNIGHTS_FORTRESS)) {
            // Rough staging based on inventory/state
            if (!Inventory.contains(CABBAGE)) return 80; // cabbage used
            int v = getProgressValue();
            return v > 0 ? 50 : 30;
        }
        return 0;
    }

    @Override
    public boolean isQuestComplete() {
        // Use DreamBot quest state to avoid cross-quest var conflicts
        if (Quests.isFinished(FreeQuest.BLACK_KNIGHTS_FORTRESS)) return true;
        return super.isQuestComplete();
    }
}
