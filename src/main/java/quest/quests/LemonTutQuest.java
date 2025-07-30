package quest.quests;

import org.dreambot.api.Client;
import org.dreambot.api.input.Keyboard;
import org.dreambot.api.methods.Calculations;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.container.impl.equipment.EquipmentSlot;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.input.Camera;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.methods.magic.Magic;
import org.dreambot.api.methods.magic.Normal;
import org.dreambot.api.methods.map.Map;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.tabs.Tab;
import org.dreambot.api.methods.tabs.Tabs;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.widget.Widgets;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.wrappers.widgets.WidgetChild;
import quest.core.QuestScript;
import quest.core.QuestDatabase;

public class LemonTutQuest implements QuestScript {

    private final int TUT_PROG = 281;
    private final int POLL_OPEN = 375;

    private final String RUNESCAPE_GUIDE = "Gielinor Guide";
    private final String SURVIVAL_EXPERT = "Survival Expert";
    private final String COOK_GUIDE = "Master Chef";
    private final String QUEST_GUIDE = "Quest Guide";
    private final String MINING_GUIDE = "Mining Instructor";
    private final String COMBAT_GUIDE = "Combat Instructor";
    private final String FINANCIAL_GUIDE = "Account Guide";
    private final String PRAY_GUIDE = "Brother Brace";
    private final String MAGIC_GUIDE = "Magic Instructor";

    private Tile[] cookToQuest;
    private Tile[] combatToLadder;
    private Tile[] finToPray;
    private Tile[] prayToMage;
    
    // Added required fields for QuestScript interface
    private AbstractScript script;
    private QuestDatabase database;
    private boolean questComplete = false;

    public LemonTutQuest() {
        // Initialize path arrays
        cookToQuest = new Tile[] {
                new Tile(3076, 3123, 0), new Tile(3077, 3125, 0), new Tile(3079, 3125, 0),
                new Tile(3080, 3126, 0), new Tile(3082, 3126, 0), new Tile(3084, 3126, 0),
                new Tile(3086, 3126, 0)
        };

        combatToLadder = new Tile[] {
                new Tile(3112, 9524, 0), new Tile(3111, 9525, 0)
        };

        finToPray = new Tile[] {
                new Tile(3130, 3110, 0), new Tile(3130, 3108, 0)
        };

        prayToMage = new Tile[] {
                new Tile(3133, 3093, 0), new Tile(3134, 3092, 0), new Tile(3135, 3091, 0),
                new Tile(3136, 3090, 0), new Tile(3137, 3089, 0), new Tile(3138, 3088, 0),
                new Tile(3140, 3087, 0), new Tile(3141, 3088, 0)
        };
    }

    @Override
    public void initialize(AbstractScript script, QuestDatabase database) {
        this.script = script;
        this.database = database;
        determineInitialQuestState();
    }

    @Override
    public String getQuestId() {
        return "TUTORIAL_ISLAND";
    }

    @Override
    public String getQuestName() {
        return "Tutorial Island";
    }

    @Override
    public boolean canStart() {
        // Tutorial Island can always be started if not complete
        return !isComplete();
    }

    @Override
    public boolean startQuest() {
        // Tutorial Island starts automatically
        return true;
    }

    @Override
    public boolean executeCurrentStep() {
        int tutorialStage = PlayerSettings.getConfig(TUT_PROG);

        // Handle appearance customization and name creation screens
        if (Widgets.get(558, 3) != null && Widgets.get(558, 3).isVisible()) {
            handleDisplayNameCreation();
            return true;
        }

        if (Widgets.get(679, 74) != null && Widgets.get(679, 74).isVisible()) {
            randomizeCharacterAppearance();
            return true;
        }

        // Process the main tutorial steps
        processTutorialStage();
        return !questComplete;
    }

    @Override
    public int getCurrentProgress() {
        int tutorialStage = PlayerSettings.getConfig(TUT_PROG);
        if (tutorialStage == 1000) {
            return 100;
        }
        // Convert tutorial stage to percentage (1000 is complete)
        return Math.max(0, Math.min(100, (tutorialStage * 100) / 1000));
    }

    @Override
    public boolean isComplete() {
        return PlayerSettings.getConfig(TUT_PROG) == 1000 || questComplete;
    }

    @Override
    public String getCurrentStepDescription() {
        int tutorialStage = PlayerSettings.getConfig(TUT_PROG);
        if (tutorialStage == 1000) {
            return "Tutorial Island completed!";
        }
        return "Tutorial Island - Stage " + tutorialStage;
    }

    @Override
    public boolean hasRequiredItems() {
        // Tutorial Island provides all needed items
        return true;
    }

    @Override
    public String[] getRequiredItems() {
        // Tutorial Island requires no items
        return new String[0];
    }

    @Override
    public boolean handleDialogue() {
        // Tutorial Island handles dialogue within the main execution loop
        return true;
    }

    @Override
    public boolean navigateToObjective() {
        // Tutorial Island handles navigation within the main execution loop
        return true;
    }

    @Override
    public void cleanup() {
        // No cleanup needed for Tutorial Island
    }

    @Override
    public void onQuestStart() {
        log("Tutorial Island started!");
    }

    @Override
    public void onQuestComplete() {
        log("Tutorial Island completed successfully!");
        questComplete = true;
    }
    
    // Additional methods for quest framework compatibility
    public String[] getSkillRequirements() {
        // Tutorial Island has no skill requirements
        return new String[0];
    }

    public int getQuestPointReward() {
        // Tutorial Island gives no quest points
        return 0;
    }

    private void determineInitialQuestState() {
        if (PlayerSettings.getConfig(TUT_PROG) == 1000) {
            questComplete = true;
            log("Tutorial Island is already complete.");
        } else {
            log("Tutorial Island in progress, stage: " + PlayerSettings.getConfig(TUT_PROG));
        }
    }

    private void log(String message) {
        if (script != null) {
            script.log(message);
        }
    }

    /**
     * Process the current tutorial stage
     */
    private void processTutorialStage() {
        int tutorialStage = PlayerSettings.getConfig(TUT_PROG);
        log("Current tutorial progress: " + tutorialStage);

        // Process the current stage
        switch (tutorialStage) {
            case 0:
            case 1:
                randomizeCharacterAppearance();
                break;

            case 2:
            case 7:
                talkTo(RUNESCAPE_GUIDE);
                break;

            case 3:
                log("Opening Settings Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Tabs.isOpen(Tab.OPTIONS)) {
                    Tabs.open(Tab.OPTIONS);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.OPTIONS), Calculations.random(1000, 1500));
                }
                break;

            case 10:
                log("Walking to next area");
                if (!Walking.isRunEnabled()) {
                    Walking.toggleRun();
                }
                GameObject door = GameObjects.closest("Door");
                if (door != null && door.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 10), Calculations.random(1600, 2000));
                }
                break;

            case 20:
                talkTo(SURVIVAL_EXPERT);
                break;

            case 30:
                log("Opening Inventory Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Tabs.isOpen(Tab.INVENTORY)) {
                    Tabs.open(Tab.INVENTORY);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.INVENTORY), Calculations.random(1000, 1500));
                }
                break;

            case 40:
                log("Catching shrimp");
                if (Players.getLocal().getAnimation() != -1) {
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 40), 5000);
                    break;
                }
                NPC fishingSpot = NPCs.closest("Fishing spot");
                if (fishingSpot != null && fishingSpot.interact("Net")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 40), Calculations.random(4000, 5000));
                }
                break;

            case 50:
                log("Opening Skills Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Tabs.isOpen(Tab.SKILLS)) {
                    Tabs.open(Tab.SKILLS);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.SKILLS), Calculations.random(1000, 1500));
                }
                break;

            case 60:
                log("Talking to Survival Expert");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                talkTo(SURVIVAL_EXPERT);
                break;

            case 70:
                log("Chopping a tree");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (Inventory.contains("Logs")) {
                    script.sleep(1000);
                    return;
                }
                GameObject tree = GameObjects.closest(t -> t != null && t.getName().equals("Tree") && Map.canReach(t));
                if (tree != null) {
                    if (tree.interact("Chop down")) {
                        walkingSleep();
                        Sleep.sleepUntil(() -> Inventory.contains("Logs"), Calculations.random(4000, 6000));
                    }
                }
                break;

            case 80:
                log("Lighting a fire");
                if (!Inventory.contains("Logs")) {
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 80), 5000);
                    break;
                }
                lightFire();
                break;

            case 90:
            case 100:
                log("Cooking shrimp");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    break;
                }
                cookShrimp();
                break;

            case 110:
                log("Catching more shrimp");
                if (Inventory.contains("Raw shrimps")) {
                    cookShrimp();
                    break;
                }
                NPC spot = NPCs.closest("Fishing spot");
                if (spot != null && spot.interact("Net")) {
                    Sleep.sleepUntil(() -> Inventory.contains("Raw shrimps"),
                            () -> !(!Players.getLocal().isMoving() && !Players.getLocal().isAnimating()), 4000, 100);
                }
                break;

            case 120:
                log("Walking through gate");
                if (Players.getLocal().distance(new Tile(3091, 3092, 0)) > 5.0D) {
                    walkHuman(new Tile(3090, 3092, 0));
                    break;
                }
                GameObject gate = GameObjects.closest("Gate");
                if (gate != null && gate.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 120),
                            Calculations.random(1400, 1800));
                }
                break;

            case 130:
                log("Opening door to cooking area");
                if (Players.getLocal().distance(new Tile(3080, 3084, 0)) > 5.0D) {
                    walkHuman(new Tile(3080, 3084, 0));
                    break;
                }
                GameObject door130 = GameObjects.closest("Door");
                if (door130 != null && door130.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 130), 1200);
                }
                break;

            case 140:
                log("Talking to Master Chef");
                talkTo(COOK_GUIDE);
                break;

            case 150:
                log("Mixing ingredients");
                if (!Inventory.isItemSelected()) {
                    if (Inventory.interact("Bucket of water", "Use")) {
                        Sleep.sleepUntil(Inventory::isItemSelected, Calculations.random(1200, 1400));
                    }
                    break;
                }
                if (Inventory.interact("Pot of flour", "Use")) {
                    Sleep.sleepUntil(() -> Inventory.contains("Bread dough"), Calculations.random(1200, 1400));
                }
                break;

            case 160:
                log("Cooking dough");
                if (!Inventory.isItemSelected()) {
                    if (Inventory.interact("Bread dough", "Use")) {
                        Sleep.sleepUntil(Inventory::isItemSelected, Calculations.random(1200, 1400));
                    }
                    break;
                }
                GameObject range = GameObjects.closest("Range");
                if (range != null && range.interact("Use")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> Inventory.contains("Bread"), Calculations.random(2000, 3000));
                }
                break;

            case 170:
                log("Opening door after cooking");
                if (Players.getLocal().distance(new Tile(3073, 3090, 0)) > 5.0D) {
                    walkHuman(new Tile(3073, 3090, 0));
                    break;
                }
                GameObject door170 = GameObjects.closest("Door");
                if (door170 != null && door170.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 170),
                            Calculations.random(1200, 1600));
                }
                break;

            case 200:
            case 210:
                log("Walking toward Quest area");
                if (Players.getLocal().distance(new Tile(3086, 3126, 0)) > 5.0D) {
                    walkHuman(cookToQuest[cookToQuest.length - 1]);
                    Sleep.sleepUntil(() -> Players.getLocal().isMoving(), 1200);
                    Sleep.sleepUntil(() -> {
                        Tile dest = Client.getDestination();
                        return !(Players.getLocal().isMoving() && dest != null
                                && Players.getLocal().distance(dest) >= 5.0D);
                    }, Calculations.random(2600, 3000));
                    break;
                }
                GameObject gate210 = GameObjects.closest("Door");
                if (gate210 != null && gate210.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 210),
                            Calculations.random(1200, 1400));
                }
                break;

            case 220:
            case 240:
                log("Talking to Quest Guide");
                talkTo(QUEST_GUIDE);
                break;

            case 230:
                log("Opening Quest Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Widgets.getAllContainingText("Click on the flashing icon").isEmpty()) {
                    Tabs.open(Tab.QUEST);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.QUEST), Calculations.random(1000, 1500));
                    return;
                }
                talkTo(QUEST_GUIDE);
                break;

            case 250:
                log("Climbing down ladder");
                GameObject ladder = GameObjects.closest("Ladder");
                if (ladder != null && ladder.interact("Climb-down")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 250),
                            Calculations.random(4000, 6000));
                }
                break;

            case 260:
            case 270:
            case 290:
            case 330:
                log("Talking to Mining Instructor");
                talkTo(MINING_GUIDE);
                break;

            case 300:
                log("Mining tin");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (Inventory.contains("Tin ore")) {
                    script.sleep(1000);
                    return;
                }
                GameObject tinRock = GameObjects.closest(rock -> rock != null && rock.getName().equals("Tin rocks")
                        && rock.hasAction("Mine") && Map.canReach(rock));
                if (tinRock == null) {
                    log("No reachable Tin Rocks found!");
                    return;
                }
                if (tinRock.distance(Players.getLocal()) > 1.5D) {
                    walkHuman(tinRock.getTile());
                    Sleep.sleepUntil(() -> (tinRock.distance(Players.getLocal()) <= 1.5D),
                            Calculations.random(1500, 2500));
                    return;
                }
                if (tinRock.interact("Mine")) {
                    Sleep.sleepUntil(() -> (Players.getLocal().getAnimation() != -1), Calculations.random(1000, 2000));
                    Sleep.sleepUntil(() -> !(!Inventory.contains("Tin ore") && Players.getLocal().getAnimation() != -1),
                            Calculations.random(5000, 7000));
                }
                break;

            case 310:
                log("Mining copper");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (Inventory.contains("Copper ore")) {
                    script.sleep(1000);
                    return;
                }
                GameObject copperRock = GameObjects
                        .closest(rock -> rock != null && rock.getName().equals("Copper rocks")
                                && rock.hasAction("Mine") && Map.canReach(rock));
                if (copperRock == null) {
                    log("No reachable Copper Rocks found!");
                    return;
                }
                if (copperRock.distance(Players.getLocal()) > 1.5D) {
                    walkHuman(copperRock.getTile());
                    Sleep.sleepUntil(() -> (copperRock.distance(Players.getLocal()) <= 1.5D),
                            Calculations.random(1500, 2500));
                    return;
                }
                if (copperRock.interact("Mine")) {
                    Sleep.sleepUntil(() -> (Players.getLocal().getAnimation() != -1), Calculations.random(1000, 2000));
                    Sleep.sleepUntil(
                            () -> !(!Inventory.contains("Copper ore") && Players.getLocal().getAnimation() != -1),
                            Calculations.random(5000, 7000));
                }
                break;

            case 320:
                log("Smelting bronze bar");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(900, 1200);
                    break;
                }
                if (!Inventory.isItemSelected()) {
                    script.sleep(1200, 1800);
                    Inventory.interact("Tin ore", "Use");
                    Sleep.sleepUntil(Inventory::isItemSelected, Calculations.random(800, 1200));
                    break;
                }
                GameObject furnace = GameObjects.closest(10082);
                if (furnace != null && furnace.interact("Use")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> Inventory.contains("Bronze bar"), Calculations.random(2000, 3000));
                }
                break;

            case 340:
                log("Smithing Bronze Dagger");
                if (!Inventory.isItemSelected()) {
                    Inventory.interact("Bronze bar", "Use");
                    Sleep.sleepUntil(Inventory::isItemSelected, 1200);
                    break;
                }
                GameObject anvil = GameObjects.closest("Anvil");
                if (anvil != null && anvil.interact("Use")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 340),
                            Calculations.random(2000, 3000));
                }
                break;

            case 350:
                log("Selecting dagger on interface");
                Widgets.get(312, 9).interact();
                Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 350), 3000);
                break;

            case 360:
                log("Walking to Combat Instructor");
                if (Players.getLocal().distance(new Tile(3094, 9502, 0)) > 5.0D) {
                    walkHuman(new Tile(3094, 9502, 0));
                    break;
                }
                GameObject gate2 = GameObjects.closest("Gate");
                if (gate2 != null && gate2.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 360),
                            Calculations.random(1200, 1800));
                }
                break;

            case 370:
            case 410:
                log("Talking to Combat Instructor");
                talkTo(COMBAT_GUIDE);
                break;

            case 390:
                log("Opening Equipment Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Widgets.getAllContainingText("Click on the flashing icon").isEmpty()) {
                    Tabs.open(Tab.EQUIPMENT);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.EQUIPMENT), Calculations.random(1000, 1500));
                    return;
                }
                talkTo(COMBAT_GUIDE);
                break;

            case 400:
                log("Opening Equipment Stats");
                Widgets.get(387, 1).interact();
                Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 400), Calculations.random(1200, 1600));
                break;

            case 405:
                log("Equipping Bronze Dagger");
                if (Inventory.interact("Bronze dagger", "Equip")) {
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 405),
                            Calculations.random(1200, 1600));
                } else if (Inventory.interact("Bronze dagger", "Wield")) {
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 405),
                            Calculations.random(1200, 1600));
                }
                Widgets.get(84, 4).interact();
                break;

            case 420:
                log("Equipping Sword & Shield");
                Item weapon = Equipment.getItemInSlot(EquipmentSlot.WEAPON.getSlot());
                if (weapon != null && weapon.getName().contains("dagger")) {
                    Equipment.unequip(EquipmentSlot.WEAPON);
                    break;
                }
                if (weapon != null) {
                    Inventory.interact("Wooden shield", "Wield");
                    Sleep.sleepUntil(() -> Equipment.isSlotFull(EquipmentSlot.SHIELD.getSlot()),
                            Calculations.random(1200, 1600));
                    break;
                }
                Inventory.interact("Bronze sword", "Wield");
                Sleep.sleepUntil(() -> Equipment.isSlotFull(EquipmentSlot.WEAPON.getSlot()),
                        Calculations.random(1200, 1600));
                break;

            case 430:
                log("Opening Combat Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Tabs.isOpen(Tab.COMBAT)) {
                    Tabs.open(Tab.COMBAT);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.COMBAT), Calculations.random(1000, 1500));
                }
                break;

            case 440:
                log("Crossing gate to giant rats");
                if (Players.getLocal().distance(new Tile(3111, 9518, 0)) > 5.0D) {
                    walkHuman(new Tile(3111, 9518, 0));
                    break;
                }
                GameObject gate3 = GameObjects.closest("Gate");
                if (gate3 != null && gate3.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 440),
                            Calculations.random(1200, 1600));
                }
                break;

            case 450:
                log("Fighting giant rat");
                NPC rat = NPCs.closest(n -> n != null && n.getName() != null &&
                        n.getName().equals("Giant rat") && !n.isInCombat());
                if (rat != null) {
                    if (rat.interact("Attack")) {
                        walkingSleep();
                        Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 450),
                                Calculations.random(1200, 2000));
                        break;
                    }
                    if (Camera.getPitch() < Calculations.random(150, 200)) {
                        Camera.rotateToPitch(Calculations.random(200, 360));
                    }
                }
                break;

            case 460:
                log("Waiting after rat fight");
                Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 460), 2400);
                break;

            case 470:
                log("Talking to Combat Instructor after rat fight");
                if (!Map.canReach(new Tile(3112, 9518, 0))) {
                    GameObject gate4 = GameObjects.closest("Gate");
                    if (gate4 != null && gate4.interact("Open")) {
                        walkingSleep();
                        Sleep.sleepUntil(() -> (Players.getLocal().getTile().getX() == 3111),
                                Calculations.random(1200, 1400));
                    }
                    break;
                }
                talkTo(COMBAT_GUIDE);
                break;

            case 480:
                log("Equipping Bow & Arrows / attacking rat");
                if (Equipment.isSlotEmpty(EquipmentSlot.ARROWS.getSlot())) {
                    Inventory.interact("Bronze arrow", "Wield");
                    Sleep.sleepUntil(() -> Equipment.isSlotFull(EquipmentSlot.ARROWS.getSlot()),
                            Calculations.random(1200, 1600));
                    break;
                }
                if (Inventory.contains("Shortbow")) {
                    Inventory.interact("Shortbow", "Wield");
                    Sleep.sleepUntil(() -> !Inventory.contains("Shortbow"), Calculations.random(1200, 1600));
                    break;
                }
                NPC rat2 = NPCs.closest(n -> n != null && n.getName() != null &&
                        n.getName().equals("Giant rat") && !n.isInCombat());
                if (rat2 != null && rat2.interact("Attack")) {
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 480),
                            Calculations.random(2400, 3600));
                }
                break;

            case 490:
                log("Attacking giant rat with bow");
                if (Equipment.isSlotEmpty(EquipmentSlot.ARROWS.getSlot())) {
                    Inventory.interact("Bronze arrow", "Wield");
                    Sleep.sleepUntil(() -> Equipment.isSlotFull(EquipmentSlot.ARROWS.getSlot()), 1200);
                    break;
                }
                if (Inventory.contains("Shortbow")) {
                    Inventory.interact("Shortbow", "Wield");
                    Sleep.sleepUntil(() -> !Inventory.contains("Shortbow"), 1200);
                    break;
                }
                if (Players.getLocal().getInteractingCharacter() == null) {
                    NPC rat3 = NPCs.closest(n -> n != null && n.getName() != null &&
                            n.getName().equals("Giant rat") && !n.isInCombat());
                    if (rat3 != null && rat3.interact("Attack")) {
                        Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 490),
                                Calculations.random(2400, 3600));
                    }
                    break;
                }
                Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 490),
                        Calculations.random(2400, 3000));
                break;

            case 500:
                log("Climbing ladder to surface");
                if (Players.getLocal().distance(new Tile(3112, 9525, 0)) > 5.0D) {
                    walkHuman(combatToLadder[combatToLadder.length - 1]);
                    break;
                }
                GameObject ladderUp = GameObjects.closest("Ladder");
                if (ladderUp != null && ladderUp.interact("Climb-up")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 500),
                            Calculations.random(2400, 3600));
                }
                break;

            case 510:
                log("Using the Bank");
                Tile tile510 = new Tile(3122, 3123, 0);
                if (Players.getLocal().distance(tile510) > 5.0D) {
                    walkHuman(tile510);
                    break;
                }
                if (Dialogues.getOptionIndex("Yes.") > 0) {
                    Dialogues.clickOption("Yes.");
                    Sleep.sleepUntil(Bank::isOpen, Calculations.random(1200, 1600));
                    Bank.depositAllItems();
                    Sleep.sleepUntil(Inventory::isEmpty, Calculations.random(800, 1200));
                    Bank.depositAllEquipment();
                    Sleep.sleepUntil(Equipment::isEmpty, Calculations.random(800, 1200));
                    Bank.close();
                    Sleep.sleepUntil(() -> !Bank.isOpen(), Calculations.random(800, 1200));
                    break;
                }
                if (!Dialogues.canContinue()) {
                    GameObject bankBooth = GameObjects.closest("Bank booth");
                    if (bankBooth != null && bankBooth.interact("Use")) {
                        Sleep.sleepUntil(Dialogues::canContinue, Calculations.random(2400, 3000));
                    }
                    break;
                }
                Dialogues.clickContinue();
                Sleep.sleepUntil(() -> !Dialogues.canContinue(), Calculations.random(1200, 1400));
                break;

            case 520:
                log("Using Poll booth");
                if (Bank.isOpen()) {
                    Bank.close();
                    Sleep.sleepUntil(() -> !Bank.isOpen(), Calculations.random(1200, 1600));
                    break;
                }
                GameObject pollBooth = GameObjects.closest("Poll booth");
                if (pollBooth != null && pollBooth.interact("Use")) {
                    walkingSleep();
                    Sleep.sleepUntil(Dialogues::canContinue, 2400);
                    if (Dialogues.canContinue()) {
                        while (Dialogues.canContinue() || PlayerSettings.getConfig(375) == 0) {
                            Dialogues.clickContinue();
                            script.sleep(300, 500);
                        }
                    }
                    log("Poll config: " + PlayerSettings.getConfig(375));
                }
                script.sleep(300, 500);
                if (PlayerSettings.getConfig(375) > 0) {
                    WidgetChild bar = Widgets.get(310, 1);
                    if (bar != null) {
                        bar = bar.getChild(11);
                    }
                    if (bar != null && bar.isVisible()) {
                        bar.interact();
                        Sleep.sleepUntil(() -> (PlayerSettings.getConfig(375) == 0),
                                Calculations.random(1200, 1500));
                    }
                }
                break;

            case 525:
                log("Exiting poll booth area");
                WidgetChild bar2 = Widgets.get(345, 1);
                if (bar2 != null) {
                    bar2 = bar2.getChild(11);
                }
                if (bar2 != null && bar2.isVisible()) {
                    bar2.interact();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(375) == 0),
                            Calculations.random(1200, 1500));
                    break;
                }
                GameObject door525 = GameObjects.closest(g -> g != null && g.getName() != null &&
                        g.getName().equals("Door") && g.getTile().equals(new Tile(3125, 3124, 0)));
                if (door525 != null && door525.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 525),
                            Calculations.random(1600, 2400));
                }
                break;

            case 530:
                log("Talking to Financial Guide");
                talkTo(FINANCIAL_GUIDE);
                break;

            case 531:
            case 532:
                log("Opening Account Management Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Widgets.getAllContainingText("Click on the flashing icon").isEmpty()) {
                    Tabs.open(Tab.ACCOUNT_MANAGEMENT);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.ACCOUNT_MANAGEMENT), Calculations.random(1000, 1500));
                    return;
                }
                talkTo(FINANCIAL_GUIDE);
                return;

            case 540:
                log("Opening door to next area");
                GameObject door540 = GameObjects.closest(g -> g != null && g.getName() != null &&
                        g.getName().equals("Door") && g.getTile().equals(new Tile(3130, 3124, 0)));
                if (door540 != null && door540.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 540),
                            Calculations.random(1600, 2400));
                }
                break;

            case 550:
                log("Talking to Brother Brace");
                if (Players.getLocal().distance(new Tile(3126, 3106, 0)) > 5.0D) {
                    walkHuman(finToPray[finToPray.length - 1]);
                    break;
                }
                GameObject doorLarge = GameObjects.closest(g -> g != null && g.getName() != null &&
                        g.getName().equals("Large door") && g.getTile().equals(new Tile(3129, 3107, 0)));
                if (doorLarge != null && !Map.canReach(NPCs.closest(PRAY_GUIDE).getTile()) &&
                        doorLarge.interact("Open")) {
                    script.sleep(600, 900);
                }
                talkTo(PRAY_GUIDE);
                break;

            case 560:
                log("Opening Prayer Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Widgets.getAllContainingText("Click on the flashing icon").isEmpty()) {
                    Tabs.open(Tab.PRAYER);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.PRAYER), Calculations.random(1000, 1500));
                    return;
                }
                talkTo(PRAY_GUIDE);
                return;

            case 570:
            case 600:
                log("Talking to Brother Brace");
                talkTo(PRAY_GUIDE);
                break;

            case 580:
                log("Opening Friends Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Tabs.isOpen(Tab.FRIENDS)) {
                    Tabs.open(Tab.FRIENDS);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.FRIENDS), Calculations.random(1000, 1500));
                } else {
                    script.sleep(1000);
                }
                return;

            case 590:
                log("Opening Ignore List Tab");
                return;

            case 610:
                log("Opening door toward Magic Instructor");
                GameObject door610 = GameObjects.closest("Door");
                if (door610 != null && door610.interact("Open")) {
                    walkingSleep();
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 610),
                            Calculations.random(1600, 2400));
                }
                break;

            case 620:
                log("Talking to Magic Instructor");
                if (Players.getLocal().distance(new Tile(3141, 3088, 0)) > 5.0D) {
                    walkHuman(prayToMage[prayToMage.length - 1]);
                    break;
                }
                talkTo(MAGIC_GUIDE);
                break;

            case 630:
                log("Opening Magic Tab");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (!Tabs.isOpen(Tab.MAGIC)) {
                    Tabs.open(Tab.MAGIC);
                    Sleep.sleepUntil(() -> Tabs.isOpen(Tab.MAGIC), Calculations.random(1000, 1500));
                } else {
                    script.sleep(1000);
                }
                return;

            case 640:
                log("Talking to Magic Guide");
                talkTo(MAGIC_GUIDE);
                break;

            case 650:
                log("Casting Wind Strike on Chicken");
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(900, 1200));
                    return;
                }
                if (Players.getLocal().distance(new Tile(3139, 3091, 0)) > 2.0D) {
                    walkHuman(new Tile(3139, 3091, 0));
                    break;
                }
                if (Magic.castSpellOn(Normal.WIND_STRIKE, NPCs.closest("Chicken"))) {
                    Sleep.sleepUntil(() -> (PlayerSettings.getConfig(TUT_PROG) != 650),
                            Calculations.random(1600, 2400));
                }
                break;

            case 670:
                log("Completing magic training");
                if (Magic.isSpellSelected()) {
                    Magic.deselect();
                    script.sleep(Calculations.random(500, 800));
                }
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                    script.sleep(Calculations.random(600, 900));
                    break;
                }
                if (Dialogues.areOptionsAvailable()) {
                    String[] options = Dialogues.getOptions();
                    if (options != null) {
                        for (String option : options) {
                            String lower = option.toLowerCase();
                            if (lower.contains("no, i'm not planning to do that")) {
                                Dialogues.chooseOption(option);
                                script.sleep(Calculations.random(700, 1000));
                                Sleep.sleepUntil(() -> !Dialogues.areOptionsAvailable(), 2000);
                                break;
                            }
                            if (lower.contains("yes.")) {
                                Dialogues.chooseOption(option);
                                script.sleep(Calculations.random(700, 1000));
                                Sleep.sleepUntil(() -> !Dialogues.areOptionsAvailable(), 2000);
                                break;
                            }
                        }
                    }
                    break;
                }
                talkTo(MAGIC_GUIDE);
                break;

            case 1000:
                script.sleep(Calculations.random(6000, 8000));
                log("Tutorial Island complete!");
                questComplete = true;
                break;

            default:
                // For any other stages, handle generically
                log("Generic handling for stage: " + tutorialStage);
                if (Dialogues.canContinue()) {
                    Dialogues.clickContinue();
                }
                break;
        }
    }

    /**
     * Handle display name creation interface
     */
    private void handleDisplayNameCreation() {
        if (Widgets.get(558, 3) != null && Widgets.get(558, 3).isVisible()) {
            log("Handling display name creation");
            Widgets.get(558, 3).interact();
            script.sleep(Calculations.random(600, 1200));

            // Type a random name
            String displayName = generateRandomDisplayName();
            Keyboard.type(displayName, true);
            script.sleep(Calculations.random(600, 1000));

            // Confirm the name
            WidgetChild confirmButton = Widgets.get(558, 19);
            if (confirmButton != null && confirmButton.isVisible()) {
                confirmButton.interact("Set name");
                Sleep.sleepUntil(() -> !(Widgets.getWidget(279) == null && Widgets.get(558, 13) == null),
                        Calculations.random(3000, 5000));
            }
        }

        // Check for error message and try again with a new name if needed
        WidgetChild errorMessage = Widgets.get(558, 13);
        if (errorMessage != null && errorMessage.isVisible()) {
            String text = errorMessage.getText();
            if (text != null && text.contains("Sorry, the display name")) {
                log("Name not available. Retrying with a new name...");
                WidgetChild altInput = Widgets.get(558, 12);
                if (altInput != null && altInput.isVisible()) {
                    altInput.interact();
                    script.sleep(600, 800);

                    // Clear existing text
                    for (int i = 0; i < 15; i++) {
                        Keyboard.typeKey(8);
                        script.sleep(50, 100);
                    }

                    // Enter new name
                    String newName = generateRandomDisplayName();
                    Keyboard.type(newName, true);
                    script.sleep(Calculations.random(600, 1000));

                    // Try to confirm again
                    WidgetChild confirmAgain = Widgets.get(558, 19);
                    if (confirmAgain != null && confirmAgain.isVisible()) {
                        confirmAgain.interact("Set name");
                        Sleep.sleepUntil(() -> !(Widgets.getWidget(279) == null &&
                                errorMessage.isVisible()), Calculations.random(3000, 5000));
                    }
                }
            }
        }
    }

    /**
     * Generate a random display name for character creation
     */
    private String generateRandomDisplayName() {
        String[] syllables = { "ab", "ac", "ad", "af", "ag", "ak", "al", "am", "an", "ap", "ar", "as", "at", "ax", "az",
                "ba", "be", "bi", "bo", "bu", "by", "ca", "ce", "ch", "ci", "co", "cu", "cy", "da", "de", "di", "do",
                "du", "dy", "ed", "ef", "ek", "el", "em", "en", "ep", "er", "es", "et", "ex", "ey", "fa", "fe", "fi",
                "fo", "fu", "fy", "ga", "ge", "gi", "go", "gu", "gy", "ha", "he", "hi", "ho", "hu", "hy", "in", "is",
                "it", "ja", "je", "ji", "jo", "ju", "ka", "ke", "ki", "ko", "ku", "ky", "la", "le", "li", "lo", "lu",
                "ly", "ma", "me", "mi", "mo", "mu", "my", "na", "ne", "ni", "no", "nu", "ny", "ob", "od", "of", "ok",
                "ol", "om", "on", "op", "or", "os", "ow", "ox", "oy", "pa", "pe", "pi", "po", "pu", "ra", "re", "ri",
                "ro", "ru", "ry", "sa", "se", "sh", "si", "so", "su", "sy", "ta", "te", "ti", "to", "tu", "ty", "ub",
                "ul", "um", "un", "up", "ur", "us", "ut", "va", "ve", "vi", "vo", "vu", "wa", "we", "wi", "wo", "wu",
                "xa", "xe", "xi", "xu", "ya", "ye", "yi", "yo", "yu", "za", "ze", "zi", "zo", "zu" };

        int syllableCount = Calculations.random(2, 4);
        StringBuilder sb = new StringBuilder();
        String prevSyllable = null;

        for (int i = 0; i < syllableCount; i++) {
            String nextSyllable;
            int attempts = 0;
            do {
                attempts++;
                nextSyllable = syllables[Calculations.random(0, syllables.length - 1)];
            } while (attempts < 10 && prevSyllable != null
                    && nextSyllable.charAt(0) == prevSyllable.charAt(prevSyllable.length() - 1));

            sb.append(nextSyllable);
            prevSyllable = nextSyllable;
        }

        // Occasionally add a number
        if (Calculations.random(0, 10) < 3) {
            int randomNum = Calculations.random(1, 9999);
            sb.append(randomNum);
        }

        // Ensure name isn't too long
        String name = sb.toString();
        if (name.length() > 12) {
            name = name.substring(0, 12);
        }

        // Capitalize first letter
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);

        return name;
    }

    /**
     * Talk to a specific NPC guide
     */
    private void talkTo(String npc) {
        if (Dialogues.canContinue()) {
            Dialogues.clickContinue();
            script.sleep(Calculations.random(900, 1200));
            return;
        }

        if (Dialogues.areOptionsAvailable()) {
            if (npc.equals(RUNESCAPE_GUIDE)) {
                int optionIndex = Dialogues.getOptionIndex("I am an experienced player.");
                if (optionIndex != -1) {
                    Dialogues.chooseOption(optionIndex);
                    Sleep.sleepUntil(() -> !Dialogues.areOptionsAvailable(), 2000);
                    return;
                }
            }

            // If no specific option found, choose the first one
            Dialogues.chooseOption(1);
            script.sleep(Calculations.random(600, 900));
            return;
        }

        if (!Dialogues.canContinue()) {
            NPC target = NPCs.closest(npc);
            if (target != null) {
                if (target.isOnScreen()) {
                    if (target.interact("Talk-to")) {
                        walkingSleep();
                        Sleep.sleepUntil(Dialogues::canContinue, Calculations.random(1200, 1600));
                    }
                } else {
                    walkHuman(target.getTile());
                }
            } else {
                log("Could not find NPC: " + npc);
                Camera.setZoom(0);
                script.sleep(500);
            }
        } else {
            Dialogues.clickContinue();
            script.sleep(Calculations.random(600, 900));
        }
    }

    /**
     * Randomize character appearance during creation
     */
    private void randomizeCharacterAppearance() {
        log("Randomizing character appearance...");

        // Click confirm button on appearance screen
        WidgetChild confirm = Widgets.get(679, 74);
        if (confirm != null && confirm.isVisible()) {
            confirm.interact();
            Sleep.sleepUntil(() -> !confirm.isVisible(), 5000);
        }
    }

    /**
     * Walk to a tile in a human-like manner
     */
    private void walkHuman(Tile tile) {
        Walking.walk(tile);
        Sleep.sleepUntil(() -> Players.getLocal().isMoving(), Calculations.random(1200, 1600));
        Sleep.sleepUntil(() -> !Players.getLocal().isMoving(), Calculations.random(2400, 3600));
    }

    /**
     * Sleep while walking
     */
    private void walkingSleep() {
        Sleep.sleepUntil(
                () -> !(Players.getLocal().isMoving() && Players.getLocal().distance(Walking.getDestination()) >= 3.0D),
                Calculations.random(2400, 3600));
    }

    /**
     * Light a fire using logs and tinderbox
     */
    private void lightFire() {
        if (!Inventory.contains("Logs")) {
            GameObject tree = GameObjects.closest("Tree");
            if (tree != null && tree.interact("Chop down")) {
                walkingSleep();
                Sleep.sleepUntil(() -> Inventory.contains("Logs"), Calculations.random(1200, 1600));
            }
            if (Players.getLocal().getAnimation() != -1)
                Sleep.sleepUntil(() -> Inventory.contains("Logs"), Calculations.random(4000, 5000));
        }

        if (Inventory.contains("Logs")) {
            if (!Inventory.isItemSelected()) {
                Inventory.interact("Tinderbox", "Use");
                Sleep.sleepUntil(Inventory::isItemSelected, Calculations.random(800, 1200));
            }
            if (Inventory.isItemSelected()) {
                Inventory.interact("Logs", "Use");
                Sleep.sleepUntil(() -> (Players.getLocal().getAnimation() != -1), Calculations.random(1200, 1600));
                Sleep.sleepUntil(() -> (Players.getLocal().getAnimation() == -1), Calculations.random(6000, 8000));
            }
        }
    }

    /**
     * Cook raw shrimp on a fire
     */
    private void cookShrimp() {
        GameObject fire = GameObjects.closest("Fire");
        if (fire == null) {
            lightFire();
        } else {
            if (!Inventory.isItemSelected() && Inventory.interact("Raw shrimps", "Use"))
                Sleep.sleepUntil(Inventory::isItemSelected, Calculations.random(800, 1200));
            if (Inventory.isItemSelected() && fire.interact("Use")) {
                walkingSleep();
                Sleep.sleepUntil(() -> (Players.getLocal().getAnimation() != -1), Calculations.random(2000, 3000));
                Sleep.sleepUntil(() -> (Players.getLocal().getAnimation() == -1), Calculations.random(2000, 3000));
            }
        }
    }
}
