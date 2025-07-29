package quest.utils;

import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.utilities.Sleep;
import org.dreambot.api.utilities.Logger;
import java.util.*;

/**
 * Universal Dialogue Handler
 * Handles both simple continue dialogues and complex option selection
 */
public class DialogueUtil {
    
    /**
     * Simple dialogue progression - just continues through NPC text
     */
    public static boolean continueDialogue() {
        return continueDialogue(10000); // 10 second timeout
    }
    
    /**
     * Continue dialogue with custom timeout
     */
    public static boolean continueDialogue(int timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (Dialogues.inDialogue() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            if (Dialogues.areOptionsAvailable()) {
                Logger.log("WARNING: Dialogue options detected but continueDialogue() called. Use selectDialogueOption() instead.");
                return false;
            }
            
            if (Dialogues.continueDialogue()) {
                Sleep.sleep(600, 1200);
            } else {
                Sleep.sleep(100, 300);
            }
        }
        
        return !Dialogues.inDialogue();
    }
    
    /**
     * Select specific dialogue option by text content
     */
    public static boolean selectDialogueOption(String optionText) {
        return selectDialogueOption(optionText, 10000);
    }
    
    /**
     * Select dialogue option with timeout
     */
    public static boolean selectDialogueOption(String optionText, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while (Dialogues.inDialogue() && (System.currentTimeMillis() - startTime) < timeoutMs) {
            if (Dialogues.areOptionsAvailable()) {
                String[] options = Dialogues.getOptions();
                
                for (int i = 0; i < options.length; i++) {
                    if (options[i].toLowerCase().contains(optionText.toLowerCase())) {
                        Logger.log("Selecting dialogue option: " + options[i]);
                        if (Dialogues.chooseOption(options[i])) {
                            Sleep.sleep(1000, 2000);
                            return true;
                        }
                    }
                }
                
                Logger.log("WARNING: Could not find dialogue option containing: " + optionText);
                Logger.log("Available options: " + Arrays.toString(options));
                return false;
            } else {
                // Continue through NPC dialogue until we get options
                if (Dialogues.continueDialogue()) {
                    Sleep.sleep(600, 1200);
                } else {
                    Sleep.sleep(100, 300);
                }
            }
        }
        
        return false;
    }
    
    /**
     * Handle complex dialogue sequence with multiple steps
     */
    public static boolean handleDialogueSequence(DialogueStep... steps) {
        for (DialogueStep step : steps) {
            if (!executeDialogueStep(step)) {
                Logger.log("Failed to execute dialogue step: " + step.getDescription());
                return false;
            }
        }
        return true;
    }
    
    /**
     * Execute a single dialogue step
     */
    private static boolean executeDialogueStep(DialogueStep step) {
        switch (step.getType()) {
            case CONTINUE:
                return continueDialogue(step.getTimeout());
            case SELECT_OPTION:
                return selectDialogueOption(step.getOptionText(), step.getTimeout());
            case WAIT_FOR_OPTIONS:
                return waitForDialogueOptions(step.getTimeout());
            default:
                return false;
        }
    }
    
    /**
     * Wait for dialogue options to appear
     */
    public static boolean waitForDialogueOptions(int timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        while ((System.currentTimeMillis() - startTime) < timeoutMs) {
            if (Dialogues.areOptionsAvailable()) {
                return true;
            }
            Sleep.sleep(100, 300);
        }
        
        return false;
    }
    
    /**
     * Check if we're in dialogue with specific NPC
     */
    public static boolean isInDialogueWith(String npcName) {
        if (!Dialogues.inDialogue()) return false;
        
        // This would need to be implemented based on DreamBot's dialogue API
        // For now, return true if we're in any dialogue
        return true;
    }
    
    /**
     * Emergency exit from dialogue
     */
    public static void escapeDialogue() {
        int attempts = 0;
        while (Dialogues.inDialogue() && attempts < 10) {
            // Try pressing escape or clicking away
            // Implementation depends on DreamBot's capabilities
            Sleep.sleep(500, 1000);
            attempts++;
        }
    }
    
    /**
     * Dialogue Step Definition
     */
    public static class DialogueStep {
        public enum StepType {
            CONTINUE,           // Just continue through NPC text
            SELECT_OPTION,      // Select a specific option
            WAIT_FOR_OPTIONS    // Wait for options to appear
        }
        
        private final StepType type;
        private final String optionText;
        private final String description;
        private final int timeout;
        
        public DialogueStep(StepType type, String description) {
            this(type, null, description, 10000);
        }
        
        public DialogueStep(StepType type, String optionText, String description) {
            this(type, optionText, description, 10000);
        }
        
        public DialogueStep(StepType type, String optionText, String description, int timeout) {
            this.type = type;
            this.optionText = optionText;
            this.description = description;
            this.timeout = timeout;
        }
        
        // Getters
        public StepType getType() { return type; }
        public String getOptionText() { return optionText; }
        public String getDescription() { return description; }
        public int getTimeout() { return timeout; }
        
        // Convenience factory methods
        public static DialogueStep continueDialogue(String description) {
            return new DialogueStep(StepType.CONTINUE, description);
        }
        
        public static DialogueStep selectOption(String optionText, String description) {
            return new DialogueStep(StepType.SELECT_OPTION, optionText, description);
        }
        
        public static DialogueStep waitForOptions(String description) {
            return new DialogueStep(StepType.WAIT_FOR_OPTIONS, description);
        }
    }
}
