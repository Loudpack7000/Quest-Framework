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
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;
import org.dreambot.api.methods.settings.PlayerSettings;
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
 * - Uses PlayerSettings config/varbit tracking for precise quest state
 * - Uses DreamBot's Quests API for high-level status when available
 * - Implements proper resumability by detecting current quest progress
 */
public class CorsairCurseTree extends QuestTree {

    // Quest configuration tracking - DISCOVERED from manual logs
    private static final int CORSAIR_CURSE_VARBIT_ID = 6071; // DISCOVERED: Quest uses varbit 6071
    private static final int CORSAIR_CURSE_CONFIG_ID = 1404; // Fallback config ID
    
    // DISCOVERED: Exact varbit progression from manual completion
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_AT_COVE = 15;
    private static final int QUEST_FIRST_INTERVIEW = 20;
    private static final int QUEST_FIRST_REPORT = 25;
    private static final int QUEST_GNOCCI_DONE = 30;
    private static final int QUEST_ARSEN_FOLLOWUP = 35;
    private static final int QUEST_ITHOI_CONFRONTED = 40;
    private static final int QUEST_ITHOI_ACCUSED = 45;
    private static final int QUEST_FIRE_LIT = 49;
    private static final int QUEST_ITHOI_CAUGHT = 50;
    private static final int QUEST_ITHOI_REPORTED = 52;
    private static final int QUEST_ITHOI_DEFEATED = 55;
    private static final int QUEST_COMPLETED = 60;

    // Key tiles from recording (verified coordinates)
    private static final Tile PORT_SARIM_TOCK = new Tile(2906, 3226, 0); // Updated to correct location
    private static final Tile RIMMINGTON_TOCK = new Tile(3030, 3273, 0); // Keep as backup
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
    private static final Tile DOLL_DIG_TILE = new Tile(2504, 2840, 0); // Near palm tree
    private static final Tile TELESCOPE_TILE = new Tile(2529, 2835, 1);
    
    // DISCOVERED: Ithoi boss fight area coordinates
    private static final Tile ITHOI_BOSS_AREA = new Tile(12257, 2011, 1); // Combat cutscene area

    // Items
    private static final String SPADE = "Spade";
    private static final String TINDERBOX = "Tinderbox";
    private static final String DRIFTWOOD = "Driftwood";
    private static final int OGRE_ARTEFACT_ID = 21837; // Ogre artefact item ID

    // Session flags - now enhanced with discovered quest mechanics
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
    
    // DISCOVERED: New quest mechanics from manual logs
    private boolean confrontedIthoi;
    private boolean accusedIthoi;
    private boolean litFire;
    private boolean caughtIthoi;
    private boolean reportedIthoi;
    private boolean defeatedIthoi;

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
    
    // DISCOVERED: New quest nodes from manual completion
    private QuestNode confrontIthoiNode;
    private QuestNode accuseIthoiNode;
    private QuestNode lightFireNode;
    private QuestNode fightIthoiNode;
    private QuestNode finalReportNode;

    public CorsairCurseTree() {
        super("The Corsair Curse");
    }

    @Override
    protected void buildTree() {
        createNodes();
        rootNode = smart;
    }

    private void createNodes() {
        // Start: Talk to Captain Tock to begin quest (try both locations)
        startWithTock = new ActionNode("start_tock_quest", "Talk to Captain Tock to begin") {
            @Override
            protected boolean performAction() {
                log("Starting quest - looking for Captain Tock to begin The Corsair Curse");
                
                // First try Rimmington location (traditional quest start)
                NPC tock = NPCs.closest("Captain Tock");
                if (tock != null && tock.getTile().distance(RIMMINGTON_TOCK) < 20) {
                    log("Found Captain Tock at Rimmington - starting quest there");
                    QuestNode talk = new TalkToNPCNode("talk_tock_start_rim", "Captain Tock", RIMMINGTON_TOCK);
                    boolean ok = talk.execute().isSuccess();
                    if (ok) {
                        started = true;
                        log("Successfully started The Corsair Curse quest at Rimmington");
                        return true;
                    }
                }
                
                // If not found at Rimmington or failed, try Port Sarim
                if (Players.getLocal().getTile().distance(PORT_SARIM_TOCK) < 50) {
                    log("Trying Captain Tock at Port Sarim location");
                    QuestNode talk = new TalkToNPCNode("talk_tock_start_ps", "Captain Tock", PORT_SARIM_TOCK);
                    boolean ok = talk.execute().isSuccess();
                    if (ok) {
                        started = true;
                        log("Successfully started The Corsair Curse quest at Port Sarim");
                        return true;
                    }
                }
                
                // Walk to Rimmington as primary quest start location
                if (Players.getLocal().getTile().distance(RIMMINGTON_TOCK) > 15) {
                    log("Walking to Rimmington to find Captain Tock");
                    new WalkToLocationNode("walk_to_rim_tock", RIMMINGTON_TOCK, 8, "Captain Tock (Rimmington)").execute();
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(RIMMINGTON_TOCK) <= 15, 20000);
                }
                
                // Final attempt at Rimmington
                QuestNode talk = new TalkToNPCNode("talk_tock_start_final", "Captain Tock", RIMMINGTON_TOCK);
                boolean ok = talk.execute().isSuccess();
                if (ok) {
                    started = true;
                    log("Successfully started The Corsair Curse quest");
                } else {
                    log("Failed to start quest with Captain Tock - will retry");
                }
                return ok;
            }
        };

        // Travel: Use Captain Tock / gangplank to reach Corsair Cove
        travelToCorsairCove = new ActionNode("travel_corsair_cove", "Travel to Corsair Cove") {
            @Override
            protected boolean performAction() {
                // Check if already at Corsair Cove (on the island, not just dock)
                if (isAtCorsairCoveIsland()) {
                    log("Already at Corsair Cove island - travel complete");
                    return true;
                }

                // Check if we're at the dock but haven't crossed the gangplank yet
                if (isAtCorsairCoveDock() && !isAtCorsairCoveIsland()) {
                    log("At dock - looking for gangplank to cross");
                    GameObject gangplank = GameObjects.closest("Gangplank");
                    if (gangplank != null && gangplank.interact("Cross")) {
                        log("Crossing gangplank to Corsair Cove");
                        Sleep.sleepUntil(() -> isAtCorsairCoveIsland() || Players.getLocal().getTile().distance(COVE_GANGPLANK) < 10, 15000);
                        if (isAtCorsairCoveIsland()) {
                            log("Successfully arrived at Corsair Cove island");
                            return true;
                        }
                    }
                }

                // Not at Corsair Cove - need to find Captain Tock to sail there
                log("Not at Corsair Cove - need to find Captain Tock to sail");

                // First, try to find Captain Tock nearby
                NPC tock = NPCs.closest("Captain Tock");
                if (tock != null && tock.getTile().distance(Players.getLocal().getTile()) < 15) {
                    log("Found Captain Tock nearby - attempting to sail");
                    return talkToTockForTravel(tock);
                }

                // Captain Tock not nearby - navigate to Port Sarim location
                Tile tockLocation = PORT_SARIM_TOCK; // Use the corrected coordinates
                if (Players.getLocal().getTile().distance(tockLocation) > 8) {
                    log("Walking to Captain Tock at Port Sarim: " + tockLocation);
                    new WalkToLocationNode("walk_to_tock", tockLocation, 6, "Captain Tock").execute();
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(tockLocation) <= 8, 20000);
                }

                // Now try to find Captain Tock again
                tock = NPCs.closest("Captain Tock");
                if (tock != null) {
                    log("Arrived at Captain Tock - attempting to sail");
                    return talkToTockForTravel(tock);
                } else {
                    log("Captain Tock not found at expected location - waiting for spawn");
                    Sleep.sleep(3000, 5000); // Wait for NPC to spawn/load
                    tock = NPCs.closest("Captain Tock");
                    if (tock != null) {
                        return talkToTockForTravel(tock);
                    }
                }

                log("Unable to find Captain Tock - will retry");
                return false; // Retry the travel node
            }

            private boolean talkToTockForTravel(NPC tock) {
                if (tock.interact("Talk-to")) {
                    if (Sleep.sleepUntil(Dialogues::inDialogue, 8000)) {
                        log("Dialogue opened with Captain Tock");

                        // Progress through dialogue to find travel option
                        int guard = 0;
                        boolean foundTravelOption = false;

                        while (Dialogues.inDialogue() && guard++ < 25 && !foundTravelOption) {
                            if (Dialogues.areOptionsAvailable()) {
                                String[] opts = Dialogues.getOptions();
                                log("Available dialogue options: " + String.join(", ", opts));

                                for (int i = 0; i < opts.length; i++) {
                                    String option = opts[i].toLowerCase();
                                    if (option.contains("ready") ||
                                        option.contains("corsair cove") ||
                                        option.contains("take me") ||
                                        option.contains("sail") ||
                                        option.contains("travel")) {
                                        log("Selecting travel option: " + opts[i]);
                                        Dialogues.chooseOption(i + 1);
                                        foundTravelOption = true;
                                        break;
                                    }
                                }

                                // If no specific travel option found, try first option as fallback
                                if (!foundTravelOption && opts.length > 0) {
                                    log("No specific travel option found, selecting first: " + opts[0]);
                                    Dialogues.chooseOption(1);
                                }
                            } else if (Dialogues.canContinue()) {
                                if (!Dialogues.spaceToContinue()) {
                                    Dialogues.continueDialogue();
                                }
                            }
                            Sleep.sleep(800, 1200);
                        }

                        // Wait for travel to complete
                        if (foundTravelOption) {
                            log("Waiting for travel to Corsair Cove to complete...");
                            Sleep.sleepUntil(() -> isAtCorsairCoveDock() || isAtCorsairCoveIsland(), 30000);

                            if (isAtCorsairCoveIsland()) {
                                log("Successfully sailed to Corsair Cove island");
                                return true;
                            } else if (isAtCorsairCoveDock()) {
                                log("Arrived at Corsair Cove dock - will cross gangplank next");
                                return true;
                            }
                        }
                    } else {
                        log("Failed to open dialogue with Captain Tock");
                    }
                } else {
                    log("Failed to interact with Captain Tock");
                }

                return false;
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
                log("Attempting to report findings to Captain Tock on ship");
                
                // Check if we need to get to the ship level (Z=1)
                if (Players.getLocal().getTile().getZ() == 0) {
                    log("Currently on ground level - need to board ship");
                    
                    // Walk to gangplank area if not close
                    if (Players.getLocal().getTile().distance(COVE_DOCK_GROUND) > 15) {
                        log("Walking to dock area near gangplank");
                        new WalkToLocationNode("walk_to_dock", COVE_DOCK_GROUND, 8, "Dock area").execute();
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(COVE_DOCK_GROUND) <= 15, 10000);
                    }
                    
                    // Find and cross gangplank
                    GameObject gangplank = GameObjects.closest("Gangplank");
                    if (gangplank != null) {
                        log("Found gangplank at: " + gangplank.getTile() + " - crossing to ship");
                        if (gangplank.interact("Cross")) {
                            Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 10000);
                            if (Players.getLocal().getTile().getZ() == 1) {
                                log("Successfully boarded ship");
                            } else {
                                log("Failed to board ship - still on ground level");
                                return false;
                            }
                        } else {
                            log("Failed to interact with gangplank");
                            return false;
                        }
                    } else {
                        log("Gangplank not found - cannot board ship");
                        return false;
                    }
                } else {
                    log("Already on ship level");
                }
                
                // Now talk to Captain Tock on the ship
                QuestNode talk = new TalkToNPCNode("talk_tock_ship", "Captain Tock", TOCK_ON_SHIP);
                boolean ok = talk.execute().isSuccess();
                if (ok) {
                    log("Successfully reported findings to Captain Tock");
                    reportedFindingsOnce = true;
                } else {
                    log("Failed to report findings to Captain Tock");
                }
                return ok;
            }
        };

        finalReportToTock = new ActionNode("final_report_tock", "Report final findings to Captain Tock") {
            @Override
            protected boolean performAction() {
                log("Attempting final report to Captain Tock on ship");
                
                // Check if we need to get to the ship level (Z=1)
                if (Players.getLocal().getTile().getZ() == 0) {
                    log("Currently on ground level - need to board ship for final report");
                    
                    // Walk to gangplank area if not close
                    if (Players.getLocal().getTile().distance(COVE_DOCK_GROUND) > 15) {
                        log("Walking to dock area near gangplank");
                        new WalkToLocationNode("walk_to_dock_final", COVE_DOCK_GROUND, 8, "Dock area").execute();
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(COVE_DOCK_GROUND) <= 15, 10000);
                    }
                    
                    // Find and cross gangplank
                    GameObject gangplank = GameObjects.closest("Gangplank");
                    if (gangplank != null) {
                        log("Found gangplank at: " + gangplank.getTile() + " - crossing to ship for final report");
                        if (gangplank.interact("Cross")) {
                            Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 10000);
                            if (Players.getLocal().getTile().getZ() == 1) {
                                log("Successfully boarded ship for final report");
                            } else {
                                log("Failed to board ship - still on ground level");
                                return false;
                            }
                        } else {
                            log("Failed to interact with gangplank");
                            return false;
                        }
                    } else {
                        log("Gangplank not found - cannot board ship");
                        return false;
                    }
                } else {
                    log("Already on ship level for final report");
                }
                
                // Final talk to Captain Tock
                QuestNode talk = new TalkToNPCNode("talk_tock_final", "Captain Tock", TOCK_ON_SHIP);
                boolean ok = talk.execute().isSuccess();
                if (ok) {
                    log("Successfully completed final report - quest should be finished!");
                    completed = true;
                } else {
                    log("Failed to complete final report to Captain Tock");
                }
                return ok;
            }
        };

        visitOgreChief = new ActionNode("visit_ogre_chief", "Return relic and speak to Chief Tess") {
            @Override
            protected boolean performAction() {
                // First ensure we're at ground level near the hole
                if (Players.getLocal().getTile().getZ() != 0) {
                    log("Need to be at ground level to enter ogre cave");
                    return false;
                }

                // Enter hole to cave
                GameObject hole = GameObjects.closest("Hole");
                if (hole == null || Players.getLocal().getTile().distance(OGRE_HOLE_GROUND) > 8) {
                    // Walk to hole
                    new WalkToLocationNode("walk_hole", OGRE_HOLE_GROUND, 3, "Ogre cave hole").execute();
                    hole = GameObjects.closest("Hole");
                }

                if (hole != null && hole.interact("Enter")) {
                    log("Entering ogre cave...");
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(OGRE_CAVE_CHIEF) < 50, 10000);
                } else {
                    log("Failed to enter ogre cave hole");
                    return false;
                }

                // Talk to Chief Tess
                QuestNode talk = new TalkToNPCNode("talk_chief_tess", "Chief Tess", OGRE_CAVE_CHIEF);
                boolean ok = talk.execute().isSuccess();
                if (!ok) {
                    log("Failed to speak with Chief Tess");
                    return false;
                }
                spokeChiefTess = true;
                log("Successfully spoke with Chief Tess and returned relic");

                // Wait a moment for any post-dialogue processing
                Sleep.sleep(2000, 3000);

                // Climb vine ladder to leave (must be done to progress quest)
                GameObject vine = GameObjects.closest(go -> go.getName().contains("Vine") ||
                                                      go.getName().contains("Vine ladder") ||
                                                      go.getName().contains("Climbing rope"));

                if (vine == null) {
                    // Walk to vine tile as fallback
                    log("Vine not found, walking to vine location");
                    new WalkToLocationNode("walk_vine", OGRE_VINE_LADDER, 2, "Vine ladder").execute();
                    vine = GameObjects.closest(go -> go.getName().contains("Vine") ||
                                               go.getName().contains("Vine ladder") ||
                                               go.getName().contains("Climbing rope"));
                }

                if (vine != null) {
                    String action = null;
                    if (vine.hasAction("Climb")) action = "Climb";
                    else if (vine.hasAction("Climb-up")) action = "Climb-up";
                    else if (vine.hasAction("Climb-down")) action = "Climb-down";

                    if (action != null) {
                        log("Climbing vine ladder to exit cave...");
                        vine.interact(action);
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 0, 10000);
                    } else {
                        log("Vine ladder has no valid climb action");
                        return false;
                    }
                } else {
                    log("Cannot find vine ladder to exit cave");
                    return false;
                }

                climbedOutOfOgreCave = (Players.getLocal().getTile().getZ() == 0);
                if (climbedOutOfOgreCave) {
                    log("Successfully exited ogre cave");
                } else {
                    log("Failed to exit ogre cave - still underground");
                }

                return climbedOutOfOgreCave;
            }
        };

        digForDollNode = new ActionNode("dig_for_doll", "Dig for the possessed doll") {
            @Override
            protected boolean performAction() {
                // Walk to dig location if not already there
                if (Players.getLocal().getTile().distance(DOLL_DIG_TILE) > 4) {
                    log("Walking to dig location near palm tree");
                    new WalkToLocationNode("walk_dig_tile", DOLL_DIG_TILE, 2, "Doll dig tile").execute();
                }

                // Verify we have a spade
                if (!Inventory.contains(SPADE)) {
                    log("Missing Spade - cannot dig for possessed doll");
                    return false;
                }

                // Dig at the location
                log("Digging for possessed doll...");
                if (!Inventory.interact(SPADE, "Dig")) {
                    log("Failed to dig - spade interaction failed");
                    return false;
                }

                // Wait for dig animation and potential dialogue
                Sleep.sleep(2000, 3000);

                // Handle the search dialogue that appears after digging
                if (Sleep.sleepUntil(Dialogues::inDialogue, 8000)) {
                    log("Search dialogue appeared after digging");

                    // Continue through search dialogue
                    int dialogueGuard = 0;
                    while (Dialogues.inDialogue() && dialogueGuard++ < 15) {
                        if (Dialogues.canContinue()) {
                            if (!Dialogues.spaceToContinue()) {
                                Dialogues.continueDialogue();
                            }
                        } else if (Dialogues.areOptionsAvailable()) {
                            String[] options = Dialogues.getOptions();
                            boolean foundValidOption = false;

                            // Look for options related to searching or the doll
                            for (int i = 0; i < options.length; i++) {
                                String option = options[i].toLowerCase();
                                if (option.contains("search") ||
                                    option.contains("possessed doll") ||
                                    option.contains("investigate") ||
                                    option.contains("examine")) {
                                    log("Selecting search option: " + options[i]);
                                    Dialogues.chooseOption(i + 1);
                                    foundValidOption = true;
                                    break;
                                }
                            }

                            // If no specific option found, take the first available
                            if (!foundValidOption) {
                                log("No specific search option found, selecting first option");
                                Dialogues.chooseOption(1);
                            }
                        }
                        Sleep.sleep(800, 1200);
                    }

                    // Check if we successfully found the doll
                    if (!Dialogues.inDialogue()) {
                        log("Successfully dug up and examined the possessed doll");
                        dugForDoll = true;
                        return true;
                    } else {
                        log("Dialogue still open after processing - may need manual intervention");
                        return false;
                    }
                } else {
                    log("No dialogue appeared after digging - checking if doll was found anyway");
                    // Sometimes the doll might be found without dialogue
                    Sleep.sleep(2000, 3000);
                    dugForDoll = true; // Assume success if no dialogue issues
                    return true;
                }
            }
        };

        observeTelescopeNode = new ActionNode("observe_telescope", "Observe the telescope") {
            @Override
            protected boolean performAction() {
                // Ensure we're in the navigation room upstairs
                if (!ensureUpstairsAt(ITHOI_STAIRS_GROUND, 1)) {
                    log("Failed to reach navigation room for telescope observation");
                    return false;
                }

                // Find and interact with telescope
                GameObject telescope = GameObjects.closest("Telescope");
                if (telescope == null) {
                    log("Cannot find telescope in navigation room");
                    return false;
                }

                if (telescope.interact("Observe")) {
                    log("Observing telescope...");

                    // Wait for observation animation and any resulting dialogue
                    Sleep.sleep(2000, 3000);

                    // Handle any observation dialogue
                    if (Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
                        log("Observation dialogue appeared");

                        int dialogueGuard = 0;
                        while (Dialogues.inDialogue() && dialogueGuard++ < 10) {
                            if (Dialogues.canContinue()) {
                                if (!Dialogues.spaceToContinue()) {
                                    Dialogues.continueDialogue();
                                }
                            } else if (Dialogues.areOptionsAvailable()) {
                                // For telescope observation, usually just continue
                                Dialogues.chooseOption(1);
                            }
                            Sleep.sleep(800, 1200);
                        }

                        if (!Dialogues.inDialogue()) {
                            log("Successfully observed telescope and processed results");
                            observedTelescope = true;
                            return true;
                        } else {
                            log("Observation dialogue did not complete properly");
                            return false;
                        }
                    } else {
                        // No dialogue - observation might still be successful
                        log("Telescope observed successfully (no dialogue)");
                        observedTelescope = true;
                        return true;
                    }
                } else {
                    log("Failed to interact with telescope");
                    return false;
                }
            }
        };

        reinterviewCrewNode = new ActionNode("reinterview_crew", "Re-interview crew with new theories") {
            @Override
            protected boolean performAction() {
                boolean allReinterviewed = true;

                // Re-interview Gnocci the Cook (must be done first for new dialogue)
                if (!ensureUpstairsAt(COOK_STAIRS_GROUND, 1)) {
                    log("Failed to reach cook's quarters for reinterview");
                    return false;
                }
                if (!new TalkToNPCNode("re_talk_gnocci", "Gnocci the Cook", GNOCCHI_LOCATION).execute().isSuccess()) {
                    log("Failed to reinterview Gnocci");
                    allReinterviewed = false;
                }

                // Re-interview Arsen the Thief
                if (!ensureUpstairsAt(TOWER_STAIRS_GROUND, 1)) {
                    log("Failed to reach tower for Arsen reinterview");
                    return false;
                }
                if (!new TalkToNPCNode("re_talk_arsen", "Arsen the Thief", ARSEN_LOCATION).execute().isSuccess()) {
                    log("Failed to reinterview Arsen");
                    allReinterviewed = false;
                }

                // Re-interview Cabin Boy Colin
                if (!new TalkToNPCNode("re_talk_colin", "Cabin Boy Colin", COLIN_LOCATION).execute().isSuccess()) {
                    log("Failed to reinterview Colin");
                    allReinterviewed = false;
                }

                // Re-interview Ithoi the Navigator
                if (!ensureUpstairsAt(ITHOI_STAIRS_GROUND, 1)) {
                    log("Failed to reach navigation room for Ithoi reinterview");
                    return false;
                }
                if (!new TalkToNPCNode("re_talk_ithoi", "Ithoi the Navigator", ITHOI_LOCATION).execute().isSuccess()) {
                    log("Failed to reinterview Ithoi");
                    allReinterviewed = false;
                }

                if (allReinterviewed) {
                    log("Successfully reinterviewed all crew members");
                    reinterviewedCrew = true;
                    return true;
                } else {
                    log("Some crew reinterviews failed - will retry");
                    return false; // Allow retry
                }
            }
        };

        // DISCOVERED: New quest nodes from manual completion logs
        
        // Confront Ithoi about the curse
        confrontIthoiNode = new ActionNode("confront_ithoi", "Confront Ithoi about the curse") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(ITHOI_STAIRS_GROUND, 1)) return false;
                
                QuestNode talk = new TalkToNPCNode("talk_ithoi_confront", "Ithoi the Navigator", ITHOI_LOCATION);
                boolean ok = talk.execute().isSuccess();
                if (ok) {
                    log("Confronted Ithoi about the curse");
                    confrontedIthoi = true;
                }
                return ok;
            }
        };

        // Accuse Ithoi of faking the curse
        accuseIthoiNode = new ActionNode("accuse_ithoi", "Accuse Ithoi of faking the curse") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(ITHOI_STAIRS_GROUND, 1)) return false;
                
                QuestNode talk = new TalkToNPCNode("talk_ithoi_accuse", "Ithoi the Navigator", ITHOI_LOCATION);
                boolean ok = talk.execute().isSuccess();
                if (ok) {
                    log("Accused Ithoi of faking the curse");
                    accusedIthoi = true;
                }
                return ok;
            }
        };

        // Light fire to prove Ithoi can move
        lightFireNode = new ActionNode("light_fire", "Light fire to prove Ithoi can move") {
            @Override
            protected boolean performAction() {
                if (!ensureUpstairsAt(ITHOI_STAIRS_GROUND, 1)) return false;
                
                // Check we have tinderbox and driftwood
                if (!Inventory.contains(TINDERBOX)) {
                    log("Missing Tinderbox - cannot light fire");
                    return false;
                }
                
                if (!Inventory.contains(DRIFTWOOD)) {
                    log("Missing Driftwood - cannot light fire");
                    return false;
                }
                
                log("Using Tinderbox on Driftwood to light fire...");
                if (Inventory.interact(TINDERBOX, "Use")) {
                    Sleep.sleep(1000, 1500);
                    if (Inventory.interact(DRIFTWOOD, "Use")) {
                        Sleep.sleepUntil(() -> getQuestVarbit() >= QUEST_FIRE_LIT, 8000);
                        if (getQuestVarbit() >= QUEST_FIRE_LIT) {
                            log("Successfully lit fire - Ithoi should move now");
                            litFire = true;
                            caughtIthoi = true; // Fire automatically catches Ithoi
                            return true;
                        }
                    }
                }
                
                log("Failed to light fire");
                return false;
            }
        };

        // Fight Ithoi in the boss area
        fightIthoiNode = new ActionNode("fight_ithoi", "Fight Ithoi the Navigator") {
            @Override
            protected boolean performAction() {
                // Check if we're in the boss fight area
                Tile currentTile = Players.getLocal().getTile();
                boolean inBossArea = currentTile.getX() > 10000; // Boss area has X > 10000
                
                if (!inBossArea) {
                    log("Not in boss fight area - waiting for cutscene teleport");
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().getX() > 10000, 15000);
                    
                    if (Players.getLocal().getTile().getX() <= 10000) {
                        log("Cutscene teleport didn't happen - may need to trigger it");
                        return false;
                    }
                }
                
                log("In boss fight area - attacking Ithoi the Navigator");
                
                // Find and attack Ithoi
                NPC ithoi = NPCs.closest("Ithoi the Navigator");
                int attempts = 0;
                
                while (ithoi != null && attempts < 30) {
                    if (!Players.getLocal().isInCombat()) {
                        if (ithoi.interact("Attack")) {
                            log("Attacking Ithoi the Navigator (attempt " + (attempts + 1) + ")");
                            Sleep.sleepUntil(() -> Players.getLocal().isInCombat(), 3000);
                        }
                    }
                    
                    // Wait for combat to finish
                    Sleep.sleep(2000, 3000);
                    
                    // Check if Ithoi is defeated (quest progresses)
                    if (getQuestVarbit() >= QUEST_ITHOI_DEFEATED) {
                        log("Ithoi defeated! Quest should progress now");
                        defeatedIthoi = true;
                        return true;
                    }
                    
                    ithoi = NPCs.closest("Ithoi the Navigator");
                    attempts++;
                }
                
                // Check final quest state
                if (getQuestVarbit() >= QUEST_ITHOI_DEFEATED) {
                    log("Ithoi defeated successfully");
                    defeatedIthoi = true;
                    return true;
                }
                
                log("Failed to defeat Ithoi after " + attempts + " attempts");
                return false;
            }
        };

        // Final report to Captain Tock after defeating Ithoi
        finalReportNode = new ActionNode("final_report_ithoi", "Report Ithoi's defeat to Captain Tock") {
            @Override
            protected boolean performAction() {
                log("Attempting final report about Ithoi's defeat to Captain Tock");
                
                // Ensure we're on the ship
                if (Players.getLocal().getTile().getZ() == 0) {
                    log("Currently on ground level - need to board ship for final report");
                    
                    if (Players.getLocal().getTile().distance(COVE_DOCK_GROUND) > 15) {
                        log("Walking to dock area near gangplank");
                        new WalkToLocationNode("walk_to_dock_final_ithoi", COVE_DOCK_GROUND, 8, "Dock area").execute();
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(COVE_DOCK_GROUND) <= 15, 10000);
                    }
                    
                    GameObject gangplank = GameObjects.closest("Gangplank");
                    if (gangplank != null) {
                        log("Found gangplank - crossing to ship for final report");
                        if (gangplank.interact("Cross")) {
                            Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 10000);
                        }
                    }
                }
                
                // Talk to Captain Tock with final report
                QuestNode talk = new TalkToNPCNode("talk_tock_final_ithoi", "Captain Tock", TOCK_ON_SHIP);
                boolean ok = talk.execute().isSuccess();
                
                if (ok) {
                    log("Successfully reported Ithoi's defeat - quest should be complete!");
                    completed = true;
                    return true;
                }
                
                log("Failed to complete final report to Captain Tock");
                return false;
            }
        };

        // Smart decision node - routes based on quest and session state
        smart = new QuestNode("smart_decision", "Smart decision for The Corsair Curse") {
            @Override
            public ExecutionResult execute() {
                // Sync quest state from world first
                syncFromWorldState();
                
                // Check if quest is finished using DreamBot API if available
                try {
                    // Try to use FreeQuest enum if it exists for Corsair Curse
                    // Note: May need adjustment if enum name differs
                    if (isQuestFinishedByAPI()) {
                        log("Quest already completed via API");
                        setQuestComplete();
                        return ExecutionResult.questComplete();
                    }
                } catch (Exception e) {
                    log("API quest check failed, using fallback: " + e.getMessage());
                }

                // Check quest config for completion
                int questConfig = getQuestConfig();
                log("Quest config " + CORSAIR_CURSE_CONFIG_ID + " = " + questConfig);
                
                if (completed) {
                    log("Quest marked completed internally");
                    setQuestComplete();
                    return ExecutionResult.questComplete();
                }

                // Determine if quest is started using API or config
                boolean questStarted = isQuestStartedByAPI() || questConfig >= QUEST_AT_COVE;
                log("Quest started check: API=" + isQuestStartedByAPI() + ", config=" + (questConfig >= QUEST_AT_COVE) + ", result=" + questStarted);

                // Start check
                if (!questStarted) {
                    log("Quest not started - talk to Captain Tock to begin quest");
                    return ExecutionResult.success(startWithTock);
                }

                // Quest is started, ensure we're at Corsair Cove
                if (!isAtCorsairCove()) {
                    log("Quest started but not at Corsair Cove - traveling there");
                    return ExecutionResult.success(travelToCorsairCove);
                }

                // RESUMABILITY CHECK: If we have Ogre artefact, skip to Chief Tess step
                boolean hasOgreArtefact = Inventory.contains(21837); // Ogre artefact
                if (hasOgreArtefact && !spokeChiefTess) {
                    log("RESUMABILITY: Have Ogre artefact - going directly to Chief Tess");
                    return ExecutionResult.success(visitOgreChief);
                }

                // Progressive quest steps based on official OSRS sequence
                // Step 1-4: Initial crew interviews (must be done in this order)
                log("DEBUG: Crew visit status - Ithoi:" + visitedIthoi + " Arsen:" + visitedArsen +
                    " Colin:" + visitedColin + " Gnocci:" + visitedGnocci + " HasArtefact:" + hasOgreArtefact);

                if (!visitedIthoi) {
                    log("Need to interview Ithoi the Navigator first");
                    return ExecutionResult.success(talkIthoi);
                }

                if (!visitedArsen) {
                    log("Need to interview Arsen the Thief");
                    return ExecutionResult.success(talkArsen);
                }

                if (!visitedColin) {
                    log("Need to interview Cabin Boy Colin");
                    return ExecutionResult.success(talkColin);
                }

                if (!visitedGnocci) {
                    log("Need to interview Gnocci the Cook");
                    return ExecutionResult.success(talkGnocci);
                }

                // Step 5: Report initial findings to Captain Tock
                if (!reportedFindingsOnce) {
                    log("Need to report initial findings to Captain Tock");
                    return ExecutionResult.success(reportToTockOnShip);
                }

                // Step 6: Visit ogre cave and speak to Chief Tess
                if (!spokeChiefTess || !climbedOutOfOgreCave) {
                    log("Need to visit ogre cave and speak to Chief Tess");
                    return ExecutionResult.success(visitOgreChief);
                }

                // Step 7: Dig for possessed doll
                if (!dugForDoll) {
                    log("Need to dig for the possessed doll");
                    return ExecutionResult.success(digForDollNode);
                }

                // Step 8: Observe telescope
                if (!observedTelescope) {
                    log("Need to observe the telescope");
                    return ExecutionResult.success(observeTelescopeNode);
                }

                // Step 9: Re-interview crew with new information
                if (!reinterviewedCrew) {
                    log("Need to re-interview crew with new theories");
                    return ExecutionResult.success(reinterviewCrewNode);
                }

                // DISCOVERED: New quest steps from manual completion
                // Step 10: Confront Ithoi about the curse
                if (!confrontedIthoi) {
                    log("Need to confront Ithoi about the curse");
                    return ExecutionResult.success(confrontIthoiNode);
                }

                // Step 11: Accuse Ithoi of faking the curse
                if (!accusedIthoi) {
                    log("Need to accuse Ithoi of faking the curse");
                    return ExecutionResult.success(accuseIthoiNode);
                }

                // Step 12: Light fire to prove Ithoi can move
                if (!litFire || !caughtIthoi) {
                    log("Need to light fire to catch Ithoi moving");
                    return ExecutionResult.success(lightFireNode);
                }

                // Step 13: Report Ithoi to Captain Tock
                if (!reportedIthoi && caughtIthoi) {
                    log("Need to report Ithoi to Captain Tock");
                    return ExecutionResult.success(reportToTockOnShip);
                }

                // Step 14: Fight Ithoi in boss area (after cutscene)
                if (!defeatedIthoi) {
                    log("Need to fight and defeat Ithoi");
                    return ExecutionResult.success(fightIthoiNode);
                }

                // Step 15: Final report about Ithoi's defeat
                log("All investigation complete - final report about defeating Ithoi");
                return ExecutionResult.success(finalReportNode, "Final report about Ithoi's defeat");
            }
        };
    }

    private boolean isAtCorsairCove() {
        // Legacy method - now uses more precise checks
        return isAtCorsairCoveDock() || isAtCorsairCoveIsland();
    }

    private boolean isAtCorsairCoveDock() {
        // Check if at the dock area (ground level near gangplank)
        Tile t = Players.getLocal().getTile();
        return t.distance(COVE_DOCK_GROUND) < 30 && t.getZ() == 0;
    }

    private boolean isAtCorsairCoveIsland() {
        // Check if on the Corsair Cove island (ship level or crew areas)
        Tile t = Players.getLocal().getTile();
        boolean nearIsland = t.distance(COVE_GANGPLANK) < 50;
        boolean onIslandLevel = t.getZ() == 1; // Most of the island is on Z=1
        boolean inIslandArea = (t.getX() >= 2520 && t.getX() <= 2580 && t.getY() >= 2830 && t.getY() <= 2870);

        return (nearIsland && onIslandLevel) || inIslandArea;
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

    /**
     * Get current quest configuration value
     */
    private int getQuestConfig() {
        try {
            return PlayerSettings.getConfig(CORSAIR_CURSE_CONFIG_ID);
        } catch (Exception e) {
            log("Failed to get quest config: " + e.getMessage());
            return 0;
        }
    }

    /**
     * DISCOVERED: Get quest varbit value for precise state detection
     */
    private int getQuestVarbit() {
        try {
            return PlayerSettings.getBitValue(CORSAIR_CURSE_VARBIT_ID);
        } catch (Exception e) {
            log("Failed to get quest varbit: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Check if quest is started using DreamBot API
     */
    private boolean isQuestStartedByAPI() {
        try {
            // Use the correct FreeQuest enum value from DreamBot API
            return Quests.isStarted(FreeQuest.CORSAIR_CURSE);
        } catch (Exception e) {
            log("API quest started check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if quest is finished using DreamBot API
     */
    private boolean isQuestFinishedByAPI() {
        try {
            // Use the correct FreeQuest enum value from DreamBot API
            return Quests.isFinished(FreeQuest.CORSAIR_CURSE);
        } catch (Exception e) {
            log("API quest finished check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sync quest state from world state to avoid redoing completed steps
     */
    private void syncFromWorldState() {
        try {
            // If quest is finished via API, mark all steps complete
            if (isQuestFinishedByAPI()) {
                visitedIthoi = visitedArsen = visitedColin = visitedGnocci = true;
                reportedFindingsOnce = spokeChiefTess = climbedOutOfOgreCave = true;
                dugForDoll = observedTelescope = reinterviewedCrew = true;
                started = completed = true;
                return;
            }

            // If quest is started via API or config, mark as started
            if (isQuestStartedByAPI() || getQuestConfig() >= QUEST_AT_COVE) {
                started = true;
            }

            // DISCOVERED: Enhanced state detection using exact varbit values
            int questVarbit = getQuestVarbit();
            log("RESUMABILITY: Quest varbit 6071 = " + questVarbit);

            // Item-based state detection for better resumability
            boolean hasOgreArtefact = Inventory.contains(21837); // Ogre artefact
            boolean hasSpade = Inventory.contains("Spade");
            boolean hasTinderbox = Inventory.contains("Tinderbox");
            boolean hasDriftwood = Inventory.contains("Driftwood");

            if (hasOgreArtefact) {
                log("RESUMABILITY: Found Ogre artefact in inventory - need to visit Chief Tess");
                started = true;
                visitedIthoi = visitedArsen = visitedColin = visitedGnocci = true;
                reportedFindingsOnce = true;
                // Don't set spokeChiefTess = true, as we still need to do that step
            }

            // DISCOVERED: Use varbit values for precise state detection
            if (questVarbit >= QUEST_AT_COVE) {
                started = true;
                log("RESUMABILITY: Quest started (varbit >= " + QUEST_AT_COVE + ")");
            }
            if (questVarbit >= QUEST_FIRST_REPORT) {
                visitedIthoi = visitedArsen = visitedColin = visitedGnocci = true;
                reportedFindingsOnce = true;
                log("RESUMABILITY: Initial interviews completed (varbit >= " + QUEST_FIRST_REPORT + ")");
            }
            if (questVarbit >= QUEST_ITHOI_CONFRONTED) {
                reinterviewedCrew = true;
                log("RESUMABILITY: Crew re-interviews completed (varbit >= " + QUEST_ITHOI_CONFRONTED + ")");
            }
            if (questVarbit >= QUEST_ITHOI_ACCUSED) {
                confrontedIthoi = true;
                log("RESUMABILITY: Ithoi confronted (varbit >= " + QUEST_ITHOI_ACCUSED + ")");
            }
            if (questVarbit >= QUEST_FIRE_LIT) {
                accusedIthoi = true;
                log("RESUMABILITY: Ithoi accused (varbit >= " + QUEST_FIRE_LIT + ")");
            }
            if (questVarbit >= QUEST_ITHOI_CAUGHT) {
                litFire = caughtIthoi = true;
                log("RESUMABILITY: Fire lit and Ithoi caught (varbit >= " + QUEST_ITHOI_CAUGHT + ")");
            }
            if (questVarbit >= QUEST_ITHOI_REPORTED) {
                reportedIthoi = true;
                log("RESUMABILITY: Ithoi reported to Captain Tock (varbit >= " + QUEST_ITHOI_REPORTED + ")");
            }
            if (questVarbit >= QUEST_ITHOI_DEFEATED) {
                defeatedIthoi = true;
                log("RESUMABILITY: Ithoi defeated in combat (varbit >= " + QUEST_ITHOI_DEFEATED + ")");
            }
            if (questVarbit >= QUEST_COMPLETED) {
                completed = true;
                log("RESUMABILITY: Quest completed (varbit >= " + QUEST_COMPLETED + ")");
            }

            // Conservative location-based inference - only set flags if we're far into the quest
            if (Players.getLocal() != null) {
                Tile currentTile = Players.getLocal().getTile();

                // If we're at Corsair Cove, we've likely already started the quest
                if (isAtCorsairCove()) {
                    started = true;

                    // Only mark crew interviews as complete if we've progressed significantly
                    // (e.g., if we've already visited the ogre cave or dug for the doll)
                    if (spokeChiefTess || dugForDoll || observedTelescope || hasOgreArtefact) {
                        log("Advanced quest progress detected - assuming crew interviews completed");
                        visitedIthoi = visitedArsen = visitedColin = visitedGnocci = true;
                        if (!hasOgreArtefact) { // Only mark reported if we don't have the artefact
                            reportedFindingsOnce = true;
                        }
                    } else {
                        log("At Corsair Cove but haven't progressed far - will interview crew");
                    }
                }
            }

            log("Quest state sync: started=" + started + ", at_cove=" + isAtCorsairCove() + 
                ", visited_crew=" + (visitedIthoi && visitedArsen && visitedColin && visitedGnocci));
                
        } catch (Exception e) {
            log("Error syncing quest state: " + e.getMessage());
        }
    }

    @Override
    public boolean isQuestComplete() {
        // First check DreamBot API for authoritative quest completion status
        try {
            boolean apiFinished = Quests.isFinished(FreeQuest.CORSAIR_CURSE);
            if (apiFinished) {
                completed = true; // Sync internal state
                log("Quest finished check (DreamBot API): " + apiFinished);
                return true;
            }
        } catch (Exception e) {
            log("API quest completion check failed: " + e.getMessage());
        }
        
        // Fallback to internal flag
        log("Quest finished check (internal flag): " + completed);
        return completed;
    }

    @Override
    public int getQuestProgress() {
        // Check DreamBot API first for authoritative progress
        try {
            if (Quests.isFinished(FreeQuest.CORSAIR_CURSE)) return 100;
            if (Quests.isStarted(FreeQuest.CORSAIR_CURSE)) {
                // Use internal tracking for granular progress when quest is started
                int pct = 0;
                if (reinterviewedCrew) pct = 90;
                else if (observedTelescope) pct = 80;
                else if (dugForDoll) pct = 70;
                else if (spokeChiefTess) pct = 60;
                else if (reportedFindingsOnce) pct = 50;
                else if (visitedGnocci && visitedColin && visitedArsen && visitedIthoi) pct = 40;
                else pct = 20; // Quest started but minimal progress
                return pct;
            }
        } catch (Exception e) {
            log("API quest progress check failed: " + e.getMessage());
        }
        
        // Fallback to internal tracking
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
