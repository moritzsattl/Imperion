package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.agent.util.CommandQueue;
import at.ac.tuwien.ifs.sge.agent.util.Heuristics;
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
import at.ac.tuwien.ifs.sge.game.empire.map.Position;

import java.util.*;
import java.util.concurrent.Future;

public class Imperion extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    private Future<?> thread;

    private int playerId;
    private static MCTS treeSearch;
    private static final int DECISION_PACE = 2000;
    public static Logger logger;

    public static Long START_TIME_MS;
    public static Long GAME_DURATION_MS;

    // normal 5min game
    public static Long MAX_GAME_DURATION_MS = 300000L;

    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var agent = new Imperion(playerId, playerName);
        agent.start();
    }


    public Imperion(int playerId, String playerName) {
        super(Empire.class,playerId, playerName, 0);
        this.playerId = playerId;
        logger = log;
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
        //log.info("Game Updates");
        //for (var entry : actionsWithResult.entrySet()) {
        //    log.info(entry.getKey() + " " + entry.getValue());
        //}
    }

    @Override
    protected void onActionRejected(EmpireEvent empireEvent) {
        log.error("Action rejected");
        log.error(empireEvent);
    }


    /**
     * Agent main loop
     */
    private void play(){
        treeSearch = new MCTS(this);
        START_TIME_MS = System.currentTimeMillis();

        List<EmpireEvent> lastDeterminedActions = null;


        // Allocating memory for queues
        CommandQueue[] commandQueues = new CommandQueue[numberOfPlayers];

        // Initializing them
        for (int i = 0; i < commandQueues.length; i++) {
            commandQueues[i] = new CommandQueue();
        }


        while (true){
            log.trace("Start of main loop in play()");
            log._info_();
            try{
                log.trace("Start of the try-catch block");

                Empire nextGameState = copyGame();
                GAME_DURATION_MS = System.currentTimeMillis() - START_TIME_MS;
                nextGameState.getGameClock().setGameTimeMs(GAME_DURATION_MS);

                // Apply the next actions to the copied game
                // Only schedule events, when it has not already been done on the server side
                if(lastDeterminedActions != null)
                    for (var action : lastDeterminedActions)
                        if(nextGameState.isValidAction(action, playerId)){
                            nextGameState.scheduleActionEvent(new GameActionEvent<>(playerId, action, getGame().getGameClock().getGameTimeMs() + 1));
                        }

                // At this time the next actions should be sent to the server
                nextGameState.advance(DECISION_PACE);

                // Init MCTS Tree
                var gameStateTree = new DoubleLinkedTree<>(new ImperionGameNode(nextGameState, playerId,null, commandQueues, null));

                // Heuristics.logHeuristics(nextGameState, playerId);

                long timeForCalculations = System.currentTimeMillis() + DECISION_PACE;

                // Build MCTS Tree
                while (System.currentTimeMillis() < timeForCalculations){
                    log.trace("Start of MCTS calculations in play()");

                    // Select the best from the children according to the upper confidence bound
                    log.trace("Start selection MCTS");
                    var bestLeaf = treeSearch.selection(gameStateTree);
                    log.trace("Selected Leaf: " + bestLeaf.getNode());
                    log.trace("End selection MCTS");

                    log.trace("Start expansion MCTS");
                    var expandedLeaf = treeSearch.expansion(bestLeaf);
                    log.trace("(Random Chosen) Expanded Leaf: " + expandedLeaf.getNode());
                    log.trace("End expansion MCTS");
                    //log.debug("Tree after expansion");
                    //log.debug(printTree(gameStateTree,"" ,0));

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


                var mostVisitedNode = Collections.max(gameStateTree.getChildren(), treeSearch.getTreeMoveComparator()).getNode();
                for (var child : gameStateTree.getChildren()) {
                    log.info("Action taken: " + child.getNode().getActionsTaken() + "(" + child.getNode().getMacroAction().getType() +") visits: " + child.getNode().getVisits() + " wins: " + child.getNode().getWinsForPlayer(playerId));
                }

                lastDeterminedActions = mostVisitedNode.getActionsTaken();
                log.info("Determined next action: " + lastDeterminedActions);

                commandQueues = mostVisitedNode.copyCommandQueues();
                log.info("Current command queues: ");
                for(var commandQueue : commandQueues){
                    log.info(commandQueue);
                }

                // If best action is to do nothing, just continue without sending action to server
                if(lastDeterminedActions == null) continue;

                for (int i = 0; i < lastDeterminedActions.size(); i++) {
                    sendAction(lastDeterminedActions.get(i), System.currentTimeMillis() + 50 + i);
                }

                log.trace("End of the try-catch block");
            }catch (Exception e){
                log.info(e);
                log.printStackTrace(e);
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

    public static void assertWithMessage(boolean condition, String message) {
        if (!condition) {
            logger.debug("Assertion failed: " + message);
        }
    }

    public static void logAssertWithMessage(boolean condition, String message) {
        if (!condition) {
            logger.info("Assertion failed: " + message);
        }
    }

    public static double getEuclideanDistance(Position a, Position b) {
        var diff = a.subtract(b);
        return Math.sqrt(diff.getX() * diff.getX() + diff.getY() * diff.getY());
    }

}


