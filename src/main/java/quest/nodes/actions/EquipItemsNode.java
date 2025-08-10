package quest.nodes.actions;

import quest.nodes.ActionNode;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.equipment.Equipment;
import org.dreambot.api.methods.container.impl.equipment.EquipmentSlot;
import org.dreambot.api.utilities.Sleep;

/**
 * Equip two items (head and body) if not already equipped.
 */
public class EquipItemsNode extends ActionNode {
    private final String head;
    private final String body;

    public EquipItemsNode(String nodeId, String head, String body) {
        super(nodeId, "Equip required items");
        this.head = head;
        this.body = body;
    }

    @Override
    protected boolean performAction() {
        boolean ok = true;

        if (head != null) {
            boolean equipped = Equipment.contains(head);
            if (!equipped && Inventory.contains(head)) {
                ok &= Inventory.interact(head, "Wear");
                Sleep.sleep(300, 600);
            }
        }

        if (body != null) {
            boolean equipped = Equipment.contains(body);
            if (!equipped && Inventory.contains(body)) {
                ok &= Inventory.interact(body, "Wear");
                Sleep.sleep(300, 600);
            }
        }

        boolean headOk = head == null || Equipment.contains(head);
        boolean bodyOk = body == null || Equipment.contains(body);
        return ok && headOk && bodyOk;
    }
}
