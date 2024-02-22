package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.agent.util.*;
import at.ac.tuwien.ifs.sge.agent.util.MacroAction.*;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
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
import java.util.stream.IntStream;

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

    private final int[] winsForPlayer;
    private int visits = 0;

    // Units which are idle
    public final Set<EmpireUnit> idleUnits;

    // Idle Units with no command
    public final Set<EmpireUnit> readyUnits;

    // Occupied Idle Cities with no command
    public final Set<EmpireCity> readyCities;

    // Unit command queue for each player
    private final CommandQueue[] commandQueues;

    // Executed MacroAction
    private final MacroAction macroAction;

    public ImperionGameNode(Empire gameState, int nextPlayerId ,List<EmpireEvent> actionsTaken, CommandQueue[] commandQueues, MacroAction macroAction) {
        this.gameState = gameState;
        this.nextPlayerId = nextPlayerId;
        this.actionsTaken = actionsTaken;
        this.commandQueues = commandQueues;
        this.macroAction = macroAction;
        winsForPlayer = new int[gameState.getNumberOfPlayers()];

        idleUnits = gameState.getUnitsByPlayer(nextPlayerId).stream().filter(unit -> unit.getState() == EmpireUnitState.Idle).collect(Collectors.toSet());

        var unitsWithCommand = commandQueues[nextPlayerId].getUnitCommandQueue().entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> gameState.getUnit(entry.getKey())).collect(Collectors.toSet());

        readyUnits = idleUnits.stream()
                .filter(unit -> !unitsWithCommand.contains(unit))
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
                .map(SingletonMacroAction::new).collect(Collectors.toSet());

        actions.add(new ScheduleNothingMacroAction());

        // If no units are idle, just return
        // If all units have commands, just return
        if(idleUnits.isEmpty() || readyUnits.isEmpty()) return actions;

        addProductionMacroActionIfPossible(actions, playerId);

        addExpansionMacroActionIfPossible(actions);

        addExplorationMacroActionIfPossible(actions, playerId);

        return actions;
    }

    private void addProductionMacroActionIfPossible(Set<MacroAction> actions, int playerId) {

        if(readyCities.isEmpty()) return;

        var idleCity = Util.selectRandom(readyCities);

        // Add production action for scout
        actions.add(new ProductionMacroAction(new ProductionStartOrder(idleCity.getPosition(), 2), 5));

    }

    private void addExpansionMacroActionIfPossible(Set<MacroAction> actions) {

        // Check if unoccupied cities are in sight
        var unoccupiedCities = gameState.getCitiesByPosition().values().stream()
                .filter(city -> !city.isOccupied())
                .toList();

        if(unoccupiedCities.isEmpty()) return;

        // Select random city for expansion
        var city = Util.selectRandom(unoccupiedCities);

        // Find nearest unit from city
        var nearestUnitFromCity = findClosestUnit(city.getPosition(), readyUnits);

        var moveEvents = getMovementActionsForUnit(nearestUnitFromCity, city.getPosition());
        if(moveEvents == null) return;

        actions.add(new ExpansionMacroAction(moveEvents));
    }


    private void addExplorationMacroActionIfPossible(Set<MacroAction> actions, int playerId) {

        var farthestAwayPosition = getFarthestAwayPosition(playerId);

        // If all positions are known return
        if(farthestAwayPosition == null) return;

        var nearestUnitFromFarthestTile = findClosestUnit(farthestAwayPosition, readyUnits);

        var moveEvents = getMovementActionsForUnit(nearestUnitFromFarthestTile, farthestAwayPosition);

        if(moveEvents == null) return;

        // Only add first three moves
        actions.add(new ExplorationMacroAction(moveEvents.subList(0,3)));
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
     * Returns position which is the farthest away from discovered positions
     */
    public Position getFarthestAwayPosition(int playerId) {
        var knownPositions = gameState.getBoard().getDiscoveredByPosition().entrySet().stream()
                .filter(entry -> entry.getValue()[playerId])
                .map(Map.Entry::getKey)
                .toList();

        var unknownPositions = new Stack<Position>();
        unknownPositions.addAll(gameState.getBoard().getDiscoveredByPosition().entrySet().stream()
                .filter(entry -> !entry.getValue()[playerId])
                .map(Map.Entry::getKey)
                .toList());


        double maxDistance = -1;
        Position destination = null;
        while (!unknownPositions.isEmpty()){
            // If there are unknown tiles, select the farthest one from all known tiles (where we can move to).
            var unknownPosition = unknownPositions.pop();
            for (var knownPosition: knownPositions) {
                double dist = Imperion.getEuclideanDistance(knownPosition, unknownPosition);
                if (dist > maxDistance) {
                    maxDistance = dist;
                    destination = unknownPosition;
                }
            }
        }

        return destination;
    }

    public List<EmpireEvent> getMovementActionsForUnit(EmpireUnit unit, Position destination){
        AStar aStar = new AStar(unit.getPosition(),destination,this,getNextPlayerId());

        AStarNode currentNode = aStar.findPath();

        // If no path was found
        if(currentNode == null) return null;

        var orders = new ArrayDeque<EmpireEvent>();

        while (currentNode != null) {
            orders.addFirst(new MovementStartOrder(unit.getId(),currentNode.getPosition()));

            // Next Position to move to
            currentNode = currentNode.getPrev();
        }

        // Remove current position
        orders.poll();

        return orders.stream().toList();
    }

    public int incrementWinsForPlayer(int playerId) {
        return ++winsForPlayer[playerId];
    }

    public int incrementVisits() {
        return ++visits;
    }

    public int getWinsForPlayer(int playerId) {
        return winsForPlayer[playerId];
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
                ", winsForPlayer=" + Arrays.toString(winsForPlayer) +
                ", visits=" + visits +
                ", commandQueues=" + Arrays.toString(commandQueues) +
                '}';
    }
}
