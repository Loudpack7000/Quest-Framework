package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.DecisionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.actions.UseItemOnObjectNode;
import quest.nodes.actions.TakeGroundItemNode;

import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;

/**
 * Ernest the Chicken Quest Tree
 * Discovered config 32 progression: 0 -> 1 (started), 1 -> 2 (mid), 2 -> 3 (complete)
 * Implements minimal reliable flow based on recorded log.
 */
public class ErnestTheChickenTree extends QuestTree {

    private static final int CONFIG_ID = 32;

    // Key tiles (from recorder log where known)
    private static final Tile VERONICA_TILE = new Tile(3110, 3328, 0);
    private static final Tile MANOR_LARGE_DOOR = new Tile(3109, 3353, 0);
    private static final Tile INNER_DOOR_WEST = new Tile(3109, 3358, 0);
    private static final Tile STAIRS_GROUND = new Tile(3108, 3362, 0);
    private static final Tile STAIRS_1 = new Tile(3108, 3364, 1);
    private static final Tile STAIRS_2 = new Tile(3108, 3364, 2);
    private static final Tile BOOKCASE_SECRET = new Tile(3097, 3359, 0);
    private static final Tile LADDER_SECRET = new Tile(3092, 3362, 0);
    private static final Tile BASEMENT_ROOM_OIL_CAN = new Tile(3102, 9758, 0);
    // Basement door tiles used in exact lever/door sequence from the recorder log
    private static final Tile BASEMENT_DOOR_SOUTH_CENTER = new Tile(3108, 9758, 0);
    private static final Tile BASEMENT_DOOR_OIL_ROOM_W = new Tile(3102, 9758, 0);
    private static final Tile BASEMENT_DOOR_OIL_ROOM_S = new Tile(3100, 9760, 0);
    private static final Tile BASEMENT_DOOR_WEST_ENTRY = new Tile(3097, 9763, 0);
    private static final Tile BASEMENT_DOOR_CENTER_EAST = new Tile(3100, 9765, 0);
    private static final Tile BASEMENT_DOOR_CENTER_NORTH = new Tile(3102, 9763, 0);
    private static final Tile FOUNTAIN_TILE = new Tile(3087, 3334, 0);
    private static final Tile COMPOST_HEAP_TILE = new Tile(3084, 3360, 0);
    // Correct spade ground spawn location on manor grounds (east side)
    private static final Tile SPADE_TILE = new Tile(3121, 3359, 0);
    private static final Tile PROFESSOR_TILE = new Tile(3110, 3368, 2);
    // Interior area to determine if we're already inside the manor (ground floor)
    private static final Area MANOR_INTERIOR_GROUND = new Area(3096, 3373, 3119, 3354, 0);
    // Secret room area (behind bookcase) to control exit behavior and prevent loops
    private static final Area SECRET_ROOM_AREA = new Area(3089, 3364, 3099, 3356, 0);

    // Item names
    private static final String POISON = "Poison";
    private static final String FISH_FOOD = "Fish food";
    private static final String POISONED_FISH_FOOD = "Poisoned fish food";
    private static final String PRESSURE_GAUGE = "Pressure gauge";
    private static final String OIL_CAN = "Oil can";
    private static final String RUBBER_TUBE = "Rubber tube";
    private static final String SPADE = "Spade";

    // Nodes
    private QuestNode smart;
    private QuestNode talkVeronica;
    private QuestNode enterManor;
    private QuestNode climbUpstairs;
    private QuestNode climbDownstairs;
    private QuestNode takeFishFood1;
    private QuestNode takeFishFood2;
    private QuestNode takePoison;
    private QuestNode combinePoisonedFood;
    private QuestNode takeSpade;
    private QuestNode exitManorToSpade;
    private QuestNode searchCompost;
    private QuestNode useFoodOnFountain;
    private QuestNode searchFountain;
    private QuestNode secretToBasement;
    private QuestNode pullLeverSequence;
    private QuestNode takeOilCan;
    private QuestNode climbBackUp;
    private QuestNode pullTopRoomLever;
    private QuestNode exitSecretRoom;
    private QuestNode takeRubberTube;
    private QuestNode finishWithProfessor;

    // One-time guard to avoid re-selecting the optional top-room lever/exit branch after it's handled
    private boolean topLeverHandled = false;

    public ErnestTheChickenTree() {
        super("Ernest the Chicken");
    }

    @Override
    protected void buildTree() {
        createNodes();
        createSmartDecision();
        rootNode = smart;
    }

    private void createNodes() {
        // Start: Talk to Veronica to start quest
        talkVeronica = new TalkToNPCNode("talk_veronica", "Veronica", VERONICA_TILE);

        // Enter manor: open large door and the inner hallway door (targeted), avoid random door clicks
        enterManor = new ActionNode("enter_manor", "Enter Draynor Manor") {
            @Override
            public boolean shouldSkip() {
                // Skip if already inside the ground-floor interior area
                return Players.getLocal() != null && MANOR_INTERIOR_GROUND.contains(Players.getLocal());
            }
            @Override
            protected boolean performAction() {
                // Walk to large door if not nearby
                if (Players.getLocal() == null || Players.getLocal().getTile().distance(MANOR_LARGE_DOOR) > 6) {
                    new WalkToLocationNode("walk_manor_door", MANOR_LARGE_DOOR, 4, "Manor entrance").execute();
                }
                // Open the specific large door at the known tile
                GameObject largeDoor = GameObjects.closest(go -> go != null && "Large door".equals(go.getName()) && MANOR_LARGE_DOOR.equals(go.getTile()));
                if (largeDoor != null && largeDoor.distance() <= 7) {
                    largeDoor.interact("Open");
                    Sleep.sleep(600, 1200);
                }
                // Walk to the inner hallway door and open it specifically
                if (Players.getLocal() == null || Players.getLocal().getTile().distance(INNER_DOOR_WEST) > 6) {
                    new WalkToLocationNode("walk_inner_door", INNER_DOOR_WEST, 4, "Manor inner door").execute();
                }
                GameObject innerDoor = GameObjects.closest(go -> go != null && "Door".equals(go.getName()) && INNER_DOOR_WEST.equals(go.getTile()));
                if (innerDoor != null && innerDoor.distance() <= 6) {
                    innerDoor.interact("Open");
                    Sleep.sleep(600, 1000);
                }
                return true;
            }
        };

    // Climb upstairs to get fish food
    climbUpstairs = new InteractWithObjectNode("climb_stairs_up", "Staircase", "Climb-up", STAIRS_GROUND, "Manor staircase");
    // Climb back down after upstairs actions
    climbDownstairs = new InteractWithObjectNode("climb_stairs_down", "Staircase", "Climb-down", STAIRS_1, "Return to ground floor");
    takeFishFood1 = new TakeGroundItemNode("take_fish_food1", FISH_FOOD, new Tile(3100, 3365, 1), "Upstairs table (west)") {
            @Override
            public boolean shouldSkip() { return Inventory.contains(FISH_FOOD) || Inventory.contains(POISONED_FISH_FOOD); }
        };
        // Optionally another fish food spawn
    takeFishFood2 = new TakeGroundItemNode("take_fish_food2", FISH_FOOD, new Tile(3116, 3361, 1), "Upstairs room (east)") {
            @Override
            public boolean shouldSkip() { return Inventory.contains(FISH_FOOD) || Inventory.contains(POISONED_FISH_FOOD); }
        };

        // Get poison on ground floor north room
    takePoison = new TakeGroundItemNode("take_poison", POISON, new Tile(3102, 3371, 0), "North room") {
            @Override
            public boolean shouldSkip() { return Inventory.contains(POISONED_FISH_FOOD) || Inventory.contains(POISON); }
        };

        // Combine fish food + poison
        combinePoisonedFood = new ActionNode("combine_poisoned_food", "Combine Fish food with Poison") {
            @Override
            protected boolean performAction() {
                if (!Inventory.contains(FISH_FOOD) || !Inventory.contains(POISON)) return false;
                if (Inventory.interact(FISH_FOOD, "Use")) {
                    Sleep.sleep(200, 400);
                    boolean clicked = org.dreambot.api.methods.container.impl.Inventory.interact(POISON, "Use");
                    if (!clicked) {
                        // Try reversed order
                        if (Inventory.interact(POISON, "Use")) {
                            Sleep.sleep(150, 300);
                            clicked = org.dreambot.api.methods.container.impl.Inventory.interact(FISH_FOOD, "Use");
                        }
                    }
                    Sleep.sleep(600, 900);
                    return Inventory.contains(POISONED_FISH_FOOD);
                }
                return false;
            }
        };

        // Optional: take spade near manor grounds
    takeSpade = new TakeGroundItemNode("take_spade", SPADE, SPADE_TILE, "Manor grounds spade") {
            @Override
            public boolean shouldSkip() {
                return Inventory.contains(SPADE);
            }
        };

        // Ensure we exit the manor cleanly (open two doors) before heading to the spade
        exitManorToSpade = new ActionNode("exit_manor_to_spade", "Exit manor and head to spade") {
            @Override
            public boolean shouldSkip() {
                // Skip if already near the spade area
                return Players.getLocal() != null && Players.getLocal().getTile().distance(SPADE_TILE) <= 8;
            }
            @Override
            protected boolean performAction() {
                // Pass inner door
                ensurePassDoorGround(INNER_DOOR_WEST);
                // Pass large door
                ensurePassDoorGround(MANOR_LARGE_DOOR);
                // Walk to spade tile vicinity
                new WalkToLocationNode("walk_spade", SPADE_TILE, 2, "To spade").execute();
                return true;
            }
            private void ensurePassDoorGround(Tile doorTile) {
                // Approach
                if (Players.getLocal() == null || Players.getLocal().getTile().distance(doorTile) > 2) {
                    new WalkToLocationNode("to_door_g", doorTile, 1, "Approach door").execute();
                    Sleep.sleep(200, 400);
                }
                // Open if possible
                GameObject door = GameObjects.closest(go -> go != null && "Door".equals(go.getName()) && doorTile.equals(go.getTile()) ||
                                                             ("Large door".equals(go.getName()) && doorTile.equals(go.getTile())));
                if (door != null) {
                    try {
                        if (door.hasAction("Open")) {
                            door.interact("Open");
                            Sleep.sleep(300, 600);
                        }
                    } catch (Throwable ignored) {}
                }
                // Step through to the other side heuristically
                Tile me = Players.getLocal() != null ? Players.getLocal().getTile() : null;
                if (me == null) return;
                int dx = Integer.compare(me.getX(), doorTile.getX());
                int dy = Integer.compare(me.getY(), doorTile.getY());
                if (Math.abs(dx) > Math.abs(dy)) { dy = 0; dx = dx > 0 ? 1 : -1; } else { dx = 0; dy = dy > 0 ? 1 : -1; }
                Tile target = new Tile(doorTile.getX() + dx, doorTile.getY() + dy, doorTile.getZ());
                new WalkToLocationNode("through_door_g", target, 0, "Pass door").execute();
                Sleep.sleep(200, 400);
            }
        };

        // Search compost heap to obtain gate/fountain key (game handles usage implicitly)
        searchCompost = new InteractWithObjectNode("search_compost", "Compost heap", "Search", COMPOST_HEAP_TILE, "Garden heap") {
            @Override
            public boolean shouldSkip() {
                return Inventory.contains("Key");
            }
        };

        // Use poisoned food on fountain, then search to obtain pressure gauge
        useFoodOnFountain = new ActionNode("use_food_on_fountain", "Use poisoned fish food on Fountain") {
            @Override
            public boolean shouldSkip() {
                return Inventory.contains(PRESSURE_GAUGE) || !Inventory.contains(POISONED_FISH_FOOD);
            }
            @Override
            protected boolean performAction() {
                if (!Inventory.contains(POISONED_FISH_FOOD)) return false;
                // Always walk to the fountain tile first
                Tile fountain = FOUNTAIN_TILE;
                if (Players.getLocal() == null || Players.getLocal().getTile().distance(fountain) > 3) {
                    new WalkToLocationNode("walk_fountain", fountain, 2, "To fountain").execute();
                    Sleep.sleep(400, 700);
                }
                UseItemOnObjectNode use = new UseItemOnObjectNode("use_food", POISONED_FISH_FOOD, "Fountain");
                return use.execute().isSuccess();
            }
        };
        searchFountain = new ActionNode("search_fountain", "Search Fountain for Pressure gauge") {
            @Override
            public boolean shouldSkip() { return Inventory.contains(PRESSURE_GAUGE); }
            @Override
            protected boolean performAction() {
                InteractWithObjectNode search = new InteractWithObjectNode("search_fountain_inner", "Fountain", "Search", FOUNTAIN_TILE, "Fountain");
                boolean ok = search.execute().isSuccess();
                Sleep.sleep(600, 900);
                return ok && Inventory.contains(PRESSURE_GAUGE);
            }
        };

        // Secret passage to basement via bookcase then ladder
    secretToBasement = new ActionNode("secret_to_basement", "Enter basement via secret bookcase and ladder") {
            @Override
            protected boolean performAction() {
                InteractWithObjectNode searchBook = new InteractWithObjectNode("search_bookcase", "Bookcase", "Search", BOOKCASE_SECRET, "Secret bookcase");
                searchBook.execute();
                Sleep.sleep(500, 800);
                InteractWithObjectNode climbDown = new InteractWithObjectNode("ladder_down", "Ladder", "Climb-down", LADDER_SECRET, "Secret ladder");
                return climbDown.execute().isSuccess();
            }
        };

        // Pull levers sequence to reach oil can (exact sequence as per recorded log)
    pullLeverSequence = new ActionNode("pull_levers", "Execute exact lever+door sequence for Oil can") {
            @Override
            public boolean shouldSkip() { return Inventory.contains(OIL_CAN); }
            @Override
            protected boolean performAction() {
                // 1) Lever B
                if (!pullLeverByName("Lever B")) return false;
                // 2) Lever A
                if (!pullLeverByName("Lever A")) return false;
                // 3) Door at (3108, 9758, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_SOUTH_CENTER)) return false;
                // 4) Lever D
                if (!pullLeverByName("Lever D")) return false;
                // 5) Door at (3108, 9758, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_SOUTH_CENTER)) return false;
                // 6) Lever B
                if (!pullLeverByName("Lever B")) return false;
                // 7) Lever A
                if (!pullLeverByName("Lever A")) return false;
                // 8) Door at (3108, 9758, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_SOUTH_CENTER)) return false;
                // 9) Door at (3102, 9758, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_OIL_ROOM_W)) return false;
                // 10) Door at (3100, 9760, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_OIL_ROOM_S)) return false;
                // 11) Lever E
                if (!pullLeverByName("Lever E")) return false;
                // 12) Lever F
                if (!pullLeverByName("Lever F")) return false;
                // 13) Door at (3097, 9763, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_WEST_ENTRY)) return false;
                // 14) Door at (3100, 9765, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_CENTER_EAST)) return false;
                // 15) Lever C
                if (!pullLeverByName("Lever C")) return false;
                // 16) Door at (3108, 9758, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_SOUTH_CENTER)) return false;
                // 17) Door at (3102, 9763, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_CENTER_NORTH)) return false;
                // 18) Lever E
                if (!pullLeverByName("Lever E")) return false;
                // 19) Door at (3100, 9765, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_CENTER_EAST)) return false;
                // 20) Door at (3100, 9765, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_CENTER_EAST)) return false;
                // 21) Door at (3102, 9763, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_CENTER_NORTH)) return false;
                // 22) Door at (3102, 9758, 0)
                if (!ensurePassDoor(BASEMENT_DOOR_OIL_ROOM_W)) return false;

                return true;
             }
            private boolean ensurePassDoor(Tile doorTile) {
                // Move adjacent to the door first
                if (Players.getLocal() == null || Players.getLocal().getTile().distance(doorTile) > 2) {
                    new WalkToLocationNode("to_door", doorTile, 1, "Approach door").execute();
                    Sleep.sleep(200, 400);
                }

                // Determine target tile on opposite side relative to our position
                Tile me = Players.getLocal() != null ? Players.getLocal().getTile() : null;
                if (me == null) return false;
                int dx = Integer.compare(me.getX(), doorTile.getX());
                int dy = Integer.compare(me.getY(), doorTile.getY());
                // Normalize to one-step axis
                if (Math.abs(dx) > Math.abs(dy)) { dy = 0; dx = dx > 0 ? 1 : -1; }
                else { dx = 0; dy = dy > 0 ? 1 : -1; }
                Tile target = new Tile(doorTile.getX() + dx, doorTile.getY() + dy, doorTile.getZ());

                // If door exists and has Open action, open it first
                GameObject door = GameObjects.closest(go -> go != null && "Door".equals(go.getName()) && doorTile.equals(go.getTile()));
                if (door != null) {
                    try {
                        if (door.hasAction("Open")) {
                            if (door.interact("Open")) {
                                Sleep.sleep(300, 600);
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // Attempt to walk through to other side
                new WalkToLocationNode("through_door", target, 0, "Pass door").execute();
                boolean passed = Sleep.sleepUntil(() -> Players.getLocal() != null && Players.getLocal().getTile().distance(target) <= 0, 2000);
                if (!passed) {
                    // Retry with a small nudge
                    new WalkToLocationNode("through_door_retry", target, 0, "Pass door retry").execute();
                    Sleep.sleep(200, 400);
                }
                return Players.getLocal() != null && Players.getLocal().getTile().distance(target) <= 1;
            }
            private boolean pullLeverByName(String leverLabel) {
                GameObject lever = GameObjects.closest(leverLabel);
                if (lever == null) {
                    lever = GameObjects.closest(go -> go != null && "Lever".equals(go.getName()) && go.distance() < 12);
                }
                if (lever == null) {
                    new WalkToLocationNode("seek_lever", BASEMENT_ROOM_OIL_CAN, 8, "Basement area").execute();
                    lever = GameObjects.closest(leverLabel);
                    if (lever == null) return false;
                }
                if (lever.distance() > 3) {
                    new WalkToLocationNode("walk_to_lever", lever.getTile(), 2, leverLabel).execute();
                }
                if (!lever.interact("Pull")) return false;
                Sleep.sleepUntil(() -> Players.getLocal() != null && Players.getLocal().isAnimating(), 2000);
                Sleep.sleepUntil(() -> Players.getLocal() != null && !Players.getLocal().isAnimating(), 4000);
                Sleep.sleep(150, 300);
                return true;
            }
         };

    takeOilCan = new TakeGroundItemNode("take_oil_can", OIL_CAN, BASEMENT_ROOM_OIL_CAN, "Basement oil can room") {
            @Override
            public boolean shouldSkip() { return Inventory.contains(OIL_CAN); }
        };

    climbBackUp = new InteractWithObjectNode("ladder_up", "Ladder", "Climb-up", new Tile(3117, 9754, 0), "Basement ladder up");
    // Pull the lever once in the returned room (matches log) and avoid loops
    pullTopRoomLever = new InteractWithObjectNode("pull_room_lever", "Lever", "Pull", new Tile(3092, 3361, 0), "Secret room lever") {
            private boolean pulled = false;
            @Override
            public boolean shouldSkip() { return pulled; }
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) {
                    pulled = true;
                }
                return ok;
            }
        };

        // Exit the secret room via bookcase exactly once; prevents turning around and re-using it
        exitSecretRoom = new ActionNode("exit_secret_room", "Exit secret room via bookcase") {
            private boolean exited = false;
            @Override
            public boolean shouldSkip() {
                return exited || Players.getLocal() == null || !SECRET_ROOM_AREA.contains(Players.getLocal());
            }
            @Override
            protected boolean performAction() {
                InteractWithObjectNode book = new InteractWithObjectNode("open_secret_bookcase", "Bookcase", "Search", BOOKCASE_SECRET, "Open secret bookcase");
                book.execute();
                Sleep.sleep(400, 700);
                // Walk out towards inner door/stairs to ensure we are outside the secret room
                new WalkToLocationNode("leave_secret_room", INNER_DOOR_WEST, 4, "Leave secret").execute();
                boolean out = Sleep.sleepUntil(() -> Players.getLocal() != null && !SECRET_ROOM_AREA.contains(Players.getLocal()), 2000);
                if (out) {
                    exited = true;
                    // Mark the optional lever/exit step as handled so we don't loop back to it
                    topLeverHandled = true;
                }
                return out;
            }
        };

    takeRubberTube = new TakeGroundItemNode("take_rubber_tube", RUBBER_TUBE, new Tile(3107, 3367, 0), "Ground floor east room") {
            @Override
            public boolean shouldSkip() { return Inventory.contains(RUBBER_TUBE); }
        };

        finishWithProfessor = new TalkToNPCNode("talk_professor_finish", "Professor Oddenstein", PROFESSOR_TILE) {
            @Override
            protected boolean performAction() {
                boolean res = super.performAction();
                Sleep.sleep(800, 1200);
                return res;
            }
        };
    }

    private void createSmartDecision() {
        smart = new DecisionNode("smart", "Decide next Ernest step") {
            @Override
            protected String makeDecision() {
                int cfg = PlayerSettings.getConfig(CONFIG_ID);
                boolean started = false;
                boolean finished = false;
                try {
                    started = Quests.isStarted(FreeQuest.ERNEST_THE_CHICKEN);
                    finished = Quests.isFinished(FreeQuest.ERNEST_THE_CHICKEN);
                } catch (Throwable ignored) {}
                // Fallback to config when API not available
                started = started || cfg >= 1;
                finished = finished || cfg >= 3;

                // Completion check
                if (finished) {
                    setQuestComplete();
                    return "COMPLETE";
                }
                // Not started -> talk to Veronica
                if (!started) {
                    return "TALK_VERONICA";
                }
                // Enforce exact order from recorder log
                if (!Inventory.contains(POISON) && !Inventory.contains(POISONED_FISH_FOOD)) return "POISON_FIRST";
                if (!Inventory.contains(POISONED_FISH_FOOD)) return "FISHFOOD_AND_COMBINE";
                if (!Inventory.contains(OIL_CAN)) return "OIL_CAN_SEQUENCE";
                // Prioritize getting the spade immediately after oil can
                if (!Inventory.contains(SPADE)) return "TAKE_SPADE";
                // Optional lever step occurs once, right after returning to the secret room — never force path back
        if (!topLeverHandled && Inventory.contains(OIL_CAN) && !Inventory.contains(PRESSURE_GAUGE) && cfg < 2
            && !Inventory.contains(SPADE)
            && Players.getLocal() != null && SECRET_ROOM_AREA.contains(Players.getLocal())) {
                    return "PULL_TOP_LEVER";
                }
                // If we have the pressure gauge, skip all prior steps and go straight to rubber tube or finish
                if (Inventory.contains(PRESSURE_GAUGE)) {
                    if (!Inventory.contains(RUBBER_TUBE)) return "GET_RUBBER_TUBE";
                    return "FINISH";
                }
                // Only search compost if we don't have the Key and don't have the pressure gauge
                if (!Inventory.contains(PRESSURE_GAUGE) && !Inventory.contains("Key")) return "SEARCH_COMPOST";
                // After Key, proceed to fountain steps for pressure gauge
                if (!Inventory.contains(PRESSURE_GAUGE)) return "GET_GAUGE";
                return "FINISH";
            }
        };

        // Branches wiring
        ((DecisionNode) smart)
            .addBranch("TALK_VERONICA", chain(talkVeronica, enterManor))
            .addBranch("POISON_FIRST", chain(enterManor, takePoison))
            .addBranch("FISHFOOD_AND_COMBINE", chain(enterManor, climbUpstairs, takeFishFood1, takeFishFood2, combinePoisonedFood, climbDownstairs))
            .addBranch("OIL_CAN_SEQUENCE", chain(enterManor, secretToBasement, pullLeverSequence, takeOilCan, climbBackUp, pullTopRoomLever, exitSecretRoom))
            .addBranch("PULL_TOP_LEVER", chain(pullTopRoomLever, exitSecretRoom))
            .addBranch("TAKE_SPADE", chain(exitManorToSpade, takeSpade))
            .addBranch("SEARCH_COMPOST", chain(searchCompost))
            .addBranch("GET_GAUGE", chain(useFoodOnFountain, searchFountain))
            .addBranch("GET_RUBBER_TUBE", chain(enterManor, takeRubberTube))
            .addBranch("FINISH", chain(new InteractWithObjectNode("ensure_top_floor1", "Staircase", "Climb-up", STAIRS_GROUND, "Stairs up"),
                                         new InteractWithObjectNode("ensure_top_floor2", "Staircase", "Climb-up", STAIRS_1, "Stairs to top"),
                                         finishWithProfessor))
            .setDefaultBranch(enterManor);
    }

    // Utility to chain multiple nodes sequentially; returns a composite ActionNode
    private QuestNode chain(QuestNode... nodes) {
        return new ActionNode("chain_node", "Execute chained actions") {
            int idx = 0;
            @Override
            protected boolean performAction() {
                while (idx < nodes.length) {
                    QuestNode n = nodes[idx];
                    QuestNode.ExecutionResult r = n.execute();
                    if (r.isSuccess()) {
                        idx++;
                        Sleep.sleep(300, 600);
                        continue;
                    }
                    if (r.isRetry() || r.isInProgress()) return false; // let executor loop retry
                    if (r.isFailed()) return false;
                }
                idx = 0; // reset for next time
                return true;
            }
        };
    }
}
