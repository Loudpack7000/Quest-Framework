package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.methods.container.impl.Inventory;
import quest.utils.GrandExchangeUtil;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.methods.quest.Quests;
import org.dreambot.api.methods.quest.book.FreeQuest;

public class ImpCatcherTree extends QuestTree {
    // Prefer DreamBot Quest API to detect completion; fallback to varbit 11
    private static final int VARBIT_IMP_CATCHER = 11;
    private static final int VARBIT_COMPLETE = 3; // Fallback assumption: 3 = completed
    private static final Tile MIZGOG_LOCATION = new Tile(3103, 3163, 2);
    private static final String[] REQUIRED_BEADS = {
        "Red bead", "Yellow bead", "White bead", "Black bead"
    };

    private QuestNode buyBeadsNode, walkToMizgogNode, talkToMizgogNode, questCompleteNode, smartDecisionNode;

    public ImpCatcherTree() {
        super("Imp Catcher");
    }

    @Override
    protected void buildTree() {
        buyBeadsNode = new ActionNode("buy_beads", "Buy beads at Grand Exchange") {
            @Override
            protected boolean performAction() {
                for (String bead : REQUIRED_BEADS) {
                    if (!Inventory.contains(bead)) {
                        GrandExchangeUtil.buyItem(bead, 1, GrandExchangeUtil.PriceStrategy.CONSERVATIVE);
                        Sleep.sleepUntil(() -> Inventory.contains(bead), 20000);
                    }
                }
                return true;
            }
        };

        walkToMizgogNode = new WalkToLocationNode("walk_mizgog", MIZGOG_LOCATION, "Wizard Mizgog");

        talkToMizgogNode = new TalkToNPCNode("talk_mizgog", "Wizard Mizgog", MIZGOG_LOCATION);

        questCompleteNode = new ActionNode("quest_complete", "Quest Complete") {
            @Override
            protected boolean performAction() {
                setQuestComplete();
                return true;
            }
        };

        smartDecisionNode = new QuestNode("smart_decision", "Smart Decision") {
            @Override
            public ExecutionResult execute() {
                // Completed? Prefer Quest API, fallback to varbit
                if (Quests.isFinished(FreeQuest.IMP_CATCHER) ||
                    PlayerSettings.getBitValue(VARBIT_IMP_CATCHER) >= VARBIT_COMPLETE) {
                    return ExecutionResult.success(questCompleteNode, "Quest complete");
                }
                boolean hasAllBeads = true;
                for (String bead : REQUIRED_BEADS) {
                    if (!Inventory.contains(bead)) {
                        hasAllBeads = false;
                        break;
                    }
                }
                if (!hasAllBeads) {
                    return ExecutionResult.success(buyBeadsNode, "Buy missing beads");
                }
                if (MIZGOG_LOCATION.distance() > 5) {
                    return ExecutionResult.success(walkToMizgogNode, "Walk to Wizard Mizgog");
                }
                return ExecutionResult.success(talkToMizgogNode, "Talk to Wizard Mizgog to complete quest");
            }
        };

    // Set the root of the tree to the smart decision node
    this.rootNode = smartDecisionNode;
    }

    @Override
    public boolean isQuestComplete() {
        if (Quests.isFinished(FreeQuest.IMP_CATCHER)) return true;
        return PlayerSettings.getBitValue(VARBIT_IMP_CATCHER) >= VARBIT_COMPLETE || super.isQuestComplete();
    }

    @Override
    public int getQuestProgress() {
        if (isQuestComplete()) return 100;
        int v = PlayerSettings.getBitValue(VARBIT_IMP_CATCHER);
        // Map basic steps to a % for UI
        if (v <= 0) return 10;
        if (v == 1) return 40;
        if (v == 2) return 70;
        return 90;
    }
}
