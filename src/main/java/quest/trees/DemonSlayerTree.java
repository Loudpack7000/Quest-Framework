package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.utils.GrandExchangeUtil;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;

import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;


import java.util.Arrays;
import java.util.List;

/**
 * Demon Slayer quest implemented using the Quest Tree system.
 * Progress is tracked via varbit 2561 (observed from discovery logs):
 *  - 0: Not started
 *  - 1: Spoke to Aris (fortune-teller)
 *  - 2: Spoke to Sir Prysin, tasked to gather 3 keys (Rovin, Traiborn, Sewer)
 *  - 3: Completed (Delrith banished)
 */
public class DemonSlayerTree extends QuestTree {

    // Varbit for quest progress (from logs)
    private static final int VARBIT_DEMON_SLAYER = 2561;
    private static final int STEP_NOT_STARTED = 0;
    private static final int STEP_STARTED_ARIS = 1;
    private static final int STEP_GATHER_KEYS = 2;
    private static final int STEP_COMPLETE = 3;

    // Additional config observed (used for progress heuristics if needed)
    private static final int CONFIG_MISC = 101; // observed change 29 -> 32 near completion

    // Locations (derived from discovery log positions)
    private static final Tile ARIS_LOCATION = new Tile(3205, 3424, 0);
    private static final Tile VARROCK_CASTLE_DOOR = new Tile(3207, 3472, 0);
    private static final Tile SIR_PRYSIN_LOCATION = new Tile(3205, 3473, 0);
    private static final Tile CASTLE_STAIRS_GROUND = new Tile(3202, 3497, 0);
    private static final Tile CASTLE_STAIRS_FIRST = new Tile(3202, 3497, 1);
    private static final Tile CAPTAIN_ROVIN_LOCATION = new Tile(3205, 3496, 2);
    private static final Tile WIZARD_TOWER_STAIRS = new Tile(3103, 3159, 0);
    private static final Tile WIZARD_TOWER_INNER_DOOR = new Tile(3108, 3158, 1);
    private static final Tile TRAIBORN_LOCATION = new Tile(3112, 3162, 1);
    private static final Tile VARROCK_MANHOLE = new Tile(3237, 3458, 0);
    private static final Tile VARROCK_DRAIN = new Tile(3211, 3432, 0); // approximate near Varrock East bank
    private static final Tile SEWERS_KEY_TILE = new Tile(3232, 9901, 0);
    private static final Tile STONE_CIRCLE_ENTRANCE = new Tile(3216, 3374, 0);

    // Item names
    private static final String ITEM_BUCKET_OF_WATER = "Bucket of water";
    private static final String ITEM_RUSTY_KEY = "Rusty key";
    private static final String ITEM_SILVERLIGHT_KEY = "Silverlight key";
    private static final String ITEM_SILVERLIGHT = "Silverlight";
    private static final String ITEM_BONES = "Bones";

    // Dialogue incantation sequence
    private static final List<String> INCANTATION = Arrays.asList(
        "Carlem", "Aber", "Camerinthum", "Purchai", "Gabindo"
    );

    // Nodes
    private QuestNode smartDecisionNode;

    private QuestNode walkToAris;
    private QuestNode talkToAris;

    private QuestNode walkToCastle;
    private QuestNode openCastleDoor;
    private QuestNode talkToSirPrysin;

    private QuestNode climbToRovin;
    private QuestNode talkToCaptainRovin;

    private QuestNode acquireBonesIfNeeded;
    private QuestNode goToWizardTower;
    private QuestNode openWizardInnerDoor;
    private QuestNode climbUpWizardStairs;
    private QuestNode talkToTraiborn;

    private QuestNode prepareSewerKey;
    private QuestNode openManhole;
    private QuestNode descendManhole;
    private QuestNode walkToSewerKey;
    private QuestNode takeRustyKey;

    private QuestNode returnToSirPrysinForSilverlight;
    private QuestNode wieldSilverlight;
    private QuestNode goToStoneCircle;
    private QuestNode fightDelrithAndIncantation;

    public DemonSlayerTree() {
        super("Demon Slayer");
    }

    @Override
    protected void buildTree() {
        createNodes();
        createSmartDecisionNode();
        rootNode = smartDecisionNode;
    }

    private void createNodes() {
        // Start with Aris
        walkToAris = new WalkToLocationNode("walk_aris", ARIS_LOCATION, "Aris the fortune-teller");
        talkToAris = new TalkToNPCNode("talk_aris", "Aris", ARIS_LOCATION);

        // Sir Prysin at Varrock Castle
        walkToCastle = new WalkToLocationNode("walk_castle", VARROCK_CASTLE_DOOR, "Varrock Castle door");
        openCastleDoor = new InteractWithObjectNode("open_castle_door", "Door", "Open", VARROCK_CASTLE_DOOR, "Varrock Castle entrance");
        talkToSirPrysin = new TalkToNPCNode("talk_sir_prysin", "Sir Prysin", SIR_PRYSIN_LOCATION);

        // Captain Rovin upstairs
        climbToRovin = new ActionNode("climb_to_rovin", "Climb to Captain Rovin's floor") {
            @Override
            protected boolean performAction() {
                // Climb from ground -> first -> second
                GameObject stairsGround = GameObjects.closest("Staircase");
                if (stairsGround != null && Players.getLocal().getTile().getZ() == 0) {
                    stairsGround.interact("Climb-up");
                    Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 1, 7000);
                }
                if (Players.getLocal().getTile().getZ() == 1) {
                    GameObject stairsFirst = GameObjects.closest("Staircase");
                    if (stairsFirst != null) {
                        stairsFirst.interact("Climb-up");
                        Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() == 2, 7000);
                    }
                }
                return Players.getLocal().getTile().getZ() == 2;
            }
        };
        talkToCaptainRovin = new TalkToNPCNode("talk_captain_rovin", "Captain Rovin", CAPTAIN_ROVIN_LOCATION);

        // Wizard Traiborn (needs 25 Bones)
        acquireBonesIfNeeded = new ActionNode("acquire_bones", "Acquire 25 Bones if needed") {
            @Override
            protected boolean performAction() {
                int have = Inventory.count(ITEM_BONES);
                if (have >= 25) {
                    log("Already have " + have + " Bones");
                    return true;
                }
                GrandExchangeUtil.ItemRequest bonesReq = new GrandExchangeUtil.ItemRequest(ITEM_BONES, 25);
                boolean bought = GrandExchangeUtil.buyItems(bonesReq);
                if (!bought) {
                    log("Failed to buy Bones from GE");
                }
                return Inventory.count(ITEM_BONES) >= 25;
            }
        };
        goToWizardTower = new WalkToLocationNode("walk_wizard_tower", WIZARD_TOWER_STAIRS, "Wizard Tower");
        climbUpWizardStairs = new InteractWithObjectNode("climb_wizard_stairs", "Staircase", "Climb-up", WIZARD_TOWER_STAIRS, "Wizard Tower stairs");
        openWizardInnerDoor = new InteractWithObjectNode("open_wizard_door", "Door", "Open", WIZARD_TOWER_INNER_DOOR, "Wizard Tower inner door");
        talkToTraiborn = new TalkToNPCNode("talk_traiborn", "Wizard Traiborn", TRAIBORN_LOCATION);

        // Sewer key via drain and manhole
        prepareSewerKey = new ActionNode("use_bucket_on_drain", "Use Bucket of water on Drain") {
            @Override
            protected boolean performAction() {
                if (!Inventory.contains(ITEM_BUCKET_OF_WATER)) {
                    log("Missing Bucket of water - attempting to buy one");
                    if (!GrandExchangeUtil.buyItem(ITEM_BUCKET_OF_WATER, 1)) {
                        return false;
                    }
                }
                // Walk near drain
                new WalkToLocationNode("walk_drain", VARROCK_DRAIN, "Drain near Varrock East").execute();
                GameObject drain = GameObjects.closest("Drain");
                if (drain == null) {
                    log("Drain not found");
                    return false;
                }
                log("Using Bucket of water on Drain...");
                // Use item then use on drain
                if (Inventory.interact(ITEM_BUCKET_OF_WATER, "Use")) {
                    Sleep.sleep(300, 600);
                    drain.interact("Use");
                }
                // We can't easily verify here; proceed to manhole step
                Sleep.sleep(800, 1400);
                return true;
            }
        };
        openManhole = new InteractWithObjectNode("open_manhole", "Manhole", "Open", VARROCK_MANHOLE, "Varrock manhole");
        descendManhole = new InteractWithObjectNode("descend_manhole", "Manhole", "Climb-down", VARROCK_MANHOLE, "Varrock manhole");
        walkToSewerKey = new WalkToLocationNode("walk_sewer_key", SEWERS_KEY_TILE, "Sewer key tile");
        takeRustyKey = new ActionNode("take_rusty_key", "Take Rusty key") {
            @Override
            protected boolean performAction() {
                if (Inventory.contains(ITEM_RUSTY_KEY)) return true;
                
                // Walk to the key tile and try to pick it up
                new WalkToLocationNode("walk_key_tile", SEWERS_KEY_TILE, "Rusty key tile").execute();
                
                // Try to interact with any ground item at the location
                // Since we can't use GroundItems API, we'll assume the key is there
                // and the player can pick it up manually if needed
                log("Rusty key should be at tile: " + SEWERS_KEY_TILE);
                log("Please pick up the Rusty key manually if it's visible");
                
                // Wait a moment for manual pickup
                Sleep.sleep(2000, 3000);
                
                return Inventory.contains(ITEM_RUSTY_KEY);
            }
        };

        // Get Silverlight from Sir Prysin after keys
        returnToSirPrysinForSilverlight = new TalkToNPCNode("return_sir_prysin", "Sir Prysin", SIR_PRYSIN_LOCATION);
        wieldSilverlight = new ActionNode("wield_silverlight", "Wield Silverlight") {
            @Override
            protected boolean performAction() {
                if (!Inventory.contains(ITEM_SILVERLIGHT)) return false;
                if (Inventory.interact(ITEM_SILVERLIGHT, "Wield")) {
                    Sleep.sleep(600, 1200);
                }
                return true;
            }
        };

        // Final fight and incantation
        goToStoneCircle = new WalkToLocationNode("walk_stone_circle", STONE_CIRCLE_ENTRANCE, "Varrock Stone Circle");
        fightDelrithAndIncantation = new ActionNode("fight_delrith", "Defeat Delrith and recite incantation") {
            private int incantationIndex = 0;

            @Override
            protected boolean performAction() {
                // If we are in the instance, Delrith should be nearby
                if (NPCs.closest("Delrith") == null) {
                    // Attack to trigger fight/cutscene
                    if (NPCs.closest("Delrith") != null) {
                        NPCs.closest("Delrith").interact("Attack");
                        Sleep.sleep(800, 1200);
                    }
                }

                // Handle incantation dialogue when options appear
                if (Dialogues.areOptionsAvailable()) {
                    String nextWord = INCANTATION.get(Math.min(incantationIndex, INCANTATION.size() - 1));
                    String[] options = Dialogues.getOptions();
                    for (int i = 0; i < options.length; i++) {
                        if (options[i].contains(nextWord)) {
                            Dialogues.chooseOption(i + 1);
                            incantationIndex++;
                            Sleep.sleep(600, 1200);
                            break;
                        }
                    }
                    // If we finished all words, wait a moment for completion
                    if (incantationIndex >= INCANTATION.size()) {
                        Sleep.sleep(1500, 2500);
                    }
                } else if (Dialogues.inDialogue()) {
                    // Continue any non-option dialogue
                    Dialogues.continueDialogue();
                    Sleep.sleep(600, 1200);
                } else {
                    // Attack Delrith or Dark wizards to keep stage moving
                    if (NPCs.closest("Delrith") != null) {
                        NPCs.closest("Delrith").interact("Attack");
                        Sleep.sleep(800, 1200);
                    }
                }

                // Consider success when varbit signals completion
                return PlayerSettings.getBitValue(VARBIT_DEMON_SLAYER) >= STEP_COMPLETE;
            }
        };
    }

    private void createSmartDecisionNode() {
        smartDecisionNode = new QuestNode("demon_slayer_decision", "Demon Slayer Smart Decision") {
            @Override
            public ExecutionResult execute() {
                int step = PlayerSettings.getBitValue(VARBIT_DEMON_SLAYER);
                Tile here = Players.getLocal().getTile();

                log("[STATE] varbit=" + step + ", loc=" + here);

                // Completed
                if (step >= STEP_COMPLETE) {
                    setQuestComplete();
                    return ExecutionResult.success(null, "Quest complete");
                }

                QuestNode next = null;
                String reason = "";

                // Gather keys stage
                if (step >= STEP_GATHER_KEYS) {
                    // Heuristics: ensure we have keys from Rovin and Traiborn (silverlight keys), plus Rusty key; then get Silverlight
                    boolean hasRusty = Inventory.contains(ITEM_RUSTY_KEY);
                    boolean hasSilverlight = Inventory.contains(ITEM_SILVERLIGHT);
                    int silverlightKeys = Inventory.count(ITEM_SILVERLIGHT_KEY);

                    if (silverlightKeys < 2) {
                        // Prioritize Traiborn path if we have or can buy bones
                        if (silverlightKeys == 0 && Inventory.count(ITEM_BONES) < 25) {
                            next = acquireBonesIfNeeded;
                            reason = "Acquire Bones for Traiborn";
                        } else if (Players.getLocal().getTile().getZ() < 2 && Players.getLocal().distance(WIZARD_TOWER_STAIRS) > 6) {
                            next = goToWizardTower;
                            reason = "Go to Wizard Tower";
                        } else if (Players.getLocal().getTile().getZ() == 0) {
                            next = climbUpWizardStairs;
                            reason = "Climb Wizard Tower stairs";
                        } else if (Players.getLocal().getTile().getZ() == 1 && Players.getLocal().distance(WIZARD_TOWER_INNER_DOOR) > 5) {
                            next = openWizardInnerDoor;
                            reason = "Open inner door to Traiborn";
                        } else if (Players.getLocal().getTile().getZ() == 1 && Players.getLocal().distance(TRAIBORN_LOCATION) > 5) {
                            next = new WalkToLocationNode("walk_traiborn", TRAIBORN_LOCATION, "Traiborn");
                            reason = "Walk to Traiborn";
                        } else {
                            next = talkToTraiborn;
                            reason = "Talk to Traiborn for a key";
                        }
                    } else if (!hasRusty) {
                        // Do sewer key path
                        if (here.getZ() == 0 && here.distance(VARROCK_DRAIN) > 8 && here.distance(VARROCK_MANHOLE) > 8 && here.getY() < 9000) {
                            next = prepareSewerKey;
                            reason = "Prepare sewer key via drain";
                        } else if (here.getZ() == 0 && here.distance(VARROCK_MANHOLE) > 6 && here.getY() < 9000) {
                            next = openManhole;
                            reason = "Open manhole";
                        } else if (here.getZ() == 0 && here.distance(VARROCK_MANHOLE) <= 6) {
                            next = descendManhole;
                            reason = "Descend manhole";
                        } else if (here.getY() > 9000) { // in sewer
                            if (here.distance(SEWERS_KEY_TILE) > 5) {
                                next = walkToSewerKey;
                                reason = "Walk to sewer key tile";
                            } else {
                                next = takeRustyKey;
                                reason = "Take Rusty key";
                            }
                        }
                    } else if (silverlightKeys < 2) {
                        // If still missing Rovin's key specifically, head to Rovin
                        if (here.getZ() < 2 && here.distance(CASTLE_STAIRS_GROUND) > 6) {
                            next = new WalkToLocationNode("walk_castle_stairs", CASTLE_STAIRS_GROUND, "Varrock Castle stairs");
                            reason = "Walk to Varrock Castle stairs";
                        } else if (here.getZ() < 2) {
                            next = climbToRovin;
                            reason = "Climb to Captain Rovin";
                        } else if (here.getZ() == 2 && here.distance(CAPTAIN_ROVIN_LOCATION) > 6) {
                            next = new WalkToLocationNode("walk_rovin", CAPTAIN_ROVIN_LOCATION, "Captain Rovin");
                            reason = "Walk to Captain Rovin";
                        } else {
                            next = talkToCaptainRovin;
                            reason = "Talk to Captain Rovin for a key";
                        }
                    } else if (!hasSilverlight) {
                        // Return to Sir Prysin to receive Silverlight once keys obtained
                        if (here.distance(SIR_PRYSIN_LOCATION) > 6) {
                            next = walkToCastle;
                            reason = "Walk to Varrock Castle for Silverlight";
                        } else {
                            next = returnToSirPrysinForSilverlight;
                            reason = "Talk to Sir Prysin for Silverlight";
                        }
                    } else {
                        // Have Silverlight - ensure wielded, then proceed to ritual site and finish
                        if (Inventory.contains(ITEM_SILVERLIGHT)) {
                            next = wieldSilverlight;
                            reason = "Wield Silverlight";
                        } else if (here.distance(STONE_CIRCLE_ENTRANCE) > 6 && here.getY() < 9000) {
                            next = goToStoneCircle;
                            reason = "Go to Stone Circle";
                        } else {
                            next = fightDelrithAndIncantation;
                            reason = "Fight Delrith and recite incantation";
                        }
                    }
                }
                // After Aris but before Sir Prysin
                else if (step >= STEP_STARTED_ARIS) {
                    if (here.distance(VARROCK_CASTLE_DOOR) > 6) {
                        next = walkToCastle;
                        reason = "Walk to Varrock Castle";
                    } else if (here.distance(SIR_PRYSIN_LOCATION) > 6) {
                        next = openCastleDoor;
                        reason = "Open castle door";
                    } else {
                        next = talkToSirPrysin;
                        reason = "Talk to Sir Prysin";
                    }
                }
                // Not started - go talk to Aris
                else {
                    if (here.distance(ARIS_LOCATION) > 5) {
                        next = walkToAris;
                        reason = "Walk to Aris";
                    } else {
                        next = talkToAris;
                        reason = "Talk to Aris to start";
                    }
                }

                // Fallback: if no next decided, try key NPCs in order
                if (next == null) {
                    next = talkToAris;
                    reason = "Fallback: talk to Aris";
                }

                log("-> " + reason);
                return ExecutionResult.success(next, reason);
            }
        };
    }

    @Override
    public boolean isQuestComplete() {
        try {
            return PlayerSettings.getBitValue(VARBIT_DEMON_SLAYER) >= STEP_COMPLETE;
        } catch (Throwable t) {
            // Fallback using observed config if varbit not available
            return PlayerSettings.getConfig(CONFIG_MISC) >= 32;
        }
    }

    @Override
    public int getQuestProgress() {
        int v;
        try {
            v = PlayerSettings.getBitValue(VARBIT_DEMON_SLAYER);
        } catch (Throwable t) {
            v = 0;
        }
        if (v >= STEP_COMPLETE) return 100;
        if (v >= STEP_GATHER_KEYS) return 60;
        if (v >= STEP_STARTED_ARIS) return 30;
        return 0;
    }
}

