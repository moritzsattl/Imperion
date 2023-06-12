package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class ExplorationMacroAction<A> extends AbstractMacroAction<A>{

    private final List<EmpireUnit> units;
    private final List<Position> destinations;

    public ExplorationMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId ,log, simulation);
        this.units = game.getUnitsByPlayer(playerId);
        this.destinations = null;
    }

    public Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<A>> actions = new LinkedList<>();

        // Get units which are not busy
        ArrayList<EmpireUnit> notBusyUnits = new ArrayList<>();

        for (var unit: units) {
            if(unitCommandQueues.get(unit) == null || unitCommandQueues.get(unit).isEmpty()){
                notBusyUnits.add(unit);
            }
        }

        log.info("Units which are not busy: " + notBusyUnits);

        if (units.isEmpty()) {
            // No move action possible if no units are available.
            throw new ExecutableActionFactoryException("No units are available");
        }

        // Select scout unit
        EmpireUnit selectedUnit = null;

        List<EmpireUnit> scouts = new ArrayList<>();
        for (var unit : notBusyUnits) {
            // Select Scout
            if (unit.getUnitTypeName().equals("Scout")) {
                scouts.add(unit);
            }
        }

        if(!scouts.isEmpty()){
            selectedUnit = Util.selectRandom(scouts);
        }

        BuildAction<A> buildAction = null;

        // If no scout unit or all are busy is here, build one
        if(selectedUnit == null){
            for (var unit : notBusyUnits) {

                // If we have a unit (not a scout) on a city, make production order for scout (if not already producing)
                if(game.getCitiesByPosition().containsKey(unit.getPosition())){
                    if(game.getCity(unit.getPosition()).getState() == EmpireProductionState.Idle && !unit.getUnitTypeName().equals("2")){
                        buildAction = new BuildAction<>(gameStateNode,playerId,log,simulation,game.getCity(unit.getPosition()),unit, 2);
                        actions.add(buildAction);
                    }
                }
            }

            // Select other unit for scouting instead of scout for MoveAction
            for (var unit : notBusyUnits) {
                // If unit not on city, select it
                if(!game.getCitiesByPosition().containsKey(unit.getPosition())){
                    selectedUnit = unit;
                    break;
                }
            }
        }

        // If no units are free and all cities are producing throw exception
        if(selectedUnit == null){
            if(actions.isEmpty()){
                throw new ExecutableActionFactoryException("No units available and cities are all producing");
            }
            // If there are no units, which are free (not last unit on a city tile) just order buildAction
            return actions;
        }


        Set<Position> knownPositions = getKnownPositions(notBusyUnits.get(0).getPosition());

        Stack<Position> unknownPositions = new Stack<>();
        for (int y = 0; y < game.getBoard().getEmpireTiles().length; y++) {
            for (int x = 0; x < game.getBoard().getEmpireTiles()[y].length; x++) {
                var pos = new Position(x,y);
                if(!knownPositions.contains(pos)){
                    unknownPositions.push(pos);
                }
            }
        }



        double FAR_EXPLORATION_CONSTANT = 0.6;
        Random rand = new Random();

        double maxDistance = -1;
        Position destination = null;
        if(!unknownPositions.isEmpty()){

            if(rand.nextDouble() > FAR_EXPLORATION_CONSTANT){
                // If there are unknown tiles, select the farthest one from all known tiles (where we can move to).
                while (!unknownPositions.isEmpty()){
                    var unknownPosition = unknownPositions.pop();
                    for (var knownPosition: knownPositions) {
                        double dist = getEuclideanDistance(knownPosition, unknownPosition);
                        if (dist > maxDistance) {
                            maxDistance = dist;
                            destination = unknownPosition;
                        }
                    }

                }
            }else {
                destination = Util.selectRandom(unknownPositions);
            }

        }else {
            // If all tiles are known, move towards an enemy city.
            Map<Position, EmpireCity> cities = game.getCitiesByPosition();

            // Valid Cities
            List<EmpireCity> enemyCities = new ArrayList<>();

            for (var pos: cities.keySet()) {
                // If city doesn't belong to player with playerId, add to possible destinations
                if(game.getCity(pos).getPlayerId() != playerId){
                    enemyCities.add(game.getCity(pos));
                }
            }

            if(enemyCities == null || enemyCities.isEmpty()){
                throw new ExecutableActionFactoryException();
            }

            destination = Util.selectRandom(enemyCities).getPosition();

        }

        if (destination == null) {
            throw new ExecutableActionFactoryException();
        }

        MoveAction<A> moveAction = new MoveAction<A>(gameStateNode, selectedUnit, destination,playerId, log, simulation);
        actions.add(moveAction);

        return actions;
    }

    private double getEuclideanDistance(Position a, Position b) {
        var diff = a.subtract(b);
        return Math.sqrt(diff.getX() * diff.getX() + diff.getY() * diff.getY());
    }


    public Set<Position> getKnownPositions(Position start){
        // Get valid and visible locations the unit can move to using the FloodFill Algorithm
        Set<Position> validLocations = new HashSet<>();

        // Get empire map and empire tiles
        EmpireMap map = (EmpireMap) game.getBoard();
        EmpireTerrain[][] empireTiles = map.getEmpireTiles();

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
                if (tile != null && tile.getOccupants() != null && map.isMovementPossible(x, y, playerId)) {
                    validLocations.add(new Position(x, y));
                }

                // Check the tiles to the left, right, above, and below the current tile
                if (x > 0) positionsToCheck.add(new Position(x - 1, y));
                if (x < empireTiles.length - 1) positionsToCheck.add(new Position(x + 1, y));
                if (y > 0) positionsToCheck.add(new Position(x, y - 1));
                if (y < empireTiles[0].length - 1) positionsToCheck.add(new Position(x, y + 1));
            } catch (Exception e) {
                //log.info(e);
            }
        }

        if (validLocations.isEmpty()) {
            // No move action possible if no valid locations are available.
            throw new NoSuchElementException();
        }

        return validLocations;
    }


    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit,Deque<Command<A>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<A>> executable = generateExecutableAction(unitsCommandQueues);

        Deque<EmpireEvent> events = new LinkedList<>();
        if (executable != null) {
            while (!executable.isEmpty()){
                MacroAction<A> action = executable.poll();
                Deque<EmpireEvent> empireEvents =  action.getResponsibleActions(unitsCommandQueues);
                while (!empireEvents.isEmpty()) {
                    events.add(empireEvents.poll());
                }
            }
        }

        return events;
    }

    @Override
    public void simulate() throws ActionException {

    }

    @Override
    public String toString() {
        return "ExplorationMacroAction{" +
                "playerId=" + playerId +
                ", units=" + units +
                ", destinations=" + destinations +
                '}';
    }
}

