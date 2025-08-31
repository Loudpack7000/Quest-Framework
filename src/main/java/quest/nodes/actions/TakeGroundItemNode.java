package quest.nodes.actions;

import quest.nodes.ActionNode;
import quest.core.QuestNode;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.wrappers.items.GroundItem;

/**
 * Action node to take a ground item by name, optionally walking near an expected location first.
 */
public class TakeGroundItemNode extends ActionNode {

    private final String itemName;
    private final Tile expectedLocation;
    private final String locationDescription;

    public TakeGroundItemNode(String nodeId, String itemName) {
        this(nodeId, itemName, null, null);
    }

    public TakeGroundItemNode(String nodeId, String itemName, Tile expectedLocation, String locationDescription) {
        super(nodeId, "Take " + itemName + (locationDescription != null ? (" at " + locationDescription) : ""), null);
        this.itemName = itemName;
        this.expectedLocation = expectedLocation;
        this.locationDescription = locationDescription;
    }

    @Override
    protected boolean performAction() {
        try {
            // If we have an expected location, walk near it first to ensure visibility
            if (expectedLocation != null) {
                log("Walking near expected location for " + itemName + ": " + expectedLocation +
                    (locationDescription != null ? " (" + locationDescription + ")" : ""));

                int attempts = 0;
                while (Players.getLocal().getTile().distance(expectedLocation) > 6 && attempts < 12) {
                    attempts++;
                    if (!Walking.walk(expectedLocation)) {
                        Sleep.sleep(600, 1000);
                        continue;
                    }
                    Sleep.sleepUntil(() -> Players.getLocal().isMoving(), 2000);
                    Sleep.sleepUntil(() -> !Players.getLocal().isMoving(), 7000);
                }
            }

            GroundItem gi = GroundItems.closest(itemName);
            if (gi == null) {
                log("Ground item not found: " + itemName);
                return false;
            }

            // Walk closer if needed
            if (gi.distance() > 5) {
                int tries = 0;
                while (gi.distance() > 5 && tries < 8) {
                    tries++;
                    if (!Walking.walk(gi.getTile())) {
                        Sleep.sleep(400, 700);
                        continue;
                    }
                    Sleep.sleepUntil(() -> Players.getLocal().isMoving(), 2000);
                    Sleep.sleepUntil(() -> !Players.getLocal().isMoving(), 7000);
                }
            }

            int before = org.dreambot.api.methods.container.impl.Inventory.count(itemName);
            if (!gi.interact("Take")) {
                log("Failed to click Take on ground item: " + itemName);
                return false;
            }

            boolean picked = Sleep.sleepUntil(() -> org.dreambot.api.methods.container.impl.Inventory.count(itemName) > before, 6000);
            if (!picked) {
                // Retry once if the take didn’t register (busy pathing or misclick)
                Sleep.sleep(400, 700);
                GroundItem retry = GroundItems.closest(itemName);
                if (retry != null && retry.interact("Take")) {
                    picked = Sleep.sleepUntil(() -> org.dreambot.api.methods.container.impl.Inventory.count(itemName) > before, 5000);
                }
            }
            log("Take ground item result: " + picked);
            return picked;
        } catch (Exception e) {
            log("Exception in TakeGroundItemNode: " + e.getMessage());
            return false;
        }
    }
}
