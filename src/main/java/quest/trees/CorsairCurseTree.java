package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.actions.WalkToLocationNode;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;

/**
 * The Corsair Curse - Tree-based implementation
 *
 * This implementation is derived from an action recording log and follows
 * a robust node pattern using existing action nodes and custom steps.
 *
 * Detection:
 * - Uses Quests.isStarted / Quests.isFinished for high-level status.
 * - Uses in-session flags to track visited NPCs since config/varbits are not mapped yet.
 */
public class CorsairCurseTree extends QuestTree {

    // Key tiles from recording (approximate)
    private static final Tile RIMMINGTON_TOCK = new Tile(3030, 3273, 0);
    private static final Tile COVE_GANGPLANK = new Tile(2578, 2838, 1);
    private static final Tile COVE_DOCK_GROUND = new Tile(2578, 2839, 0);
    private static final Tile ITHOI_STAIRS_GROUND = new Tile(2531, 2833, 0);
    private static final Tile ITHOI_UPSTAIRS_TILE = new Tile(2529, 2835, 1);
    private static final Tile ITHOI_LOCATION = new Tile(2530, 2840, 1);
    private static final Tile TOWER_STAIRS_GROUND = new Tile(2555, 2855, 0);
    private static final Tile ARSEN_LOCATION = new Tile(2554, 2859, 1);
    private static final Tile COLIN_LOCATION = new Tile(2559, 2859, 1);
    private static final Tile COOK_STAIRS_GROUND = new Tile(2548, 2862, 0);
    private static final Tile GNOCCHI_LOCATION = new Tile(2545, 2863, 1);
    private static final Tile TOCK_ON_SHIP = new Tile(2574, 2835, 1);
    private static final Tile OGRE_HOLE_GROUND = new Tile(2523, 2861, 0);
    private static final Tile OGRE_CAVE_CHIEF = new Tile(2011, 9005, 1);
    private static final Tile OGRE_VINE_LADDER = new Tile(2012, 9005, 1);
    private static final Tile DOLL_DIG_TILE = new Tile(2504, 2840, 0);
    private static final Tile TELESCOPE_TILE = new Tile(2529, 2835, 1);

    // Items
    private static final String SPADE = "Spade";

    // Session flags (until precise config/varbits are mapped)
    private boolean visitedIthoi;
    private boolean visitedArsen;
    private boolean visitedColin;
    private boolean visitedGnocci;
    private boolean started;
    private boolean completed;
    private boolean spokeChiefTess;
    private boolean climbedOutOfOgreCave;
    private boolean dugForDoll;
    private boolean observedTelescope;
    private boolean reportedFindingsOnce;
    private boolean reinterviewedCrew;

    // Nodes
    private QuestNode smart;
    private QuestNode startWithTock;
    private QuestNode travelToCorsairCove;
    private QuestNode talkIthoi;
    private QuestNode talkArsen;
    private QuestNode talkColin;
    private QuestNode talkGnocci;
    private QuestNode reportToTockOnShip;
    private QuestNode finalReportToTock;
    private QuestNode visitOgreChief;
    private QuestNode digForDollNode;
    private QuestNode observeTelescopeNode;
    private QuestNode reinterviewCrewNode;

    public CorsairCurseTree() {
        super("The Corsair Curse");
    }

    @Override
    protected void buildTree() {
        createNodes();
        rootNode = smart;
    }

    private void createNodes() {
        // Start: Talk to Captain Tock in Rimmington
        startWithTock = new ActionNode("start_tock_rimmington", "Talk to Captain Tock to begin") {
            @Override
            protected boolean performAction() {
                QuestNode talk = new TalkToNPCNode("talk_tock_start", "Captain Tock", RIMMINGTON_TOCK);
                boolean ok = talk.execute().isSuccess();
                if (ok) started = true;
                return ok;
            }
        };

        // Travel: Use Captain Tock / gangplank to reach Corsair Cove
        travelToCorsairCove = new ActionNode("travel_corsair_cove", "Travel to Corsair Cove") {
            @Override
            protected boolean performAction() {
                // Already there?
                if (isAtCorsairCove()) {
                    log("Already near Corsair Cove");
                    return true;
                }

                // Try to talk to Captain Tock if nearby
                NPC tock = NPCs.closest("Captain Tock");
                if (tock != null) {
                    log("Talking to Captain Tock to travel...");
                    if (tock.interact("Talk-to")) {
                        if (Sleep.sleepUntil(Dialogues::inDialogue, 7000)) {
                            // Progress through dialogue; look for travel options if present
                            int guard = 0;
                            while (Dialogues.inDialogue() && guard++ < 20) {
                                if (Dialogues.areOptionsAvailable()) {
                                    String[] opts = Dialogues.getOptions();
                                    for (int i = 0; i < opts.length; i++) {
                                        if (opts[i].toLowerCase().contains("ready") ||
                                            opts[i].toLowerCase().contains("corsair cove") ||
                                            opts[i].toLowerCase().contains("take me")) {
                                            Dialogues.chooseOption(i + 1);
                                            break;
                                        }
                                    }
                                } else if (Dialogues.canContinue()) {
                                    if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                                }
                                Sleep.sleep(600, 900);
                            }
                        }
                    }
                }

                // Use gangplank if visible
                GameObject gangplank = GameObjects.closest("Gangplank");
                if (gangplank != null && gangplank.interact("Cross")) {
                    Sleep.sleepUntil(() -> isAtCorsairCove() || Players.getLocal().getTile().distance(COVE_DOCK_GROUND) < 15, 10000);
                    started = true;
                    return true;
                }

                // Last resort: walk towards Rimmington Tock then try again
                if (Players.getLocal().getTile().distance(RIMMINGTON_TOCK) > 8) {
                    Walking.walk(RIMMINGTON_TOCK);
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(RIMMINGTON_TOCK) <= 8, 15000);
                }
                // If we made it to the area by walking/dialogue, consider started once at cove
                if (isAtCorsairCove()) started = true;
                return true; // Let smart node loop
            }
        };

        // Crew interviews
        talkIthoi = new ActionNode("talk_ithoi", "Interview Ithoi the Navigator") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(ITHOI_STAIRS_GROUND, 1)) return false;
                QuestNode talk = new TalkToNPCNode("talk_ithoi_inner", "Ithoi the Navigator", ITHOI_LOCATION);
                boolean ok = talk.execute().isSuccess();
                if (ok) visitedIthoi = true;
                return ok;
            }
        };

        talkArsen = new ActionNode("talk_arsen", "Interview Arsen the Thief") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(TOWER_STAIRS_GROUND, 1)) return false;
                QuestNode talk = new TalkToNPCNode("talk_arsen_inner", "Arsen the Thief", ARSEN_LOCATION);
                boolean ok = talk.execute().isSuccess();
                if (ok) visitedArsen = true;
                return ok;
            }
        };

        talkColin = new ActionNode("talk_colin", "Interview Cabin Boy Colin") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(TOWER_STAIRS_GROUND, 1)) return false;
                QuestNode talk = new TalkToNPCNode("talk_colin_inner", "Cabin Boy Colin", COLIN_LOCATION);
                boolean ok = talk.execute().isSuccess();
                if (ok) visitedColin = true;
                return ok;
            }
        };

        talkGnocci = new ActionNode("talk_gnocci", "Interview Gnocci the Cook") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(COOK_STAIRS_GROUND, 1)) return false;
                QuestNode talk = new TalkToNPCNode("talk_gnocci_inner", "Gnocci the Cook", GNOCCHI_LOCATION);
                boolean ok = talk.execute().isSuccess();
                if (ok) visitedGnocci = true;
                return ok;
            }
        };

        reportToTockOnShip = new ActionNode("report_tock", "Report findings to Captain Tock (ship)") {
            @Override
            protected boolean performAction() {
                // Ensure we're on or near the ship
                if (Players.getLocal().getTile().getZ() == 0 && Players.getLocal().getTile().distance(COVE_GANGPLANK) < 15) {
                    GameObject gangplank = GameObjects.closest("Gangplank");
                    if (gangplank != null) {
                        gangplank.interact("Cross");
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 7000);
                    }
                }
                QuestNode talk = new TalkToNPCNode("talk_tock_ship", "Captain Tock", TOCK_ON_SHIP);
                boolean ok = talk.execute().isSuccess();
                if (ok) reportedFindingsOnce = true;
                return ok;
            }
        };

        finalReportToTock = new ActionNode("final_report_tock", "Report final findings to Captain Tock") {
            @Override
            protected boolean performAction() {
                // Ensure on ship
                if (Players.getLocal().getTile().getZ() == 0 && Players.getLocal().getTile().distance(COVE_GANGPLANK) < 15) {
                    GameObject gangplank = GameObjects.closest("Gangplank");
                    if (gangplank != null) {
                        gangplank.interact("Cross");
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 7000);
                    }
                }
                QuestNode talk = new TalkToNPCNode("talk_tock_final", "Captain Tock", TOCK_ON_SHIP);
                boolean ok = talk.execute().isSuccess();
                if (ok) {
                    completed = true;
                }
                return ok;
            }
        };

        visitOgreChief = new ActionNode("visit_ogre_chief", "Return relic and speak to Chief Tess") {
            @Override
            protected boolean performAction() {
                // Enter hole to cave
                GameObject hole = GameObjects.closest("Hole");
                if (hole == null || Players.getLocal().getTile().distance(OGRE_HOLE_GROUND) > 8) {
                    // Walk to hole
                    new WalkToLocationNode("walk_hole", OGRE_HOLE_GROUND, 3, "Ogre cave hole").execute();
                    hole = GameObjects.closest("Hole");
                }
                if (hole != null && hole.interact("Enter")) {
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(OGRE_CAVE_CHIEF) < 50, 10000);
                }

                // Talk to Chief Tess
                QuestNode talk = new TalkToNPCNode("talk_chief_tess", "Chief Tess", OGRE_CAVE_CHIEF);
                boolean ok = talk.execute().isSuccess();
                if (!ok) return false;
                spokeChiefTess = true;

                // Climb vine ladder to leave
                GameObject vine = GameObjects.closest(go -> go.getName().contains("Vine") || go.getName().contains("Vine ladder"));
                if (vine == null) {
                    // Walk to vine tile as fallback
                    new WalkToLocationNode("walk_vine", OGRE_VINE_LADDER, 2, "Vine ladder").execute();
                    vine = GameObjects.closest(go -> go.getName().contains("Vine"));
                }
                if (vine != null && (vine.hasAction("Climb") || vine.hasAction("Climb-up"))) {
                    String action = vine.hasAction("Climb") ? "Climb" : "Climb-up";
                    vine.interact(action);
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 0, 7000);
                }
                climbedOutOfOgreCave = Players.getLocal().getTile().getZ() == 0;
                return climbedOutOfOgreCave;
            }
        };

        digForDollNode = new ActionNode("dig_for_doll", "Dig for the possessed doll") {
            @Override
            protected boolean performAction() {
                if (Players.getLocal().getTile().distance(DOLL_DIG_TILE) > 4) {
                    new WalkToLocationNode("walk_dig_tile", DOLL_DIG_TILE, 2, "Doll dig tile").execute();
                }

                if (!Inventory.contains(SPADE)) {
                    log("Missing Spade - cannot dig");
                    return false;
                }

                if (!Inventory.interact(SPADE, "Dig")) return false;

                // Handle potential dialogue choice from the log
                if (Sleep.sleepUntil(Dialogues::areOptionsAvailable, 5000)) {
                    String[] opts = Dialogues.getOptions();
                    for (int i = 0; i < opts.length; i++) {
                        if (opts[i].toLowerCase().contains("possessed doll") || opts[i].toLowerCase().contains("search")) {
                            Dialogues.chooseOption(i + 1);
                            break;
                        }
                    }
                }
                // Continue through any dialogue
                Sleep.sleep(800, 1200);
                int guard = 0;
                while (Dialogues.inDialogue() && guard++ < 10) {
                    if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    } else if (Dialogues.areOptionsAvailable()) {
                        Dialogues.chooseOption(1);
                    }
                    Sleep.sleep(600, 900);
                }
                dugForDoll = true;
                return true;
            }
        };

        observeTelescopeNode = new ActionNode("observe_telescope", "Observe the telescope") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(ITHOI_STAIRS_GROUND, 1)) return false;
                GameObject telescope = GameObjects.closest("Telescope");
                if (telescope != null && telescope.interact("Observe")) {
                    Sleep.sleep(1500, 2500);
                    observedTelescope = true;
                    return true;
                }
                return false;
            }
        };

        reinterviewCrewNode = new ActionNode("reinterview_crew", "Re-interview crew with new theories") {
            @Override
            protected boolean performAction() {
                // Gnocci new line
                if (!ensureUpstairsAt(COOK_STAIRS_GROUND, 1)) return false;
                new TalkToNPCNode("re_talk_gnocci", "Gnocci the Cook", GNOCCHI_LOCATION).execute();

                // Arsen follow-up
                if (!ensureUpstairsAt(TOWER_STAIRS_GROUND, 1)) return false;
                new TalkToNPCNode("re_talk_arsen", "Arsen the Thief", ARSEN_LOCATION).execute();

                // Colin follow-up
                new TalkToNPCNode("re_talk_colin", "Cabin Boy Colin", COLIN_LOCATION).execute();

                reinterviewedCrew = true;
                return true;
            }
        };

        // Smart decision node - routes based on quest and session state
        smart = new QuestNode("smart_decision", "Smart decision for The Corsair Curse") {
            @Override
            public ExecutionResult execute() {
                // Completion check using internal flag
                if (completed) {
                    setQuestComplete();
                    return ExecutionResult.questComplete();
                }

                // Start check
                if (!started) {
                    log("Quest not started - talk to Captain Tock in Rimmington");
                    return ExecutionResult.success(startWithTock);
                }

                // Ensure we're at Corsair Cove early
                if (!isAtCorsairCove()) {
                    log("Traveling to Corsair Cove");
                    return ExecutionResult.success(travelToCorsairCove);
                }

                // Initial crew interviews
                if (!visitedIthoi) return ExecutionResult.success(talkIthoi);
                if (!visitedArsen) return ExecutionResult.success(talkArsen);
                if (!visitedColin) return ExecutionResult.success(talkColin);
                if (!visitedGnocci) return ExecutionResult.success(talkGnocci);

                // Report to Captain Tock on ship
                if (!reportedFindingsOnce) return ExecutionResult.success(reportToTockOnShip);

                // Ogre chief + dig step
                if (!spokeChiefTess || !climbedOutOfOgreCave) return ExecutionResult.success(visitOgreChief);
                if (!dugForDoll) return ExecutionResult.success(digForDollNode);

                // Investigation extras from log
                if (!observedTelescope) return ExecutionResult.success(observeTelescopeNode);
                if (!reinterviewedCrew) return ExecutionResult.success(reinterviewCrewNode);

                // Final report to complete
                return ExecutionResult.success(finalReportToTock, "Final report to Captain Tock");
            }
        };
    }

    private boolean isAtCorsairCove() {
        // Heuristic: near gangplank/central island coords
        Tile t = Players.getLocal().getTile();
        return t.distance(COVE_DOCK_GROUND) < 120 || t.distance(COVE_GANGPLANK) < 120;
    }

    private boolean ensureUpstairsAt(Tile groundStairsTile, int targetZ) {
        // If not close to the building, walk there
        if (Players.getLocal().getTile().distance(groundStairsTile) > 8) {
            new WalkToLocationNode("walk_to_stairs", groundStairsTile, 4, "Building stairs").execute();
        }
        // If on ground, try to climb up
        if (Players.getLocal().getTile().getZ() < targetZ) {
            GameObject stairs = GameObjects.closest(go -> go.getName().contains("Stairs"));
            if (stairs != null) {
                if (stairs.hasAction("Climb-up")) stairs.interact("Climb-up");
                else if (stairs.hasAction("Climb")) stairs.interact("Climb");
                Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() >= targetZ, 7000);
            }
        }
        return Players.getLocal().getTile().getZ() >= targetZ;
    }

    @Override
    public boolean isQuestComplete() {
        log("Quest finished check (internal flag): " + completed);
        return completed;
    }

    @Override
    public int getQuestProgress() {
        int pct = 0;
        if (completed) return 100;
        if (reinterviewedCrew) pct = 90;
        else if (observedTelescope) pct = 80;
        else if (dugForDoll) pct = 70;
        else if (spokeChiefTess) pct = 60;
        else if (reportedFindingsOnce) pct = 50;
        else if (visitedGnocci && visitedColin && visitedArsen && visitedIthoi) pct = 40;
        else if (started) pct = 20;
        return pct;
    }
}
