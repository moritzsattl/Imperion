package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.agent.macroactions.MacroAction;

import java.util.Deque;

public class Command<EmpireEvent> {

    private final MacroAction<EmpireEvent> macroAction;

    private final Deque<EmpireEvent> actions;


    public Command(MacroAction<EmpireEvent> marcoAction, Deque<EmpireEvent> actions) {
        this.macroAction = marcoAction;
        this.actions = actions;
    }

    public MacroAction<EmpireEvent> getMacroAction() {
        return macroAction;
    }

    public Deque<EmpireEvent> getActions() {
        return actions;
    }

    @Override
    public String toString() {
        StringBuilder actionsString = new StringBuilder("{");
        if(actions != null){
            for (var event :
                    actions) {
                actionsString.append(event).append(", ");
            }
        }
        actionsString.append("}");
        
        return "Command{" +
                "macroAction=" + macroAction +
                ", actions=" + actionsString +
                '}';
    }
}
