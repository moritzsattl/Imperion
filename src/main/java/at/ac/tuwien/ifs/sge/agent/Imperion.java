package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.core.util.node.RealTimeGameNode;
import at.ac.tuwien.ifs.sge.core.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
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

    public Imperion(Class<G> gameClass, int playerId, String playerName, int logLevel) {
        super(gameClass, playerId, playerName, logLevel);
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

    private Tree<GameStateNode<A>> selection(Tree<GameStateNode<A>> tree) {
        if (tree.isLeaf()) return tree;
        return selection(Util.selectRandom(tree.getChildren()));
    }

    private Tree<GameStateNode<A>> expansion(Tree<GameStateNode<A>> tree) {
        var node = tree.getNode();
        var game = node.getGame();
        List<A> actions = new ArrayList<>();
        actions.add(null);

        for (int i = 0; i < node.getNrOfUnexploredActions(); i++) {
            //log.info("Action " + i + "/" + node.getNrOfUnexploredActions());
            actions.add(node.popUnexploredAction());
        }

        Tree<GameStateNode<A>> expandedTree = null;
        for (var action : actions) {
            var nextGameState = game.copy();
            if (action != null && game.isValidAction(action, node.getPlayerId()))
                nextGameState.scheduleActionEvent(new GameActionEvent<>(node.getPlayerId(), action, game.getGameClock().getGameTimeMs() + 1));
            try {
                nextGameState.advance(DEFAULT_SIMULATION_PACE_MS);
            } catch (ActionException e) {
                log.printStackTrace(e);
                continue;
            }
            expandedTree = new DoubleLinkedTree<GameStateNode<A>>(new GameStateNode<A>(node.getNextPlayerId(), nextGameState, action));
            tree.add(expandedTree);
        }
        tree.getNode().
        return ;
    }

    private boolean[] simulation(Tree<GameStateNode<A>> tree, long nextDecisionTime) {
        return null;
    }

    private void backPropagation(Tree<GameStateNode<A>> tree, boolean[] winners) {
        do {
            var node = tree.getNode();
            node.incrementVisits();
            var playerId = (node.getPlayerId() - 1) % game.getNumberOfPlayers();
            if (winners[playerId])
                node.incrementWins();
            tree = tree.getParent();
        } while (tree != null);
    }

    public void play() {
        log.info("start play()");
        A lastDeterminedAction = null;

        var gameState = game.copy();
        while (true) {
            // Copy the game state and apply the last determined action, since this action was not yet accepted and sent back
            // from the engine server at this point in time
            if (lastDeterminedAction != null && gameState.isValidAction(lastDeterminedAction, playerId)) {
                gameState.scheduleActionEvent(new GameActionEvent<>(playerId, lastDeterminedAction, gameState.getGameClock().getGameTimeMs() + 1));
            }
            // Advance the game state in time by the decision pace since this is the point in time that the next best action will be sent
            try {
                gameState.advance(DEFAULT_DECISION_PACE_MS+50);
            } catch (ActionException e) {
                log.info("[ERROR] during gameState.advance()");
                log.printStackTrace(e);
                break;
            }

            // Create a new tree with the game state as root
            //var mcTree = new DoubleLinkedTree<>(new GameStateNode<>(playerId, gameState, null));
            var node = new RealTimeGameNode<>(playerId, gameState, null);
            // Tell the garbage collector to try recycling/reclaiming unused objects
            System.gc();

            var now = System.currentTimeMillis();
            var timeOfNextDecision = now + DEFAULT_DECISION_PACE_MS;

            List<A> actions = new ArrayList<>();
            actions.add(null);

            for (int i = 0; i < node.getNrOfUnexploredActions(); i++) {
                //log.info("Action " + i + "/" + node.getNrOfUnexploredActions());
                actions.add(node.popUnexploredAction());
            }

            log.info(actions);

            while (System.currentTimeMillis() < timeOfNextDecision) {
                lastDeterminedAction = Util.selectRandom(actions);
            }
            gameState = game.copy();
            if (!gameState.isValidAction(lastDeterminedAction)) continue;
            log.info(lastDeterminedAction);
            if (lastDeterminedAction != null)
                sendAction(lastDeterminedAction, System.currentTimeMillis()+50);
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

}