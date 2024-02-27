package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.List;

public class ConquerMacroAction extends AbstractMacroAction{
    public ConquerMacroAction(List<EmpireEvent> atomicActions) {
        super(atomicActions);
    }

    @Override
    public String getType() {
        return "Conquer";
    }

    @Override
    public String toString() {
        return "ConquerMacroAction{" + getAtomicActions() + '}';
    }
}
