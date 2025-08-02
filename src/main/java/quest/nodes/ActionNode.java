package quest.nodes;

import quest.core.QuestNode;

/**
 * Base class for action nodes - nodes that perform specific actions
 * Examples: Talk to NPC, Walk to location, Use item, etc.
 */
public abstract class ActionNode extends QuestNode {
    
    protected QuestNode nextNode;
    protected int maxRetries;
    protected int currentRetries;
    
    public ActionNode(String nodeId, String description) {
        this(nodeId, description, null, 3);
    }
    
    public ActionNode(String nodeId, String description, QuestNode nextNode) {
        this(nodeId, description, nextNode, 3);
    }
    
    public ActionNode(String nodeId, String description, QuestNode nextNode, int maxRetries) {
        super(nodeId, description);
        this.nextNode = nextNode;
        this.maxRetries = maxRetries;
        this.currentRetries = 0;
    }
    
    @Override
    public ExecutionResult execute() {
        try {
            log("Executing action: " + description);
            
            // Check if we should skip this action
            if (shouldSkip()) {
                log("Skipping action (conditions not met)");
                return ExecutionResult.success(null, "Action skipped");
            }
            
            // Perform the actual action
            boolean actionSuccess = performAction();
            
            if (actionSuccess) {
                log("Action completed successfully");
                currentRetries = 0; // Reset retry counter on success
                return ExecutionResult.success(null); // Return null to go back to smart decision node
            } else {
                currentRetries++;
                log("Action failed (attempt " + currentRetries + "/" + maxRetries + ")");
                
                if (currentRetries >= maxRetries) {
                    return ExecutionResult.failure("Action failed after " + maxRetries + " attempts");
                } else {
                    return ExecutionResult.retry("Action failed, retrying...");
                }
            }
            
        } catch (Exception e) {
            log("Exception in action execution: " + e.getMessage());
            e.printStackTrace();
            return ExecutionResult.failure("Exception during action: " + e.getMessage());
        }
    }
    
    /**
     * Perform the actual action - must be implemented by subclasses
     * @return true if action was successful, false otherwise
     */
    protected abstract boolean performAction();
    
    /**
     * Set the next node to execute after this action completes
     */
    public void setNextNode(QuestNode nextNode) {
        this.nextNode = nextNode;
    }
    
    /**
     * Get the next node
     */
    public QuestNode getNextNode() {
        return nextNode;
    }
    
    /**
     * Reset retry counter (useful for debugging)
     */
    public void resetRetries() {
        this.currentRetries = 0;
    }
}