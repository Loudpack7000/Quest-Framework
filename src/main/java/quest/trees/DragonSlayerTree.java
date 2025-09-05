package quest.trees;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.interactive.GameObject;

import quest.core.QuestNode;
import quest.core.QuestTree;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.nodes.actions.UseItemOnObjectNode;
import quest.utils.RunEnergyUtil;

/**
 * Dragon Slayer Quest Implementation - Stage 1: Getting Started
 * 
 * STAGE 1 FLOW:
 * 1. Talk to Guildmaster (Champions' Guild) → Config 176 = 1
 * 2. Travel to Oziach (Edgeville) 
 * 3. Talk to Oziach → Config 176 = 2, Config 177 = 63488
 * 4. Return to Guildmaster
 * 5. Get all quest information → Config 177 tracks dialogue flags
 * 6. Stage 1 complete when Config 177 = 0 (all info gathered)
 * 
 * Based on manual logs: quest_logs/QUEST_Free_Discovery_20250902_010659.log
 */
public class DragonSlayerTree extends QuestTree {

    // Dragon Slayer Quest Config IDs (discovered from logs)
    private static final int DRAGON_SLAYER_CONFIG = 176;  // Main progress tracker
    private static final int DRAGON_SLAYER_DIALOGUE_CONFIG = 177;  // Dialogue flags
    private static final int COMPLETE_VALUE = 2; // Stage 1 complete when config 176 = 2 and dialogue config 177 = 0
    
    // Quest progress states (Config 176 values)
    private static final int QUEST_NOT_STARTED = 0;
    private static final int QUEST_STARTED = 1;      // Talked to Guildmaster
    private static final int TALKED_TO_OZIACH = 2;   // Got quest details from Oziach
    
    // Dialogue info states (Config 177 values)
    private static final int ALL_INFO_GATHERED = 0;     // Stage 1 complete
    private static final int INITIAL_DIALOGUE = 63488;  // Just talked to Oziach
    
    // Key locations (from logs)
    private static final Tile CHAMPIONS_GUILD_TILE = new Tile(3191, 3362, 0);
    private static final Tile GUILDMASTER_TILE = new Tile(3189, 3359, 0);
    private static final Tile OZIACH_TILE = new Tile(3068, 3517, 0);
    private static final Tile ORACLE_TILE = new Tile(3013, 3500, 0);
    private static final Tile TRAPDOOR_TILE = new Tile(3019, 3450, 0);
    private static final Tile MAGIC_DOOR_TILE = new Tile(3049, 9839, 0);
    private static final Tile CHEST_TILE = new Tile(3057, 9841, 0);
    
    // Melzar's Maze coordinates (Stage 3)
    private static final Tile MELZAR_MAZE_ENTRANCE = new Tile(2941, 3248, 0);
    private static final Tile MELZAR_RED_DOOR = new Tile(2926, 3253, 0);
    private static final Tile MELZAR_FLOOR1_LADDER = new Tile(2924, 3250, 0);
    private static final Tile MELZAR_ORANGE_DOOR = new Tile(2931, 3247, 1);
    private static final Tile MELZAR_FLOOR2_LADDER = new Tile(2934, 3254, 1);
    private static final Tile MELZAR_YELLOW_DOOR = new Tile(2924, 3249, 2);
    private static final Tile MELZAR_BASEMENT = new Tile(2933, 9640, 0);
    private static final Tile MELZAR_BLUE_DOOR = new Tile(2931, 9644, 0);
    private static final Tile MELZAR_MAGENTA_DOOR = new Tile(2929, 9652, 0);
    private static final Tile MELZAR_GREEN_DOOR = new Tile(2936, 9655, 0);
    private static final Tile MELZAR_FINAL_CHEST = new Tile(2935, 9657, 0);
    
    // Port Sarim Jail coordinates (Stage 4 - Lozar's Map Piece)
    private static final Tile PORT_SARIM_JAIL = new Tile(3010, 3188, 0);
    
    // Boat Obtaining coordinates (Stage 5)
    private static final Tile PORT_SARIM_DOCKS = new Tile(3041, 3202, 0); // Southern docks near Void Outpost boat
    private static final Tile KLARENSE_TILE = new Tile(3041, 3202, 0); // Klarense location
    private static final Tile DRAYNOR_VILLAGE = new Tile(3104, 3251, 0); // Ned location
    private static final Tile NED_TILE = new Tile(3104, 3251, 0); // Ned's exact location
    
    // Monster IDs for precise targeting
    private static final int ZOMBIE_RAT_ID = 3969;
    private static final int GHOST_ID = 3975;
    private static final int SKELETON_ID = 3972;
    
    // Key IDs for Melzar's Maze (for proper resumability)
    private static final int RED_KEY_ID = 1543;
    private static final int ORANGE_KEY_ID = 1544;
    private static final int YELLOW_KEY_ID = 1545;
    
    private QuestNode smart;
    private final boolean forceStage2;
    private final boolean forceLozar;
    private final boolean forceMelzar;
    private final boolean forceBoat;

    public DragonSlayerTree() {
        super("Dragon Slayer");
        this.forceStage2 = false;
        this.forceLozar = false;
        this.forceMelzar = false;
        this.forceBoat = false;
    }

    // Debug constructor to jump directly to Stage 2 flow
    public DragonSlayerTree(boolean forceStage2) {
        super(forceStage2 ? "Dragon Slayer - Stage 2 (debug)" : "Dragon Slayer - Stage 1");
        this.forceStage2 = forceStage2;
        this.forceLozar = false;
        this.forceMelzar = false;
        this.forceBoat = false;
    }

    // Debug constructor for specific stages
    public DragonSlayerTree(String debugStage) {
        super("Dragon Slayer - " + debugStage + " (debug)");
        this.forceStage2 = false;
        this.forceLozar = "lozar".equals(debugStage);
        this.forceMelzar = "melzar".equals(debugStage);
        this.forceBoat = "boat".equals(debugStage);
    }

    @Override
    protected void buildTree() {
        smart = new QuestNode("smart_decision", "Decide next step for Dragon Slayer Stage 1") {
            @Override
            public ExecutionResult execute() {
                // Debug paths: directly run specific stages
                if (forceStage2) {
                    return ExecutionResult.success(stage2_OracleToChestSequence(), "Debug: Run Stage 2 directly");
                }
                if (forceMelzar) {
                    if (hasMelzarMapPiece()) {
                        log("✓ Melzar debug mode: Already have Melzar's map piece (ID 1535) - quest complete");
                        return ExecutionResult.success(new ActionNode("melzar_complete", "Melzar debug complete") {
                            @Override
                            protected boolean performAction() {
                                setQuestComplete();
                                return true;
                            }
                        }, "Melzar's Map Piece (debug) complete");
                    }
                    return ExecutionResult.success(stage3_MelzarsMaze(), "Debug: Run Melzar's Maze directly");
                }
                if (forceLozar) {
                    if (hasLozarMapPiece()) {
                        log("✓ Lozar debug mode: Already have Lozar's map piece - quest complete");
                        return ExecutionResult.success(new ActionNode("lozar_complete", "Lozar debug complete") {
                            @Override
                            protected boolean performAction() {
                                setQuestComplete();
                                return true;
                            }
                        }, "Lozar's Map Piece (debug) complete");
                    }
                    return ExecutionResult.success(stage4_LozarMapPiece(), "Debug: Run Lozar's Map Piece directly");
                }
                if (forceBoat) {
                    if (hasBoatObtained()) {
                        log("✓ Boat debug mode: Already have boat - quest complete");
                        return ExecutionResult.success(new ActionNode("boat_complete", "Boat debug complete") {
                            @Override
                            protected boolean performAction() {
                                setQuestComplete();
                                return true;
                            }
                        }, "Boat Obtaining (debug) complete");
                    }
                    return ExecutionResult.success(stage5_BoatObtaining(), "Debug: Run Boat Obtaining directly");
                }
                // Completion check first
                if (PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) >= TALKED_TO_OZIACH && 
                    PlayerSettings.getConfig(DRAGON_SLAYER_DIALOGUE_CONFIG) == ALL_INFO_GATHERED) {
                    // Stage 1 complete → proceed through map piece stages
                    if (!hasThalzarMapPiece()) {
                        return ExecutionResult.success(stage2_OracleToChestSequence(), "Stage 2: Oracle → Magic door → Chest (Thalzar's piece)");
                    } else if (!hasMelzarMapPiece()) {
                        return ExecutionResult.success(stage3_MelzarsMaze(), "Stage 3: Melzar's Maze (Melzar's piece)");
                    } else if (!hasLozarMapPiece()) {
                        return ExecutionResult.success(stage4_LozarMapPiece(), "Stage 4: Lozar's Map Piece from Wormbrain");
                    } else if (!hasBoatObtained()) {
                        return ExecutionResult.success(stage5_BoatObtaining(), "Stage 5: Obtain boat from Klarense");
                    }
                    return ExecutionResult.success(new ActionNode("finish", "Mark Dragon Slayer Stage 1 complete") {
                        @Override
                        protected boolean performAction() {
                            setQuestComplete();
                            return true;
                        }
                    }, "Dragon Slayer Stage 1 complete");
                }
                
                int mainProgress = PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG);
                int dialogueFlags = PlayerSettings.getConfig(DRAGON_SLAYER_DIALOGUE_CONFIG);
                
                // Talked to Oziach, now gathering info from Guildmaster
                if (mainProgress == TALKED_TO_OZIACH && dialogueFlags > ALL_INFO_GATHERED) {
                    return ExecutionResult.success(gatherInformationNode(), "Gather all quest information from Guildmaster");
                }
                
                // Started but haven't talked to Oziach yet
                if (mainProgress == QUEST_STARTED) {
                    return ExecutionResult.success(travelToOziachNode(), "Travel to Oziach in Edgeville");
                }
                
                // Quest not started - talk to Guildmaster
                if (mainProgress == QUEST_NOT_STARTED) {
                    return ExecutionResult.success(startQuestNode(), "Start Dragon Slayer quest");
                }
                
                // Fallback
                return ExecutionResult.success(startQuestNode(), "Fallback: Start quest");
            }
        };

        this.rootNode = smart;
    }

    /**
     * Stage 2: Oracle → Dwarven Mine magic door → Chest (Thalzar's map piece)
     */
    private QuestNode stage2_OracleToChestSequence() {
        return new ActionNode("ds_stage2", "Stage 2: Oracle → Magic door → Chest") {
            private boolean oracleCompleted = false;
            private boolean trapdoorCompleted = false;
            private boolean silkUsed = false;
            private boolean lobsterPotUsed = false;
            private boolean bowlUsed = false;
            private boolean mindBombUsed = false;
            private boolean magicDoorSequenceCompleted = false;
            
            @Override
            protected boolean performAction() {
                // 1) Talk to Oracle (only if not completed)
                if (!oracleCompleted) {
                    // Walk to Oracle if not nearby
                    if (Players.getLocal().distance(ORACLE_TILE) > 3) {
                        RunEnergyUtil.manageRunEnergy();
                        if (new WalkToLocationNode("walk_oracle", ORACLE_TILE, "Oracle").execute().isSuccess()) {
                            Sleep.sleep(600, 900); // Wait for arrival
                        } else {
                            log("Failed to walk to Oracle");
                            return false;
                        }
                    }
                    
                    // Talk to Oracle
                    NPC oracle = NPCs.closest("Oracle");
                    if (oracle != null && oracle.distance() <= 5) {
                        log("Talking to Oracle about Crandor map piece");
                        if (oracle.interact("Talk-to") && Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
                            int guard = 0;
                            boolean foundOption = false;
                            while (Dialogues.inDialogue() && guard++ < 25) {
                                if (Dialogues.areOptionsAvailable()) {
                                    String[] opts = Dialogues.getOptions();
                                    int idx = indexOfOption(opts, "I seek a piece of the map to the island of Crandor.");
                                    if (idx != -1) {
                                        Dialogues.chooseOption(idx + 1);
                                        foundOption = true;
                                        log("Selected Oracle dialogue option about Crandor map");
                                        Sleep.sleep(800, 1200);
                                    } else {
                                        log("Oracle dialogue options: " + String.join(", ", opts));
                                        Sleep.sleep(600, 900);
                                    }
                                } else if (Dialogues.canContinue()) {
                                    Dialogues.continueDialogue();
                                    Sleep.sleep(600, 900);
                                }
                                Sleep.sleep(300, 600);
                            }
                            if (foundOption) {
                                oracleCompleted = true;
                                log("Oracle dialogue completed successfully");
                            } else {
                                log("Failed to find correct Oracle dialogue option");
                                return false;
                            }
                        } else {
                            log("Failed to start dialogue with Oracle");
                            return false;
                        }
                    } else {
                        log("Oracle not found or too far away");
                        return false;
                    }
                }

                // 2) Go to trapdoor and climb down (only if not completed)
                if (!trapdoorCompleted && Players.getLocal().getTile().getZ() == 0) {
                    if (Players.getLocal().distance(TRAPDOOR_TILE) > 5) {
                        RunEnergyUtil.manageRunEnergy();
                        new WalkToLocationNode("walk_trapdoor_ds", TRAPDOOR_TILE, "Trapdoor").execute();
                    }
                    new InteractWithObjectNode("climb_down_trapdoor_ds", "Trapdoor", "Climb-down", TRAPDOOR_TILE, "Trapdoor").execute();
                    Sleep.sleep(600, 900);
                    trapdoorCompleted = true;
                    log("Trapdoor descent completed");
                }

                // 3) Navigate to magic door tile underground
                if (trapdoorCompleted && Players.getLocal().distance(MAGIC_DOOR_TILE) > 4) {
                    new WalkToLocationNode("walk_magic_door", MAGIC_DOOR_TILE, 4, "Magic door").execute();
                }

                // 4) Use items on Magic door in CORRECT order: Unfired bowl → Lobster pot → Wizard's mind bomb → Silk
                if (!magicDoorSequenceCompleted && GameObjects.closest("Magic door") != null) {
                    // STEP 1: Use Unfired bowl on Magic door (first item)
                    if (!bowlUsed) {
                        if (Inventory.contains("Unfired bowl")) {
                            UseItemOnObjectNode useUnfiredBowl = new UseItemOnObjectNode("use_unfired_bowl_magic_door", "Unfired bowl", "Magic door");
                            if (useUnfiredBowl.execute().isSuccess()) {
                                bowlUsed = true;
                                log("✓ Step 1/4: Successfully used Unfired bowl on Magic door");
                                Sleep.sleep(800, 1200);
                            } else {
                                log("Failed to use Unfired bowl on Magic door");
                                return false;
                            }
                        } else if (Inventory.contains("Bowl")) {
                            UseItemOnObjectNode useBowl = new UseItemOnObjectNode("use_bowl_magic_door", "Bowl", "Magic door");
                            if (useBowl.execute().isSuccess()) {
                                bowlUsed = true;
                                log("✓ Step 1/4: Successfully used Bowl on Magic door");
                                Sleep.sleep(800, 1200);
                            } else {
                                log("Failed to use Bowl on Magic door");
                                return false;
                            }
                        } else {
                            log("Missing bowl item for Magic door");
                            return false;
                        }
                        return true; // Return to process next step in next iteration
                    }
                    
                    // STEP 2: Use Lobster pot on Magic door (second item)
                    if (bowlUsed && !lobsterPotUsed) {
                        if (Inventory.contains("Lobster pot")) {
                            UseItemOnObjectNode useLobsterPot = new UseItemOnObjectNode("use_lobsterpot_magic_door", "Lobster pot", "Magic door");
                            if (useLobsterPot.execute().isSuccess()) {
                                lobsterPotUsed = true;
                                log("✓ Step 2/4: Successfully used Lobster pot on Magic door");
                                Sleep.sleep(800, 1200);
                            } else {
                                log("Failed to use Lobster pot on Magic door");
                                return false;
                            }
                        } else {
                            log("Missing Lobster pot for Magic door");
                            return false;
                        }
                        return true; // Return to process next step in next iteration
                    }
                    
                    // STEP 3: Use Wizard's mind bomb on Magic door (third item)
                    if (bowlUsed && lobsterPotUsed && !mindBombUsed) {
                        if (Inventory.contains("Wizard's mind bomb")) {
                            UseItemOnObjectNode useMindBomb = new UseItemOnObjectNode("use_mind_bomb_magic_door", "Wizard's mind bomb", "Magic door");
                            if (useMindBomb.execute().isSuccess()) {
                                mindBombUsed = true;
                                log("✓ Step 3/4: Successfully used Wizard's mind bomb on Magic door");
                                Sleep.sleep(800, 1200);
                            } else {
                                log("Failed to use Wizard's mind bomb on Magic door");
                                return false;
                            }
                        } else {
                            log("Missing Wizard's mind bomb for Magic door");
                            return false;
                        }
                        return true; // Return to process next step in next iteration
                    }
                    
                    // STEP 4: Use Silk on Magic door (final item)
                    if (bowlUsed && lobsterPotUsed && mindBombUsed && !silkUsed) {
                        if (Inventory.contains("Silk")) {
                            UseItemOnObjectNode useSilk = new UseItemOnObjectNode("use_silk_magic_door", "Silk", "Magic door");
                            if (useSilk.execute().isSuccess()) {
                                silkUsed = true;
                                log("✓ Step 4/4: Successfully used Silk on Magic door");
                                Sleep.sleep(800, 1200);
                            } else {
                                log("Failed to use Silk on Magic door");
                                return false;
                            }
                        } else {
                            log("Missing Silk for Magic door");
                            return false;
                        }
                    }
                    
                    // Check if all required items have been used in sequence
                    if (bowlUsed && lobsterPotUsed && mindBombUsed && silkUsed) {
                        magicDoorSequenceCompleted = true;
                        log("✓ Magic door sequence completed successfully (all 4 items used)");
                    }
                }

                // 5) Open and search chest for map piece (only if magic door sequence is completed)
                if (magicDoorSequenceCompleted) {
                    new InteractWithObjectNode("open_chest_ds", "Chest", "Open", CHEST_TILE, "Chest").execute();
                    Sleep.sleep(300, 600);
                    new InteractWithObjectNode("search_chest_ds", "Chest", "Search", CHEST_TILE, "Chest").execute();
                    Sleep.sleep(600, 900);
                }

                return hasAnyMapPiece();
            }
        };
    }

    /**
     * Stage 3: Melzar's Maze - Complex multi-floor dungeon with combat sequence
     * Based on log: quest_logs/QUEST_Free_Discovery_20250904_021031.log
     */
    private QuestNode stage3_MelzarsMaze() {
        return new ActionNode("ds_stage3", "Stage 3: Navigate Melzar's Maze") {
            @Override
            protected boolean performAction() {
                try {
                    // Check if we already have Melzar's map piece
                    if (hasMelzarMapPiece()) {
                        log("✓ Already have Melzar's map piece (ID 1535)");
                        return true;
                    }
                    
                    // Navigate based on current floor/location
                    Tile currentTile = Players.getLocal().getTile();
                    int currentZ = currentTile.getZ();
                    
                    // DEBUG: Enhanced Z-level detection
                    log("Melzar's Maze - Current location: " + formatTile(currentTile) + " (Floor " + currentZ + ")");
                    log("DEBUG: Z-level detection - Raw Z: " + currentZ + ", Tile: " + currentTile);
                    
                    // Force correct Z-level detection for Melzar's Maze
                    // Sometimes DreamBot reports wrong Z-level, so we need to detect based on coordinates
                    if (currentTile.getX() >= 2920 && currentTile.getX() <= 2940 && 
                        currentTile.getY() >= 3240 && currentTile.getY() <= 3260) {
                        // We're in Melzar's Maze area - check if we're actually on Floor 1
                        if (currentZ == 0 && (currentTile.getY() >= 3250 || currentTile.getX() >= 2925)) {
                            log("DEBUG: Correcting Z-level from 0 to 1 based on coordinates");
                            currentZ = 1; // Force Floor 1 detection
                        }
                    }
                    
                    // Key-ID aware routing for better resumability
                    // KEY-ID AWARE ROUTING: Route based on specific key possession (resumability)
                    
                    // YELLOW KEY (Stage 3) - Always go to Floor 2 if we have it
                    if (hasYellowKey()) {
                        if (currentZ != 2) {
                            log("Have yellow key (ID " + YELLOW_KEY_ID + ") but not on Floor 2 - navigating to Floor 2");
                            // Navigate to Floor 2 first
                            if (currentZ == 0) {
                                if (hasUsedRedKey()) {
                                    useRedKeyAndClimbUp(); // Skip door, go to ladder
                                } else {
                                    handleGroundFloor(); // Get red key first
                                }
                            } else if (currentZ == 1) {
                                if (hasUsedOrangeKey()) {
                                    useOrangeKeyAndClimbUp(); // Skip door, go to ladder
                                } else {
                                    handleFloor1(); // Get orange key first
                                }
                            }
                            return true;
                        } else {
                            log("Have yellow key (ID " + YELLOW_KEY_ID + ") on Floor 2 - using yellow door");
                            useYellowKeyAndClimbDown();
                            return true;
                        }
                    }
                    
                    // ORANGE KEY (Stage 2) - Always go to Floor 1 if we have it
                    if (hasOrangeKey()) {
                        if (currentZ != 1) {
                            log("Have orange key (ID " + ORANGE_KEY_ID + ") but not on Floor 1 - navigating to Floor 1");
                            // Navigate to Floor 1 first
                            if (currentZ == 0) {
                                if (hasUsedRedKey()) {
                                    climbToFloor1(); // Skip door, go directly to ladder
                                } else {
                                    handleGroundFloor(); // Get red key first
                                }
                            } else if (currentZ == 2) {
                                log("On Floor 2 with orange key - need to go down to Floor 1");
                                // This shouldn't happen normally, but handle it
                                GameObject ladder = GameObjects.closest("Ladder");
                                if (ladder != null && ladder.interact("Climb-down")) {
                                    Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 5000);
                                }
                            }
                            return true;
                        } else {
                            log("Have orange key (ID " + ORANGE_KEY_ID + ") on Floor 1 - using orange door");
                            useOrangeKeyAndClimbUp();
                            return true;
                        }
                    }
                    
                    // RED KEY (Stage 1) - Always use on ground floor
                    if (hasRedKey() && currentZ == 0) {
                        log("Have red key (ID " + RED_KEY_ID + ") on ground floor - using red door");
                        useRedKeyAndClimbUp();
                        return true;
                    }
                    
                    // BASEMENT LEVEL (Z=0, underground coordinates ~9600+)
                    if (currentZ == 0 && currentTile.getY() > 9000) {
                        handleBasementLevel();
                        return true; // Always continue processing - don't fail the action
                    }
                    
                    // SIMPLE: Ground floor - go directly to zombie rat area
                    if (currentZ == 0) {
                        log("Ground floor - going directly to zombie rat area");
                        handleGroundFloor();
                        return true;
                    }
                    
                    // FLOOR 2 (Z=2) - Only if actually in maze area
                    if (currentZ == 2) {
                        // Check if we're actually in the maze area on Floor 2
                        if (currentTile.getX() >= 2920 && currentTile.getX() <= 2945 && currentTile.getY() >= 3240 && currentTile.getY() <= 3260) {
                            handleFloor2();
                            return true;
                        } else {
                            log("On Floor 2 but not in maze area - navigating to maze entrance first");
                            navigateToMazeEntrance();
                            return true;
                        }
                    }
                    
                    // FLOOR 1 (Z=1) - Only if actually in maze area
                    if (currentZ == 1) {
                        // Check if we're actually in the maze area on Floor 1
                        if (currentTile.getX() >= 2920 && currentTile.getX() <= 2945 && currentTile.getY() >= 3240 && currentTile.getY() <= 3260) {
                            handleFloor1();
                            return true;
                        } else {
                            log("On Floor 1 but not in maze area - navigating to maze entrance first");
                            navigateToMazeEntrance();
                            return true;
                        }
                    }
                    
                    // This case is now handled by isInsideMazeGround() check above
                    
                    // Default: Navigate to maze entrance (if not near maze and not already inside)
                    navigateToMazeEntrance();
                    return true; // Always continue processing - don't fail the action
                } catch (Exception e) {
                    log("Error in Melzar's Maze navigation: " + e.getMessage());
                    return false; // Only fail on actual errors
                }
            }
            
            private boolean navigateToMazeEntrance() {
                Tile currentPos = Players.getLocal().getTile();
                log("Navigating to Melzar's Maze entrance at " + formatTile(MELZAR_MAZE_ENTRANCE));
                log("Current position: " + formatTile(currentPos));
                
                // Check distance to maze entrance
                double distance = Players.getLocal().distance(MELZAR_MAZE_ENTRANCE);
                log("Current distance to maze entrance: " + (int)distance + " tiles");
                
                // If already inside maze ground area, don't walk back to entrance
                if (isInsideMazeGround(currentPos)) {
                    log("Already inside maze ground area - skipping entrance navigation");
                    return false;
                }
                
                if (distance > 5) {
                    RunEnergyUtil.manageRunEnergy();
                    if (new WalkToLocationNode("walk_melzar_entrance", MELZAR_MAZE_ENTRANCE, "Melzar's Maze entrance").execute().isSuccess()) {
                        log("Successfully walked towards Melzar's Maze entrance");
                        Sleep.sleep(600, 900);
                    } else {
                        log("Failed to walk to Melzar's Maze entrance");
                    }
                    return false; // Continue next iteration
                }
                
                // We're near the entrance, try to open the door and enter
                log("Near maze entrance, attempting robust door open");
                if (openDoorAt(MELZAR_MAZE_ENTRANCE, "Door", new Tile(2940, 3248, 0))) {
                    log("✓ Opened maze entrance door");
                    
                    // Walk inside the maze to ensure we're in the right position
                    Tile insideMaze = new Tile(2940, 3248, 0);
                    if (Players.getLocal().distance(insideMaze) > 2) {
                        log("Walking inside maze to proper position");
                        if (new WalkToLocationNode("walk_inside_maze", insideMaze, "Inside maze").execute().isSuccess()) {
                            log("✓ Successfully entered Melzar's Maze interior");
                            Sleep.sleep(600, 900);
                        } else {
                            log("Failed to walk inside maze");
                        }
                    } else {
                        log("✓ Already at proper position inside maze");
                    }
                } else {
                    log("Failed to open entrance door - will retry");
                }
                return false; // Continue to ground floor handling
            }
            
            private boolean handleGroundFloor() {
                log("Ground Floor: Looking for zombie rat (ID " + ZOMBIE_RAT_ID + ") to get red key");
                
                // SIMPLE: Walk directly to zombie rat area (2927, 3253, 2935, 3245)
                Tile currentPos = Players.getLocal().getTile();
                log("Current position: " + formatTile(currentPos));
                
                // Walk to center of zombie rat area
                Tile zombieRatArea = new Tile(2931, 3249, 0);
                if (Players.getLocal().distance(zombieRatArea) > 5) {
                    log("Walking directly to zombie rat area at " + formatTile(zombieRatArea));
                    RunEnergyUtil.manageRunEnergy();
                    if (new WalkToLocationNode("walk_zombie_area", zombieRatArea, "Zombie rat area").execute().isSuccess()) {
                        log("Successfully walked to zombie rat area");
                        Sleep.sleep(600, 900);
                    } else {
                        log("Failed to walk to zombie rat area");
                    }
                    return false; // Continue next iteration
                }
                
                // Check if we have red key (specific ID)
                if (hasRedKey() && !hasUsedRedKey()) {
                    log("Already have red key (ID " + RED_KEY_ID + "), proceeding to use it");
                    return useRedKeyAndClimbUp();
                }
                
                // If we've already used red key (key dissolved), try to climb ladder directly
                if (hasUsedRedKey() && !hasRedKey()) {
                    log("Red key already used (dissolved) - proceeding to ladder");
                    return useRedKeyAndClimbUp(); // This will skip door opening and go to ladder
                }
                
                // Look for key on ground first (in case zombie rat was already killed)
                GroundItem keyOnGround = GroundItems.closest("Key");
                if (keyOnGround != null) {
                    log("Found key on ground at " + formatTile(keyOnGround.getTile()) + " - picking it up");
                    if (keyOnGround.interact("Take")) {
                        Sleep.sleepUntil(() -> Inventory.contains("Key"), 5000);
                        if (Inventory.contains("Key")) {
                            log("✓ Successfully picked up red key from ground");
                        }
                        return false; // Continue next iteration
                    }
                }
                
                // Look for specific zombie rat with ID 3969
                NPC zombieRat = NPCs.closest(npc -> npc.getID() == ZOMBIE_RAT_ID);
                if (zombieRat != null) {
                    log("Found zombie rat (ID " + ZOMBIE_RAT_ID + ") at " + formatTile(zombieRat.getTile()));
                    if (zombieRat.interact("Attack")) {
                        log("Attacking zombie rat (ID " + ZOMBIE_RAT_ID + ") for red key");
                        Sleep.sleepUntil(() -> !zombieRat.exists(), 15000);
                        
                        // After killing, look for the key drop
                        Sleep.sleep(1000, 2000); // Wait for loot to appear
                        GroundItem droppedKey = GroundItems.closest("Key");
                        if (droppedKey != null) {
                            log("Key dropped at " + formatTile(droppedKey.getTile()) + " - picking it up");
                            if (droppedKey.interact("Take")) {
                                Sleep.sleepUntil(() -> Inventory.contains("Key"), 5000);
                                if (Inventory.contains("Key")) {
                                    log("✓ Successfully looted red key from zombie rat");
                                }
                            }
                        } else {
                            log("No key found after killing zombie rat - checking inventory");
                            if (Inventory.contains("Key")) {
                                log("✓ Key already in inventory");
                            }
                        }
                        return false; // Continue next iteration
                    } else {
                        log("Failed to attack zombie rat");
                        return false;
                    }
                } else {
                    log("Zombie rat (ID " + ZOMBIE_RAT_ID + ") not found - checking if already killed");
                    // Try to move around a bit to find the zombie rat or key
                    Tile searchTile = new Tile(2936, 3249, 0); // From logs: zombie rat location
                    if (Players.getLocal().distance(searchTile) > 3) {
                        new WalkToLocationNode("search_zombie_rat", searchTile, "Search for zombie rat or key").execute();
                        Sleep.sleep(1000, 1500);
                    }
                    return false;
                }
            }
            
            private boolean useRedKeyAndClimbUp() {
                log("Using red key and climbing to Floor 1");
                
                // If we already used the key, skip door opening and go straight to ladder
                if (hasUsedRedKey() && !hasRedKey()) {
                    log("Red key already used - skipping door, going to ladder");
                } else {
                    // Walk to red door
                    if (Players.getLocal().distance(MELZAR_RED_DOOR) > 3) {
                        log("Walking to red door at " + formatTile(MELZAR_RED_DOOR));
                        new WalkToLocationNode("walk_red_door", MELZAR_RED_DOOR, "Red door").execute();
                        return false; // Continue next iteration after walking
                    }
                    
                    // Open red door robustly (no inside tile change here, just verify proximity)
                    openDoorAt(MELZAR_RED_DOOR, "Red door", null);
                }
                
                // Walk to ladder
                if (Players.getLocal().distance(MELZAR_FLOOR1_LADDER) > 3) {
                    log("Walking to Floor 1 ladder at " + formatTile(MELZAR_FLOOR1_LADDER));
                    new WalkToLocationNode("walk_f1_ladder", MELZAR_FLOOR1_LADDER, "Floor 1 ladder").execute();
                    return false; // Continue next iteration after walking
                }
                
                // Climb up ladder
                GameObject ladder = GameObjects.closest("Ladder");
                if (ladder != null) {
                    log("Climbing ladder to Floor 1");
                    if (ladder.interact("Climb-up")) {
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 5000);
                        log("✓ Successfully climbed to Floor 1");
                        return false; // Let the main loop detect we're on Floor 1 now
                    }
                } else {
                    log("Ladder not found near " + formatTile(MELZAR_FLOOR1_LADDER));
                }
                
                return false; // Continue processing
            }
            
            private boolean handleFloor1() {
                log("Floor 1: Looking for ghost (ID " + GHOST_ID + ") to get orange key");
                
                // Check if we have orange key (specific ID)
                if (hasOrangeKey() && !hasUsedOrangeKey()) {
                    log("Already have orange key (ID " + ORANGE_KEY_ID + "), proceeding to use it");
                    return useOrangeKeyAndClimbUp();
                }
                
                // If we've already used orange key (key dissolved), try to climb ladder directly
                if (hasUsedOrangeKey() && !hasOrangeKey()) {
                    log("Orange key already used (dissolved) - proceeding to ladder");
                    return useOrangeKeyAndClimbUp(); // This will skip door opening and go to ladder
                }
                
                // Look for key on ground first (resumability)
                GroundItem keyOnGround = GroundItems.closest("Key");
                if (keyOnGround != null) {
                    log("Found orange key on ground - picking it up");
                    if (keyOnGround.interact("Take")) {
                        Sleep.sleepUntil(() -> Inventory.contains("Key"), 5000);
                        return false;
                    }
                }
                
                // Kill ghost for orange key (handle both IDs seen in logs: 3975, 3976)
                NPC ghost = NPCs.closest(npc -> npc != null && (npc.getID() == GHOST_ID || npc.getID() == 3976));
                if (ghost != null) {
                    log("Found ghost (ID " + GHOST_ID + ") - attacking for orange key");
                    if (ghost.interact("Attack")) {
                        log("Attacking Ghost (ID " + GHOST_ID + ") for orange key");
                        Sleep.sleepUntil(() -> !ghost.exists(), 15000);
                        
                        // Look for key drop
                        Sleep.sleep(1000, 2000);
                        GroundItem droppedKey = GroundItems.closest("Key");
                        if (droppedKey != null && droppedKey.interact("Take")) {
                            Sleep.sleepUntil(() -> Inventory.contains("Key"), 5000);
                            log("✓ Successfully looted orange key from ghost");
                        }
                        return false;
                    }
                } else {
                    log("Ghost (ID " + GHOST_ID + ") not found on Floor 1");
                }
                return false;
            }
            
            private boolean useOrangeKeyAndClimbUp() {
                log("Using orange key and climbing to Floor 2");
                
                // If we already used the key, skip door opening and go straight to ladder
                if (hasUsedOrangeKey() && !hasOrangeKey()) {
                    log("Orange key already used - skipping door, going to ladder");
                } else {
                    // Walk to orange door
                    if (Players.getLocal().distance(MELZAR_ORANGE_DOOR) > 3) {
                        log("Walking to orange door at " + formatTile(MELZAR_ORANGE_DOOR));
                        new WalkToLocationNode("walk_orange_door", MELZAR_ORANGE_DOOR, "Orange door").execute();
                        return false;
                    }
                    
                    // Open orange door robustly
                    openDoorAt(MELZAR_ORANGE_DOOR, "Orange door", null);
                }
                
                // Walk to ladder
                if (Players.getLocal().distance(MELZAR_FLOOR2_LADDER) > 3) {
                    log("Walking to Floor 2 ladder at " + formatTile(MELZAR_FLOOR2_LADDER));
                    new WalkToLocationNode("walk_f2_ladder", MELZAR_FLOOR2_LADDER, "Floor 2 ladder").execute();
                    return false;
                }
                
                // Climb up ladder
                GameObject ladder = GameObjects.closest("Ladder");
                if (ladder != null) {
                    log("Climbing ladder to Floor 2");
                    if (ladder.interact("Climb-up")) {
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 2, 5000);
                        log("✓ Successfully climbed to Floor 2");
                        return false;
                    }
                } else {
                    log("Ladder not found near " + formatTile(MELZAR_FLOOR2_LADDER));
                }
                
                return false;
            }
            
            private boolean handleFloor2() {
                log("Floor 2: Looking for skeleton (ID " + SKELETON_ID + ") to get yellow key");
                
                // Check if we have yellow key (specific ID)
                if (hasYellowKey() && !hasUsedYellowKey()) {
                    log("Already have yellow key (ID " + YELLOW_KEY_ID + "), proceeding to use it");
                    return useYellowKeyAndClimbDown();
                }
                
                // If we've already used yellow key (key dissolved), try to climb down directly
                if (hasUsedYellowKey() && !hasYellowKey()) {
                    log("Yellow key already used (dissolved) - proceeding to basement");
                    return useYellowKeyAndClimbDown(); // This will skip door opening and go to ladder
                }
                
                // Look for key on ground first (resumability)
                GroundItem keyOnGround = GroundItems.closest("Key");
                if (keyOnGround != null) {
                    log("Found yellow key on ground - picking it up");
                    if (keyOnGround.interact("Take")) {
                        Sleep.sleepUntil(() -> Inventory.contains("Key"), 5000);
                        return false;
                    }
                }
                
                // Kill specific skeleton (ID 3972) for yellow key
                NPC skeleton = NPCs.closest(npc -> npc.getID() == SKELETON_ID);
                if (skeleton != null) {
                    log("Found skeleton (ID " + SKELETON_ID + ") - attacking for yellow key");
                    if (skeleton.interact("Attack")) {
                        log("Attacking Skeleton (ID " + SKELETON_ID + ") for yellow key");
                        Sleep.sleepUntil(() -> !skeleton.exists(), 15000);
                        
                        // Look for key drop
                        Sleep.sleep(1000, 2000);
                        GameObject droppedKey = GameObjects.closest("Key");
                        if (droppedKey != null && droppedKey.interact("Take")) {
                            Sleep.sleepUntil(() -> Inventory.contains("Key"), 5000);
                            log("✓ Successfully looted yellow key from skeleton");
                        }
                        return false;
                    }
                } else {
                    log("Skeleton (ID " + SKELETON_ID + ") not found on Floor 2");
                }
                return false;
            }
            
            private boolean useYellowKeyAndClimbDown() {
                log("Using yellow key and climbing down to basement");
                
                // If we already used the key, skip door opening and go straight to ladder
                if (hasUsedYellowKey() && !hasYellowKey()) {
                    log("Yellow key already used - skipping door, going to basement ladder");
                } else {
                    // Walk to yellow door
                    if (Players.getLocal().distance(MELZAR_YELLOW_DOOR) > 3) {
                        log("Walking to yellow door at " + formatTile(MELZAR_YELLOW_DOOR));
                        new WalkToLocationNode("walk_yellow_door", MELZAR_YELLOW_DOOR, "Yellow door").execute();
                        return false;
                    }
                    
                    // Open yellow door robustly
                    openDoorAt(MELZAR_YELLOW_DOOR, "Yellow door", null);
                }
                
                // Find and climb down ladder to basement
                GameObject ladder = GameObjects.closest("Ladder");
                if (ladder != null) {
                    log("Climbing down ladder to basement");
                    if (ladder.interact("Climb-down")) {
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getY() > 9000, 8000); // Wait for basement coordinates
                        log("✓ Successfully climbed down to basement");
                        return false;
                    }
                } else {
                    log("Ladder not found for basement descent");
                }
                
                return false;
            }
            
            private boolean handleBasementLevel() {
                log("Basement Level: Complex sequence - Zombies → Melzar → Lesser Demon → Chest");
                
                // Check if we're at final chest
                if (Players.getLocal().distance(MELZAR_FINAL_CHEST) <= 3) {
                    return searchFinalChest();
                }
                
                // Check progression through basement doors
                if (Inventory.contains("Key")) {
                    return handleBasementProgression();
                }
                
                // Kill zombies for blue key (first step in basement)
                NPC zombie = NPCs.closest("Zombie");
                if (zombie != null && Players.getLocal().distance(MELZAR_BASEMENT) <= 10) {
                    if (zombie.interact("Attack")) {
                        log("Attacking zombies for blue key");
                        Sleep.sleepUntil(() -> !zombie.exists() || Inventory.contains("Key"), 15000);
                        return false;
                    }
                }
                return false;
            }
            
            private boolean handleBasementProgression() {
                // Blue door → Melzar fight → Magenta door → Lesser Demon → Green door → Chest
                
                // Open blue door if near it
                if (Players.getLocal().distance(MELZAR_BLUE_DOOR) <= 5) {
                    GameObject blueDoor = GameObjects.closest("Blue door");
                    if (blueDoor != null && blueDoor.interact("Open")) {
                        log("Opened blue door - proceeding to Melzar fight");
                        Sleep.sleep(1000, 1500);
                        return false;
                    }
                }
                
                // Fight Melzar the Mad
                NPC melzar = NPCs.closest("Melzar the Mad");
                if (melzar != null) {
                    if (melzar.interact("Attack")) {
                        log("Fighting Melzar the Mad for magenta key");
                        Sleep.sleepUntil(() -> !melzar.exists() || Inventory.contains("Key"), 30000);
                        return false;
                    }
                }
                
                // Open magenta door
                if (Players.getLocal().distance(MELZAR_MAGENTA_DOOR) <= 5) {
                    GameObject magentaDoor = GameObjects.closest("Magenta door");
                    if (magentaDoor != null && magentaDoor.interact("Open")) {
                        log("Opened magenta door - proceeding to Lesser Demon");
                        Sleep.sleep(1000, 1500);
                        return false;
                    }
                }
                
                // Fight Lesser Demon (level 82 - highest threat)
                NPC lesserDemon = NPCs.closest("Lesser demon");
                if (lesserDemon != null) {
                    if (lesserDemon.interact("Attack")) {
                        log("Fighting Lesser Demon (Level 82) for green key");
                        Sleep.sleepUntil(() -> !lesserDemon.exists() || Inventory.contains("Key"), 60000);
                        return false;
                    }
                }
                
                // Open green door
                if (Players.getLocal().distance(MELZAR_GREEN_DOOR) <= 5) {
                    GameObject greenDoor = GameObjects.closest("Green door");
                    if (greenDoor != null && greenDoor.interact("Open")) {
                        log("Opened green door - final chest accessible");
                        Sleep.sleep(1000, 1500);
                        return false;
                    }
                }
                
                return false;
            }
            
            private boolean searchFinalChest() {
                GameObject chest = GameObjects.closest("Chest");
                if (chest != null) {
                    if (chest.interact("Open")) {
                        Sleep.sleep(600, 900);
                    }
                    if (chest.interact("Search")) {
                        log("Searching final chest for Melzar's map piece");
                        Sleep.sleepUntil(() -> hasMelzarMapPiece(), 5000);
                        if (hasMelzarMapPiece()) {
                            log("✓ Successfully obtained Melzar's map piece (ID 1535)!");
                            return true;
                        }
                    }
                }
                return false;
            }
            
            // Helper methods for key usage tracking (improved resumability)
            private boolean hasUsedRedKey() {
                Tile pos = Players.getLocal().getTile();
                // If we're above ground floor (Floor 1+) OR if we're in the post-red-door area
                // Post-red-door area includes ladder area and areas past the door (X < 2930 or near ladder)
                return pos.getZ() > 0 || (pos.getZ() == 0 && (pos.getX() < 2930 || (pos.getX() >= 2924 && pos.getX() <= 2930 && pos.getY() >= 3250)));
            }
            
            private boolean hasUsedOrangeKey() {
                Tile pos = Players.getLocal().getTile();
                // If we're above floor 1 OR if we're past the orange door area on floor 1
                return pos.getZ() > 1 || (pos.getZ() == 1 && pos.getX() > 2931);
            }
            
            private boolean hasUsedYellowKey() {
                Tile pos = Players.getLocal().getTile();
                // If we're in basement (underground coordinates) OR if we're past yellow door on floor 2
                return (pos.getZ() == 0 && pos.getY() > 9000) || (pos.getZ() == 2 && pos.getY() < 3249);
            }
            
            private String formatTile(Tile tile) {
                return "(" + tile.getX() + ", " + tile.getY() + ", " + tile.getZ() + ")";
            }
            
            // Detect if player is inside the ground-floor area of Melzar's Maze
            private boolean isInsideMazeGround(Tile t) {
                return t != null && t.getZ() == 0 && t.getX() >= 2920 && t.getX() <= 2945 && t.getY() >= 3240 && t.getY() <= 3260;
            }
            
            // Detect if player is actually inside the maze (past the entrance door)
            private boolean isActuallyInsideMaze(Tile t) {
                return t != null && t.getZ() == 0 && t.getX() <= 2940 && t.getY() >= 3248 && t.getY() <= 3260;
            }
            
            // Key-specific detection methods for better resumability
            private boolean hasRedKey() {
                return Inventory.contains(RED_KEY_ID);
            }
            
            private boolean hasOrangeKey() {
                return Inventory.contains(ORANGE_KEY_ID);
            }
            
            private boolean hasYellowKey() {
                return Inventory.contains(YELLOW_KEY_ID);
            }
            
            private boolean hasAnyKey() {
                return hasRedKey() || hasOrangeKey() || hasYellowKey();
            }
            
            // Simple method to climb to Floor 1 when we have orange key but are on Floor 0
            private boolean climbToFloor1() {
                log("Climbing to Floor 1 to use orange key");
                
                // Walk to Floor 1 ladder
                if (Players.getLocal().distance(MELZAR_FLOOR1_LADDER) > 3) {
                    log("Walking to Floor 1 ladder at " + formatTile(MELZAR_FLOOR1_LADDER));
                    new WalkToLocationNode("climb_to_f1", MELZAR_FLOOR1_LADDER, "Floor 1 ladder").execute();
                    return false; // Continue next iteration after walking
                }
                
                // Climb up ladder
                GameObject ladder = GameObjects.closest("Ladder");
                if (ladder != null) {
                    log("Climbing ladder to Floor 1");
                    if (ladder.interact("Climb-up")) {
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 5000);
                        log("✓ Successfully climbed to Floor 1");
                        return false; // Let the main loop detect we're on Floor 1 now
                    }
                } else {
                    log("Ladder not found near " + formatTile(MELZAR_FLOOR1_LADDER));
                }
                
                return false;
            }

            // Robust door opener: walks to exact door tile, retries interaction, and optionally verifies insideTile
            private boolean openDoorAt(Tile doorTile, String doorName, Tile insideTileIfAny) {
                try {
                    // Ensure we are adjacent to the door
                    if (Players.getLocal().distance(doorTile) > 2) {
                        new WalkToLocationNode("walk_door_" + doorName, doorTile, doorName + " tile").execute();
                        Sleep.sleep(400, 700);
                    }
                    // Locate exact door at tile
                    GameObject door = GameObjects.closest(go -> doorName.equals(go.getName()) && go.getTile().equals(doorTile));
                    if (door == null) {
                        // Nudge closer and retry once
                        Walking.walk(doorTile);
                        Sleep.sleep(500, 900);
                        door = GameObjects.closest(go -> doorName.equals(go.getName()) && go.getTile().equals(doorTile));
                    }
                    if (door == null) {
                        log("Could not find door '" + doorName + "' at " + formatTile(doorTile));
                        return false;
                    }
                    // Attempt up to 3 times
                    for (int attempt = 1; attempt <= 3; attempt++) {
                        log("Opening '" + doorName + "' at " + formatTile(doorTile) + " (attempt " + attempt + ")");
                        if (door.hasAction("Open") && door.interact("Open")) {
                            if (insideTileIfAny != null) {
                                if (Sleep.sleepUntil(() -> Players.getLocal().distance(insideTileIfAny) <= 2, 3000)) {
                                    return true;
                                }
                            } else {
                                Sleep.sleep(600, 900);
                                return true;
                            }
                        }
                        Sleep.sleep(400, 700);
                    }
                    return false;
                } catch (Exception e) {
                    log("openDoorAt error: " + e.getMessage());
                    return false;
                }
            }
        };
    }

    /**
     * Stage 5: Boat Obtaining - Get boat from Klarense and repair it
     * Based on OSRS Wiki: Talk to Guildmaster → Get items → Pay Klarense → Repair boat → Talk to Ned
     */
    private QuestNode stage5_BoatObtaining() {
        return new ActionNode("ds_stage5", "Stage 5: Obtain boat from Klarense") {
            private boolean itemsChecked = false;
            private boolean guildmasterAsked = false;
            private boolean boatBought = false;
            private boolean boatRepaired = false;
            private boolean nedTalked = false;
            
            @Override
            protected boolean performAction() {
                // Check if we already have boat obtained (config 176 >= 7)
                if (hasBoatObtained()) {
                    log("✓ Already have boat obtained (config 176 >= 7)");
                    return true;
                }
                
                // Step 1: Check and gather required items
                if (!itemsChecked) {
                    if (!checkAndGatherItems()) {
                        return false;
                    }
                    itemsChecked = true;
                }
                
                // Step 2: Talk to Guildmaster about ship (if not done yet)
                if (!guildmasterAsked) {
                    if (!askGuildmasterAboutShip()) {
                        return false;
                    }
                    guildmasterAsked = true;
                }
                
                // Step 3: Buy boat from Klarense (config 176: 2 → 3)
                if (!boatBought && PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) < 3) {
                    if (!buyBoatFromKlarense()) {
                        return false;
                    }
                    boatBought = true;
                }
                
                // Step 4: Repair boat (config 176: 3 → 6)
                if (!boatRepaired && PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) < 6) {
                    if (!repairBoat()) {
                        return false;
                    }
                    boatRepaired = true;
                }
                
                // Step 5: Talk to Ned (config 176: 6 → 7)
                if (!nedTalked && PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) < 7) {
                    if (!talkToNed()) {
                        return false;
                    }
                    nedTalked = true;
                }
                
                return true;
            }
        };
    }

    /**
     * Stage 4: Lozar's Map Piece - Port Sarim Jail (Wormbrain)
     * Based on OSRS Wiki: Either kill Wormbrain or pay 10,000 coins
     */
    private QuestNode stage4_LozarMapPiece() {
        return new ActionNode("ds_stage4", "Stage 4: Get Lozar's Map Piece from Wormbrain") {
            @Override
            protected boolean performAction() {
                // Check if we already have Lozar's map piece
                if (hasLozarMapPiece()) {
                    log("✓ Already have Lozar's map piece");
                    return true;
                }
                
                // Navigate to Port Sarim jail
                if (Players.getLocal().distance(PORT_SARIM_JAIL) > 5) {
                    RunEnergyUtil.manageRunEnergy();
                    if (new WalkToLocationNode("walk_port_sarim_jail", PORT_SARIM_JAIL, "Port Sarim jail").execute().isSuccess()) {
                        Sleep.sleep(600, 900);
                    } else {
                        log("Failed to walk to Port Sarim jail");
                        return false;
                    }
                }
                
                // Find Wormbrain in jail
                NPC wormbrain = NPCs.closest("Wormbrain");
                if (wormbrain == null) {
                    log("Wormbrain not found in Port Sarim jail");
                    return false;
                }
                
                // Check if we have 10,000 coins for payment option
                boolean hasCoins = Inventory.count("Coins") >= 10000;
                
                if (hasCoins) {
                    // Option 1: Pay Wormbrain 10,000 coins (peaceful method)
                    log("Attempting to pay Wormbrain 10,000 coins for map piece");
                    if (wormbrain.interact("Talk-to") && Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
                        int guard = 0;
                        boolean foundFirstOption = false;
                        boolean foundPaymentOption = false;
                        boolean foundConfirmation = false;
                        
                        while (Dialogues.inDialogue() && guard++ < 25) {
                            if (Dialogues.areOptionsAvailable()) {
                                String[] opts = Dialogues.getOptions();
                                
                                // Step 1: "I believe you've got a piece of map that I need."
                                if (!foundFirstOption) {
                                    int firstIdx = indexOfOption(opts, "I believe you've got a piece of map that I need.");
                                    if (firstIdx != -1) {
                                        Dialogues.chooseOption(firstIdx + 1);
                                        log("Selected: I believe you've got a piece of map that I need.");
                                        foundFirstOption = true;
                                        Sleep.sleep(800, 1200);
                                        continue;
                                    }
                                }
                                
                                // Step 2: "I suppose I could pay you for the map piece..."
                                if (foundFirstOption && !foundPaymentOption) {
                                int paymentIdx = indexOfOption(opts, "I suppose I could pay you for the map piece...");
                                if (paymentIdx != -1) {
                                    Dialogues.chooseOption(paymentIdx + 1);
                                        log("Selected: I suppose I could pay you for the map piece...");
                                        foundPaymentOption = true;
                                    Sleep.sleep(800, 1200);
                                    continue;
                                    }
                                }
                                
                                // Step 3: "Alright then, 10,000 it is."
                                if (foundPaymentOption && !foundConfirmation) {
                                int confirmIdx = indexOfOption(opts, "Alright then, 10,000 it is.");
                                if (confirmIdx != -1) {
                                    Dialogues.chooseOption(confirmIdx + 1);
                                        log("Selected: Alright then, 10,000 it is.");
                                        foundConfirmation = true;
                                    Sleep.sleep(800, 1200);
                                    continue;
                                    }
                                }
                                
                                // Default: continue dialogue if no specific option found
                                if (opts.length > 0) {
                                    Dialogues.chooseOption(1);
                                    Sleep.sleep(600, 900);
                                }
                            } else if (Dialogues.canContinue()) {
                                Dialogues.continueDialogue();
                                Sleep.sleep(600, 900);
                            }
                            Sleep.sleep(300, 600);
                        }
                        
                        // Check if we completed the payment sequence
                        if (foundFirstOption && foundPaymentOption && foundConfirmation) {
                            log("✓ Completed payment sequence with Wormbrain");
                        }
                    }
                } else {
                    // Option 2: Kill Wormbrain for map piece (combat method)
                    log("Insufficient coins, attempting to kill Wormbrain for map piece");
                    if (wormbrain.interact("Attack")) {
                        log("Attacking Wormbrain for Lozar's map piece");
                        Sleep.sleepUntil(() -> !wormbrain.exists() || hasLozarMapPiece(), 30000);
                    }
                }
                
                // Check if we got the map piece
                if (hasLozarMapPiece()) {
                    log("✓ Successfully obtained Lozar's map piece!");
                    return true;
                }
                
                return false;
            }
        };
    }

    private boolean hasAnyMapPiece() {
        return Inventory.contains("Map piece") || Inventory.contains("Map Piece") || Inventory.contains("Crandor map piece");
    }
    
    private boolean hasThalzarMapPiece() {
        // Thalzar's map piece has ID 1537 (from Stage 2 logs)
        return Inventory.contains(item -> item.getID() == 1537);
    }
    
    private boolean hasMelzarMapPiece() {
        // Melzar's map piece has ID 1535 (from Stage 3 logs)
        return Inventory.contains(item -> item.getID() == 1535);
    }
    
    private boolean hasLozarMapPiece() {
        // Lozar's map piece - check for any map piece that's not Thalzar's or Melzar's
        // From logs: player gets map piece from Wormbrain after paying 10,000 coins
        return Inventory.contains("Map piece") && !hasThalzarMapPiece() && !hasMelzarMapPiece();
    }
    
    private boolean hasBoatObtained() {
        // Boat obtained when config 176 >= 7 (after talking to Ned)
        return PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) >= 7;
    }
    
    // Helper methods for boat obtaining stage
    private boolean checkAndGatherItems() {
        log("Checking required items for boat obtaining...");
        
        // Required items: Hammer, 3 planks, 90 steel nails, 2,000 coins, Crandor map
        boolean hasHammer = Inventory.contains("Hammer");
        boolean hasPlanks = Inventory.count("Plank") >= 3;
        boolean hasNails = Inventory.count("Steel nails") >= 90;
        boolean hasCoins = Inventory.count("Coins") >= 2000;
        boolean hasMap = Inventory.contains("Crandor map");
        
        if (hasHammer && hasPlanks && hasNails && hasCoins && hasMap) {
            log("✓ All required items present in inventory");
            return true;
        }
        
        // Missing items - need to get them
        log("Missing required items - need to gather them");
        log("Required: Hammer=" + hasHammer + ", Planks=" + hasPlanks + " (have " + Inventory.count("Plank") + "), Nails=" + hasNails + " (have " + Inventory.count("Steel nails") + "), Coins=" + hasCoins + " (have " + Inventory.count("Coins") + "), Map=" + hasMap);
        
        // TODO: Implement bank/GE gathering logic
        // For now, just return false to indicate missing items
        log("ERROR: Missing required items - please gather them manually");
        return false;
    }
    
    private boolean askGuildmasterAboutShip() {
        log("Asking Guildmaster about ship location...");
        
        // Walk to Guildmaster if not nearby
        if (Players.getLocal().distance(GUILDMASTER_TILE) > 6) {
            RunEnergyUtil.manageRunEnergy();
            if (new WalkToLocationNode("walk_guildmaster_ship", GUILDMASTER_TILE, "Guildmaster").execute().isSuccess()) {
                Sleep.sleep(600, 900);
            } else {
                log("Failed to walk to Guildmaster");
                return false;
            }
        }
        
        // Talk to Guildmaster
        NPC guildmaster = NPCs.closest("Guildmaster");
        if (guildmaster == null) {
            log("Guildmaster not found");
            return false;
        }
        
        if (guildmaster.interact("Talk-to") && Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
            int guard = 0;
            boolean foundShipOption = false;
            
            while (Dialogues.inDialogue() && guard++ < 25) {
                if (Dialogues.areOptionsAvailable()) {
                    String[] opts = Dialogues.getOptions();
                    
                    // Look for "About my quest to kill the dragon..." first
                    if (!foundShipOption) {
                        int questIdx = indexOfOption(opts, "About my quest to kill the dragon...");
                        if (questIdx != -1) {
                            Dialogues.chooseOption(questIdx + 1);
                            log("Selected: About my quest to kill the dragon...");
                            Sleep.sleep(800, 1200);
                            continue;
                        }
                    }
                    
                    // Then look for "Where can I find the right ship?"
                    int shipIdx = indexOfOption(opts, "Where can I find the right ship?");
                    if (shipIdx != -1) {
                        Dialogues.chooseOption(shipIdx + 1);
                        log("Selected: Where can I find the right ship?");
                        foundShipOption = true;
                        Sleep.sleep(800, 1200);
                        continue;
                    }
                    
                    // Look for "Okay, I'll get going!" to finish
                    int finishIdx = indexOfOption(opts, "Okay, I'll get going!");
                    if (finishIdx != -1) {
                        Dialogues.chooseOption(finishIdx + 1);
                        log("Selected: Okay, I'll get going!");
                        Sleep.sleep(800, 1200);
                        break;
                    }
                    
                    // Default: continue dialogue
                    if (opts.length > 0) {
                        Dialogues.chooseOption(1);
                        Sleep.sleep(600, 900);
                    }
                } else if (Dialogues.canContinue()) {
                    Dialogues.continueDialogue();
                    Sleep.sleep(600, 900);
                }
                Sleep.sleep(300, 600);
            }
        } else {
            log("Failed to start dialogue with Guildmaster");
            return false;
        }
        
        return true;
    }
    
    private boolean buyBoatFromKlarense() {
        log("Buying boat from Klarense...");
        
        // Walk to Klarense at Port Sarim docks
        if (Players.getLocal().distance(KLARENSE_TILE) > 6) {
            RunEnergyUtil.manageRunEnergy();
            log("Walking to Klarense at Port Sarim docks...");
            // Try walking multiple times if needed
            int walkAttempts = 0;
            while (Players.getLocal().distance(KLARENSE_TILE) > 6 && walkAttempts < 3) {
                walkAttempts++;
                log("Walk attempt " + walkAttempts + " to Klarense");
                if (new WalkToLocationNode("walk_klarense", KLARENSE_TILE, "Klarense").execute().isSuccess()) {
                    Sleep.sleep(2000, 3000); // Wait longer for movement
                    if (Players.getLocal().distance(KLARENSE_TILE) <= 6) {
                        log("✓ Successfully reached Klarense");
                        break;
                    }
                } else {
                    log("Walk attempt " + walkAttempts + " failed, retrying...");
                    Sleep.sleep(1000, 2000);
                }
            }
            
            // If still not close enough, try one more time with a different approach
            if (Players.getLocal().distance(KLARENSE_TILE) > 6) {
                log("Direct walking failed, trying alternative approach...");
                // Try walking to a nearby tile first
                Tile nearbyTile = new Tile(3040, 3200, 0);
                if (new WalkToLocationNode("walk_near_klarense", nearbyTile, "Near Klarense").execute().isSuccess()) {
                    Sleep.sleep(2000, 3000);
                }
            }
        }
        
        // Talk to Klarense
        NPC klarense = NPCs.closest("Klarense");
        if (klarense == null) {
            log("Klarense not found at Port Sarim docks");
            return false;
        }
        
        if (klarense.interact("Talk-to") && Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
            int guard = 0;
            boolean foundBuyOption = false;
            boolean confirmedPurchase = false;
            
            while (Dialogues.inDialogue() && guard++ < 25) {
                if (Dialogues.areOptionsAvailable()) {
                    String[] opts = Dialogues.getOptions();
                    
                    // Look for "I'd like to buy her."
                    if (!foundBuyOption) {
                        int buyIdx = indexOfOption(opts, "I'd like to buy her.");
                        if (buyIdx != -1) {
                            Dialogues.chooseOption(buyIdx + 1);
                            log("Selected: I'd like to buy her.");
                            foundBuyOption = true;
                            Sleep.sleep(800, 1200);
                            continue;
                        }
                    }
                    
                    // Look for "Yep, sounds good." confirmation
                    if (foundBuyOption && !confirmedPurchase) {
                        int confirmIdx = indexOfOption(opts, "Yep, sounds good.");
                        if (confirmIdx != -1) {
                            Dialogues.chooseOption(confirmIdx + 1);
                            log("Selected: Yep, sounds good.");
                            confirmedPurchase = true;
                            Sleep.sleep(800, 1200);
                            break;
                        }
                    }
                    
                    // Default: continue dialogue
                    if (opts.length > 0) {
                        Dialogues.chooseOption(1);
                        Sleep.sleep(600, 900);
                    }
                } else if (Dialogues.canContinue()) {
                    Dialogues.continueDialogue();
                    Sleep.sleep(600, 900);
                }
                Sleep.sleep(300, 600);
            }
            
            // Check if purchase was successful (config should change from 2 to 3)
            if (confirmedPurchase) {
                Sleep.sleep(1000, 2000); // Wait for config update
                if (PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) >= 3) {
                    log("✓ Successfully bought boat from Klarense (config 176 = " + PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) + ")");
                    return true;
                }
            }
        } else {
            log("Failed to start dialogue with Klarense");
            return false;
        }
        
        return false;
    }
    
    private boolean repairBoat() {
        log("Repairing boat (3 repairs needed)...");
        
        // Check if we're already inside the boat (Z=1 and underground coordinates)
        Tile currentTile = Players.getLocal().getTile();
        boolean isInsideBoat = currentTile.getZ() == 1 && currentTile.getY() > 9000;
        
        if (!isInsideBoat) {
            // Walk to boat gangplank
            Tile gangplankTile = new Tile(3047, 3204, 0);
            if (Players.getLocal().distance(gangplankTile) > 3) {
                RunEnergyUtil.manageRunEnergy();
                log("Walking to boat gangplank...");
                if (new WalkToLocationNode("walk_gangplank", gangplankTile, "Gangplank").execute().isSuccess()) {
                    Sleep.sleep(1000, 1500);
                } else {
                    log("Failed to walk to gangplank");
                    return false;
                }
            }
            
            // Cross gangplank
            GameObject gangplank = GameObjects.closest("Gangplank");
            if (gangplank != null && gangplank.interact("Cross")) {
                log("Crossing gangplank to board boat");
                Sleep.sleep(2000, 3000);
            }
            
            // Climb down ladder to boat interior
            GameObject ladder = GameObjects.closest("Ladder");
            if (ladder != null && ladder.interact("Climb-down")) {
                log("Climbing down ladder to boat interior");
                Sleep.sleepUntil(() -> {
                    Tile pos = Players.getLocal().getTile();
                    return pos.getZ() == 1 && pos.getY() > 9000;
                }, 5000);
            }
        } else {
            log("Already inside boat interior, proceeding with repairs");
        }
        
        // Wait a moment for the environment to load
        Sleep.sleep(1000, 2000);
        
        // Repair hole 3 times with better detection and timing
        int repairsDone = 0;
        int maxRepairs = 3;
        int repairAttempts = 0;
        int maxRepairAttempts = 20; // Prevent infinite loop
        
        while (repairsDone < maxRepairs && repairAttempts < maxRepairAttempts) {
            repairAttempts++;
            log("Repair attempt " + repairAttempts + " - Looking for hole...");
            
            // Look for hole with multiple search methods
            GameObject hole = GameObjects.closest("Hole");
            if (hole == null) {
                // Try alternative search methods
                hole = GameObjects.closest(go -> go.getName().toLowerCase().contains("hole"));
                if (hole == null) {
                    hole = GameObjects.closest(go -> go.hasAction("Repair"));
                }
            }
            
            if (hole != null) {
                log("Found hole at " + hole.getTile() + " - Repairing hole " + (repairsDone + 1) + "/" + maxRepairs);
                if (hole.interact("Repair")) {
                    log("✓ Successfully started repair " + (repairsDone + 1) + "/" + maxRepairs);
                    repairsDone++;
                    
                    // Wait for repair animation to complete
                    Sleep.sleep(3000, 4000);
                    
                    // Wait a bit more for the hole to potentially change state
                    Sleep.sleep(1000, 2000);
                } else {
                    log("Failed to interact with hole");
                    Sleep.sleep(1000, 2000);
                }
            } else {
                log("Hole not found - waiting and searching again...");
                Sleep.sleep(2000, 3000);
                
                // Try moving around a bit to find the hole
                if (repairAttempts % 3 == 0) {
                    log("Moving around to search for hole...");
                    Tile searchTile = new Tile(3047, 9640, 1);
                    if (Players.getLocal().distance(searchTile) > 2) {
                        Walking.walk(searchTile);
                        Sleep.sleep(1000, 2000);
                    }
                }
            }
        }
        
        // Check if repairs completed (config should change from 3 to 6)
        if (repairsDone >= maxRepairs) {
            log("✓ Completed " + repairsDone + " repairs, waiting for config update...");
            Sleep.sleep(2000, 3000); // Wait for config update
            
            if (PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) >= 6) {
                log("✓ Successfully repaired boat (config 176 = " + PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) + ")");
                
                // Climb back up and cross gangplank
                log("Exiting boat...");
                GameObject upLadder = GameObjects.closest("Ladder");
                if (upLadder != null && upLadder.interact("Climb-up")) {
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 0, 5000);
                }
                
                GameObject exitGangplank = GameObjects.closest("Gangplank");
                if (exitGangplank != null && exitGangplank.interact("Cross")) {
                    Sleep.sleep(1000, 2000);
                }
                
                return true;
            } else {
                log("Repairs completed but config not updated yet (current: " + PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) + ")");
                return false;
            }
        } else {
            log("Failed to complete all repairs (completed " + repairsDone + "/" + maxRepairs + ")");
            return false;
        }
    }
    
    private boolean talkToNed() {
        log("Talking to Ned in Draynor Village...");
        
        // Walk to Ned in Draynor Village
        if (Players.getLocal().distance(NED_TILE) > 6) {
            RunEnergyUtil.manageRunEnergy();
            log("Walking to Ned in Draynor Village...");
            // Try walking multiple times if needed
            int walkAttempts = 0;
            while (Players.getLocal().distance(NED_TILE) > 6 && walkAttempts < 3) {
                walkAttempts++;
                log("Walk attempt " + walkAttempts + " to Ned");
                if (new WalkToLocationNode("walk_ned", NED_TILE, "Ned").execute().isSuccess()) {
                    Sleep.sleep(2000, 3000); // Wait longer for movement
                    if (Players.getLocal().distance(NED_TILE) <= 6) {
                        log("✓ Successfully reached Ned");
                        break;
                    }
                } else {
                    log("Walk attempt " + walkAttempts + " failed, retrying...");
                    Sleep.sleep(1000, 2000);
                }
            }
            
            // If still not close enough, try one more time with a different approach
            if (Players.getLocal().distance(NED_TILE) > 6) {
                log("Direct walking failed, trying alternative approach...");
                // Try walking to a nearby tile first
                Tile nearbyTile = new Tile(3100, 3260, 0);
                if (new WalkToLocationNode("walk_near_ned", nearbyTile, "Near Ned").execute().isSuccess()) {
                    Sleep.sleep(2000, 3000);
                }
            }
        }
        
        // Talk to Ned
        NPC ned = NPCs.closest("Ned");
        if (ned == null) {
            log("Ned not found in Draynor Village");
            return false;
        }
        
        if (ned.interact("Talk-to") && Sleep.sleepUntil(Dialogues::inDialogue, 5000)) {
            int guard = 0;
            boolean foundSailorOption = false;
            
            while (Dialogues.inDialogue() && guard++ < 25) {
                if (Dialogues.areOptionsAvailable()) {
                    String[] opts = Dialogues.getOptions();
                    
                    // Look for "You're a sailor? Could you take me to Crandor?"
                    int sailorIdx = indexOfOption(opts, "You're a sailor? Could you take me to Crandor?");
                    if (sailorIdx != -1) {
                        Dialogues.chooseOption(sailorIdx + 1);
                        log("Selected: You're a sailor? Could you take me to Crandor?");
                        foundSailorOption = true;
                        Sleep.sleep(800, 1200);
                        break;
                    }
                    
                    // Default: continue dialogue
                    if (opts.length > 0) {
                        Dialogues.chooseOption(1);
                        Sleep.sleep(600, 900);
                    }
                } else if (Dialogues.canContinue()) {
                    Dialogues.continueDialogue();
                    Sleep.sleep(600, 900);
                }
                Sleep.sleep(300, 600);
            }
            
            // Check if conversation completed (config should change from 6 to 7)
            if (foundSailorOption) {
                Sleep.sleep(1000, 2000); // Wait for config update
                if (PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) >= 7) {
                    log("✓ Successfully talked to Ned (config 176 = " + PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) + ")");
                    return true;
                }
            }
        } else {
            log("Failed to start dialogue with Ned");
            return false;
        }
        
        return false;
    }

    private QuestNode startQuestNode() {
        return new ActionNode("start_quest", "Talk to Guildmaster to start Dragon Slayer") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before walking to Guildmaster
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Walk to Guildmaster if not nearby
                if (Players.getLocal().distance(GUILDMASTER_TILE) > 6) {
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_guildmaster", GUILDMASTER_TILE, "Guildmaster").execute();
                }
                
                NPC guildmaster = NPCs.closest("Guildmaster");
                if (guildmaster == null) return false;
                if (!guildmaster.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                
                int guard = 0;
                while (Dialogues.inDialogue() && guard++ < 30) {
                    if (Dialogues.areOptionsAvailable()) {
                        String[] opts = Dialogues.getOptions();
                        int idx = indexOfOption(opts, "Can I have a quest?");
                        if (idx != -1) {
                            Dialogues.chooseOption(idx + 1);
                        } else {
                            idx = indexOfOption(opts, "Yes.");
                            if (idx != -1) Dialogues.chooseOption(idx + 1);
                        }
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

    private QuestNode travelToOziachNode() {
        return new ActionNode("travel_oziach", "Travel to Oziach in Edgeville") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before walking to Oziach
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Walk to Oziach if not nearby
                if (Players.getLocal().distance(OZIACH_TILE) > 6) {
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_oziach", OZIACH_TILE, "Oziach").execute();
                }
                
                NPC oziach = NPCs.closest("Oziach");
                if (oziach == null) return false;
                if (!oziach.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                
                int guard = 0;
                while (Dialogues.inDialogue() && guard++ < 30) {
                    if (Dialogues.areOptionsAvailable()) {
                        String[] opts = Dialogues.getOptions();
                        int idx = indexOfOption(opts, "Can you sell me a rune platebody?");
                        if (idx == -1) idx = indexOfOption(opts, "The Guildmaster of the Champions' Guild told me.");
                        if (idx == -1) idx = indexOfOption(opts, "I thought you were going to give me a quest.");
                        if (idx == -1) idx = indexOfOption(opts, "A dragon, that sounds like fun.");
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

    private QuestNode gatherInformationNode() {
        return new ActionNode("gather_info", "Gather all quest information from Guildmaster") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before walking to Guildmaster
                int currentEnergy = (int) Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Walk to Guildmaster if not nearby
                if (Players.getLocal().distance(GUILDMASTER_TILE) > 6) {
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("walk_guildmaster_info", GUILDMASTER_TILE, "Guildmaster").execute();
                }
                
                NPC guildmaster = NPCs.closest("Guildmaster");
                if (guildmaster == null) return false;
                if (!guildmaster.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;
                
                int guard = 0;
                boolean talkedAboutOziach = false;
                boolean askedAboutRoute = false;
                boolean askedMelzar = false;
                boolean askedThalzar = false;
                boolean askedLozar = false;
                boolean askedShip = false;
                boolean askedProtection = false;
                boolean finishedAllQuestions = false;
                
                while (Dialogues.inDialogue() && guard++ < 60) { // Increased timeout for long dialogue
                    if (Dialogues.areOptionsAvailable()) {
                        String[] opts = Dialogues.getOptions();
                        int idx = -1;
                        
                        // Priority 1: Start with Oziach conversation (only once)
                        if (!talkedAboutOziach && indexOfOption(opts, "I talked to Oziach") != -1) {
                            idx = indexOfOption(opts, "I talked to Oziach");
                            talkedAboutOziach = true;
                        }
                        // Priority 2: Ask about route to Crandor (only once)
                        else if (!askedAboutRoute && indexOfOption(opts, "How can I find the route to Crandor?") != -1) {
                            idx = indexOfOption(opts, "How can I find the route to Crandor?");
                            askedAboutRoute = true;
                        }
                        // Priority 3: Ask about Melzar's map piece (only once)
                        else if (!askedMelzar && indexOfOption(opts, "Where is Melzar's map piece?") != -1) {
                            idx = indexOfOption(opts, "Where is Melzar's map piece?");
                            askedMelzar = true;
                        }
                        // Priority 4: Ask about Thalzar's map piece (only once)
                        else if (!askedThalzar && indexOfOption(opts, "Where is Thalzar's map piece?") != -1) {
                            idx = indexOfOption(opts, "Where is Thalzar's map piece?");
                            askedThalzar = true;
                        }
                        // Priority 5: Ask about Lozar's map piece (only once)
                        else if (!askedLozar && indexOfOption(opts, "Where is Lozar's map piece?") != -1) {
                            idx = indexOfOption(opts, "Where is Lozar's map piece?");
                            askedLozar = true;
                        }
                        // Priority 6: Ask about ship (only once)
                        else if (!askedShip && indexOfOption(opts, "Where can I find the right ship?") != -1) {
                            idx = indexOfOption(opts, "Where can I find the right ship?");
                            askedShip = true;
                        }
                        // Priority 7: Ask about dragon breath protection (only once)
                        else if (!askedProtection && indexOfOption(opts, "How can I protect myself from the dragon's breath?") != -1) {
                            idx = indexOfOption(opts, "How can I protect myself from the dragon's breath?");
                            askedProtection = true;
                        }
                        // Priority 8: End conversation
                        else if (indexOfOption(opts, "Okay, I'll get going!") != -1) {
                            idx = indexOfOption(opts, "Okay, I'll get going!");
                            finishedAllQuestions = true; // Mark as completed
                        }
                        
                        if (idx != -1) {
                            Dialogues.chooseOption(idx + 1);
                            Sleep.sleep(1000, 1500); // Wait for dialogue to process
                        }
                        
                        // Check if we've asked all questions and can finish
                        if (talkedAboutOziach && askedAboutRoute && askedMelzar && 
                            askedThalzar && askedLozar && askedShip && askedProtection) {
                            // All questions asked, look for exit option
                            if (indexOfOption(opts, "Okay, I'll get going!") != -1) {
                                idx = indexOfOption(opts, "Okay, I'll get going!");
                                finishedAllQuestions = true;
                                if (idx != -1) {
                                    Dialogues.chooseOption(idx + 1);
                                    Sleep.sleep(1000, 1500);
                                }
                            }
                        }
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    Sleep.sleep(600, 900);
                }
                Sleep.sleep(800, 1200);
                
                // Return true only if we actually completed all questions or dialogue config changed
                int newDialogueFlags = PlayerSettings.getConfig(DRAGON_SLAYER_DIALOGUE_CONFIG);
                
                return finishedAllQuestions || newDialogueFlags == ALL_INFO_GATHERED;
            }
        };
    }

    private int indexOfOption(String[] options, String needle) {
        if (options == null) return -1;
        for (int i = 0; i < options.length; i++) {
            String opt = options[i];
            if (opt != null && opt.toLowerCase().contains(needle.toLowerCase())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isQuestComplete() {
        // In Stage 2 debug mode, completion = obtained any map piece
        if (forceStage2) {
            return hasAnyMapPiece();
        }
        // In Melzar debug mode, completion = obtained Melzar's map piece
        if (forceMelzar) {
            return hasMelzarMapPiece();
        }
        // In Lozar debug mode, completion = obtained Lozar's map piece
        if (forceLozar) {
            return hasLozarMapPiece();
        }
        // In Boat debug mode, completion = obtained boat
        if (forceBoat) {
            return hasBoatObtained();
        }
        return PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG) >= TALKED_TO_OZIACH &&
               PlayerSettings.getConfig(DRAGON_SLAYER_DIALOGUE_CONFIG) == ALL_INFO_GATHERED &&
               hasThalzarMapPiece() && hasMelzarMapPiece() && hasLozarMapPiece() && hasBoatObtained() ||
               super.isQuestComplete();
    }

    @Override
    public int getQuestProgress() {
        if (isQuestComplete()) return 100;
        int mainProgress = PlayerSettings.getConfig(DRAGON_SLAYER_CONFIG);
        int dialogueFlags = PlayerSettings.getConfig(DRAGON_SLAYER_DIALOGUE_CONFIG);
        
        // Rough mapping for UI
        if (mainProgress == QUEST_NOT_STARTED) return 0;    // not started
        if (mainProgress == QUEST_STARTED) return 10;       // started, going to Oziach
        if (mainProgress == TALKED_TO_OZIACH && dialogueFlags > ALL_INFO_GATHERED) return 20;  // gathering info
        if (dialogueFlags == ALL_INFO_GATHERED) {
            // Stage 1 complete, now collecting map pieces and boat
            int mapPieces = 0;
            if (hasThalzarMapPiece()) mapPieces++;
            if (hasMelzarMapPiece()) mapPieces++;
            if (hasLozarMapPiece()) mapPieces++;
            
            int progress = 20 + (mapPieces * 20); // 20% base + 20% per map piece
            
            // Add boat stage progress
            if (hasBoatObtained()) {
                progress += 20; // 20% for boat
            }
            
            return progress;
        }
        return 50; // fallback
    }
}