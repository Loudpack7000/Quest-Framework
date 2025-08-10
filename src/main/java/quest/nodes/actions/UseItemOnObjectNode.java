package quest.nodes.actions;

import quest.nodes.ActionNode;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.utilities.Sleep;

public class UseItemOnObjectNode extends ActionNode {
    private final String itemName;
    private final String objectName;

    public UseItemOnObjectNode(String nodeId, String itemName, String objectName) {
        super(nodeId, "Use " + itemName + " on " + objectName);
        this.itemName = itemName;
        this.objectName = objectName;
    }

    @Override
    protected boolean performAction() {
        if (!Inventory.contains(itemName)) return false;
        GameObject obj = GameObjects.closest(objectName);
        if (obj == null) return false;

        if (Inventory.interact(itemName, "Use")) {
            Sleep.sleep(150, 300);
            if (obj.interact("Use")) {
                Sleep.sleep(600, 1200);
                return true;
            }
        }
        return false;
    }
}
