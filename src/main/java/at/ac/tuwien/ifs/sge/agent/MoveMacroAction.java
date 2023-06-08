package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class MoveMacroAction<A> extends AbstractMacroAction<A>{


    private final int playerId;
    private final List<EmpireUnit> units;
    private final List<Position> destinations;

    public MoveMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log) {
        super(gameStateNode, playerId ,log);
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
        List<Position> validLocations = new ArrayList<>();

        // Get empire map and empire tiles
        EmpireMap map = game.getBoard();
        EmpireTerrain[][] empireTiles = game.getBoard().getEmpireTiles();

        // Initialize queue for positions to be checked and set to store checked positions
        Queue<Position> positionsToCheck = new LinkedList<>();
        Set<Position> checkedPositions = new HashSet<>();

        // Add starting position to the queue
        positionsToCheck.add(selectedUnit.getPosition());

        while (!positionsToCheck.isEmpty()) {
            Position current = positionsToCheck.poll();
            int x = current.getX();
            int y = current.getY();

            // If the current position has already been checked, skip it
            if (checkedPositions.contains(current) || !map.isInside(x,y) || empireTiles[y][x] == null) {
                continue;
            }

            // Mark the current position as checked
            checkedPositions.add(current);

            try {
                // If the tile is inside, not null and movement is possible, add it to valid locations
                var tile = map.getTile(x,y);
                if (tile != null && tile.getOccupants() != null && map.isMovementPossible(x, y, playerId) && tile.getPosition() != selectedUnit.getPosition()) {
                    validLocations.add(new Position(x, y));
                }

                // Check the tiles to the left, right, above, and below the current tile
                if (x > 0) positionsToCheck.add(new Position(x - 1, y));
                if (x < empireTiles.length - 1) positionsToCheck.add(new Position(x + 1, y));
                if (y > 0) positionsToCheck.add(new Position(x, y - 1));
                if (y < empireTiles[0].length - 1) positionsToCheck.add(new Position(x, y + 1));
            } catch (Exception e) {
                log.info(e);
            }
        }

        //log.info("Valid Locations to go: \n"+ validLocations);

        if (validLocations.isEmpty()) {
            // No move action possible if no valid locations are available.
            throw new NoSuchElementException();
        }

        // Select a random location.
        int locationIndex = random.nextInt(validLocations.size());
        Position destination = validLocations.get(locationIndex);

        // Return the move action.
        return new MoveAction<A>(gameStateNode, selectedUnit, destination,playerId, log);
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

