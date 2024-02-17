package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.util.node.RealTimeGameNode;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
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
    private final EmpireEvent actionsTaken;

    // Unexplored actions
    private Stack<EmpireEvent> unexploredActions = new Stack<>();

    private final int[] winsForPlayer;
    private int visits = 0;

    public ImperionGameNode(Empire gameState, int nextPlayerId ,EmpireEvent actionsTaken) {
        this.gameState = gameState;
        this.nextPlayerId = nextPlayerId;
        this.actionsTaken = actionsTaken;
        winsForPlayer = new int[gameState.getNumberOfPlayers()];

        // add all possible actions to unexplored actions
        unexploredActions.addAll(getPossiblePrunedActions(nextPlayerId));
    }

    /**
     * Post-Cond: possibleActions != null && possibleActions.size() > 0
     */
    public Set<EmpireEvent> getPossiblePrunedActions(int playerId){
        Set<EmpireEvent> possibleActions = gameState.getPossibleActions(playerId);

        // Filter actions of busy units
        var busyUnits = gameState.getUnitsByPlayer(playerId).stream().filter(unit -> unit.getState() != EmpireUnitState.Idle);
        Set<EmpireEvent> actionsOfBusyUnits = busyUnits.map(unit -> {
            try {
                return gameState.getBoard().getPossibleActions(unit);
            } catch (EmpireMapException e) {
                throw new RuntimeException(e);
            }
        }).flatMap(Collection::stream).collect(Collectors.toSet());
        possibleActions.removeAll(actionsOfBusyUnits);


        // Convert the set to a list and shuffle
        List<EmpireEvent> possibleActionsList = new ArrayList<>(possibleActions);
        Collections.shuffle(possibleActionsList);

        return new HashSet<>(possibleActionsList);
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

    public EmpireEvent getActionTaken() {
        return actionsTaken;
    }

    public int getNextPlayerId() {
        return nextPlayerId;
    }

    public Stack<EmpireEvent> getUnexploredActions() {
        return unexploredActions;
    }

    public boolean hasUnexploredActions(){
        return !unexploredActions.isEmpty();
    }

    public EmpireEvent popUnexploredAction() {
        return !this.unexploredActions.isEmpty() ? this.unexploredActions.pop() : null;
    }

    @Override
    public String toString() {
        return "ImperionGameNode{" +
                "gameState=" + gameState +
                ", nextPlayerId=" + nextPlayerId +
                ", actionsTaken=" + actionsTaken +
                ", winsForPlayer=" + Arrays.toString(winsForPlayer) +
                ", visits=" + visits +
                '}';
    }
}
