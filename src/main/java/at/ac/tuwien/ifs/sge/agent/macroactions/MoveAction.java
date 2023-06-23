package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.Command;
import at.ac.tuwien.ifs.sge.agent.ExecutableActionFactoryException;
import at.ac.tuwien.ifs.sge.agent.GameStateNode;
import at.ac.tuwien.ifs.sge.agent.astar.AStar;
import at.ac.tuwien.ifs.sge.agent.astar.AStarNode;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class MoveAction<EmpireEvent> extends AbstractMacroAction<EmpireEvent>{

    private final EmpireUnit unit;
    private final Position destination;
    private final MacroActionType type;

    private final boolean force;


    private Deque<EmpireEvent> path;

    public EmpireUnit getUnit() {
        return unit;
    }

    public Position getDestination() {
        return destination;
    }

    public MoveAction(GameStateNode<EmpireEvent> gameStateNode, EmpireUnit unit, MacroActionType type, Position destination, int playerId, Logger log, boolean simulation, boolean force) {
        super(gameStateNode, playerId, log, simulation);
        this.unit = unit;
        this.type = type;
        this.destination = destination;
        this.log = log;
        this.force = force;
    }


    @Override
    public Deque<MacroAction<EmpireEvent>> generateExecutableAction(Map<UUID, Deque<Command<EmpireEvent>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        return null;
    }

    public MacroActionType getType() {
        return type;
    }

    public boolean isForce() {
        return force;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<UUID,Deque<Command<EmpireEvent>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        if(path == null){
            log.info("A Star starting");
            AStar aStar = new AStar(unit.getPosition(),destination,gameStateNode,playerId, log);
            log.info("A Star finishing");
            AStarNode currentNode = aStar.findPath(simulation);
            if(currentNode == null) return null;

            path = new ArrayDeque<>();

            while (currentNode != null) {
                path.addFirst((EmpireEvent) new MovementStartOrder(unit.getId(),currentNode.getPosition()));

                // Next Position to move to
                currentNode = currentNode.getPrev();
            }


        }
        path.poll();

        if(path.isEmpty()){
            throw new ExecutableActionFactoryException("Path to " + destination + " was not found by unit " + unit);
        }

        //log.info(path);

        return path;

    }


    //TODO: Add further methods...


    @Override
    public String toString() {
        return "MoveAction{" +
                "unit=" + unit +
                ", destination=" + destination +
                ", type=" + type +
                '}';
    }
}
