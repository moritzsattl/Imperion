package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;

import java.util.*;

class ImperionRealTimeGameNode<A> {

    protected RealTimeGame<A, ?> game;

    protected final Map<Integer, MacroActionType> actionsTaken;

    private final Map<Integer, Stack<MacroActionType>> unexploredActions;

    public ImperionRealTimeGameNode(RealTimeGame<A, ?> game, Map<Integer, MacroActionType> actionsTaken) {
        this.game = game;
        if (actionsTaken == null){
            this.actionsTaken = new HashMap<>();
        }else{
            this.actionsTaken = new HashMap<>(actionsTaken);
        }
        unexploredActions = getAllPossibleMacroActionsByAllPlayer();
    }


    // This method return all possible macro actions by each individual player
    public Map<Integer, Stack<MacroActionType>> getAllPossibleMacroActionsByAllPlayer() {
        Map<Integer, Stack<MacroActionType>> getAllPossibleMacroActionsByPlayer = new HashMap<>();
        for (int playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
            Stack<MacroActionType> actions = new Stack<>();

            actions.addAll(Arrays.asList(MacroActionType.values()));

            getAllPossibleMacroActionsByPlayer.put(playerId,actions);
        }

        return getAllPossibleMacroActionsByPlayer;
    }

    // This method return all possible actions by each individual player
    public Map<Integer, Stack<A>> getAllPossibleActionsByAllPlayer() {
        Map<Integer, Stack<A>> getAllPossibleActionsByPlayer = new HashMap<>();
        for (int playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
            Set<A> possibleActions = game.getPossibleActions(playerId);
            Stack<A> actions = new Stack<>();

            // Only shuffle and add to the stack if possibleActions is not null
            if (possibleActions != null) {
                List<A> shuffledActions = new ArrayList<>(possibleActions);
                Collections.shuffle(shuffledActions);
                actions.addAll(shuffledActions);
            }

            getAllPossibleActionsByPlayer.put(playerId, actions);
        }
        return getAllPossibleActionsByPlayer;
    }

    public boolean hasUnexploredActions(int playerId) {
        Stack<MacroActionType> playerActions = unexploredActions.get(playerId);
        return (playerActions != null && !playerActions.isEmpty());
    }

    public MacroActionType popUnexploredAction(int playerId) {
        Stack<MacroActionType> playerActions = unexploredActions.get(playerId);
        if (playerActions != null && !playerActions.isEmpty())
            return playerActions.pop();
        return null;
    }

    public void setGame(RealTimeGame<A, ?> game) {
        this.game = game;
    }

    public MacroActionType getResponsibleMacroActionForPlayer(int playerId) {
        return actionsTaken.get(playerId);
    }

    public RealTimeGame<A, ?> getGame() {
        return game;
    }
}
