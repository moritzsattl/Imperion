package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.List;

public class ExpansionMacroAction extends AbstractMacroAction{

    public ExpansionMacroAction(List<EmpireEvent> atomicActions) {
        super(atomicActions);
    }

    @Override
    public String getType() {
        return "Expansion";
    }

    @Override
    public String toString() {
        return "ExpansionMacroAction{" + getAtomicActions() + '}';
    }
}
