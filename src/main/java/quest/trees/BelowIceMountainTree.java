package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.DecisionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.nodes.actions.InteractWithObjectNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.GrandExchangeUtil.ItemRequest;
import quest.utils.GrandExchangeUtil.PriceStrategy;

import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.input.Camera;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;
import org.dreambot.api.methods.emotes.Emotes;
import org.dreambot.api.methods.emotes.Emote;

/**
 * Below Ice Mountain - Tree-based automation based on manual recording (bim.log)
 * Emphasizes strict state gating to avoid regression.
 */
public class BelowIceMountainTree extends QuestTree {

    // Optional config (not strictly needed due to Quests API)
    private static final int POSSIBLE_CONFIG_ID = 101; // seen in log changing 28->29

    // Key tiles/NPCs from log
    private static final Tile WILLOW_TILE = new Tile(3003, 3435, 0); // Ice Mountain south
    private static final Tile CHECKAL_TILE = new Tile(3087, 3415, 0); // Barbarian Village
    private static final Tile LONGHALL_DOOR_TILE = new Tile(3078, 3435, 0);
    private static final Tile ATLAS_TILE = new Tile(3076, 3440, 0);
    private static final Tile CHARLIE_TRAMP_TILE = new Tile(3209, 3392, 0); // Varrock south
    private static final Tile COOK_TILE = new Tile(3231, 3401, 0); // Varrock castle kitchen area
    private static final Tile MARLEY_TILE = new Tile(3087, 3471, 0); // Edgeville vicinity from log
    private static final Tile BURNTOF_TILE = new Tile(2956, 3368, 0); // Falador west NPC from log
    private static final Tile WEST_RUINS_TILE = new Tile(2989, 3442, 0); // West side entrance meeting
    private static final Tile WILLOW_NORTH_TILE = new Tile(2994, 3496, 0); // Willow near north after regroup

    // Areas to help with simple proximity checks
    private static final Area LONGHALL_AREA = new Area(3071, 3443, 3083, 3433, 0);
    // Broad bounding box around Ice Mountain (south + north + west ruins)
    private static final Area ICE_MOUNTAIN_AREA = new Area(2940, 3515, 3055, 3410, 0);

    // Items observed/needed in recording
    private static final String KNIFE = "Knife";
    private static final String BREAD = "Bread";
    private static final String COOKED_MEAT = "Cooked meat";

    // Internal step flags to prevent regression
    private boolean spokeWillow = false;
    private boolean spokeCheckal = false;
    private boolean openedLonghall = false;
    private boolean spokeAtlas = false;
    private boolean flexDone = false;
    private boolean spokeCharlie = false;
    private boolean spokeCook = false;
    private boolean madeSandwich = false;
    private boolean spokeMarley = false;
    private boolean spokeBurntof = false;
    private boolean guardianDefeated = false;
    private boolean syncedFromWorld = false;

    // Nodes
    private QuestNode smart;

    private QuestNode talkWillow;
    private QuestNode talkCheckal;
    private QuestNode openLonghallDoor;
    private QuestNode talkAtlas;
    private QuestNode doFlexEmote;
    private QuestNode talkCharlie;
    private QuestNode talkCook;
    private QuestNode buyBIMItems;
    private QuestNode makeSandwich;
    private QuestNode talkMarley;
    private QuestNode talkBurntof;
    private QuestNode walkToRuins;
    private QuestNode openMapAtRuins;
    private QuestNode talkWillowEntrance;
    private QuestNode defeatAncientGuardian;
    private QuestNode returnToWillowFinish;

    public BelowIceMountainTree() {
        super("Below Ice Mountain");
    }

    @Override
    protected void buildTree() {
        createNodes();
        createSmartDecision();
        rootNode = smart;
    }

    private void createNodes() {
        // GE purchase node for required sandwich items
        buyBIMItems = new ActionNode("ge_bim_items", "Buy Knife/Bread/Cooked meat from GE", null) {
            @Override
            public boolean shouldSkip() {
                return !isMissingSandwichItems();
            }
            @Override
            protected boolean performAction() {
                java.util.List<ItemRequest> requests = new java.util.ArrayList<>();
                if (!Inventory.contains(KNIFE)) requests.add(new ItemRequest(KNIFE, 1, PriceStrategy.FIXED_500_GP));
                if (!Inventory.contains(BREAD)) requests.add(new ItemRequest(BREAD, 1, PriceStrategy.FIXED_500_GP));
                if (!Inventory.contains(COOKED_MEAT)) requests.add(new ItemRequest(COOKED_MEAT, 1, PriceStrategy.FIXED_500_GP));
                if (requests.isEmpty()) return true;
                ItemRequest[] arr = requests.toArray(new ItemRequest[0]);
                return GrandExchangeUtil.buyItems(arr);
            }
        };
        talkWillow = new TalkToNPCNode("talk_willow", "Willow", WILLOW_TILE) {
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) spokeWillow = true;
                return ok;
            }
        };

    talkCheckal = new TalkToNPCNode("talk_checkal", "Checkal", CHECKAL_TILE) {
            @Override
            public boolean shouldSkip() {
                // Skip Checkal only if we've clearly progressed past the Barbarian Village stage
                return spokeCheckal || openedLonghall || spokeAtlas || flexDone || spokeCharlie ||
                       spokeCook || madeSandwich || spokeMarley || spokeBurntof || guardianDefeated;
            }
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) spokeCheckal = true;
                return ok;
            }
        };

        openLonghallDoor = new InteractWithObjectNode("open_longhall", "Longhall door", "Open", LONGHALL_DOOR_TILE, "Longhall door") {
            @Override
            public boolean shouldSkip() {
                // If we're already inside, mark step complete so decision can advance
                if (Players.getLocal() != null && LONGHALL_AREA.contains(Players.getLocal())) {
                    openedLonghall = true;
                    return true;
                }
                return openedLonghall;
            }
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) openedLonghall = true;
                return ok;
            }
        };

        talkAtlas = new TalkToNPCNode("talk_atlas", "Atlas", ATLAS_TILE) {
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) spokeAtlas = true;
                return ok;
            }
        };

        doFlexEmote = new ActionNode("do_flex", "Perform Flex emote", null) {
            @Override
            protected boolean performAction() {
                if (Players.getLocal() == null) return false;
                // Ensure we're near Checkal before flexing, like in the manual recording
                if (Players.getLocal().getTile().distance(CHECKAL_TILE) > 6) {
                    new WalkToLocationNode("walk_checkal", CHECKAL_TILE, 3, "To Checkal").execute();
                    if (Players.getLocal().getTile().distance(CHECKAL_TILE) > 6) return false;
                }
                // Use DreamBot's Emotes API to perform FLEX reliably
                boolean ok = Emotes.doEmote(Emote.FLEX);
                if (!ok) return false;
                // Small wait for the emote to play
                Sleep.sleep(600, 1000);
                flexDone = true;
                return true;
            }
        };

        talkCharlie = new TalkToNPCNode("talk_charlie", "Charlie the Tramp", CHARLIE_TRAMP_TILE) {
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) spokeCharlie = true;
                return ok;
            }
        };

        talkCook = new TalkToNPCNode("talk_cook", "Cook", COOK_TILE) {
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) spokeCook = true;
                return ok;
            }
        };

        makeSandwich = new ActionNode("make_sandwich", "Slice bread with knife", null) {
            @Override
            public boolean shouldSkip() {
                // Only skip once successfully made; missing items should route to GE/COOK and not skip
                return madeSandwich;
            }
            @Override
            protected boolean performAction() {
                // If items are missing, let decision route us to GE/COOK
                if (!Inventory.contains(KNIFE) || !Inventory.contains(BREAD)) {
                    return false;
                }
                boolean usedKnife = Inventory.interact(KNIFE, "Use");
                if (!usedKnife) return false;
                Sleep.sleep(200, 400);
                boolean usedOnBread = Inventory.interact(BREAD, "Use");
                if (!usedOnBread) return false;
                Sleep.sleep(500, 900);
                // Consider it done when both interactions occurred
                madeSandwich = true;
                return true;
            }
        };

        talkMarley = new TalkToNPCNode("talk_marley", "Marley", MARLEY_TILE) {
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) spokeMarley = true;
                return ok;
            }
        };

        talkBurntof = new TalkToNPCNode("talk_burntof", "Burntof", BURNTOF_TILE) {
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                if (ok) spokeBurntof = true;
                return ok;
            }
        };

        walkToRuins = new WalkToLocationNode("walk_west_ruins", WEST_RUINS_TILE, 4, "Ancient Ruins (west side)");

        openMapAtRuins = new InteractWithObjectNode("open_ruins_map", "Map", "Open", WEST_RUINS_TILE, "Map at west ruins") {
            @Override
            public boolean shouldSkip() {
                // Make optional and harmless: always skip to avoid loops if object isn't present
                return true;
            }
        };

        talkWillowEntrance = new TalkToNPCNode("talk_willow_entrance", "Willow", WILLOW_NORTH_TILE) {
            @Override
            public boolean shouldSkip() {
                // If we can already see the Ancient Guardian, skip
                return NPCs.closest(n -> n != null && "Ancient Guardian".equals(n.getName())) != null;
            }
        };

        defeatAncientGuardian = new ActionNode("kill_guardian", "Defeat Ancient Guardian", null) {
            @Override
            public boolean shouldSkip() { return guardianDefeated; }
            @Override
            protected boolean performAction() {
                NPC guardian = NPCs.closest(n -> n != null && n.getName() != null && n.getName().equals("Ancient Guardian"));
                if (guardian == null) {
                    // In recording this is in an instance; just wait briefly and retry
                    Sleep.sleep(800, 1200);
                    return false;
                }
                if (guardian.distance() > 7) {
                    new WalkToLocationNode("walk_guardian", guardian.getTile(), 3, "To Guardian").execute();
                }
                if (!guardian.interact("Attack")) return false;
                Sleep.sleepUntil(() -> !guardian.exists() || guardian.getHealthPercent() <= 0, 20000);
                guardianDefeated = !guardian.exists() || guardian.getHealthPercent() <= 0;
                return guardianDefeated;
            }
        };

        returnToWillowFinish = new TalkToNPCNode("finish_willow", "Willow", WILLOW_TILE) {
            @Override
            public boolean shouldSkip() {
                try {
                    return Quests.isFinished(FreeQuest.BELOW_ICE_MOUNTAIN);
                } catch (Throwable t) {
                    return false;
                }
            }
            @Override
            protected boolean performAction() {
                boolean ok = super.performAction();
                Sleep.sleep(600, 900);
                return ok;
            }
        };
    }

    private void createSmartDecision() {
        smart = new DecisionNode("smart_bim", "Decide next BIM step") {
            @Override
            protected String makeDecision() {
                // Continuously sync from world so resume works even if player/location weren't ready on first tick
                try { syncFromWorldState(); } catch (Throwable ignored) {}
                boolean finished = false;
                boolean started = false;
                try {
                    finished = Quests.isFinished(FreeQuest.BELOW_ICE_MOUNTAIN);
                    started = Quests.isStarted(FreeQuest.BELOW_ICE_MOUNTAIN);
                } catch (Throwable ignored) {}
                if (finished) { setQuestComplete(); return "COMPLETE"; }

                // Strict fast-path: only after ALL prerequisites are explicitly done
                if (Players.getLocal() != null && ICE_MOUNTAIN_AREA.contains(Players.getLocal().getTile())
                        && spokeCharlie && spokeCook && madeSandwich && spokeMarley && spokeBurntof) {
                    if (Players.getLocal().getTile().distance(WEST_RUINS_TILE) > 12) return "MEET_RUINS";
                    if (NPCs.closest(n -> n != null && n.getName() != null && n.getName().equals("Ancient Guardian")) == null) return "WILLOW_ENTRANCE";
                    if (!guardianDefeated) return "GUARDIAN";
                    return "FINISH";
                }

                // Strict forward-only gating to avoid regression
                if (!spokeWillow) return "WILLOW";
                if (!spokeCheckal) return "CHECKAL";
                if (!openedLonghall) return "OPEN_LONGHALL";
                if (!spokeAtlas) return "ATLAS";
                if (!flexDone) return "FLEX";
                if (!spokeCharlie) return "CHARLIE";
                // Ensure sandwich items before Cook/Sandwich steps
                if (!spokeCook && isMissingSandwichItems()) return "GE_SHOP";
                if (!spokeCook) return "COOK";
                if (!madeSandwich && isMissingSandwichItems()) return "GE_SHOP";
                if (!madeSandwich) return "SANDWICH";
                // Crew recruited: must explicitly do Marley then Burntof
                if (!spokeMarley) return "MARLEY";
                if (!spokeBurntof) return "BURNTOF";
                // After recruiting, meet at ruins then talk to Willow near entrance before guardian
                if (Players.getLocal() != null && Players.getLocal().getTile().distance(WEST_RUINS_TILE) > 10) return "MEET_RUINS";
                if (NPCs.closest(n -> n != null && n.getName() != null && n.getName().equals("Ancient Guardian")) == null) return "WILLOW_ENTRANCE";
                if (!guardianDefeated) return "GUARDIAN";
                return "FINISH";
            }
        };

        ((DecisionNode) smart)
            .addBranch("WILLOW", chain(talkWillow))
            .addBranch("CHECKAL", chain(talkCheckal))
            .addBranch("OPEN_LONGHALL", chain(openLonghallDoor))
            .addBranch("ATLAS", chain(talkAtlas))
            .addBranch("FLEX", chain(doFlexEmote))
            .addBranch("CHARLIE", chain(talkCharlie))
            .addBranch("GE_SHOP", chain(buyBIMItems))
            .addBranch("COOK", chain(talkCook))
            .addBranch("SANDWICH", chain(makeSandwich))
            .addBranch("MARLEY", chain(talkMarley))
            .addBranch("BURNTOF", chain(talkBurntof))
            .addBranch("MEET_RUINS", chain(walkToRuins, openMapAtRuins))
            .addBranch("WILLOW_ENTRANCE", chain(talkWillowEntrance))
            .addBranch("GUARDIAN", chain(defeatAncientGuardian))
            .addBranch("FINISH", chain(returnToWillowFinish))
            .setDefaultBranch(talkWillow);
    }

    private QuestNode chain(QuestNode... nodes) {
        return new ActionNode("bim_chain", "Execute BIM chain", null) {
            int idx = 0;
            @Override
            protected boolean performAction() {
                while (idx < nodes.length) {
                    QuestNode n = nodes[idx];
                    QuestNode.ExecutionResult r = n.execute();
                    if (r.isSuccess()) {
                        idx++;
                        Sleep.sleep(250, 500);
                        continue;
                    }
                    if (r.isRetry() || r.isInProgress()) return false;
                    if (r.isFailed()) return false;
                }
                idx = 0;
                return true;
            }
        };
    }

    private void syncFromWorldState() {
        // Use DreamBot quest API and coarse world signals to forward-fill flags when resuming
        try {
            if (Quests.isFinished(FreeQuest.BELOW_ICE_MOUNTAIN)) {
                spokeWillow = spokeCheckal = openedLonghall = spokeAtlas = flexDone = spokeCharlie = spokeCook = madeSandwich = spokeMarley = guardianDefeated = true;
                return;
            }
        } catch (Throwable ignored) {}

        try {
            if (Quests.isStarted(FreeQuest.BELOW_ICE_MOUNTAIN)) {
                // At minimum, quest is started — we already talked to Willow
                spokeWillow = true;
            }
        } catch (Throwable ignored) {}

        // Location-based inference to avoid redoing early steps (conservative)
        if (Players.getLocal() != null) {
            // Do NOT blanket-complete early steps just for being near Ice Mountain; only set what is certain

            // If we're inside the longhall, we must have done Checkal and opened the door
            if (LONGHALL_AREA.contains(Players.getLocal())) {
                spokeWillow = true;
                spokeCheckal = true;
                openedLonghall = true;
            }

            // Near Atlas tile typically indicates we progressed past door
            if (!spokeAtlas && Players.getLocal().getTile().distance(ATLAS_TILE) <= 8) {
                spokeWillow = true;
                spokeCheckal = true;
                openedLonghall = true;
            }

            // If we're in Varrock south near Charlie, assume up to FLEX done
            if (Players.getLocal().getTile().distance(CHARLIE_TRAMP_TILE) <= 10) {
                spokeWillow = true;
                spokeCheckal = true;
                openedLonghall = true;
                spokeAtlas = true;
                flexDone = true;
            }

            // If near Varrock cook, assume Charlie done as well
            if (Players.getLocal().getTile().distance(COOK_TILE) <= 10) {
                spokeWillow = true;
                spokeCheckal = true;
                openedLonghall = true;
                spokeAtlas = true;
                flexDone = true;
                spokeCharlie = true;
            }

            // If near Marley, assume Marley recruited
            if (Players.getLocal().getTile().distance(MARLEY_TILE) <= 10) {
                spokeMarley = true;
            }

            // If near Burntof in Falador west, assume Burntof recruited
            if (Players.getLocal().getTile().distance(BURNTOF_TILE) <= 10) {
                spokeBurntof = true;
            }

            // Do NOT auto-complete at west ruins or Willow north; decision logic will route appropriately
        }
    }

    private boolean isMissingSandwichItems() {
        return !Inventory.contains(KNIFE) || !Inventory.contains(BREAD) || !Inventory.contains(COOKED_MEAT);
    }

    private boolean assumeBurntofBasedOnProgress() {
        // If all earlier prerequisite steps are done, it's safe to infer Burntof was recruited
        return spokeWillow && spokeCheckal && openedLonghall && spokeAtlas && flexDone && spokeCharlie && spokeCook && madeSandwich;
    }

    private boolean crewRecruitedAssumed() {
        return (spokeMarley && (spokeBurntof || assumeBurntofBasedOnProgress()))
                || (assumeBurntofBasedOnProgress() && spokeMarley);
    }

    private void ensureEarlyStepsComplete() {
        spokeWillow = true;
        spokeCheckal = true;
        openedLonghall = true;
        spokeAtlas = true;
        flexDone = true;
        spokeCharlie = true;
        spokeCook = true;
        madeSandwich = true;
    }
}
