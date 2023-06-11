package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.Queue;

public class Command<A> {
    private MacroAction<A> macroAction;

    private Queue<EmpireEvent> actions;

    public Command(MacroAction<A> marcoAction, Queue<EmpireEvent> actions) {
        this.macroAction = marcoAction;
        this.actions = actions;
    }

    public MacroAction<A> getMacroAction() {
        return macroAction;
    }

    public Queue<EmpireEvent> getActions() {
        return actions;
    }

    @Override
    public String toString() {
        String actionsString = "{";
        if(actions != null){
            for (var event :
                    actions) {
                actionsString += event +", ";
            }
        }


        actionsString += "}";
        
        return "Command{" +
                "macroAction=" + macroAction +
                ", actions=" + actionsString +
                '}';
    }
}
