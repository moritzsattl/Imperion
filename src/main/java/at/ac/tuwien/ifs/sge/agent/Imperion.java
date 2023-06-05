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
    private static final int DEFAULT_DECISION_PACE_MS = 1000;

    private final Comparator<Tree<GameStateNode<A>>> gameMcTreeSelectionComparator;

    private final Comparator<Tree<GameStateNode<A>>> gameMcTreeMoveComparator;

    public Imperion(Class<G> gameClass, int playerId, String playerName, int logLevel) {
        super(gameClass, playerId, playerName, logLevel);


        // Compares two nodes based on their UCB values
        Comparator<Tree<GameStateNode<A>>> gameMcTreeUCTComparator = Comparator
                .comparingDouble(t -> upperConfidenceBound(t, DEFAULT_EXPLOITATION_CONSTANT));

        // gameComparator is an object variable introduced by AbstractGameAgent, which comparison depends on the specific game
        Comparator<GameStateNode<A>> gameMcNodeGameComparator = (n1, n2) -> gameComparator.compare(n1.getGame(), n2.getGame());
        Comparator<Tree<GameStateNode<A>>> gameMcTreeGameComparator = (t1, t2) -> gameMcNodeGameComparator
                .compare(t1.getNode(), t2.getNode());

        gameMcTreeSelectionComparator = gameMcTreeUCTComparator.thenComparing(gameMcTreeGameComparator);


        // Simple comparsion of visits and wins
        Comparator<GameStateNode<A>> gameMcNodeVisitComparator = Comparator.comparingInt(GameStateNode::getVisits);
        Comparator<GameStateNode<A>> gameMcNodeWinComparator = Comparator.comparingInt(GameStateNode::getWins);


        Comparator<GameStateNode<A>> gameMcNodeMoveComparator = gameMcNodeVisitComparator.thenComparing(gameMcNodeWinComparator)
                .thenComparing(gameMcNodeGameComparator);

        gameMcTreeMoveComparator = (t1, t2) -> gameMcNodeMoveComparator
                .compare(t1.getNode(), t2.getNode());


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
        double w = tree.getNode().getWins();
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
            var bestChild = Collections.max(tree.getChildren(), gameMcTreeSelectionComparator);
            var node = tree.getNode();
            // if the node has unexplored actions compare it to the best child
            if (node.hasUnexploredActions()) {
                // if it has a better heuristic value keep exploring the root node even though it is no leaf
                if (gameMcTreeSelectionComparator.compare(tree, bestChild) >= 0)
                    break;
            }
            tree = bestChild;
        }

        return tree;
    }

    private Tree<GameStateNode<A>> expansion(Tree<GameStateNode<A>> tree) {
        var node = tree.getNode();
        var game = node.getGame();
        List<A> actions = new ArrayList<>();


        if (tree.isRoot()) {
            // when it's the root expand all possible actions
            while (node.hasUnexploredActions()) {
                actions.add(node.popUnexploredAction());
            }
            //actions.add(null);
        } else {
            if (tree.isLeaf()) {
                // always explore the possibility of doing nothing first (null action)
                //actions.add(null);
            } else {
                // otherwise, choose a random unexplored action
                actions.add(node.popUnexploredAction());
            }
        }

        Tree<GameStateNode<A>> expandedTree = null;
        for (var action : actions) {
            var nextGameState = game.copy();
            if (action != null && nextGameState.isValidAction(action, node.getPlayerId()))
                nextGameState.scheduleActionEvent(new GameActionEvent<>(node.getPlayerId(), action, game.getGameClock().getGameTimeMs() + 1));
            try {
                nextGameState.advance(DEFAULT_SIMULATION_PACE_MS);
            } catch (ActionException e) {
                if (!tree.isRoot() && node.hasUnexploredActions() && action != null)
                    return expansion(tree);
                log.info(e);
                continue;
            }
            expandedTree = new DoubleLinkedTree<>(new GameStateNode<A>(node.getNextPlayerId(), nextGameState, action));
            tree.add(expandedTree);
        }

        if (expandedTree == null) {
            return tree;
        }

        return expandedTree;
    }

    private boolean[] simulation(Tree<GameStateNode<A>> tree, long nextDecisionTime) {
        var node = tree.getNode();
        var game = node.getGame().copy();
        var playerId = node.getPlayerId();
        var depth = 0;
        try {
            // Simulate until the simulation depth or time of next decision is reached or the game is over
            while (!game.isGameOver() && depth++ <= DEFAULT_SIMULATION_DEPTH && System.currentTimeMillis() < nextDecisionTime) {


                // Apply a random action and advance the game state by the simulation pace
                var possibleActions = game.getPossibleActions(playerId);
                //log.info(possibleActions);
                if (possibleActions.size() > 0) {
                    var nextAction = Util.selectRandom(possibleActions);
                    //log.info(nextAction);

                    game.scheduleActionEvent(new GameActionEvent<>(playerId, nextAction, game.getGameClock().getGameTimeMs() + 1));

                    // Only advance game, when there is a possible action
                    game.advance(DEFAULT_SIMULATION_PACE_MS);
                    playerId = (playerId + 1) % game.getNumberOfPlayers();
                }

            }
        } catch (ActionException e) {
            // If we have partial information (Fog of War) the result of some actions might be ambiguous leading in an ActionException
            // Stop the simulation there
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
            var playerId = (node.getPlayerId() - 1);
            if (playerId < 0) playerId = game.getNumberOfPlayers() - 1;
            if (winners[playerId])
                node.incrementWins();
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
                var advancedGameState = new DoubleLinkedTree<>(new GameStateNode<>(playerId, gameState, null));
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
                    // TODO: Implement proper MCTS
                    // Select the best from the children according to the upper confidence bound
                    var tree = selection(advancedGameState);

                    //log.info("Selected Node: \n" + printTree(tree,"",0));

                    // Expand the selected node by one action
                    var expandedTree = expansion(tree);

                    //log.info("Selected Node expanded\n" + printTree(tree,"",0));


                    // Simulate until the simulation depth is reached and determine winners
                    var winners = simulation(expandedTree, timeOfNextDecision);

                    // Back propagate the wins of the agent
                    backPropagation(expandedTree, winners);


                    iterations++;
                }


                //log.info("After MCTS: \n" + printTree(advancedGameState,"",0));

                A action = null;
                if (advancedGameState.isLeaf()) {
                    log.info("Could not find a move! Doing nothing...");
                } else {
                    // Select the most visited node from the root node and send it to the engine server
                    log._info_();
                    log.info("Iterations: " + iterations);
                    for (var child : advancedGameState.getChildren())
                        log.info(child.getNode().getResponsibleAction() + " visits:" + child.getNode().getVisits() + " wins:" + child.getNode().getWins());
                    var mostVisitedNode = Collections.max(advancedGameState.getChildren(), gameMcTreeMoveComparator).getNode();

                    action = mostVisitedNode.getResponsibleAction();
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

    // the player id of the player who determines the NEXT action
    protected final int playerId;

    protected RealTimeGame<A, ?> game;
    protected final A responsibleAction;

    private final Stack<A> unexploredActions = new Stack<>();

    public ImperionRealTimeGameNode(int playerId, RealTimeGame<A, ?> game, A responsibleAction) {
        this.game = game;
        this.playerId = playerId;
        this.responsibleAction = responsibleAction;
        unexploredActions.addAll(game.getPossibleActions(playerId));
        Collections.shuffle(unexploredActions);
    }

    public int getNrOfUnexploredActions() {
        return unexploredActions.size();
    }

    public boolean hasUnexploredActions() {
        return !unexploredActions.isEmpty();
    }

    public A popUnexploredAction() {
        if (!unexploredActions.isEmpty())
            return unexploredActions.pop();
        return null;
    }

    public void setGame(RealTimeGame<A, ?> game) {
        this.game = game;
    }

    public int getNextPlayerId() {
        return (playerId + 1) % game.getNumberOfPlayers();
    }

    public int getPlayerId() {
        return playerId;
    }

    public A getResponsibleAction() {
        return responsibleAction;
    }

    public RealTimeGame<A, ?> getGame() {
        return game;
    }

}

class GameStateNode<A> extends ImperionRealTimeGameNode<A> {

    private int wins = 0;
    private int visits = 0;

    public GameStateNode(int playerId, RealTimeGame<A, ?> game, A responsibleAction) {
        super(playerId, game, responsibleAction);
    }


    public int incrementWins() {
        return ++wins;
    }

    public int incrementVisits() {
        return ++visits;
    }

    public int getWins() {
        return wins;
    }

    public int getVisits() {
        return visits;
    }

    @Override
    public String toString() {
        return "GameStateNode{" +
                "wins=" + wins +
                ", visits=" + visits +
                ", playerId=" + playerId +
                ", responsibleAction=" + responsibleAction +
                '}';
    }
}