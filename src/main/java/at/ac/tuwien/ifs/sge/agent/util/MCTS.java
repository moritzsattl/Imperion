package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.agent.ImperionGameNode;
import at.ac.tuwien.ifs.sge.agent.util.MacroAction.DoNothingMacroAction;
import at.ac.tuwien.ifs.sge.agent.util.MacroAction.MacroAction;
import at.ac.tuwien.ifs.sge.agent.util.MacroAction.WaitEvent;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.core.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.*;
import java.util.stream.IntStream;

public class MCTS {

    private Imperion agent;
    private Comparator<Tree<ImperionGameNode>> selectionComparator;
    private Comparator<Tree<ImperionGameNode>> treeMoveComparator;

    private static final int DEFAULT_SIMULATION_PACE_MS = 1000;
    private static final int DEFAULT_SIMULATION_DEPTH = 20;

    public MCTS(Imperion agent) {

        this.agent = agent;

        // Compares two nodes based on their UCB values
        Comparator<Tree<ImperionGameNode>> gameMcTreeUCTComparator = Comparator
                .comparingDouble(t -> upperConfidenceBound(t, Math.sqrt(2)));

        // Compares two game nodes based on a game-specific metric
        Comparator<ImperionGameNode> gameSpecificComparator = (n1, n2) -> agent.getGameComperator().compare(n1.getGameState(), n2.getGameState());

        // Selection comparator: first compares UCB, then (if UCB is the same) the game-specific metric
        selectionComparator = gameMcTreeUCTComparator.thenComparing((t1, t2) -> gameSpecificComparator.compare(t1.getNode(), t2.getNode()));

        // Simple comparison of visits
        Comparator<ImperionGameNode> visitComparator = Comparator.comparingInt(ImperionGameNode::getVisits);

        // Simple comparison of wins
        Comparator<ImperionGameNode> winComparator = Comparator.comparingDouble(t -> t.getEvaluationForPlayer(agent.getPlayerId()));

        // Move comparator: first compares visits, then (if visits are the same) wins, and finally (if wins are also the same) the game-specific metric
        Comparator<ImperionGameNode> moveComparator = visitComparator.thenComparing(winComparator).thenComparing(gameSpecificComparator);

        treeMoveComparator = (t1, t2) -> moveComparator.compare(t1.getNode(), t2.getNode());

    }

    // Calculates the upper confidence bound (UCB) from mcts node
    private double upperConfidenceBound(Tree<ImperionGameNode> tree, double c) {
        double w = tree.getNode().getEvaluationForPlayer(agent.getPlayerId());
        double n = Math.max(tree.getNode().getVisits(), 1);
        double N = n;
        if (!tree.isRoot()) {
            N = tree.getParent().getNode().getVisits();
        }

        return (w / n) + c * Math.sqrt(Math.log(N) / n);
    }

    public Tree<ImperionGameNode> selection(Tree<ImperionGameNode> tree) {
        while (!tree.isLeaf()) {
            var bestChild = Collections.max(tree.getChildren(), selectionComparator);

            // if the node has unexplored actions compare it to the best child
            if (tree.getNode().hasUnexploredActions()) {
                // if it has a better heuristic value keep selecting the tree even though it is no leaf
                if (selectionComparator.compare(tree, bestChild) >= 0) break;
            }

            tree = bestChild;
        }

        return tree;
    }

    public Tree<ImperionGameNode> expansion(Tree<ImperionGameNode> bestNode) {
        var gameState = bestNode.getNode();

        var actionsToExpand = new HashSet<MacroAction>();
        if (bestNode.isRoot()) {
            // If root node, then expand all actions

            while (gameState.hasUnexploredActions()) {
                actionsToExpand.add(gameState.popUnexploredAction());
            }
            actionsToExpand.add(new DoNothingMacroAction());

            Imperion.logger.info("Actions to expand in root: " + actionsToExpand);
        } else {
            // If leaf explore action of doing nothing first
            if (bestNode.isLeaf())
                actionsToExpand.add(new DoNothingMacroAction());
            else
                // otherwise, choose random unexplored action
                actionsToExpand.add(gameState.popUnexploredAction());
        }

        expandActions(bestNode, actionsToExpand);

        // bestLeaf should always have the action of doing nothing
        Imperion.logAssertWithMessage(!bestNode.getChildren().isEmpty(), "bestLeaf has no child action, but should always have one");

        return Util.selectRandom(bestNode.getChildren());
    }

    /**
     * Expands bestChild by all actions if actions are valid or null
     */
    private void expandActions(Tree<ImperionGameNode> bestChild, Set<MacroAction> actionsToExpand) {
        Imperion.logAssertWithMessage(!actionsToExpand.isEmpty(), "actionsToExpand is empty, but should always have atleast one action (null action) has no child action");
        Imperion.logger.trace("Start ExpandActions");
        Imperion.logger.trace("Actions to expand: " + actionsToExpand);
        Imperion.logger.trace("For player: " + bestChild.getNode().getNextPlayerId());

        var gameState = bestChild.getNode();
        var playerOnTurn = gameState.getNextPlayerId();

        for (MacroAction macroAction : actionsToExpand) {
            // Expand the tree by copying the game state and advancing it by the simulation pace
            var game = (Empire) gameState.getGameState().copy();
            var commandQueues = gameState.copyCommandQueues();

            // Add macroAction to commandQueue
            commandQueues[playerOnTurn].addCommand(macroAction);

            List<EmpireEvent> executedActions = null;

            try {
                executedActions = scheduleAndAdvance(commandQueues, game, playerOnTurn);
            } catch (Exception e) {
                // If we have partial information (Fog of War) the result of some actions might be ambiguous leading in an ActionException
                // Stop the simulation there
                Imperion.logger.trace("simulation reached invalid game state (partial information)");
                continue;
            }

            // If actions were successfully executed, add to leaf
            var expandState = new ImperionGameNode(game, (playerOnTurn + 1) % game.getNumberOfPlayers(), executedActions, commandQueues, macroAction);
            Imperion.logger.trace("Expand state: " + expandState);
            bestChild.add(new DoubleLinkedTree<>(expandState));
        }

        Imperion.logger.trace("End ExpandActions");
    }

    /**
     * Simulates a game given a certain game state and determines winner
     * Each simulation just simulates what would happen if the scheduled commands in the command queue would all be executed
     * In that sense a simulation shows us if a certain macro action will lead to winning or losing node
     */
    public double[] simulation(Tree<ImperionGameNode> tree, long nextDecisionTime) {
        var gameState = tree.getNode();
        var game = (Empire) gameState.getGameState().copy();
        var commandQueues = gameState.copyCommandQueues();
        var playerToTurn = gameState.getNextPlayerId();
        var depth = 0;


        Imperion.logger.debug("Simulate on: " + gameState);
        Imperion.logger.debug(" with " + Arrays.toString(commandQueues));
        try {
            while (!game.isGameOver() && depth++ <= DEFAULT_SIMULATION_DEPTH && System.currentTimeMillis() < nextDecisionTime
                    // Check if command queues are both empty
                    && IntStream.range(0, commandQueues.length).filter(i -> !commandQueues[i].isEmpty()).count() > 0
            ) {
                Imperion.logger.trace("Start inner loop simulation");
                Imperion.logger.trace("Command queue for player " + playerToTurn + Arrays.toString(commandQueues));

                scheduleAndAdvance(commandQueues, game, playerToTurn);

                playerToTurn = (playerToTurn + 1) % game.getNumberOfPlayers();

                Imperion.logger.trace("End inner loop simulation");
            }
        } catch (Exception e) {
            // If we have partial information (Fog of War) the result of some actions might be ambiguous leading in an ActionException
            // Stop the simulation there
            Imperion.logger.trace("simulation reached invalid game state (partial information)");
        }

        return evaluateGameState(game);
    }

    /**
     * Tries to schedule the next action in queue for each unit and city and advances game
     */
    private static List<EmpireEvent> scheduleAndAdvance(CommandQueue[] commandQueues, Empire game, int playerToTurn) throws ActionException {
        Imperion.logger.trace("Start scheduleAndAdvance");

        var scheduledEvents = new ArrayList<EmpireEvent>();

        if(commandQueues[playerToTurn].doNothing) commandQueues[playerToTurn].doNothing = false;
        else{
            // Try to schedule the next action in queue for each unit and city
            for (var command : commandQueues[playerToTurn].getUnitCommandQueue().entrySet()) {
                Imperion.logger.trace("Next command for Unit with ID: " + command.getKey());
                // Only schedule next command if unit is not busy
                if(game.getUnit(command.getKey()).isIdle()) schedule(game, playerToTurn, command, scheduledEvents); else Imperion.logger.trace("Not Idle");
            }
            for (var command : commandQueues[playerToTurn].getCityCommandQueue().entrySet()) {
                Imperion.logger.trace("Next command for City with Position: " + command.getKey());
                schedule(game, playerToTurn, command, scheduledEvents);
            }
        }

        // Advance the game
        game.advance(DEFAULT_SIMULATION_PACE_MS);

        //Imperion.logger.debug("Command queue after: " + Arrays.toString(commandQueues));
        Imperion.logger.trace("End scheduleAndAdvance");

        return !scheduledEvents.isEmpty() ? scheduledEvents : null;
    }

    /**
     * Returns true if command could be scheduled, otherwise false
     */
    private static boolean schedule(Empire game, int playerToTurn, Map.Entry<?, ArrayDeque<EmpireEvent>> command, ArrayList<EmpireEvent> scheduledEvents) {
        Imperion.logger.trace("Start schedule");

        // Just continue if command queue is empty for unit
        if(command.getValue().isEmpty()){ Imperion.logger.trace("Queue empty"); return true; }

        var action = command.getValue().poll();
        Imperion.logger.trace("Schedule/Poll action: " + action);

        // In case of a wait event just return
        if(action instanceof WaitEvent) return true;

        if (action != null)
            if (!game.isValidAction(action, playerToTurn)){
                Imperion.logger.trace("Not valid action");

                if(action instanceof MovementStartOrder mso){
                    var dest = mso.getDestination();
                    var isHeldByPlayerId = game.getBoard().getEmpireTiles()[dest.getY()][dest.getX()].getPlayerId();

                    // If movement was not possible, because of ally unit on destination, add order back to queue and try in next iteration
                    if(isHeldByPlayerId == playerToTurn) command.getValue().addFirst(action);
                }

                // TODO: What happens if movement is not possible because of enemy?
                // Maybe schedule AttackAction

                // If action is not null and not valid, do nothing
                return false;
            } else {
                // If action is not null and valid, do schedule event
                Imperion.logger.trace("Valid action");
                game.scheduleActionEvent(new GameActionEvent<>(playerToTurn, action, game.getGameClock().getGameTimeMs() + 1));

                scheduledEvents.add(action);
            }

        Imperion.logger.trace("End schedule");
        return true;
    }

    public void backPropagation(Tree<ImperionGameNode> tree, double[] evaluations) {
        // Go back up in the tree and increment the visits of evey node as well as the evaluation of the players nodes
        do {
            var node = tree.getNode();
            node.incrementVisits();

            for (var playerId = 0; playerId < tree.getNode().getGameState().getNumberOfPlayers(); playerId++) {
                node.incrementEvaluation(evaluations[playerId], playerId);
            }
            tree = tree.getParent();
        } while (tree != null);
    }

    /**
     * Evaluates the advantage of each player in the current game ranging from 0 (losing) to 1 (winning)
     * Due to fog of war we can only evaluate our current game state to our worst/best possible game state.
     */
    private double[] evaluateGameState(Empire game) {
        var evaluation = new double[game.getNumberOfPlayers()];
        if (game.isGameOver()) {
            double[] gameUtilityValue = game.getGameUtilityValue();
            for (var pid = 0; pid < game.getNumberOfPlayers(); pid++)
                if (gameUtilityValue[pid] == 1D)
                    gameUtilityValue[pid] = 1.0;
        } else {
            for (var pid = 0; pid < game.getNumberOfPlayers(); pid++) {
                evaluation[pid] = Heuristics.determineNormalizedHeuristicValue(game, agent.getPlayerId());
            }
        }
        return evaluation;
    }

    public Comparator<Tree<ImperionGameNode>> getTreeMoveComparator() {
        return treeMoveComparator;
    }
}
