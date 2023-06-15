package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.Command;
import at.ac.tuwien.ifs.sge.agent.ExecutableActionFactoryException;
import at.ac.tuwien.ifs.sge.agent.GameStateNode;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

public class AttackMacroAction<A> extends AbstractMacroAction<A>{

    public AttackMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
    }

    @Override
    public Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit, Deque<Command<A>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<A>> actions = new LinkedList<>();

        HashSet<EmpireUnit> enemyUnits = new HashSet<>();
        // Get all enemy Units
        for (var unit : units) {
            if(unit.getPlayerId() != playerId){
                enemyUnits.add(unit);
            }
        }

        if(enemyUnits.isEmpty()){
            throw new ExecutableActionFactoryException("No enemy units in sight");
        }


        return actions;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit, Deque<Command<A>>> unitCommandQueues) throws ExecutableActionFactoryException {
        return null;
    }
}
