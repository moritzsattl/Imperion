package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.agent.util.*;
import at.ac.tuwien.ifs.sge.agent.util.MacroAction.*;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.CombatStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.CombatStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.MovementStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitState;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents a node of the MCTS Tree which contains information about the current game state
 * and the actions taken to get to this certain node
 */
public class ImperionGameNode {

    private Empire gameState;

    // The player which can take then next action
    private final int nextPlayerId;

    // Actions taken by player with nextPlayerId from parent node
    private final List<EmpireEvent> actionsTaken;

    // Unexplored actions
    private Stack<MacroAction> unexploredActions = new Stack<>();

    // Saves the heuristic value of this node
    private final double[] evaluation;
    private int visits = 0;

    // Units which are idle
    public final Set<EmpireUnit> idleUnits;

    // Idle Units with no command
    public final Set<EmpireUnit> readyUnits;

    // Ready units not on city
    public final Set<EmpireUnit> readyUnitsNotLastOnCity;

    // Occupied Idle Cities with no command
    public final Set<EmpireCity> readyCities;

    // Unit command queue for each player
    private final CommandQueue[] commandQueues;

    // Executed MacroAction
    private final MacroAction macroAction;

    private final static Random random = new Random();

    public ImperionGameNode(Empire gameState, int nextPlayerId ,List<EmpireEvent> actionsTaken, CommandQueue[] commandQueues, MacroAction macroAction) {
        this.gameState = gameState;
        this.nextPlayerId = nextPlayerId;
        this.actionsTaken = actionsTaken;
        this.commandQueues = commandQueues;
        this.macroAction = macroAction;
        evaluation = new double[gameState.getNumberOfPlayers()];

        // Remove dead units from command queue (if necessary)
        commandQueues[nextPlayerId].removeDeadUnits(gameState.getUnitsByPlayer(nextPlayerId));

        idleUnits = gameState.getUnitsByPlayer(nextPlayerId).stream().filter(unit -> unit.getState() == EmpireUnitState.Idle).collect(Collectors.toSet());

        var unitsWithCommand = commandQueues[nextPlayerId].getUnitCommandQueue().entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> gameState.getUnit(entry.getKey())).collect(Collectors.toSet());

        readyUnits = idleUnits.stream()
                .filter(unit -> !unitsWithCommand.contains(unit))
                .collect(Collectors.toSet());

        var cityPositions = gameState.getCitiesByPosition().keySet();
        readyUnitsNotLastOnCity = readyUnits.stream()
                // Either unit is not on city position or is not last on city tile
                .filter(unit -> !cityPositions.contains(unit.getPosition()) || gameState.getCitiesByPosition().get(unit.getPosition()).getOccupants().size() > 1)
                .collect(Collectors.toSet());

        var citiesWithCommand = commandQueues[nextPlayerId].getCityCommandQueue().entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> gameState.getCitiesByPosition().get(entry.getKey()))
                .collect(Collectors.toSet());

        readyCities = gameState.getCitiesByPosition().values().stream()
                .filter(city -> city.getPlayerId() == nextPlayerId && city.getState() == EmpireProductionState.Idle && !citiesWithCommand.contains(city))
                .collect(Collectors.toSet());

        // add all possible actions to unexplored actions
        unexploredActions.addAll(getPossiblePrunedActions(nextPlayerId));
    }

    /**
     * Post-Cond: possibleActions != null && possibleActions.size() > 0
     */
    public Set<MacroAction> getPossiblePrunedActions(int playerId){
        Set<MacroAction> actions = gameState.getPossibleActions(playerId).stream()
                .filter(event -> !(event instanceof MovementStartOrder))
                .filter(event -> !(event instanceof MovementStopOrder))
                .filter(event -> !(event instanceof ProductionStopOrder))
                .filter(event -> !(event instanceof ProductionStartOrder))
                .filter(event -> !(event instanceof CombatStartOrder))
                .filter(event -> !(event instanceof CombatStopOrder))
                .map(SingletonMacroAction::new).collect(Collectors.toSet());

        actions.add(new ScheduleNothingMacroAction());

        // If no units are idle, just return
        // If all units have commands, just return
        if(idleUnits.isEmpty() || readyUnits.isEmpty()) return actions;

        addProductionMacroActionIfPossible(actions, playerId);

        addExpansionMacroActionIfPossible(actions, playerId);

        addExplorationMacroActionIfPossible(actions, playerId);

        addConquerMacroActionIfPossible(actions, playerId);

        return actions;
    }

    private void addConquerMacroActionIfPossible(Set<MacroAction> actions, int playerId) {
        var enemyCities = getEnemiesCities(playerId);

        if(enemyCities.isEmpty()) return;

        // Select random city
        var city = Util.selectRandom(enemyCities);

        // Try to use cavalry
        EmpireUnit nearestUnit = findClosestUnit(city.getPosition(), readyUnits, 3);

        // else return
        if(nearestUnit == null) return;

        var nearestAdjacentPositionFromEnemyCity = getNearestMovablePositionAdjacentTo(nearestUnit, city.getPosition(), playerId);

        List<EmpireEvent> moveEvents = BFS.findShortestPath(nearestUnit, nearestAdjacentPositionFromEnemyCity, gameState, playerId);

        if(moveEvents == null) return;

        actions.add(new ConquerMacroAction(moveEvents));
    }

    /**
     * Returns adjacent position to position where movement is possible which is nearest from unit
     */
    private Position getNearestMovablePositionAdjacentTo(EmpireUnit unit, Position position, int playerId) {
        // All neighbours where movement is possible from position
        var neighbours = new BFS.Node(position).getNeighbours(gameState, playerId);

        var smallestDist = Double.MAX_VALUE;
        Position bestPos = null;

        for(var neighbour : neighbours){
            var dist = Imperion.getEuclideanDistance(unit.getPosition(), neighbour.position);
            if(dist < smallestDist) {
                smallestDist = dist;
                bestPos = neighbour.position;
            }
        }

        Imperion.logAssertWithMessage(bestPos != null, "getNearestMovablePositionAdjacentTo(...) should never return null, but does");

        return bestPos;
    }

    private void addProductionMacroActionIfPossible(Set<MacroAction> actions, int playerId) {

        if(readyCities.isEmpty()) return;

        var idleCity = Util.selectRandom(readyCities);

        // Add production action for scout
        actions.add(new ProductionMacroAction(new ProductionStartOrder(idleCity.getPosition(), 2), 5));

        // Add production action for cavalry if enemies are in sight
        if(!getEnemiesInSight(playerId).isEmpty()) actions.add(new ProductionMacroAction(new ProductionStartOrder(idleCity.getPosition(), 3), 10));
    }

    private void addExpansionMacroActionIfPossible(Set<MacroAction> actions, int playerId) {

        // Check if unoccupied cities are in sight
        var unoccupiedCities = gameState.getCitiesByPosition().values().stream()
                .filter(city -> !city.isOccupied())
                .toList();

        if(unoccupiedCities.isEmpty()) return;

        // Select random city for expansion
        var city = Util.selectRandom(unoccupiedCities);

        // TODO: Check if another unit is already expanding to this city

        // Find nearest unit, from city, which is not on a city itself
        if(readyUnitsNotLastOnCity.isEmpty()) return;
        var nearestUnitFromCity = findClosestUnit(city.getPosition(), readyUnitsNotLastOnCity);

        List<EmpireEvent> moveEvents = BFS.findShortestPath(nearestUnitFromCity, city.getPosition(), gameState, playerId);

        if(moveEvents == null) return;

        actions.add(new ExpansionMacroAction(moveEvents));
    }


    private void addExplorationMacroActionIfPossible(Set<MacroAction> actions, int playerId) {

        Position destination = null;
        if(random.nextDouble() > 0.6) destination = getFarthestAwayPosition(playerId); else destination = Util.selectRandom(getUnknownPositions(playerId));

        // If all positions are known return
        if(destination == null) return;

        // Try to use a scout for exploration
        EmpireUnit nearestUnitFromFarthestTile = findClosestUnit(destination, readyUnitsNotLastOnCity, 2);

        // If no scout exits or is available, choose another unit
        if(nearestUnitFromFarthestTile == null) nearestUnitFromFarthestTile = findClosestUnit(destination, readyUnitsNotLastOnCity);

        if(nearestUnitFromFarthestTile == null) return;

        var moveEvents = BFS.findShortestPath(nearestUnitFromFarthestTile, destination, gameState, playerId);

        if(moveEvents == null) return;

        // Only add first three moves
        actions.add(new ExplorationMacroAction(moveEvents));
    }

    /**
     * Returns closest unit from position
     */
    private static EmpireUnit findClosestUnit(Position position, Set<EmpireUnit> units) {
        EmpireUnit result = null;
        double closest = Double.MAX_VALUE;
        for (var unit : units) {
            double temp = Imperion.getEuclideanDistance(position, unit.getPosition());
            if (temp < closest) {
                result = unit;
            }
        }
        return result;
    }

    /**
     * Returns closest unit with certain type from position
     */
    private static EmpireUnit findClosestUnit(Position position, Set<EmpireUnit> units, int type) {
        return findClosestUnit(position, units.stream().filter(unit -> unit.getUnitTypeId() == type).collect(Collectors.toSet()));
    }

    /**
     * Returns position which is the farthest away from all discovered positions
     */
    public Position getFarthestAwayPosition(int playerId) {
        var knownPositions = gameState.getBoard().getDiscoveredByPosition().entrySet().stream()
                .filter(entry -> entry.getValue()[playerId])
                .map(Map.Entry::getKey)
                .toList();

        var unknownPositions = new Stack<Position>();
        unknownPositions.addAll(getUnknownPositions(playerId));

        double maxSumOfDistances = -1;
        Position destination = null;
        while (!unknownPositions.isEmpty()){
            // If there are unknown tiles, select the one which is the farthest away from all known tiles,
            // which means the sum of all distances from known to certain unknown tile is the greatest

            var unknownPosition = unknownPositions.pop();
            double dists = 0;

            for (var knownPosition: knownPositions) {
                dists += Imperion.getEuclideanDistance(knownPosition, unknownPosition);
            }

            if (dists > maxSumOfDistances) {
                maxSumOfDistances = dists;
                destination = unknownPosition;
            }
        }

        return destination;
    }

    /**
     * Return unknown positions from player
     */
    private List<Position> getUnknownPositions(int playerId){
        return gameState.getBoard().getDiscoveredByPosition().entrySet().stream()
                .filter(entry -> !entry.getValue()[playerId])
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<EmpireUnit> getEnemiesInSight(int playerId){
        List<EmpireUnit> enemies = new ArrayList<>();

        for (int pid = 0; pid < gameState.getNumberOfPlayers(); pid++) {
            if(pid == playerId) continue;

            enemies.addAll(gameState.getUnitsByPlayer(pid));
        }

        return enemies;
    }

    private List<EmpireCity> getEnemiesCities(int playerId){
        return gameState.getCitiesByPosition().values().stream()
                .filter(city -> city.isOccupied() && city.getPlayerId() != playerId)
                .toList();
    }


    public double incrementEvaluation(double increment, int playerId) {
        evaluation[playerId] += increment;
        return evaluation[playerId];
    }

    public int incrementVisits() {
        return ++visits;
    }

    public double getEvaluationForPlayer(int playerId) {
        return evaluation[playerId];
    }

    public int getVisits() {
        return visits;
    }

    public Empire getGameState() {
        return gameState;
    }

    public List<EmpireEvent> getActionsTaken() {
        return actionsTaken;
    }

    public int getNextPlayerId() {
        return nextPlayerId;
    }

    public MacroAction getMacroAction() {
        return macroAction;
    }

    public boolean hasUnexploredActions(){
        return !unexploredActions.isEmpty();
    }

    public MacroAction popUnexploredAction() {
        return !this.unexploredActions.isEmpty() ? this.unexploredActions.pop() : null;
    }

    public CommandQueue[] copyCommandQueues() {
        CommandQueue[] commandQueues = new CommandQueue[this.commandQueues.length];

        for (int i = 0; i < this.commandQueues.length; i++) {
            commandQueues[i] = new CommandQueue(this.commandQueues[i]);
        }

        return commandQueues;
    }
    @Override
    public String toString() {
        return "ImperionGameNode{" +
                "nextPlayerId=" + nextPlayerId +
                ", actionsTaken=" + actionsTaken +
                ", winsForPlayer=" + Arrays.toString(evaluation) +
                ", visits=" + visits +
                ", commandQueues=" + Arrays.toString(commandQueues) +
                '}';
    }
}
