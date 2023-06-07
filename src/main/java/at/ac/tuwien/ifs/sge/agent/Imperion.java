package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.Game;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.core.util.node.RealTimeGameNode;
import at.ac.tuwien.ifs.sge.core.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.*;
import java.util.concurrent.Future;

public class Imperion<G extends RealTimeGame<A, ?>, A> extends AbstractRealTimeGameAgent<G, A> {
    private Future<?> thread;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var agent = new Imperion<>(Empire.class, playerId, playerName, 0);
        agent.start();
    }

    private static final double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private static final int DEFAULT_SIMULATION_PACE_MS = 1000;
    private static final int DEFAULT_SIMULATION_DEPTH = 20;
    private static final int DEFAULT_DECISION_PACE_MS = 2500;

    private final Comparator<Tree<GameStateNode<A>>> selectionComparator;

    private final Comparator<Tree<GameStateNode<A>>> treeMoveComparator;

    public Imperion(Class<G> gameClass, int playerId, String playerName, int logLevel) {
        super(gameClass, playerId, playerName, logLevel);


        // Compares two nodes based on their UCB values
        Comparator<Tree<GameStateNode<A>>> gameMcTreeUCTComparator = Comparator
                .comparingDouble(t -> upperConfidenceBound(t, DEFAULT_EXPLOITATION_CONSTANT));

        // Compares two game nodes based on a game-specific metric
        Comparator<GameStateNode<A>> gameSpecificComparator = (n1, n2) -> gameComparator.compare(n1.getGame(), n2.getGame());

        // Tree version of the game-specific metric comparator
        Comparator<Tree<GameStateNode<A>>> treeGameSpecificComparator = (t1, t2) -> gameSpecificComparator.compare(t1.getNode(), t2.getNode());

        // Selection comparator: first compares UCB, then (if UCB is the same) the game-specific metric
        selectionComparator = gameMcTreeUCTComparator.thenComparing(treeGameSpecificComparator);

        // Simple comparison of visits
        Comparator<GameStateNode<A>> visitComparator = Comparator.comparingInt(GameStateNode::getVisits);

        // Simple comparison of wins
        Comparator<GameStateNode<A>> winComparator = Comparator.comparingInt(t -> t.getWinsForPlayer(playerId));

        // Move comparator: first compares visits, then (if visits are the same) wins, and finally (if wins are also the same) the game-specific metric
        Comparator<GameStateNode<A>> moveComparator = visitComparator.thenComparing(winComparator).thenComparing(gameSpecificComparator);

        // Tree version of the move comparator
        treeMoveComparator = (t1, t2) -> moveComparator.compare(t1.getNode(), t2.getNode());


    }

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 1;
    }

    @Override
    protected void onGameUpdate(Object action, ActionResult result) {
    }

    @Override
    protected void onActionRejected(Object action) {
        log.info("action rejected");
    }

    @Override
    public void shutdown() {
        log.info("shutdown");
        thread.cancel(true);
    }

    @Override
    public void startPlaying() {
        log.info("start playing");
        thread = pool.submit(this::play);
    }

    // Calculates the upper confidence bound (UCB) from mcts node
    private double upperConfidenceBound(Tree<GameStateNode<A>> tree, double c) {
        double w = tree.getNode().getWinsForPlayer(playerId);
        double n = Math.max(tree.getNode().getVisits(), 1);
        double N = n;
        if (!tree.isRoot()) {
            N = tree.getParent().getNode().getVisits();
        }

        return (w / n) + c * Math.sqrt(Math.log(N) / n);
    }

    private Tree<GameStateNode<A>> selection(Tree<GameStateNode<A>> tree) {
        while (!tree.isLeaf()) {
            // Choose node based on UCB and when tie, use heuristic values from game
            var bestChild = Collections.max(tree.getChildren(), selectionComparator);
            tree = bestChild;
        }
        return tree;
    }


    private Tree<GameStateNode<A>> expansion(Tree<GameStateNode<A>> tree) {
        var node = tree.getNode();
        var game = node.getGame();
        var nextGameState = game.copy();

        Map<Integer, A> actionsTaken = new HashMap<>();
        Tree<GameStateNode<A>> expandedTree = null;

        int EXPAND_NODES_COUNT = 2;

        for (int i = 1; i <= EXPAND_NODES_COUNT; i++) {

            // schedule action events for all players
            for (var playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {

                // Pop one action from unexplored actions
                var action = node.popUnexploredAction(playerId);

                // Schedule action
                if (action != null && nextGameState.isValidAction(action, playerId)) {
                    nextGameState.scheduleActionEvent(new GameActionEvent<>(playerId, action, game.getGameClock().getGameTimeMs() + 1));
                    actionsTaken.put(playerId,action);
                }
            }

            try {
                nextGameState.advance(DEFAULT_SIMULATION_PACE_MS);
            } catch (ActionException e) {
                log.info("[ERROR] in expansion: "+ e);
                continue;
            }

            expandedTree = new DoubleLinkedTree<>(new GameStateNode<A>(nextGameState, actionsTaken));
            actionsTaken = new HashMap<>();

            tree.add(expandedTree);
        }

        if(expandedTree == null){
            return tree;
        }

        return expandedTree;
    }



    private Tree<GameStateNode<A>> expansionOld(Tree<GameStateNode<A>> tree) {
        var node = tree.getNode();
        var game = node.getGame();
        var nextGameState = game.copy();

        List<Stack<A>> possibleActions = new ArrayList<>();

        int EXPAND_COUNT = 2;

        // Fetch two actions from each player
        for (int i = 0; i < EXPAND_COUNT; i++) {

            for (var playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
                if(i == 0){
                    possibleActions.add(new Stack<>());
                }

                if(node.hasUnexploredActions(playerId)) {
                    possibleActions.get(playerId).add(node.popUnexploredAction(playerId));
                }
            }
        }

        Tree<GameStateNode<A>> expandedTree = null;

        for (int i = 0; i < EXPAND_COUNT; i++) {

            Map<Integer, A> actionsTaken = new HashMap<>();

            // Collect i-th-actions from each player
            for (int playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
                A action = null;
                if(!possibleActions.get(playerId).isEmpty()){
                    action = possibleActions.get(playerId).pop();
                }

                // Schedule those actions
                if (action != null && nextGameState.isValidAction(action, playerId)) {
                    nextGameState.scheduleActionEvent(new GameActionEvent<>(playerId, action, game.getGameClock().getGameTimeMs() + 1));
                    actionsTaken.put(playerId,action);
                }
            }

            // Try to advance game
            try {
                nextGameState.advance(DEFAULT_SIMULATION_PACE_MS);
            } catch (ActionException e) {
                log.info("[ERROR] in expansion: "+ e);
                continue;
            }

            // if advancing was possible
            expandedTree = new DoubleLinkedTree<>(new GameStateNode<A>(nextGameState, actionsTaken));
            tree.add(expandedTree);

        }

        if(expandedTree == null){
            return tree;
        }

        return expandedTree;
    }


    private boolean[] simulation(Tree<GameStateNode<A>> tree, long nextDecisionTime) {
        var node = tree.getNode();
        var game = node.getGame().copy();
        var depth = 0;
        try {
            while (!game.isGameOver() && depth++ <= DEFAULT_SIMULATION_DEPTH && System.currentTimeMillis() < nextDecisionTime) {

                // Apply random action for each player and advance game
                for (var playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
                    var possibleActions = game.getPossibleActions(playerId);
                    if (possibleActions.size() > 0) {
                        var nextAction = Util.selectRandom(possibleActions);
                        game.scheduleActionEvent(new GameActionEvent<>(playerId, nextAction, game.getGameClock().getGameTimeMs() + 1));
                    }
                }

                game.advance(DEFAULT_SIMULATION_PACE_MS);
            }
        } catch (ActionException e) {
            log.info("simulation reached invalid game state (partial information)");
        } catch (Exception e) {
            log.printStackTrace(e);
        }
        return determineWinner(game);
    }


    private boolean[] determineWinner(Game<A, ?> game) {
        var winners = new boolean[game.getNumberOfPlayers()];
        if (game.isGameOver()) {
            var evaluation = game.getGameUtilityValue();
            for (var pid = 0; pid < game.getNumberOfPlayers(); pid++)
                if (evaluation[pid] == 1D)
                    winners[pid] = true;
        } else {
            var evaluation = game.getGameHeuristicValue();
            var maxIndex = 0;
            for (var pid = 1; pid < game.getNumberOfPlayers(); pid++) {
                if (evaluation[pid] > evaluation[maxIndex])
                    maxIndex = pid;
            }
            winners[maxIndex] = true;
        }
        return winners;
    }

    private void backPropagation(Tree<GameStateNode<A>> tree, boolean[] winners) {
        // Go back up in the tree and increment the visits of evey node as well as the wins of the players nodes
        do {
            var node = tree.getNode();
            node.incrementVisits();

            for (var playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
                if (winners[playerId])
                    node.incrementWinsForPlayer(playerId);
            }
            tree = tree.getParent();
        } while (tree != null);
    }

    private String printTree(Tree<GameStateNode<A>> tree, String s, int level) {

        for (int i = 0; i < level; i++) {
            s += "  ";
        }
        s += tree.getNode() + "\n";
        if (tree.getChildren() != null) {
            for (int i = 0; i < tree.getChildren().size(); i++) {
                s = printTree(tree.getChildren().get(i), s, level + 1);
            }
        }
        return s;
    }

    public void play() {
        log.info("start play()");
        A lastDeterminedAction = null;


        while (true) {
            try {
                // Copy the game state and apply the last determined action, since this action was not yet accepted and sent back
                // from the engine server at this point in time
                var gameState = game.copy();
                if (lastDeterminedAction != null && gameState.isValidAction(lastDeterminedAction, playerId)) {
                    gameState.scheduleActionEvent(new GameActionEvent<>(playerId, lastDeterminedAction, gameState.getGameClock().getGameTimeMs() + 1));
                }
                // Advance the game state in time by the decision pace since this is the point in time that the next best action will be sent
                try {
                    gameState.advance(DEFAULT_DECISION_PACE_MS + 50);
                } catch (ActionException e) {
                    log.info("[ERROR] during gameState.advance()");
                    log.printStackTrace(e);
                    continue;
                }

                // Create a new tree with the game state as root
                var advancedGameState = new DoubleLinkedTree<>(new GameStateNode<>(gameState, null));

                // Tell the garbage collector to try recycling/reclaiming unused objects
                System.gc();


                var iterations = 0;
                var now = System.currentTimeMillis();
                var timeOfNextDecision = now + DEFAULT_DECISION_PACE_MS;


                // Select actions randomly
                // List<A> actions = new ArrayList<>();
                // actions.add(null);

                // for (int i = 0; i < advancedGameState.getNrOfUnexploredActions(); i++) {
                //     //log.info("Action " + i + "/" + advancedGameState.getNrOfUnexploredActions());
                //     actions.add(advancedGameState.popUnexploredAction());
                // }

                // log.info(actions);
                //log.info("Before MCTS: \n" + printTree(advancedGameState,"",0));

                while (System.currentTimeMillis() < timeOfNextDecision) {
                    //lastDeterminedAction = Util.selectRandom(actions);
                    //log.info(iterations);
                    // Select the best from the children according to the upper confidence bound
                    var tree = selection(advancedGameState);

                    //log.info("Selected Node: \n" + printTree(tree,"",0));

                    // Expand the selected node by all actions
                    var expandedTree = expansion(tree);

                    //log.info("Selected Node expanded\n" + printTree(tree,"",0));

                    // Simulate until the simulation depth is reached and determine winners
                    var winners = simulation(expandedTree, timeOfNextDecision);

                    // Back propagate the wins of the agent
                    backPropagation(expandedTree, winners);


                    iterations++;
                }


                log.info("After MCTS: \n" + printTree(advancedGameState,"",0));

                A action = null;
                if (advancedGameState.isLeaf()) {
                    log.info("Could not find a move! Doing nothing...");
                } else {
                    // Select the most visited node from the root node and send it to the engine server
                    log._info_();
                    log.info("Iterations: " + iterations);
                    for (var child : advancedGameState.getChildren())
                        log.info(child.getNode().getResponsibleActionForPlayer(playerId) + " visits:" + child.getNode().getVisits() + " wins:" + child.getNode().getWinsForPlayer(playerId));
                    var mostVisitedNode = Collections.max(advancedGameState.getChildren(), treeMoveComparator).getNode();

                    action = mostVisitedNode.getResponsibleActionForPlayer(playerId);
                    log.info("Determined next action: " + action);

                    if (action != null && gameState.isValidAction(action,playerId)) {
                        sendAction(action, System.currentTimeMillis() + 50);
                    } else {
                        log.info("Action was not valid");
                    }

                }
                lastDeterminedAction = action;

            } catch (Exception e) {
                log.info(e);
                break;
            } catch (OutOfMemoryError e) {
                log.info("Out of Memory");
                break;
            }
        }
        log.info("stopped playing");
    }
}


class ImperionRealTimeGameNode<A> {

    protected RealTimeGame<A, ?> game;

    protected final Map<Integer, A> actionsTaken;

    private final Map<Integer, Stack<A>> unexploredActions;

    public ImperionRealTimeGameNode(RealTimeGame<A, ?> game, Map<Integer, A> actionsTaken) {
        this.game = game;
        if (actionsTaken == null){
            this.actionsTaken = new HashMap<>();
        }else{
            this.actionsTaken = new HashMap<>(actionsTaken);
        }
        unexploredActions = getAllPossibleActionsByAllPlayer();
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
        Stack<A> playerActions = unexploredActions.get(playerId);
        return (playerActions != null && !playerActions.isEmpty());
    }

    public A popUnexploredAction(int playerId) {
        Stack<A> playerActions = unexploredActions.get(playerId);
        if (playerActions != null && !playerActions.isEmpty())
            return playerActions.pop();
        return null;
    }

    public void setGame(RealTimeGame<A, ?> game) {
        this.game = game;
    }

    public A getResponsibleActionForPlayer(int playerId) {
        return actionsTaken.get(playerId);
    }

    public RealTimeGame<A, ?> getGame() {
        return game;
    }
}

class GameStateNode<A> extends ImperionRealTimeGameNode<A> {

    private int[] winsForPlayer;
    private int visits = 0;

    public GameStateNode(RealTimeGame<A, ?> game, Map<Integer, A> actionsTaken) {
        super(game, actionsTaken);
        winsForPlayer = new int[game.getNumberOfPlayers()];
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

    @Override
    public String toString() {
        return "GameStateNode{" +
                "wins=" + Arrays.toString(winsForPlayer) +
                ", visits=" + visits +
                ", actionsTaken=" + actionsTaken +
                '}';
    }
}