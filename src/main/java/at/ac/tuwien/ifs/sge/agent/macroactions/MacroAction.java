package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.Command;
import at.ac.tuwien.ifs.sge.agent.ExecutableActionFactoryException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.Deque;
import java.util.Map;

public interface MacroAction<A>{

    Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit,Deque<Command<A>>> unitsCommandQueues) throws ExecutableActionFactoryException;

    Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues) throws ExecutableActionFactoryException;

}
