package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.agent.ImperionGameNode;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.core.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;

import java.util.*;

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
        Comparator<ImperionGameNode> winComparator = Comparator.comparingInt(t -> t.getWinsForPlayer(agent.getPlayerId()));

        // Move comparator: first compares visits, then (if visits are the same) wins, and finally (if wins are also the same) the game-specific metric
        Comparator<ImperionGameNode> moveComparator = visitComparator.thenComparing(winComparator).thenComparing(gameSpecificComparator);

        treeMoveComparator = (t1, t2) -> moveComparator.compare(t1.getNode(), t2.getNode());

    }

    // Calculates the upper confidence bound (UCB) from mcts node
    private double upperConfidenceBound(Tree<ImperionGameNode> tree, double c) {
        double w = tree.getNode().getWinsForPlayer(agent.getPlayerId());
        double n = Math.max(tree.getNode().getVisits(), 1);
        double N = n;
        if (!tree.isRoot()) {
            N = tree.getParent().getNode().getVisits();
        }

        return (w / n) + c * Math.sqrt(Math.log(N) / n);
    }

    public Tree<ImperionGameNode> selection(Tree<ImperionGameNode> tree) {
        while (!tree.isLeaf()){
            var bestChild = Collections.max(tree.getChildren(), selectionComparator);

            // if the node has unexplored actions compare it to the best child
            if(tree.getNode().hasUnexploredActions()){
                // if it has a better heuristic value keep selecting the tree even though it is no leaf
                if(selectionComparator.compare(tree, bestChild) >= 0) break;
            }

            tree = bestChild;
        }

        return tree;
    }

    public Tree<ImperionGameNode> expansion(Tree<ImperionGameNode> bestNode) {
        var gameState = bestNode.getNode();

        var actionsToExpand = new HashSet<EmpireEvent>();

        if(bestNode.isRoot()){
            // If root node, then expand all actions
            while (gameState.hasUnexploredActions()){
                actionsToExpand.add(gameState.popUnexploredAction());
            }
            actionsToExpand.add(null);
        }else{
            // If leaf explore action of doing nothing first
            if(bestNode.isLeaf())
                actionsToExpand.add(null);
            else
                // otherwise, choose random unexplored action
                actionsToExpand.add(gameState.popUnexploredAction());
        }

        expandActions(bestNode,actionsToExpand);

        // bestLeaf should always have the action of doing nothing
        agent.assertWithMessage(!bestNode.getChildren().isEmpty(), "bestLeaf has no child action, but should always have one");

        return Util.selectRandom(bestNode.getChildren());

    }

    /**
     * Expands bestChild by all actions if actions are valid or null
     */
    private void expandActions(Tree<ImperionGameNode> bestChild, Set<EmpireEvent> actionsToExpand) {
        agent.assertWithMessage(!actionsToExpand.isEmpty(), "actionsToExpand is empty, but should always have atleast one action (null action) has no child action");

        var gameState = bestChild.getNode();
        var playerIdOnTurn = gameState.getNextPlayerId();

        for (EmpireEvent action : actionsToExpand) {
            // Expand the tree by copying the game state and advancing it by the simulation pace
            var nextGameState = (Empire) gameState.getGameState().copy();

            // If action is not null and valid, do schedule event
            if(action != null && nextGameState.isValidAction(action, playerIdOnTurn)){
                GameActionEvent<EmpireEvent> actionEvent = new GameActionEvent<>(playerIdOnTurn, action, nextGameState.getGameClock().getGameTimeMs() + 1);
                nextGameState.scheduleActionEvent(actionEvent);
            }

            // Advance the game
            try {
                nextGameState.advance(DEFAULT_SIMULATION_PACE_MS);
            } catch (Exception e) {
                // agent.getLogger().debug("Action: " + action);
                // agent.getLogger().debug(e);
                // Actions could not be executed successful
                continue;
            }

            // If actions were successfully executed, add to leaf
            bestChild.add(new DoubleLinkedTree<>(new ImperionGameNode(nextGameState, (playerIdOnTurn + 1) % nextGameState.getNumberOfPlayers(), action)));
        }

    }

    /**
     * Simulates a game given a certain game state and determines winner
     */
    public boolean[] simulation(Tree<ImperionGameNode> tree, long nextDecisionTime) {
        var gameState = tree.getNode();
        var game = (Empire) gameState.getGameState().copy();
        var playerToTurn = gameState.getNextPlayerId();
        var depth = 0;
        try {
            while (!game.isGameOver() && depth++ <= DEFAULT_SIMULATION_DEPTH && System.currentTimeMillis() < nextDecisionTime) {

                var possibleActions = gameState.getPossiblePrunedActions(playerToTurn);

                // Always add possibility of doing nothing
                possibleActions.add(null);

                var action = Util.selectRandom(possibleActions);

                // If action is not null and valid, do schedule event
                if(action != null && game.isValidAction(action, playerToTurn)){
                    game.scheduleActionEvent(new GameActionEvent<>(playerToTurn, action, game.getGameClock().getGameTimeMs() + 1));
                };

                // Advance the game
                game.advance(DEFAULT_SIMULATION_PACE_MS);

                playerToTurn = (playerToTurn + 1) % game.getNumberOfPlayers();
            }
        } catch (Exception e) {
            // If we have partial information (Fog of War) the result of some actions might be ambiguous leading in an ActionException
            // Stop the simulation there
            // agent.getLogger().info("simulation reached invalid game state (partial information)");
        }

        return determineWinner(game);
    }

    public void backPropagation(Tree<ImperionGameNode> tree, boolean[] winners) {
        // Go back up in the tree and increment the visits of evey node as well as the wins of the players nodes
        do {
            var node = tree.getNode();
            node.incrementVisits();

            for (var playerId = 0; playerId < tree.getNode().getGameState().getNumberOfPlayers(); playerId++) {
                if (winners[playerId])
                    node.incrementWinsForPlayer(playerId);
            }
            tree = tree.getParent();
        } while (tree != null);
    }

    private boolean[] determineWinner(Empire game) {
        var winners = new boolean[game.getNumberOfPlayers()];
        if (game.isGameOver()) {
            double[] evaluation = game.getGameUtilityValue();
            for (var pid = 0; pid < game.getNumberOfPlayers(); pid++)
                if (evaluation[pid] == 1D)
                    winners[pid] = true;
        } else {
            winners[agent.getPlayerId()] = isWinner(game, agent.getPlayerId());
        }
        return winners;
    }

    /**
     * A game state is considered a winner, when it occupies more than half of the cities visible
     */
    private boolean isWinner(Empire game, int playerId) {
        // Visible Cities
        var visibleCities = game.getCitiesByPosition().values();
        var playerCities = visibleCities.stream().filter(city -> city.getPlayerId() == playerId).count();

        // Avoid dividing by 0
        if(playerCities == 0) return false;

        long occupation_ratio = playerCities / visibleCities.size();

        double winningThreshold = 0.5;

        return occupation_ratio > winningThreshold;

    }

    public Comparator<Tree<ImperionGameNode>> getTreeMoveComparator() {
        return treeMoveComparator;
    }
}
