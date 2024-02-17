package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.agent.util.MCTS;
import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.Game;
import at.ac.tuwien.ifs.sge.core.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public class Imperion extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    private Future<?> thread;

    private int playerId;
    private static MCTS treeSearch;
    private static final int DECISION_PACE = 2000;

    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var agent = new Imperion(playerId, playerName);
        agent.start();
    }


    public Imperion(int playerId, String playerName) {
        super(Empire.class,playerId, playerName, 0);
        this.playerId = playerId;
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

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 2;
    }

    @Override
    protected void onGameUpdate(HashMap<EmpireEvent, ActionResult> actionsWithResult) {
        for (var entry : actionsWithResult.entrySet()) {
            log.info(entry.getKey() + " " + entry.getValue());
        }
    }

    @Override
    protected void onActionRejected(EmpireEvent empireEvent) {
        log.info(empireEvent);
    }


    /**
     * Agent main loop
     */
    private void play(){
        treeSearch = new MCTS(this);

        EmpireEvent lastDeterminedAction = null;

        while (true){
            log.trace("Start of main loop in play()");
            log._info_();
            try{
                Empire nextGameState = copyGame();

                // Apply the next action to the copied game
                // Only schedule event, when it has not already been done on the server side
                if(lastDeterminedAction != null && nextGameState.isValidAction(lastDeterminedAction, playerId)){
                    log.info("Schedule next event");
                    nextGameState.scheduleActionEvent(new GameActionEvent<>(playerId, lastDeterminedAction, getGame().getGameClock().getGameTimeMs() + 1));
                }

                // At this time the next action should be sent to the server
                nextGameState.advance(DECISION_PACE);

                // Init MCTS Tree
                var gameStateTree = new DoubleLinkedTree<>(new ImperionGameNode(nextGameState, playerId,null));

                long timeForCalculations = System.currentTimeMillis() + DECISION_PACE;

                // Build MCTS Tree
                while (System.currentTimeMillis() < timeForCalculations){
                    log.trace("Start of MCTS calculations in play()");

                    // Select the best from the children according to the upper confidence bound
                    log.trace("Start selection MCTS");
                    var bestLeaf = treeSearch.selection(gameStateTree);
                    log.trace("End selection MCTS");

                    log.trace("Start expansion MCTS");
                    var expandedLeaf = treeSearch.expansion(bestLeaf);
                    log.trace("End expansion MCTS");

                    log.trace("Start simulation MCTS");
                    // Simulate until the simulation depth is reached and determine winners
                    var winners = treeSearch.simulation(expandedLeaf, timeForCalculations);
                    log.trace("End simulation MCTS");

                    log.trace("Start backPropagation MCTS");
                    treeSearch.backPropagation(expandedLeaf, winners);
                    log.trace("End backPropagation MCTS");

                    log.trace("End of MCTS calculations in play()");
                }
                log.debug(printTree(gameStateTree,"" ,0));

                EmpireEvent action = null;
                if(gameStateTree.isLeaf()){
                    log.info("Could not find a move! Doing nothing...");
                    log.info("This should never happen");
                }else{
                    var mostVisitedNode = Collections.max(gameStateTree.getChildren(), treeSearch.getTreeMoveComparator()).getNode();
                    for (var child : gameStateTree.getChildren()) {
                        log.info(  child.getNode().getActionTaken() + " visits: " + child.getNode().getVisits() + " wins: " + child.getNode().getWinsForPlayer(playerId));
                    }
                    action = mostVisitedNode.getActionTaken();
                    log.info("Determined next action: " + action);
                }

                lastDeterminedAction = action;

                // If best action is to do nothing, just continue without sending action to server
                if(action == null) continue;

                sendAction(action, System.currentTimeMillis() + 50);
            }catch (Exception e){
                log.debug(e);
                log.debugPrintStackTrace(e);
            }
        }



    }

    private String printTree(Tree<ImperionGameNode> tree, String s, int level) {
        s = s + "  ".repeat(Math.max(0, level));
        s += tree.getNode() + "\n";
        if (tree.getChildren() != null) {
            for (int i = 0; i < tree.getChildren().size(); i++) {
                if(level == 5){
                    return s;
                }
                s = printTree(tree.getChildren().get(i), s, level + 1);
            }
        }
        return s;
    }
    
    public Comparator<Game<EmpireEvent, ?>> getGameComperator(){
        return gameComparator;
    }

    public int getPlayerId() {
        return playerId;
    }

    public Logger getLogger(){
        return log;
    }


    public void assertWithMessage(boolean condition, String message) {
        if (!condition) {
            log.debug("Assertion failed: " + message);
        }
    }

}


