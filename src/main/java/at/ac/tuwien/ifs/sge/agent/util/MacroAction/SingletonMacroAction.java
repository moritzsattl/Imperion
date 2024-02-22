package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.Collections;

/**
 * Macro Action containing a single action
 */
public class SingletonMacroAction extends AbstractMacroAction {

    public SingletonMacroAction(EmpireEvent event) {
        super(Collections.singletonList(event));
    }

    @Override
    public String getType() {
        return "Singleton";
    }

    public String toString() {
        return getAtomicActions().get(0).toString();
    }
}
