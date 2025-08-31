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
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.settings.PlayerSettings;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.methods.dialogues.Dialogues;
import quest.utils.RunEnergyUtil;

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
    private QuestNode startQuest;
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

                // Check if we have all required items (inventory + bank)
                boolean haveAllBase = hasRequiredItems();
                boolean haveDyed = hasDyedItems();
                
                log("Item check - Base items: " + haveAllBase + ", Dyed items: " + haveDyed);

                // Ensure quest is properly started by talking to generals first
                boolean atVillage = Players.getLocal() != null && Players.getLocal().getTile().distance(GOBLIN_VILLAGE_TILE) <= 8;
                
                if (progress <= 0) {
                    if (!haveAllBase) {
                        log("Quest not started and missing base items - need to buy items first");
                        return "buy";
                    }
                    return atVillage ? "start" : "walk";
                }

                // Check if we already have dyed items (inventory only, since we need them for the quest)
                boolean hasDyedInInventory = Inventory.contains(ORANGE_GOBLIN_MAIL) && Inventory.contains(BLUE_GOBLIN_MAIL);
                
                if (!haveAllBase && !haveDyed) {
                    log("Missing base items - need to buy from GE");
                    return "buy";
                }
                
                // If we have base items but no dyed items in inventory, we need to dye them
                if (haveAllBase && !hasDyedInInventory) {
                    log("Have base items but need to dye them for quest");
                    return "dye";
                }
                
                // If we already have dyed items in inventory, we can skip dyeing
                if (hasDyedInInventory) {
                    log("Already have dyed goblin mails in inventory - skipping dyeing step");
                    haveDyed = true;
                }
                
                // We have dyed mails and one plain (brown)
                // Decide whether to walk to village or present specific color
                if (!atVillage) {
                    log("Have dyed items but not at village - need to walk");
                    return "walk";
                }
                
                // Present items in correct order
                if (Inventory.contains(ORANGE_GOBLIN_MAIL)) {
                    log("Ready to present orange goblin mail");
                    return "present_orange";
                }
                if (Inventory.contains(BLUE_GOBLIN_MAIL)) {
                    log("Ready to present blue goblin mail");
                    return "present_blue";
                }
                if (Inventory.count(GOBLIN_MAIL) >= 1) {
                    log("Ready to present brown goblin mail");
                    return "present_brown";
                }
                
                log("All items presented - quest should be complete");
                return "complete";
            }
            
            // Helper method to check if we have all required base items (inventory + bank)
            private boolean hasRequiredItems() {
                int goblinMailCount = Inventory.count(GOBLIN_MAIL) + getBankCount(GOBLIN_MAIL);
                boolean hasOrangeDye = Inventory.contains(ORANGE_DYE) || Bank.contains(ORANGE_DYE);
                boolean hasBlueDye = Inventory.contains(BLUE_DYE) || Bank.contains(BLUE_DYE);
                
                log("Item check - Goblin mail: " + goblinMailCount + "/3, Orange dye: " + hasOrangeDye + ", Blue dye: " + hasBlueDye);
                return goblinMailCount >= 3 && hasOrangeDye && hasBlueDye;
            }
            
            // Helper method to check if we have dyed items
            private boolean hasDyedItems() {
                boolean hasOrangeMail = Inventory.contains(ORANGE_GOBLIN_MAIL) || Bank.contains(ORANGE_GOBLIN_MAIL);
                boolean hasBlueMail = Inventory.contains(BLUE_GOBLIN_MAIL) || Bank.contains(BLUE_GOBLIN_MAIL);
                int plainMailCount = Inventory.count(GOBLIN_MAIL) + getBankCount(GOBLIN_MAIL);
                
                log("Dyed item check - Orange mail: " + hasOrangeMail + ", Blue mail: " + hasBlueMail + ", Plain mail: " + plainMailCount);
                return hasOrangeMail && hasBlueMail && plainMailCount >= 1;
            }
            
            // Helper method to get bank count of an item
            private int getBankCount(String itemName) {
                if (!Bank.isOpen()) {
                    return 0;
                }
                return Bank.count(itemName);
            }
        };

        // Buy items from GE
        buyItems = new ActionNode("gd_buy", "Buy dyes and goblin mail from GE") {
            @Override
            protected boolean performAction() {
                log("Checking what items we need to buy...");
                
                // Check and manage run energy before GE operations
                int currentEnergy = (int) org.dreambot.api.methods.walking.impl.Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    log("Low on run energy (" + currentEnergy + "%) - checking for energy potions...");
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        log("No energy potions found - attempting to restock from bank...");
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    // Drink a potion if we have one
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Check what we already have
                int goblinMailNeeded = Math.max(0, 3 - Inventory.count(GOBLIN_MAIL) - getBankCount(GOBLIN_MAIL));
                boolean needOrangeDye = !Inventory.contains(ORANGE_DYE) && !Bank.contains(ORANGE_DYE);
                boolean needBlueDye = !Inventory.contains(BLUE_DYE) && !Bank.contains(BLUE_DYE);
                
                log("Items needed - Goblin mail: " + goblinMailNeeded + ", Orange dye: " + needOrangeDye + ", Blue dye: " + needBlueDye);
                
                // If we have everything, we're done
                if (goblinMailNeeded == 0 && !needOrangeDye && !needBlueDye) {
                    log("Already have all required items!");
                    return true;
                }
                
                // Build list of items to buy
                java.util.List<ItemRequest> itemsToBuy = new java.util.ArrayList<>();
                
                if (needOrangeDye) {
                    itemsToBuy.add(new ItemRequest(ORANGE_DYE, 1, PriceStrategy.TWENTY_PERCENT));
                }
                if (needBlueDye) {
                    itemsToBuy.add(new ItemRequest(BLUE_DYE, 1, PriceStrategy.TWENTY_PERCENT));
                }
                if (goblinMailNeeded > 0) {
                    itemsToBuy.add(new ItemRequest(GOBLIN_MAIL, goblinMailNeeded, PriceStrategy.TWENTY_PERCENT));
                }
                
                if (itemsToBuy.isEmpty()) {
                    log("No items need to be bought");
                    return true;
                }
                
                log("Buying " + itemsToBuy.size() + " items from GE...");
                ItemRequest[] items = itemsToBuy.toArray(new ItemRequest[0]);
                boolean ok = GrandExchangeUtil.buyItems(items);
                if (!ok) {
                    log("Failed to buy items from GE");
                    return false;
                }
                
                // Wait briefly for inventory update
                Sleep.sleep(800, 1500);
                
                // Verify we now have everything
                boolean hasAllItems = hasRequiredItems();
                log("After purchase - has all items: " + hasAllItems);
                return hasAllItems;
            }
            
            // Helper method to get bank count of an item
            private int getBankCount(String itemName) {
                if (!Bank.isOpen()) {
                    return 0;
                }
                return Bank.count(itemName);
            }
            
            // Helper method to check if we have all required base items (inventory + bank)
            private boolean hasRequiredItems() {
                int goblinMailCount = Inventory.count(GOBLIN_MAIL) + getBankCount(GOBLIN_MAIL);
                boolean hasOrangeDye = Inventory.contains(ORANGE_DYE) || Bank.contains(ORANGE_DYE);
                boolean hasBlueDye = Inventory.contains(BLUE_DYE) || Bank.contains(BLUE_DYE);
                return goblinMailCount >= 3 && hasOrangeDye && hasBlueDye;
            }
        };

        // Start the quest by selecting the correct dialogue with the generals
        startQuest = new ActionNode("gd_start", "Start Goblin Diplomacy with the generals") {
            @Override
            protected boolean performAction() {
                // Check and manage run energy before starting
                int currentEnergy = (int) org.dreambot.api.methods.walking.impl.Walking.getRunEnergy();
                if (currentEnergy < 20) {
                    log("Low on run energy (" + currentEnergy + "%) - checking for energy potions...");
                    if (!RunEnergyUtil.hasEnergyPotions()) {
                        log("No energy potions found - attempting to restock from bank...");
                        RunEnergyUtil.restockEnergyPotions();
                    }
                    // Drink a potion if we have one
                    RunEnergyUtil.manageRunEnergy();
                }
                
                // Ensure we are at the village
                if (Players.getLocal() == null || Players.getLocal().getTile().distance(GOBLIN_VILLAGE_TILE) > 8) {
                    // Manage run energy before walking
                    RunEnergyUtil.manageRunEnergy();
                    new WalkToLocationNode("gd_walk_start", GOBLIN_VILLAGE_TILE, "Goblin Village").execute();
                }

                // Try Wartface, then Bentnoze
                NPC npc = NPCs.closest("General Wartface");
                if (npc == null) npc = NPCs.closest("General Bentnoze");
                if (npc == null) return false;
                if (!npc.interact("Talk-to")) return false;
                if (!Sleep.sleepUntil(Dialogues::inDialogue, 7000)) return false;

                long start = System.currentTimeMillis();
                int guard = 0;
                while (Dialogues.inDialogue() && System.currentTimeMillis() - start < 30000 && guard++ < 80) {
                    if (Dialogues.areOptionsAvailable()) {
                        String[] options = Dialogues.getOptions();
                        int idx = indexOfOption(options,
                                "pick an armour colour",
                                "pick an armor color",
                                "pick an armour color",
                                "Can I help",
                                "I'll help",
                                "What",
                                "armour",
                                "armor");
                        if (idx == -1) idx = 0;
                        Dialogues.chooseOption(idx + 1);
                    } else if (Dialogues.canContinue()) {
                        if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                    }
                    // Early success if varbit moved
                    if (PlayerSettings.getBitValue(VARBIT_GOBLIN_DIPLOMACY) >= 1) break;
                    Sleep.sleep(300, 600);
                }

                // Final small wait for varbit
                Sleep.sleep(400, 800);
                return PlayerSettings.getBitValue(VARBIT_GOBLIN_DIPLOMACY) >= 1;
            }

            private int indexOfOption(String[] options, String... needles) {
                if (options == null) return -1;
                for (int i = 0; i < options.length; i++) {
                    String opt = options[i] == null ? "" : options[i].toLowerCase();
                    for (String n : needles) {
                        if (n != null && opt.contains(n.toLowerCase())) return i;
                    }
                }
                return -1;
            }
        };

        // Dye two goblin mails: one orange and one blue
        dyeMail = new ActionNode("gd_dye", "Dye goblin mail orange and blue") {
            @Override
            protected boolean performAction() {
                log("Starting to dye goblin mail...");
                
                // Check what we already have
                boolean hasOrangeMail = Inventory.contains(ORANGE_GOBLIN_MAIL);
                boolean hasBlueMail = Inventory.contains(BLUE_GOBLIN_MAIL);
                int plainMailCount = Inventory.count(GOBLIN_MAIL);
                boolean hasOrangeDye = Inventory.contains(ORANGE_DYE);
                boolean hasBlueDye = Inventory.contains(BLUE_DYE);
                
                log("Current status - Orange mail: " + hasOrangeMail + ", Blue mail: " + hasBlueMail + 
                    ", Plain mail: " + plainMailCount + ", Orange dye: " + hasOrangeDye + ", Blue dye: " + hasBlueDye);
                
                // If we already have both dyed mails, we're done
                if (hasOrangeMail && hasBlueMail) {
                    log("Already have both dyed goblin mails!");
                    return true;
                }
                
                // Dye orange mail first if we don't have it
                if (!hasOrangeMail && hasOrangeDye && plainMailCount > 0) {
                    log("Dyeing goblin mail orange...");
                    if (Inventory.interact(ORANGE_DYE, "Use")) {
                        Sleep.sleep(200, 400);
                        if (Inventory.interact(GOBLIN_MAIL, "Use")) {
                            Sleep.sleep(600, 900);
                            log("Successfully dyed goblin mail orange");
                        } else {
                            log("Failed to use goblin mail with orange dye");
                        }
                    } else {
                        log("Failed to use orange dye");
                    }
                } else if (!hasOrangeMail) {
                    log("Cannot dye orange mail - missing orange dye or plain goblin mail");
                }
                
                // Dye blue mail second if we don't have it
                if (!hasBlueMail && hasBlueDye && plainMailCount > 0) {
                    log("Dyeing goblin mail blue...");
                    if (Inventory.interact(BLUE_DYE, "Use")) {
                        Sleep.sleep(200, 400);
                        if (Inventory.interact(GOBLIN_MAIL, "Use")) {
                            Sleep.sleep(600, 900);
                            log("Successfully dyed goblin mail blue");
                        } else {
                            log("Failed to use goblin mail with blue dye");
                        }
                    } else {
                        log("Failed to use blue dye");
                    }
                } else if (!hasBlueMail) {
                    log("Cannot dye blue mail - missing blue dye or plain goblin mail");
                }
                
                // Final validation - check if we have both dyed mails and at least one plain remains
                boolean finalOrangeMail = Inventory.contains(ORANGE_GOBLIN_MAIL);
                boolean finalBlueMail = Inventory.contains(BLUE_GOBLIN_MAIL);
                int finalPlainMailCount = Inventory.count(GOBLIN_MAIL);
                
                log("Final result - Orange mail: " + finalOrangeMail + ", Blue mail: " + finalBlueMail + 
                    ", Plain mail remaining: " + finalPlainMailCount);
                
                return finalOrangeMail && finalBlueMail && finalPlainMailCount >= 1;
            }
        };

        // Travel to goblin village center
        walkToVillage = new WalkToLocationNode("gd_walk_village", GOBLIN_VILLAGE_TILE, "Goblin Village") {
            @Override
            protected boolean performAction() {
                // Manage run energy before walking
                RunEnergyUtil.manageRunEnergy();
                return super.performAction();
            }
        };

        // Present orange armour
        presentOrange = new TalkToNPCNode(
            "gd_present_orange",
            "General Wartface",
            GOBLIN_VILLAGE_TILE,
            new String[]{"orange armour"},
            "orange armour",
            null
        );

        // Present blue armour
        presentBlue = new TalkToNPCNode(
            "gd_present_blue",
            "General Wartface",
            GOBLIN_VILLAGE_TILE,
            new String[]{"blue armour"},
            "blue armour",
            null
        );

        // Present brown armour
        presentBrown = new TalkToNPCNode(
            "gd_present_brown",
            "General Wartface",
            GOBLIN_VILLAGE_TILE,
            new String[]{"brown armour"},
            "brown armour",
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
    d.addBranch("start", startQuest);
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
