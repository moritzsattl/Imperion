package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.Deque;
import java.util.Queue;

public class Command<A> {


    private MacroAction<A> macroAction;

    private Deque<EmpireEvent> actions;


    public Command(MacroAction<A> marcoAction, Deque<EmpireEvent> actions) {
        this.macroAction = marcoAction;
        this.actions = actions;
    }

    public Command() {
    }

    public MacroAction<A> getMacroAction() {
        return macroAction;
    }

    public Deque<EmpireEvent> getActions() {
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
