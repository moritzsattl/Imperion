package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

/**
 * This actions forces the command queue to hold for one turn
 */
public class DoNothingMacroAction extends AbstractMacroAction {
    public DoNothingMacroAction() {
        super();
    }

    @Override
    public String getType() {
        return "DoNothing";
    }

    @Override
    public String toString() {
        return "DoNothingMacroAction";
    }
}
