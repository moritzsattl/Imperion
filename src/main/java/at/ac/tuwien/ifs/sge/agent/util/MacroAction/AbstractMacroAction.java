package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.List;

public abstract class AbstractMacroAction implements MacroAction {
    private List<EmpireEvent> atomicActions;

    public AbstractMacroAction() {
    }

    public AbstractMacroAction(List<EmpireEvent> atomicActions) {
        this.atomicActions = atomicActions;
    }

    public List<EmpireEvent> getAtomicActions() {
        return atomicActions;
    }

}
