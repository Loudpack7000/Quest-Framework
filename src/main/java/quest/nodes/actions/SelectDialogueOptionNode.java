package quest.nodes.actions;

import quest.nodes.ActionNode;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.utilities.Sleep;

public class SelectDialogueOptionNode extends ActionNode {
    private final String containsText;

    public SelectDialogueOptionNode(String nodeId, String containsText) {
        super(nodeId, "Select dialogue: " + containsText);
        this.containsText = containsText;
    }

    @Override
    protected boolean performAction() {
        boolean opts = Sleep.sleepUntil(Dialogues::areOptionsAvailable, 8000);
        if (!opts) return false;
        String[] options = Dialogues.getOptions();
        if (options == null) return false;
        for (int i = 0; i < options.length; i++) {
            if (options[i] != null && options[i].contains(containsText)) {
                if (Dialogues.chooseOption(i + 1)) {
                    // finish out the dialogue
                    Sleep.sleep(300, 600);
                    while (Dialogues.inDialogue()) {
                        if (Dialogues.canContinue()) {
                            if (!Dialogues.spaceToContinue()) Dialogues.continueDialogue();
                        } else if (Dialogues.areOptionsAvailable()) {
                            break; // let decision loop pick next
                        }
                        Sleep.sleep(250, 450);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
