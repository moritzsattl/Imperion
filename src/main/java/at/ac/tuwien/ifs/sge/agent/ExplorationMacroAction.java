package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class ExplorationMacroAction<A> extends AbstractMacroAction<A>{


    private final int playerId;
    private final List<EmpireUnit> units;
    private final List<Position> destinations;

    public ExplorationMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId ,log, simulation);
        this.playerId = playerId;
        this.units = game.getUnitsByPlayer(playerId);
        this.destinations = null;
    }

    public MoveAction<A> generateExecutableAction() throws NoSuchElementException{
        if (units.isEmpty()) {
            // No move action possible if no units are available.
            throw new NoSuchElementException();
        }

        Random random = new Random();

        // Select a random unit.
        int unitIndex = random.nextInt(units.size());
        EmpireUnit selectedUnit = units.get(unitIndex);

        //log.info("Selected Unit: \n"+ selectedUnit);


        // Get valid and visible locations the unit can move to using the FloodFill Algorithm
        List<Position> validLocations = getKnownPositions(selectedUnit.getPosition())

        // Select a random location.
        int locationIndex = random.nextInt(validLocations.size());
        Position destination = validLocations.get(locationIndex);

        // Return the move action.
        return new MoveAction<A>(gameStateNode, selectedUnit, destination,playerId, log, simulation);
    }


    @Override
    public List<Position> getResponsibleActions(){
        MoveAction<A> executable = generateExecutableAction();
        if (executable != null) {
            return executable.getResponsibleActions();
        }

        return null;
    }

    @Override
    public void simulate() throws ActionException {
        MoveAction<A> executable = generateExecutableAction();
        if (executable != null) {
            executable.simulate();
        }
    }

    @Override
    public String toString() {
        return "MoveMacroAction{" +
                "playerId=" + playerId +
                ", units=" + units +
                ", destinations=" + destinations +
                '}';
    }
}

