package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.List;

public class ExplorationMacroAction extends AbstractMacroAction {

    public ExplorationMacroAction(List<EmpireEvent> atomicActions) {
        super(atomicActions);
    }

    @Override
    public String getType() {
        return "Exploration";
    }

    @Override
    public String toString() {
        return "ExplorationMacroAction{" + getAtomicActions() + '}';
    }
}
