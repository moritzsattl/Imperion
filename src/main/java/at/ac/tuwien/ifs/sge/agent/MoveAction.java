package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.agent.Agent;
import at.ac.tuwien.ifs.sge.core.agent.GameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.SgeEvent;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.MovementAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class MoveAction<A> extends AbstractMacroAction<A>{

    private final EmpireUnit unit;
    private final Position destination;


    private List<Position> path;

    public EmpireUnit getUnit() {
        return unit;
    }

    public Position getDestination() {
        return destination;
    }

    public MoveAction(GameStateNode<A> gameStateNode, EmpireUnit unit, Position destination, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
        this.unit = unit;
        this.destination = destination;
        this.log = log;
    }


    @Override
    public List<Position> getResponsibleActions() {
        if(path == null){
            AStar aStar = new AStar(unit.getPosition(),destination,gameStateNode,playerId, log);
            AStarNode currentNode = aStar.findPath(simulation);
            if(!simulation){
                log.info("Found path " + currentNode);
            }


            if(currentNode == null) return null;


            path = new ArrayList<>();

            while (currentNode != null) {
                path.add(currentNode.getPosition());

                // Next Position to move to
                currentNode = currentNode.getPrev();
            }
        }

        Collections.reverse(path);
        return path;

    }

    @Override
    public void simulate() throws ActionException {
        //TODO: Add implementation
    }

    //TODO: Add further methods...


    @Override
    public String toString() {
        return "MoveAction{" +
                "unit=" + unit +
                ", destination=" + destination +
                '}';
    }
}
