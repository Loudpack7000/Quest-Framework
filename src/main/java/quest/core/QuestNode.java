package quest.core;

import org.dreambot.api.utilities.Logger;

/**
 * Base class for all quest tree nodes
 * Represents a single step or decision point in a quest
 */
public abstract class QuestNode {
    
    protected final String description;
    protected final String nodeId;
    
    public QuestNode(String nodeId, String description) {
        this.nodeId = nodeId;
        this.description = description;
    }
    
    /**
     * Execute this node's logic
     * @return ExecutionResult indicating success, failure, or in-progress status
     */
    public abstract ExecutionResult execute();
    
    /**
     * Get a human-readable description of this node
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the unique identifier for this node
     */
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * Check if this node should be skipped based on current game state
     * Override in subclasses for conditional logic
     */
    public boolean shouldSkip() {
        return false;
    }
    
    /**
     * Get estimated time this node might take (for progress tracking)
     * Override in subclasses, default is 10 seconds
     */
    public int getEstimatedDurationSeconds() {
        return 10;
    }
    
    /**
     * Log a message with node context
     */
    protected void log(String message) {
        Logger.log("[" + nodeId + "] " + message);
    }
    
    /**
     * Result of executing a quest node
     */
    public static class ExecutionResult {
        
        public enum Status {
            SUCCESS,        // Node completed successfully
            FAILED,         // Node failed and quest should stop
            IN_PROGRESS,    // Node is still executing
            RETRY           // Node should be retried
        }
        
        private final Status status;
        private final QuestNode nextNode;
        private final String failureReason;
        private final String statusMessage;
        
        // Success constructors
        public static ExecutionResult success(QuestNode nextNode) {
            return new ExecutionResult(Status.SUCCESS, nextNode, null, "Node completed successfully");
        }
        
        public static ExecutionResult success(QuestNode nextNode, String message) {
            return new ExecutionResult(Status.SUCCESS, nextNode, null, message);
        }
        
        public static ExecutionResult questComplete() {
            return new ExecutionResult(Status.SUCCESS, null, null, "Quest completed");
        }
        
        // Failure constructors
        public static ExecutionResult failure(String reason) {
            return new ExecutionResult(Status.FAILED, null, reason, "Node failed");
        }
        
        // In-progress constructors
        public static ExecutionResult inProgress() {
            return new ExecutionResult(Status.IN_PROGRESS, null, null, "Node in progress");
        }
        
        public static ExecutionResult inProgress(String message) {
            return new ExecutionResult(Status.IN_PROGRESS, null, null, message);
        }
        
        // Retry constructors
        public static ExecutionResult retry() {
            return new ExecutionResult(Status.RETRY, null, null, "Node needs retry");
        }
        
        public static ExecutionResult retry(String reason) {
            return new ExecutionResult(Status.RETRY, null, null, reason);
        }
        
        private ExecutionResult(Status status, QuestNode nextNode, String failureReason, String statusMessage) {
            this.status = status;
            this.nextNode = nextNode;
            this.failureReason = failureReason;
            this.statusMessage = statusMessage;
        }
        
        // Getters
        public Status getStatus() { return status; }
        public QuestNode getNextNode() { return nextNode; }
        public String getFailureReason() { return failureReason; }
        public String getStatusMessage() { return statusMessage; }
        
        public boolean isSuccess() { return status == Status.SUCCESS; }
        public boolean isFailed() { return status == Status.FAILED; }
        public boolean isInProgress() { return status == Status.IN_PROGRESS; }
        public boolean isRetry() { return status == Status.RETRY; }
    }
}