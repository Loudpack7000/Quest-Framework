package quest.trees;

import quest.core.QuestTree;
import quest.core.QuestNode;
import quest.nodes.ActionNode;
import quest.nodes.actions.TalkToNPCNode;
import quest.nodes.actions.WalkToLocationNode;
import quest.utils.GrandExchangeUtil;
import quest.utils.GrandExchangeUtil.ItemRequest;
import quest.utils.GrandExchangeUtil.PriceStrategy;

import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.methods.interactive.Players;

/**
 * Goblin Diplomacy quest tree
 * Requirements purchased from GE: Orange dye x1, Blue dye x1, Goblin mail x3
 * Progress varbit (from static map): 62
 */
public class GoblinDiplomacyTree extends QuestTree {

    // Varbit for Goblin Diplomacy progress
    private static final int VARBIT_GOBLIN_DIPLOMACY = 62;

    // Items
    private static final String ORANGE_DYE = "Orange dye";
    private static final String BLUE_DYE = "Blue dye";
    private static final String GOBLIN_MAIL = "Goblin mail";
    private static final String ORANGE_GOBLIN_MAIL = "Orange goblin mail";
    private static final String BLUE_GOBLIN_MAIL = "Blue goblin mail";

    // Locations (from discovery log for Wartface)
    private static final Tile GOBLIN_VILLAGE_TILE = new Tile(2956, 3512, 0);

    // Nodes
    private QuestNode decision;
    private QuestNode buyItems;
    private QuestNode dyeMail;
    private QuestNode walkToVillage;
    private QuestNode presentOrange;
    private QuestNode presentBlue;
    private QuestNode presentBrown;
    private QuestNode questCompleteNode;

    public GoblinDiplomacyTree() {
        super("Goblin Diplomacy");
    }

    @Override
    protected void buildTree() {
        createNodes();
        rootNode = decision;
    }

    private void createNodes() {
        // Smart decision based on varbit and inventory
    decision = new quest.nodes.DecisionNode("gd_decide", "Decide next Goblin Diplomacy step") {
            @Override
            protected String makeDecision() {
                int progress = PlayerSettings.getBitValue(VARBIT_GOBLIN_DIPLOMACY);
                log("Goblin Diplomacy varbit(" + VARBIT_GOBLIN_DIPLOMACY + ") = " + progress);

        if (GoblinDiplomacyTree.this.isQuestComplete()) return "complete";

        boolean haveAllBase = Inventory.count(GOBLIN_MAIL) >= 3 && Inventory.contains(ORANGE_DYE) && Inventory.contains(BLUE_DYE);
        boolean haveDyed = Inventory.contains(ORANGE_GOBLIN_MAIL) && Inventory.contains(BLUE_GOBLIN_MAIL) && Inventory.count(GOBLIN_MAIL) >= 1;

                if (!haveAllBase && !haveDyed) {
                    return "buy";
                }
                if (!haveDyed) {
                    return "dye";
                }
        // We have dyed mails and one plain (brown)
        // Decide whether to walk to village or present specific color
        boolean atVillage = Players.getLocal() != null && Players.getLocal().getTile().distance(GOBLIN_VILLAGE_TILE) <= 8;
        if (!atVillage) return "walk";
        if (Inventory.contains(ORANGE_GOBLIN_MAIL)) return "present_orange";
        if (Inventory.contains(BLUE_GOBLIN_MAIL)) return "present_blue";
        if (Inventory.count(GOBLIN_MAIL) >= 1) return "present_brown";
        return "complete"; // fallback
            }
        };

        // Buy items from GE
        buyItems = new ActionNode("gd_buy", "Buy dyes and goblin mail from GE") {
            @Override
            protected boolean performAction() {
                ItemRequest[] items = new ItemRequest[] {
                    new ItemRequest(ORANGE_DYE, 1, PriceStrategy.MODERATE),
                    new ItemRequest(BLUE_DYE, 1, PriceStrategy.MODERATE),
                    new ItemRequest(GOBLIN_MAIL, 3, PriceStrategy.MODERATE)
                };
                boolean ok = GrandExchangeUtil.buyItems(items);
                if (!ok) return false;
                // Wait briefly for inventory update
                Sleep.sleep(800, 1500);
                return Inventory.count(GOBLIN_MAIL) >= 3 && Inventory.contains(ORANGE_DYE) && Inventory.contains(BLUE_DYE);
            }
        };

        // Dye two goblin mails: one orange and one blue
        dyeMail = new ActionNode("gd_dye", "Dye goblin mail orange and blue") {
            @Override
            protected boolean performAction() {
                // Orange
                if (!Inventory.contains(ORANGE_GOBLIN_MAIL)) {
                    if (Inventory.contains(ORANGE_DYE) && Inventory.contains(GOBLIN_MAIL)) {
                        if (Inventory.interact(ORANGE_DYE, "Use")) {
                            Sleep.sleep(200, 400);
                            Inventory.interact(GOBLIN_MAIL, "Use");
                            Sleep.sleep(600, 900);
                        }
                    }
                }
                // Blue
                if (!Inventory.contains(BLUE_GOBLIN_MAIL)) {
                    if (Inventory.contains(BLUE_DYE) && Inventory.contains(GOBLIN_MAIL)) {
                        if (Inventory.interact(BLUE_DYE, "Use")) {
                            Sleep.sleep(200, 400);
                            Inventory.interact(GOBLIN_MAIL, "Use");
                            Sleep.sleep(600, 900);
                        }
                    }
                }
                // Validate dyed results and that at least one plain remains
                return Inventory.contains(ORANGE_GOBLIN_MAIL) && Inventory.contains(BLUE_GOBLIN_MAIL) && Inventory.count(GOBLIN_MAIL) >= 1;
            }
        };

        // Travel to goblin village center
        walkToVillage = new WalkToLocationNode("gd_walk_village", GOBLIN_VILLAGE_TILE, "Goblin Village") {};

        // Present orange armour
        presentOrange = new TalkToNPCNode(
            "gd_present_orange",
            "General Wartface",
            GOBLIN_VILLAGE_TILE,
            new String[]{"I have some orange armour here."},
            "I have some orange armour here.",
            null
        );

        // Present blue armour
        presentBlue = new TalkToNPCNode(
            "gd_present_blue",
            "General Wartface",
            GOBLIN_VILLAGE_TILE,
            new String[]{"I have some blue armour here."},
            "I have some blue armour here.",
            null
        );

        // Present brown armour
        presentBrown = new TalkToNPCNode(
            "gd_present_brown",
            "General Wartface",
            GOBLIN_VILLAGE_TILE,
            new String[]{"I have some brown armour here."},
            "I have some brown armour here.",
            null
        );

        // Complete
        questCompleteNode = new ActionNode("gd_complete", "Goblin Diplomacy complete") {
            @Override
            protected boolean performAction() {
                setQuestComplete();
                return true;
            }
        };

        // Wire branches
        quest.nodes.DecisionNode d = (quest.nodes.DecisionNode) decision;
        d.addBranch("complete", questCompleteNode);
        d.addBranch("buy", buyItems);
        d.addBranch("dye", dyeMail);
    d.addBranch("walk", walkToVillage);
    d.addBranch("present_orange", presentOrange);
    d.addBranch("present_blue", presentBlue);
    d.addBranch("present_brown", presentBrown);
        d.setDefaultBranch(buyItems);

    // Note: Action nodes return to decision node automatically in this framework
    }

    @Override
    public int getQuestProgress() {
        int v = PlayerSettings.getBitValue(VARBIT_GOBLIN_DIPLOMACY);
        if (v >= 3) return 100;
        if (v == 2) return 66;
        if (v == 1) return 33;
        // Heuristic by inventory if varbit unknown
        boolean haveDyed = Inventory.contains(ORANGE_GOBLIN_MAIL) && Inventory.contains(BLUE_GOBLIN_MAIL) && Inventory.count(GOBLIN_MAIL) >= 1;
        boolean haveBase = Inventory.count(GOBLIN_MAIL) >= 3 && Inventory.contains(ORANGE_DYE) && Inventory.contains(BLUE_DYE);
        if (haveDyed) return 50;
        if (haveBase) return 25;
        return 0;
    }

    @Override
    public boolean isQuestComplete() {
        int v = PlayerSettings.getBitValue(VARBIT_GOBLIN_DIPLOMACY);
        return v >= 3 || super.isQuestComplete();
    }
}
