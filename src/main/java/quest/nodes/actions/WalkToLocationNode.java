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
            
            // ROBUST WALKING LOOP - like GrandExchangeUtil pattern
            log("Walking to " + locationDescription + "...");
            
            int walkAttempts = 0;
            long startTime = System.currentTimeMillis();
            long timeoutMs = 60000; // 60 seconds total timeout
            
            while (Players.getLocal().getTile().distance(targetLocation) > acceptableDistance) {
                // Check overall timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    log("Failed to reach " + locationDescription + " within 60 seconds");
                    return false;
                }
                
                walkAttempts++;
                currentLocation = Players.getLocal().getTile();
                currentDistance = currentLocation.distance(targetLocation);
                
                log("Walk attempt " + walkAttempts + " - Distance: " + String.format("%.1f", currentDistance) + " tiles");
                
                // Initiate walking
                if (!Walking.walk(targetLocation)) {
                    log("Walking.walk() failed on attempt " + walkAttempts);
                    Sleep.sleep(1000, 1500); // Brief pause before retry
                    continue;
                }
                
                // Wait for movement to start or arrival
                boolean movementStarted = Sleep.sleepUntil(() -> {
                    return Players.getLocal().isMoving() || 
                           Players.getLocal().getTile().distance(targetLocation) <= acceptableDistance;
                }, 5000);
                
                if (!movementStarted) {
                    log("No movement detected after walk command, retrying...");
                    continue;
                }
                
                // Wait for either arrival OR movement to stop (need to re-walk)
                Sleep.sleepUntil(() -> {
                    boolean arrived = Players.getLocal().getTile().distance(targetLocation) <= acceptableDistance;
                    boolean stoppedMoving = !Players.getLocal().isMoving() && Walking.getDestination() == null;
                    
                    if (arrived) {
                        log("Arrived at destination!");
                        return true;
                    }
                    
                    if (stoppedMoving) {
                        log("Stopped moving before reaching destination, will re-walk...");
                        return true;
                    }
                    
                    return false; // Keep waiting
                }, 15000); // 15 second timeout per walk segment
                
                // Check if we arrived
                if (Players.getLocal().getTile().distance(targetLocation) <= acceptableDistance) {
                    log("Successfully reached " + locationDescription + ": " + Players.getLocal().getTile());
                    return true;
                }
                
                // Brief pause before next attempt
                Sleep.sleep(500, 1000);
            }
            
            // Final check
            if (Players.getLocal().getTile().distance(targetLocation) <= acceptableDistance) {
                log("Successfully reached " + locationDescription + ": " + Players.getLocal().getTile());
                return true;
            }
            
            log("Failed to reach " + locationDescription + " after " + walkAttempts + " attempts");
            return false;
            
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