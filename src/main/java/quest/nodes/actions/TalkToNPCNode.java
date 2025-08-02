package quest.nodes.actions;

import quest.nodes.ActionNode;
import quest.core.QuestNode;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.wrappers.interactive.NPC;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.methods.interactive.Players;

/**
 * Action node for talking to NPCs
 * Handles walking to NPC location and initiating dialogue
 */
public class TalkToNPCNode extends ActionNode {
    
    private final String npcName;
    private final Tile npcLocation;
    private final String[] expectedDialogueOptions;
    private final String selectedOption;
    
    public TalkToNPCNode(String nodeId, String npcName, Tile npcLocation) {
        this(nodeId, npcName, npcLocation, null, null, null);
    }
    
    public TalkToNPCNode(String nodeId, String npcName, Tile npcLocation, QuestNode nextNode) {
        this(nodeId, npcName, npcLocation, null, null, nextNode);
    }
    
    public TalkToNPCNode(String nodeId, String npcName, Tile npcLocation, 
                        String[] expectedDialogueOptions, String selectedOption, QuestNode nextNode) {
        super(nodeId, "Talk to " + npcName + " at " + npcLocation, nextNode);
        this.npcName = npcName;
        this.npcLocation = npcLocation;
        this.expectedDialogueOptions = expectedDialogueOptions;
        this.selectedOption = selectedOption;
    }
    
    @Override
    protected boolean performAction() {
        try {
            log("Walking to expected location: " + npcLocation);
            if (!Walking.walk(npcLocation)) {
                log("Failed to initiate walk to NPC location");
                return false;
            }
            
            // Wait for arrival - check if we're within reasonable distance
            boolean arrived = Sleep.sleepUntil(() -> {
                double distance = Players.getLocal().getTile().distance(npcLocation);
                return distance < 8; // More lenient distance check
            }, 15000); // Longer timeout
            
            if (!arrived) {
                log("Failed to arrive at NPC location within timeout");
                return false;
            }
            
            log("Successfully walked to NPC area");
            // Now look for the NPC
            NPC targetNPC = NPCs.closest(npcName);
            if (targetNPC == null) {
                log("Still cannot find NPC after walking to location");
                return false;
            }
            log("Found NPC: " + npcName + " at " + targetNPC.getTile());
            // Walk closer if needed
            if (targetNPC.distance() > 5) {
                log("NPC is " + String.format("%.1f", targetNPC.distance()) + " tiles away, walking closer");
                final NPC finalNPC = targetNPC;
                if (!Walking.walk(targetNPC.getTile())) {
                    log("Failed to initiate walk to NPC");
                    return false;
                }
                boolean close = Sleep.sleepUntil(() -> finalNPC.distance() < 6, 8000);
                if (!close) {
                    log("Failed to walk close to NPC, current distance: " + String.format("%.1f", finalNPC.distance()));
                    return false;
                }
                log("Successfully walked close to NPC");
            }
            // Interact with the NPC
            log("Attempting to talk to " + npcName);
            if (!targetNPC.interact("Talk-to")) {
                log("Failed to interact with NPC");
                return false;
            }
            // Wait for dialogue to open
            boolean dialogueOpened = Sleep.sleepUntil(() -> Dialogues.inDialogue(), 7000);
            if (!dialogueOpened) {
                log("Dialogue did not open after talking to NPC");
                return false;
            }
            log("Successfully initiated dialogue with " + npcName);
            // Handle dialogue options if specified
            if (expectedDialogueOptions != null && selectedOption != null) {
                return handleDialogueOptions();
            }
            // If no specific dialogue handling, just continue through dialogue
            return continueDialogue();
        } catch (Exception e) {
            log("Exception in TalkToNPCNode: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Handle specific dialogue options
     */
    private boolean handleDialogueOptions() {
        try {
            log("Waiting for dialogue options...");
            
            // Wait for options to be available
            boolean optionsAvailable = Sleep.sleepUntil(() -> 
                Dialogues.areOptionsAvailable(), 10000);
            
            if (!optionsAvailable) {
                log("No dialogue options became available");
                return continueDialogue(); // Fall back to continuing dialogue
            }
            
            String[] options = Dialogues.getOptions();
            if (options == null || options.length == 0) {
                log("No dialogue options found");
                return continueDialogue();
            }
            
            log("Available dialogue options: " + java.util.Arrays.toString(options));
            
            // Find the option that contains our selected text
            for (int i = 0; i < options.length; i++) {
                if (options[i].contains(selectedOption)) {
                    log("Selecting dialogue option " + (i + 1) + ": " + options[i]);
                    
                    if (Dialogues.chooseOption(i + 1)) {
                        log("Successfully selected dialogue option");
                        return waitForDialogueComplete();
                    } else {
                        log("Failed to select dialogue option");
                        return false;
                    }
                }
            }
            
            log("Could not find matching dialogue option for: " + selectedOption);
            return continueDialogue(); // Fall back to continuing dialogue
            
        } catch (Exception e) {
            log("Exception handling dialogue options: " + e.getMessage());
            return continueDialogue();
        }
    }
    
    /**
     * Continue through dialogue by clicking continue
     */
    private boolean continueDialogue() {
        try {
            log("Continuing through dialogue...");
            
            while (Dialogues.inDialogue()) {
                if (Dialogues.canContinue()) {
                    Dialogues.continueDialogue();
                    Sleep.sleep(1000, 2000);
                } else if (Dialogues.areOptionsAvailable()) {
                    // If options are available but we don't have specific handling,
                    // just select the first option
                    log("Selecting first available dialogue option");
                    Dialogues.chooseOption(1);
                    Sleep.sleep(1000, 2000);
                } else {
                    log("Dialogue state unclear, waiting...");
                    Sleep.sleep(1000);
                }
            }
            
            log("Dialogue completed");
            return true;
            
        } catch (Exception e) {
            log("Exception continuing dialogue: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Wait for dialogue to complete after selecting an option
     */
    private boolean waitForDialogueComplete() {
        try {
            // Continue through any remaining dialogue
            return continueDialogue();
        } catch (Exception e) {
            log("Exception waiting for dialogue completion: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean shouldSkip() {
        // Could add logic here to skip if already talked to this NPC
        // For now, always execute
        return false;
    }
}