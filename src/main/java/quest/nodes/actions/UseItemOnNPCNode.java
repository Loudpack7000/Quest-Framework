package quest.nodes.actions;

import quest.nodes.ActionNode;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.utilities.Sleep;

public class UseItemOnNPCNode extends ActionNode {
    private final String itemName;
    private final String npcName;

    public UseItemOnNPCNode(String nodeId, String itemName, String npcName) {
        super(nodeId, "Use " + itemName + " on " + npcName);
        this.itemName = itemName;
        this.npcName = npcName;
    }

    @Override
    protected boolean performAction() {
        if (!Inventory.contains(itemName)) return false;
        NPC npc = NPCs.closest(npcName);
        if (npc == null) return false;

        if (Inventory.interact(itemName, "Use")) {
            Sleep.sleep(150, 300);
            if (npc.interact("Use")) {
                Sleep.sleep(600, 1200);
                return true;
            }
        }
        return false;
    }
}
