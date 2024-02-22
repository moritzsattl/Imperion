package at.ac.tuwien.ifs.sge.agent.util.MacroAction;


/**
 * In contrast to DoNothingMacroAction: The ScheduleNothingMacroAction does not schedule any actions, but the command queue will just work as usual
 */
public class ScheduleNothingMacroAction extends AbstractMacroAction {
    public ScheduleNothingMacroAction() {
        super();
    }

    @Override
    public String getType() {
        return "ScheduleNothing";
    }

    @Override
    public String toString() {
        return "ScheduleNothingMacroAction";
    }
}
