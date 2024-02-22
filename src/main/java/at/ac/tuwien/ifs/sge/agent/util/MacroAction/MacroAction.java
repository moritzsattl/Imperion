package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

import java.util.List;

/**
 * Defines macroAction interface
 */
public interface MacroAction {

    String getType();

    List<EmpireEvent> getAtomicActions();
}
