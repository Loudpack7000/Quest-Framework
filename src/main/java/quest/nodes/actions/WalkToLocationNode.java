package quest.nodes.actions;

import quest.nodes.ActionNode;
import quest.core.QuestNode;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.utilities.Sleep;

/**
 * Action node for walking to specific locations
 * Handles pathfinding and arrival confirmation
 */
public class WalkToLocationNode extends ActionNode {
    
    private final Tile targetLocation;
    private final int acceptableDistance;
    private final String locationDescription;
    
    public WalkToLocationNode(String nodeId, Tile targetLocation) {
        this(nodeId, targetLocation, 3, targetLocation.toString());
    }
    
    public WalkToLocationNode(String nodeId, Tile targetLocation, String locationDescription) {
        this(nodeId, targetLocation, 3, locationDescription);
    }
    
    public WalkToLocationNode(String nodeId, Tile targetLocation, int acceptableDistance, String locationDescription) {
        this(nodeId, targetLocation, acceptableDistance, locationDescription, null);
    }
    
    public WalkToLocationNode(String nodeId, Tile targetLocation, int acceptableDistance, 
                             String locationDescription, QuestNode nextNode) {
        super(nodeId, "Walk to " + locationDescription, nextNode);
        this.targetLocation = targetLocation;
        this.acceptableDistance = acceptableDistance;
        this.locationDescription = locationDescription;
    }
    
    @Override
    protected boolean performAction() {
        try {
            Tile currentLocation = Players.getLocal().getTile();
            double currentDistance = currentLocation.distance(targetLocation);
            
            log("Current location: " + currentLocation);
            log("Target location: " + targetLocation + " (" + locationDescription + ")");
            log("Distance: " + String.format("%.1f", currentDistance) + " tiles");
            
            // Check if we're already close enough
            if (currentDistance <= acceptableDistance) {
                log("Already at target location (within " + acceptableDistance + " tiles)");
                return true;
            }
            
            // Initiate walking with no-timeout logic
            log("Walking to " + locationDescription + "...");
            int failedAttempts = 0;
            while (Players.getLocal().getTile().distance(targetLocation) > acceptableDistance) {
                double distanceToTarget = Players.getLocal().getTile().distance(targetLocation);
                log("Current distance to target: " + String.format("%.1f", distanceToTarget) + " tiles");
                
                if (!Walking.walk(targetLocation)) {
                    failedAttempts++;
                    log("Walking.walk() failed (attempt " + failedAttempts + ")");
                    if (failedAttempts > 15) { // Max consecutive failures before giving up
                        log("Failed to walk to target location after 15 failed attempts - pathfinder may be stuck");
                        return false;
                    }
                } else {
                    failedAttempts = 0; // Reset on successful walk initiation
                    log("Walk initiated successfully, waiting for movement...");
                }
                Sleep.sleep(1500, 2500); // Wait a bit before trying again
            }
            log("Successfully reached " + locationDescription + ": " + Players.getLocal().getTile());
            return true;
            
        } catch (Exception e) {
            log("Exception in WalkToLocationNode: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    

    
    @Override
    public int getEstimatedDurationSeconds() {
        if (Players.getLocal() != null) {
            double distance = Players.getLocal().getTile().distance(targetLocation);
            // Rough estimate: 1 second per 3 tiles
            return Math.max(5, (int)(distance / 3));
        }
        return 30; // Default estimate
    }
}