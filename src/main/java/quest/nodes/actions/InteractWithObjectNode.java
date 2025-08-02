package quest.nodes.actions;

import quest.nodes.ActionNode;
import quest.core.QuestNode;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.utilities.Sleep;

/**
 * Action node for interacting with game objects
 * Examples: Open door, Climb stairs, Pick from bush, etc.
 */
public class InteractWithObjectNode extends ActionNode {
    
    private final String objectName;
    private final String action;
    private final Tile objectLocation;
    private final String locationDescription;
    
    public InteractWithObjectNode(String nodeId, String objectName, String action, Tile objectLocation) {
        this(nodeId, objectName, action, objectLocation, objectLocation.toString(), null);
    }
    
    public InteractWithObjectNode(String nodeId, String objectName, String action, 
                                 Tile objectLocation, String locationDescription) {
        this(nodeId, objectName, action, objectLocation, locationDescription, null);
    }
    
    public InteractWithObjectNode(String nodeId, String objectName, String action, 
                                 Tile objectLocation, String locationDescription, QuestNode nextNode) {
        super(nodeId, action + " " + objectName + " at " + locationDescription, nextNode);
        this.objectName = objectName;
        this.action = action;
        this.objectLocation = objectLocation;
        this.locationDescription = locationDescription;
    }
    
    @Override
    protected boolean performAction() {
        try {
            log("Looking for object: " + objectName + " near " + objectLocation);
            
            // Try to find the object
            GameObject targetObject = GameObjects.closest(objectName);
            
            if (targetObject == null) {
                log("Object not found, walking to expected location: " + objectLocation);
                Walking.walk(objectLocation);
                boolean arrived = Sleep.sleepUntil(() -> Players.getLocal().getTile().distance(objectLocation) < 5, 10000);
                if (!arrived) {
                    log("Failed to walk to object location");
                    return false;
                }
                // Wait for object to appear
                boolean found = Sleep.sleepUntil(() -> GameObjects.closest(objectName) != null, 8000);
                if (!found) {
                    log("Still cannot find object after walking to location");
                    return false;
                }
                targetObject = GameObjects.closest(objectName);
            }
            
            log("Found object: " + objectName + " at " + targetObject.getTile());
            
            // Walk closer if needed
            if (targetObject.distance() > 5) {
                final GameObject finalObject = targetObject;
                Walking.walk(targetObject.getTile());
                boolean close = Sleep.sleepUntil(() -> finalObject.distance() < 5, 5000);
                if (!close) {
                    log("Failed to walk close to object");
                    return false;
                }
            }
            
            // Store current position to detect movement/changes
            Tile beforeInteraction = Players.getLocal().getTile();
            int beforeZ = beforeInteraction.getZ();
            int beforeInventory = org.dreambot.api.methods.container.impl.Inventory.all().size();
            
            // Interact with the object
            log("Attempting to " + action + " " + objectName);
            if (!targetObject.interact(action)) {
                log("Failed to interact with object");
                return false;
            }
            
            // Wait for the interaction to complete
            boolean interactionSuccess = false;
            switch (action.toLowerCase()) {
                case "climb-up":
                    interactionSuccess = Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() > beforeZ, 7000);
                    break;
                case "climb-down":
                    interactionSuccess = Sleep.sleepUntil(() -> Players.getLocal().getTile().getZ() < beforeZ, 7000);
                    break;
                case "open":
                    interactionSuccess = Sleep.sleepUntil(() -> !Players.getLocal().getTile().equals(beforeInteraction), 5000);
                    break;
                case "pick-from":
                    interactionSuccess = Sleep.sleepUntil(() -> org.dreambot.api.methods.container.impl.Inventory.all().size() != beforeInventory, 5000);
                    break;
                default:
                    interactionSuccess = Sleep.sleepUntil(() -> !Players.getLocal().getTile().equals(beforeInteraction) || Players.getLocal().getTile().getZ() != beforeZ, 5000);
                    break;
            }
            if (interactionSuccess) {
                log("Successfully completed " + action + " " + objectName);
                return true;
            } else {
                log("Interaction may have failed or timed out");
                return false;
            }
            
        } catch (Exception e) {
            log("Exception in InteractWithObjectNode: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Wait for interaction to complete based on the type of action
     */
    private boolean waitForInteractionComplete(Tile beforeInteraction, int beforeZ) {
        try {
            // Different actions have different success conditions
            switch (action.toLowerCase()) {
                case "climb-up":
                    // Wait for Z level to increase
                    return Sleep.sleepUntil(() -> 
                        Players.getLocal().getTile().getZ() > beforeZ, 5000);
                    
                case "climb-down":
                    // Wait for Z level to decrease
                    return Sleep.sleepUntil(() -> 
                        Players.getLocal().getTile().getZ() < beforeZ, 5000);
                    
                case "open":
                    // Wait for position to change (moving through door) or small delay
                    Sleep.sleep(1000, 2000);
                    return true;
                    
                case "pick-from":
                    // Wait for inventory to change or small delay
                    Sleep.sleep(2000, 3000);
                    return true;
                    
                default:
                    // Generic wait - look for position change or timeout
                    boolean positionChanged = Sleep.sleepUntil(() -> {
                        Tile current = Players.getLocal().getTile();
                        return !current.equals(beforeInteraction) || 
                               current.getZ() != beforeZ;
                    }, 5000);
                    
                    if (!positionChanged) {
                        // If position didn't change, just wait a bit and assume success
                        Sleep.sleep(1000, 2000);
                    }
                    
                    return true;
            }
            
        } catch (Exception e) {
            log("Exception waiting for interaction complete: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean shouldSkip() {
        // Could add logic here to skip if object interaction already completed
        // For now, always execute
        return false;
    }
    
    @Override
    public int getEstimatedDurationSeconds() {
        // Object interactions are generally quick
        return 5;
    }
}