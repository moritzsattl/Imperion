package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.Game;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.core.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitState;

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

    private Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues;
    private Map<EmpireUnit,Deque<Command<A>>> simulatedUnitCommandQueues;

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

        this.unitCommandQueues = new HashMap<>();
        this.simulatedUnitCommandQueues = new HashMap<>();

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
        onActionRejection(unitCommandQueues,action);
    }

    private void onActionRejection(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues, Object action){
        // Try to execute the old action, with new information about terrain
        if(action instanceof MovementStartOrder){
            MovementStartOrder movementStartOrder = (MovementStartOrder) action;
            EmpireUnit unit = ((Empire) game).getUnit(movementStartOrder.getUnitId());
            Queue<Command<A>> unitCommandQueue = unitCommandQueues.get(unit);
            Command commandWhichWasRejected = unitCommandQueue.peek();

            // If null then there are no more commands in queue
            if(commandWhichWasRejected != null){
                // Try executing command again
                log.info("Trying to execute command again");
                MacroAction<A> macroAction = commandWhichWasRejected.getMacroAction();
                if(macroAction instanceof MoveAction<A>){
                    MoveAction<A> moveAction = (MoveAction<A>) commandWhichWasRejected.getMacroAction();
                    GameStateNode<A> advancedGameState = new GameStateNode<>(game.copy(),null);
                    overwriteFirstCommandInCommandQueue(unitCommandQueues,new MoveAction<>(advancedGameState,unit,moveAction.getDestination(),playerId,log,false));
                }

            }

        }
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
        var gameState = tree.getNode();

        Map<Integer, MacroActionType> actionsTaken = new HashMap<>();
        Tree<GameStateNode<A>> expandedTree = null;

        // Determinized Game
        var determinizedGame = determinize((Empire) game.copy());
        gameState.setGame((RealTimeGame<A, ?>) determinizedGame);

        int EXPAND_NODES_COUNT = 1;

        var possibleActions = gameState.getAllPossibleMacroActionsByAllPlayer(simulatedUnitCommandQueues);
        for (int i = 1; i <= EXPAND_NODES_COUNT; i++) {

            // schedule action events for all players
            for (var playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {

                // Pop one action from unexplored actions
                MacroActionType actionType = null;
                if(possibleActions.get(playerId) != null && possibleActions.get(playerId).size() > 0){
                    actionType = Util.selectRandom(possibleActions.get(playerId));
                }

                if(actionType != null){
                    try{
                        //log.info("expand");
                        executeMacroAction(actionType,playerId,gameState,true);
                    }catch (ActionException e){
                        //log.info("[ERROR] ActionException while advancing the game in expansion" + e);
                        continue;
                    }catch (NoSuchElementException e){
                        //log.info("[ERROR] NoSuchElementException while advancing the game in expansion" + e);
                        continue;
                    }
                    //log.info("executeAction was successful");
                    // If actions were successfully executed, by each player
                    actionsTaken.put(playerId,actionType);
                    expandedTree = new DoubleLinkedTree<>(new GameStateNode<A>(gameState.getGame(), actionsTaken));
                }
            }

            if(expandedTree != null){
                tree.add(expandedTree);
            }

            actionsTaken = new HashMap<>();
        }

        if(expandedTree == null){
            return tree;
        }

        return expandedTree;
    }


    private boolean[] simulation(Tree<GameStateNode<A>> tree, long nextDecisionTime) {
        var gameState = tree.getNode();

        var depth = 0;
        DeterminizedEmpireGame determinizedGame = null;
        try {
            while (!gameState.getGame().isGameOver() && depth++ <= DEFAULT_SIMULATION_DEPTH && System.currentTimeMillis() < nextDecisionTime) {

                // Determinized Game
                determinizedGame = determinize((Empire) gameState.getGame().copy());
                gameState.setGame((RealTimeGame<A, ?>) determinizedGame);

                // Apply random action for each player and advance game
                for (var playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
                    var possibleActions = gameState.getAllPossibleMacroActionsByAllPlayer(simulatedUnitCommandQueues);
                    if (possibleActions.get(playerId) != null && possibleActions.get(playerId).size() > 0) {
                        var nextAction = Util.selectRandom(possibleActions.get(playerId));
                        //log.info("simulation");
                        try{
                            executeMacroAction(nextAction,playerId,gameState,true);
                        }catch (Exception e){
                            log.info("simulation reached invalid game state (partial information)");
                        }

                    }
                }

            }
        } catch (Exception e) {
            log.printStackTrace(e);
        }

        return determineWinner(game);
    }

    private DeterminizedEmpireGame determinize(Empire game) {
        var determinizedEmpireGame = new DeterminizedEmpireGame(game,getKnownPositions(),new HashMap<>());

        return determinizedEmpireGame;
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


    private void executeMacroAction(MacroActionType actionType, int playerId, GameStateNode<A> gameState, boolean simulate) throws ActionException, NoSuchElementException {
        DeterminizedEmpireGame determinizedGame = (DeterminizedEmpireGame) gameState.getGame();

        MacroAction<A> macroAction = new MacroActionFactory().createMacroAction(actionType, gameState, playerId, log, simulate);

        if(macroAction instanceof ExplorationMacroAction<A>){

            MoveAction<A> moveAction = ((ExplorationMacroAction<A>) macroAction).generateExecutableAction();
            if(!simulate){
                //determinize((Empire) game.copy());
                log.info(moveAction.getUnit() + " from " + moveAction.getUnit().getPosition() + " to " + moveAction.getDestination());
            }

            if(simulate){
                addToCommandQueue(simulatedUnitCommandQueues,moveAction);
                Queue<A> simulatedActions = simulateNextCommands(determinizedGame,playerId);
                // Repeat until all actions were simulated
                log.info("Simulated actions in this simulation");
                while(simulatedActions.size() > 0){
                    //for (var actions:
                    //     simulatedActions) {
                    //    log.info(actions);
                    //}
                    simulatedActions = simulateNextCommands(determinizedGame,playerId);
                }
            }else{
                addToCommandQueue(unitCommandQueues,moveAction);
            }

        }

    }

    private void addToCommandQueue(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues, MacroAction<A> macroAction) {
        Command<A> command = new Command<>(macroAction, macroAction.getResponsibleActions());
        if(macroAction instanceof MoveAction<A>){
            MoveAction moveAction = (MoveAction<A>) macroAction;
            moveAction.getUnit().setState(EmpireUnitState.Moving);

            if(unitCommandQueues.containsKey(moveAction.getUnit())){
                unitCommandQueues.get(moveAction.getUnit()).add(command);
            }else{
                Deque<Command<A>> queue = new ArrayDeque<>();
                queue.add(command);
                unitCommandQueues.put(moveAction.getUnit(),queue);
            }
        }


    }

    private void overwriteFirstCommandInCommandQueue(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues,MacroAction<A> macroAction) {
        Command<A> command = new Command<>(macroAction, macroAction.getResponsibleActions());
        if(macroAction instanceof MoveAction<A>){
            MoveAction moveAction = (MoveAction<A>) macroAction;
            moveAction.getUnit().setState(EmpireUnitState.Moving);
            if(unitCommandQueues.containsKey(moveAction.getUnit())){
                unitCommandQueues.get(moveAction.getUnit()).pollFirst();
                unitCommandQueues.get(moveAction.getUnit()).addFirst(command);
            }else{
                Deque<Command<A>> queue = new ArrayDeque<>();
                queue.add(command);
                unitCommandQueues.put(moveAction.getUnit(),queue);
            }
        }

    }

    private Queue<A> simulateNextCommands(Empire game, int playerId) {
        Queue<A> simulatedCommands = new ArrayDeque<>();
        for (var unitId : simulatedUnitCommandQueues.keySet()) {
            Queue<Command<A>> queue = simulatedUnitCommandQueues.get(unitId);

            if (!queue.isEmpty()) {

                Command<A> command = queue.peek();

                // No more actions in this command, continue with the next one
                if(command.getActions() == null || command.getActions().isEmpty()){
                    queue.poll();
                    continue;
                }

                EmpireEvent action = command.getActions().poll();

                if(action != null){

                    //TODO: Add vision to tiles which were discovered in simulation
                    MovementStartOrder order = ((MovementStartOrder)action);
                    Position pos = order.getDestination();
                    log.info(game.getBoard().getEmpireTiles()[pos.getY()][pos.getY()]);

                    try {
                        game.getBoard().addVision(pos,playerId,order.getUnitId());
                        log.info(game.getBoard().getEmpireTiles()[pos.getY()][pos.getY()]);
                    } catch (EmpireMapException e) {
                        log.info("Adding vision was not possible");
                        log.info(e);
                    }

                    // Scheduling event
                    if (game.isValidAction(action,playerId)) {
                        game.scheduleActionEvent(new GameActionEvent<EmpireEvent>(playerId, action, game.getGameClock().getGameTimeMs() + 1));
                    }

                    // Advancing the game
                    try{
                        game.advance(DEFAULT_SIMULATION_PACE_MS);
                    }catch (ActionException e){
                        log.info("[ERROR] while advancing game");
                        // If advancing failed, because of mountains in the way for example, calc new path to destination position
                        onActionRejection(simulatedUnitCommandQueues,action);
                    }

                    simulatedCommands.add((A) action);
                }
            }
        }

        return simulatedCommands;
    }

    private Queue<A> executeNextCommands() {
        Queue<A> executedCommands = new ArrayDeque<>();

        for (var unitId : unitCommandQueues.keySet()) {
            Queue<Command<A>> queue = unitCommandQueues.get(unitId);

            log.info("Commands in Queue for " + unitId);
            for(Command command : queue) {
                log.info(command);
            }

            if (!queue.isEmpty()) {
                Command<A> command = queue.peek();

                // No more actions in this command, continue with the next one
                if(command.getActions() == null || command.getActions().isEmpty()){
                    queue.poll();
                    continue;
                }

                A action = (A) command.getActions().poll();

                log.info("Next action for " + unitId + "\n" + action);

                if(action != null){
                    executedCommands.add(action);
                    sendAction(action,System.currentTimeMillis() + 50);
                }
            }
        }

        return executedCommands;
    }

    public void play() {
        log.info("start play()");

        Queue<A> lastExecutedCommands = null;
        MacroActionType lastDeterminedActionType = null;

        while (true) {
            try {
                // Copy the game state and apply the last determined actions, since those actions were not yet accepted and sent back
                // from the engine server at this point in time
                var gameState = game.copy();


                if(lastExecutedCommands != null){
                    for (var action : lastExecutedCommands) {
                        if(action != null && gameState.isValidAction(action, playerId)){
                            gameState.scheduleActionEvent(new GameActionEvent<>(playerId, action, gameState.getGameClock().getGameTimeMs() + 1));
                        }
                    }
                }

                // Advance the game state in time by the decision pace since this is the point in time that the next best action will be sent
                gameState.advance(DEFAULT_DECISION_PACE_MS + 51);

                // Create a new tree with the game state as root
                var advancedGameState = new DoubleLinkedTree<>(new GameStateNode<>(gameState, null));


                var iterations = 0;
                var now = System.currentTimeMillis();
                var timeOfNextDecision = now + DEFAULT_DECISION_PACE_MS;

                //log.info("Before MCTS: \n" + printTree(advancedGameState,"",0));

                while (System.currentTimeMillis() < timeOfNextDecision) {
                    //lastDeterminedActionType = Util.selectRandom(actions);
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


                //log.info("After MCTS: \n" + printTree(advancedGameState,"",0));

                MacroActionType action = null;
                if (advancedGameState.isLeaf()) {
                    log.info("Could not find a move! Doing nothing...");
                } else {
                    // Select the most visited node from the root node and send it to the engine server
                    log._info_();
                    log.info("Iterations: " + iterations);
                    for (var child : advancedGameState.getChildren())
                        log.info(child.getNode().getResponsibleMacroActionForPlayer(playerId) + " visits:" + child.getNode().getVisits() + " wins:" + child.getNode().getWinsForPlayer(playerId));
                    var mostVisitedNode = Collections.max(advancedGameState.getChildren(), treeMoveComparator).getNode();

                    action = mostVisitedNode.getResponsibleMacroActionForPlayer(playerId);
                    log.info("Determined next action: " + action);
                }

                lastDeterminedActionType = action;


                if(lastDeterminedActionType != null){
                    //log.info("execute");
                    try{
                        executeMacroAction(lastDeterminedActionType,playerId,advancedGameState.getNode(),false);
                    }catch (NoSuchElementException e){
                        log.info("[ERROR] while excuting action");
                    }
                }

                // Executes for each unit there next actions in their own unit action command queue
                lastExecutedCommands = executeNextCommands();

            } catch (ActionException e) {
                // Action weren't yet validated, try again
                log.info(e);
            } catch (OutOfMemoryError e) {
                log.info("Out of Memory");
                break;
            } catch (Exception e){
                log.info(e);
                break;
            }
        }
        log.info("stopped playing");
    }
    public Set<Position> getKnownPositions(){
        // Get valid and visible locations the unit can move to using the FloodFill Algorithm
        Set<Position> validLocations = new HashSet<>();

        // Get empire map and empire tiles
        EmpireMap map = (EmpireMap) game.getBoard();
        EmpireTerrain[][] empireTiles = map.getEmpireTiles();

        // Initialize queue for positions to be checked and set to store checked positions
        Queue<Position> positionsToCheck = new LinkedList<>();
        Set<Position> checkedPositions = new HashSet<>();

        // Add starting position to the queue
        positionsToCheck.add(((Empire)game).getUnitsByPlayer(playerId).get(0).getPosition());

        while (!positionsToCheck.isEmpty()) {
            Position current = positionsToCheck.poll();
            int x = current.getX();
            int y = current.getY();

            // If the current position has already been checked, skip it
            if (checkedPositions.contains(current) || !map.isInside(x,y) || empireTiles[y][x] == null) {
                continue;
            }

            // Mark the current position as checked
            checkedPositions.add(current);

            try {
                // If the tile is inside, not null and movement is possible, add it to valid locations
                var tile = map.getTile(x,y);
                if (tile != null && tile.getOccupants() != null && map.isMovementPossible(x, y, playerId)) {
                    validLocations.add(new Position(x, y));
                }

                // Check the tiles to the left, right, above, and below the current tile
                if (x > 0) positionsToCheck.add(new Position(x - 1, y));
                if (x < empireTiles.length - 1) positionsToCheck.add(new Position(x + 1, y));
                if (y > 0) positionsToCheck.add(new Position(x, y - 1));
                if (y < empireTiles[0].length - 1) positionsToCheck.add(new Position(x, y + 1));
            } catch (Exception e) {
                //log.info(e);
            }
        }

        if (validLocations.isEmpty()) {
            // No move action possible if no valid locations are available.
            throw new NoSuchElementException();
        }

        return validLocations;
    }

}


