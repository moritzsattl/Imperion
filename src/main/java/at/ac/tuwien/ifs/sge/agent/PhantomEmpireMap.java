package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.game.Game;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;

import java.util.*;

public class PhantomEmpireMap<G> {

    private List<Position> knownPositions;

    private Empire game;

    public PhantomEmpireMap(List<Position> knownPositions, Game<G, ?> game) {
        this.knownPositions = knownPositions;
        this.game = (Empire) game;
    }

    public PhantomEmpireMap(Position start) {
        knownPositions = getKnownPositions(start);
    }

    public List<Position> getKnownPositions(Position start){
        // Get valid and visible locations the unit can move to using the FloodFill Algorithm
        List<Position> validLocations = new ArrayList<>();

        // Get empire map and empire tiles
        EmpireMap map = game.getBoard();
        EmpireTerrain[][] empireTiles = game.getBoard().getEmpireTiles();

        // Initialize queue for positions to be checked and set to store checked positions
        Queue<Position> positionsToCheck = new LinkedList<>();
        Set<Position> checkedPositions = new HashSet<>();

        // Add starting position to the queue
        positionsToCheck.add(start);

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

        if (validLocations.isEmpty()) {
            // No move action possible if no valid locations are available.
            throw new NoSuchElementException();
        }

        return validLocations;
    }
}
