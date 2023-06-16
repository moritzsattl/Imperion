package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.agent.determinizedmap.DeterminizedEmpireGame;
import at.ac.tuwien.ifs.sge.agent.macroactions.BuildAction;
import at.ac.tuwien.ifs.sge.agent.macroactions.MacroAction;
import at.ac.tuwien.ifs.sge.agent.macroactions.MacroActionType;
import at.ac.tuwien.ifs.sge.agent.macroactions.MoveAction;
import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.core.engine.communication.events.GameActionEvent;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;
import java.util.concurrent.Future;

public class Imperion extends AbstractRealTimeGameAgent<Empire, EmpireEvent> {
    private Future<?> thread;

    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var agent = new Imperion(playerId, playerName, 0);
        agent.start();
    }

    private static final double DEFAULT_EXPLOITATION_CONSTANT = Math.sqrt(2);
    private static final int DEFAULT_SIMULATION_PACE_MS = 1250;
    private static final int DEFAULT_SIMULATION_DEPTH = 20;
    private static final int DEFAULT_DECISION_PACE_MS = 1250;

    private static final int DEFAULT_TIME_FOR_MACRO_ACTION_CALCULATIONS = 250;


    private final Comparator<Tree<GameStateNode<EmpireEvent>>> selectionComparator;

    private final Comparator<Tree<GameStateNode<EmpireEvent>>> treeMoveComparator;

    private final Map<EmpireUnit,Deque<Command<EmpireEvent>>> unitCommandQueues;
    private final Map<String,Deque<Command<EmpireEvent>>> cityCommandQueue;

    private final Map<String, Integer> citiesToWhichUnitTypeProducing;

    // TODO: Add simulatedCitiesToWhichUnitTypeProducing

    private final Map<EmpireUnit,Deque<Command<EmpireEvent>>> simulatedUnitCommandQueues;
    private final Map<String,Deque<Command<EmpireEvent>>> simulatedCityCommandQueues;

    private int turnsPassed = 0;

    public Imperion(int playerId, String playerName, int logLevel) {
        super(Empire.class,playerId, playerName, logLevel);


        // Compares two nodes based on their UCB values
        Comparator<Tree<GameStateNode<EmpireEvent>>> gameMcTreeUCTComparator = Comparator
                .comparingDouble(t -> upperConfidenceBound(t, DEFAULT_EXPLOITATION_CONSTANT));

        // Compares two game nodes based on a game-specific metric
        Comparator<GameStateNode<EmpireEvent>> gameSpecificComparator = (n1, n2) -> gameComparator.compare(n1.getGame(), n2.getGame());

        // Tree version of the game-specific metric comparator
        Comparator<Tree<GameStateNode<EmpireEvent>>> treeGameSpecificComparator = (t1, t2) -> gameSpecificComparator.compare(t1.getNode(), t2.getNode());

        // Selection comparator: first compares UCB, then (if UCB is the same) the game-specific metric
        selectionComparator = gameMcTreeUCTComparator.thenComparing(treeGameSpecificComparator);

        // Simple comparison of visits
        Comparator<GameStateNode<EmpireEvent>> visitComparator = Comparator.comparingInt(GameStateNode::getVisits);

        // Simple comparison of wins
        Comparator<GameStateNode<EmpireEvent>> winComparator = Comparator.comparingInt(t -> t.getWinsForPlayer(playerId));

        // Move comparator: first compares visits, then (if visits are the same) wins, and finally (if wins are also the same) the game-specific metric
        Comparator<GameStateNode<EmpireEvent>> moveComparator = visitComparator.thenComparing(winComparator).thenComparing(gameSpecificComparator);

        // Tree version of the move comparator
        treeMoveComparator = (t1, t2) -> moveComparator.compare(t1.getNode(), t2.getNode());

        this.unitCommandQueues = new HashMap<>();
        this.simulatedUnitCommandQueues = new HashMap<>();
        this.cityCommandQueue = new HashMap<>();
        this.simulatedCityCommandQueues = new HashMap<>();
        this.citiesToWhichUnitTypeProducing = new HashMap<>();
    }

    @Override
    protected int getMinimumNumberOfThreads() {
        return super.getMinimumNumberOfThreads() + 1;
    }

    @Override
    protected void onGameUpdate(EmpireEvent action, ActionResult result) {

    }

    @Override
    protected void onActionRejected(EmpireEvent action) {
        log._info_();
        log.info("Some actions were rejected");
        try {
            onActionRejection(unitCommandQueues,action);
        } catch (Exception e) {
            log.info(e);
        }
    }

    private void onActionRejection(Map<EmpireUnit, Deque<Command<EmpireEvent>>> unitCommandQueues, Object action) throws Exception {
        if(action instanceof ProductionStartOrder productionStartOrder){
            log.info(productionStartOrder + " failed");
        }

        // Try to execute the old action, with new information about terrain
        if(action instanceof MovementStartOrder){
            MovementStartOrder movementStartOrder = (MovementStartOrder) action;
            EmpireUnit unit = ((Empire) game).getUnit(movementStartOrder.getUnitId());
            log.info(action + " failed");
            Queue<Command<EmpireEvent>> unitCommandQueue = unitCommandQueues.get(unit);
            if(unitCommandQueue == null){
                throw new Exception("unitCommandQueue for " + unit +  " was empty, action could not reinited");
            }
            Command<EmpireEvent> commandWhichWasRejected = unitCommandQueue.peek();

            // If null then there are no more commands in queue
            if(commandWhichWasRejected != null){
                // Try executing command again (only if movement to tile is possible)
                log.info("Trying to execute command again:");
                MacroAction<EmpireEvent> macroAction = commandWhichWasRejected.getMacroAction();
                if(macroAction instanceof MoveAction<EmpireEvent> moveAction){
                    log.info(moveAction);
                    GameStateNode<EmpireEvent> advancedGameState = new GameStateNode<>((Empire) game.copy(),null);
                    overwriteFirstCommandInCommandQueue(unitCommandQueues,new MoveAction<>(advancedGameState,unit, moveAction.getType(),moveAction.getDestination(),unit.getPlayerId(),log,false,false));
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
    private double upperConfidenceBound(Tree<GameStateNode<EmpireEvent>> tree, double c) {
        double w = tree.getNode().getWinsForPlayer(playerId);
        double n = Math.max(tree.getNode().getVisits(), 1);
        double N = n;
        if (!tree.isRoot()) {
            N = tree.getParent().getNode().getVisits();
        }

        return (w / n) + c * Math.sqrt(Math.log(N) / n);
    }

    private Tree<GameStateNode<EmpireEvent>> selection(Tree<GameStateNode<EmpireEvent>> tree) {
        while (!tree.isLeaf()) {
            // Choose node based on UCB and when tie, use heuristic values from game
            var bestChild = Collections.max(tree.getChildren(), selectionComparator);
            tree = bestChild;
        }
        return tree;
    }


    private Tree<GameStateNode<EmpireEvent>> expansion(Tree<GameStateNode<EmpireEvent>> tree) {
        var gameState = tree.getNode();

        Map<Integer, MacroActionType> actionsTaken = new HashMap<>();
        Tree<GameStateNode<EmpireEvent>> expandedTree = null;

        // Determinized Game
        //var determinizedGame = determinize((Empire) gameState.getGame().copy());
        //gameState.setGame((RealTimeGame<A, ?>) determinizedGame);

        //log.info(((DeterminizedEmpireGame) gameState.getGame()).getDeterminizedMap());

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
                        executeMacroAction(actionType,playerId,gameState,true);
                    }catch (ActionException e){
                        //log.info("[ERROR] ActionException while advancing the game in expansion" + e);
                        continue;
                    } catch (NoSuchElementException e){
                        //log.info("[ERROR] NoSuchElementException while advancing the game in expansion" + e);
                        continue;
                    }
                    //log.info("executeAction was successful");
                    // If actions were successfully executed, by each player
                    actionsTaken.put(playerId,actionType);
                    expandedTree = new EmpireDoubleLinkedTree(new GameStateNode<EmpireEvent>( gameState.getGame(), actionsTaken));
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


    private boolean[] simulation(Tree<GameStateNode<EmpireEvent>> tree, long nextDecisionTime) {
        var gameState = tree.getNode();

        var depth = 0;
        try {
            while (!gameState.getGame().isGameOver() && depth++ <= DEFAULT_SIMULATION_DEPTH && System.currentTimeMillis() < nextDecisionTime) {

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

        //log.info(gameState.getGame().getBoard());

        return determineWinner(game);
    }

    private DeterminizedEmpireGame determinize(Empire game) {
        var determinizedEmpireGame = new DeterminizedEmpireGame(game,getKnownPositions(),new HashMap<>());

        return determinizedEmpireGame;
     }


    private boolean[] determineWinner(Empire game) {
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

    private double[] utilityValue(Empire game) {
        EmpireMap cityControl =  game.getBoard();
        var unitAdvantage = multiplyArrayElements(game.getGameHeuristicValue(), 0.25);
        return null;
    }

    private static double[] multiplyArrayElements(double[] array, double multiplier) {
        for (int i = 0; i < array.length; i++) {
            array[i] *= multiplier;
        }
        return array;
    }

    private void backPropagation(Tree<GameStateNode<EmpireEvent>> tree, boolean[] winners) {
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

    private String printTree(Tree<GameStateNode<EmpireEvent>> tree, String s, int level) {

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


    private void executeMacroAction(MacroActionType actionType, int playerId, GameStateNode<EmpireEvent> gameState, boolean simulate) throws ActionException, NoSuchElementException {
        // For simplicity, don't allow simulation until everything works
        if(!simulate){
            //var determinizedGame = (DeterminizedEmpireGame) gameState.getGame();

            MacroAction<EmpireEvent> macroAction = new MacroActionFactory().createMacroAction(actionType, gameState, playerId, log, simulate);

            Deque<MacroAction<EmpireEvent>> actions = null;
            try{
                if(simulate){
                    actions = macroAction.generateExecutableAction(simulatedUnitCommandQueues);
                }else{
                    actions = macroAction.generateExecutableAction(unitCommandQueues);
                }
            }catch (ExecutableActionFactoryException e){
                log.info(e);
            }

            log.info("Add following actions to Queues");

            log.info(actions);

            // For each type macro action add to command queue
            while (actions != null && !actions.isEmpty()){
                MacroAction<EmpireEvent> action = actions.poll();

                if(simulate){
                    addToCommandQueue(simulatedUnitCommandQueues, simulatedCityCommandQueues,action);
                    Queue<EmpireEvent> simulatedActions = simulateNextCommands(gameState,playerId);
                    // Repeat until all actions were simulated
                    while(simulatedActions.size() > 0){
                        simulatedActions = simulateNextCommands(gameState,playerId);
                    }
                }else{
                    addToCommandQueue(unitCommandQueues, cityCommandQueue,action);
                }
            }
        }
    }

    private void addToCommandQueue(Map<EmpireUnit,Deque<Command<EmpireEvent>>> unitCommandQueues, Map<String,Deque<Command<EmpireEvent>>> cityCommandQueues, MacroAction<EmpireEvent> macroAction) {

        Command<EmpireEvent> command = null;
        try {
            command = new Command<>(macroAction, macroAction.getResponsibleActions(unitCommandQueues));
        } catch (ExecutableActionFactoryException e) {
            log.info(e);
            log.info(new ExecutableActionFactoryException("Command could not be build"));
            return;
        }



        if(macroAction instanceof MoveAction<EmpireEvent> moveAction){
            // Force move action is necessary
            if(moveAction.isForce()){
                overwriteFirstCommandInCommandQueue(unitCommandQueues,moveAction);
            }else{
                if(unitCommandQueues.containsKey(moveAction.getUnit())){
                    unitCommandQueues.get(moveAction.getUnit()).add(command);
                }else{
                    Deque<Command<EmpireEvent>> queue = new ArrayDeque<>();
                    queue.add(command);
                    unitCommandQueues.put(moveAction.getUnit(),queue);
                }
            }
        }
        if(macroAction instanceof BuildAction<EmpireEvent> buildAction){

            if(((Empire) game).getCity(buildAction.getEmpireCity().getPosition()).getState() == EmpireProductionState.Producing){

                // If city is producing
                if(citiesToWhichUnitTypeProducing.containsKey(buildAction.getCityStringName())){
                    // If City production type is unit type from current build action then don't add it to queue
                    if(buildAction.getUnitTypeId() == citiesToWhichUnitTypeProducing.get(buildAction.getCityStringName())){
                        return;
                    }

                    // If next command in queue is for the same action, then don't add another one of it
                    if(!cityCommandQueues.isEmpty()){
                        Command<EmpireEvent> nextCommand = cityCommandQueues.get(buildAction.getCityStringName()).peek();
                        if(nextCommand != null){
                            if(nextCommand.getMacroAction() instanceof BuildAction<EmpireEvent> nextBuildAction){
                                if(nextBuildAction.getUnitTypeId() == buildAction.getUnitTypeId()){
                                    return;
                                }
                            }
                        }

                    }
                }
            }

            if(cityCommandQueues.containsKey(buildAction.getCityStringName())){
                cityCommandQueues.get(buildAction.getCityStringName()).add(command);
            }else{
                Deque<Command<EmpireEvent>> queue = new ArrayDeque<>();
                queue.add(command);
                cityCommandQueues.put(buildAction.getCityStringName(),queue);
            }
        }


    }

    private void overwriteFirstCommandInCommandQueue(Map<EmpireUnit,Deque<Command<EmpireEvent>>> unitCommandQueues,MacroAction<EmpireEvent> macroAction) {
        Command<EmpireEvent> command = null;
        try {
            command = new Command<>(macroAction, macroAction.getResponsibleActions(unitCommandQueues));
        } catch (ExecutableActionFactoryException e) {
            log.info(e);
            log.info(new ExecutableActionFactoryException("Command could not be build"));
            return;
        }

        if(macroAction instanceof MoveAction<EmpireEvent> moveAction){
            if(unitCommandQueues.containsKey(moveAction.getUnit())){
                unitCommandQueues.get(moveAction.getUnit()).pollFirst();
                unitCommandQueues.get(moveAction.getUnit()).addFirst(command);
            }else{
                Deque<Command<EmpireEvent>> queue = new ArrayDeque<>();
                queue.add(command);
                unitCommandQueues.put(moveAction.getUnit(),queue);
            }
        }
    }

    private Queue<EmpireEvent> simulateNextCommands(GameStateNode<EmpireEvent> gameStateNode, int playerId) {
        Empire game = ((Empire)gameStateNode.getGame());
        Queue<EmpireEvent> simulatedCommands = new ArrayDeque<>();
        for (var unitId : simulatedUnitCommandQueues.keySet()) {
            Queue<Command<EmpireEvent>> queue = simulatedUnitCommandQueues.get(unitId);

            if (!queue.isEmpty()) {

                Command<EmpireEvent> command = queue.peek();

                // No more actions in this command, continue with the next one
                if(command.getActions() == null || command.getActions().isEmpty()){
                    queue.poll();
                    continue;
                }

                EmpireEvent action = command.getActions().poll();

                if(action != null){

                    MovementStartOrder order = ((MovementStartOrder)action);
                    Position pos = order.getDestination();

                    // If tiles is unknown, continue
                    if(game.getBoard().getEmpireTiles()[pos.getY()][pos.getY()] == null){
                        continue;
                    }

                    // Apply event

                    if (game.isValidAction(action,playerId)) {
                        GameActionEvent<EmpireEvent> actionEvent = new GameActionEvent<EmpireEvent>(playerId, action, game.getGameClock().getGameTimeMs() + 1);
                        game.scheduleActionEvent(actionEvent);
                    }

                    // Advancing the game
                    try{
                        game.advance(DEFAULT_SIMULATION_PACE_MS);
                    }catch (ActionException e){
                        log.info("[ERROR] while advancing game");
                        // If advancing failed, because of mountains in the way for example, calc new path to destination position
                        //onActionRejection(simulatedUnitCommandQueues,action);
                    }


                    //log.info(game.getBoard());

                    simulatedCommands.add(action);
                }
            }
        }

        // TODO: Add simulation for city commands
        return simulatedCommands;
    }

    private Deque<EmpireEvent> executeNextCommands(GameStateNode<EmpireEvent> gameStateNode, long timeOfNextDecision) {

        //log.info("Executing next command from queue");

        Deque<EmpireEvent> executedCommands = new ArrayDeque<>();

        // Set an offset so the actions are not sent at the same time
        int offset = 50;
        for (var unit : unitCommandQueues.keySet()) {

            if(turnsPassed % 2 == 0 || unit.getUnitTypeName().equals("Scout")){
                Deque<Command<EmpireEvent>> queue = unitCommandQueues.get(unit);

                if (!queue.isEmpty()) {
                    Command<EmpireEvent> command = queue.poll();

                    if(command == null || command.getActions() == null || command.getActions().isEmpty()){
                        continue;
                    }

                    /*
                    boolean pathPossible = true;
                    boolean lastMovePossible = true;
                    if(command.getMacroAction() instanceof MoveAction<EmpireEvent> moveAction){
                        // If some move in the path was found to be not possible calc path again;
                        for (var event : command.getActions()) {
                            if(event instanceof MovementStartOrder movementStartOrder){
                                Position pos = movementStartOrder.getDestination();
                                try {
                                    var tile = gameStateNode.getGame().getBoard().getTile(pos.getX(),pos.getY());
                                    if(tile != null && tile.getOccupants() != null && !gameStateNode.getGame().getBoard().isMovementPossible(pos.getX(),pos.getY(),playerId)){
                                            pathPossible = false;

                                            if(moveAction.getDestination().equals(pos)){
                                                lastMovePossible = false;
                                                log.info("Last move in path not possible, removing from command queue");
                                            }
                                            break;
                                        }
                                    } catch (EmpireMapException e) {
                                    log.info("Move in path not possible");
                                    }
                            }
                        }

                        // If last position, was found to be not possible remove from command list;
                        if(!lastMovePossible){
                            continue;
                        }

                        if(!pathPossible){
                            log.info("Move in path not possible, calculating new path");
                            var currentUnit = gameStateNode.getGame().getUnit(moveAction.getUnit().getId());
                            MoveAction<EmpireEvent> newMoveAction = new MoveAction<>(gameStateNode,currentUnit, moveAction.getType(),moveAction.getDestination(),playerId,log,false,false);
                            try {
                                command = new Command<>(newMoveAction, newMoveAction.getResponsibleActions(unitCommandQueues));
                            } catch (ExecutableActionFactoryException e) {
                                log.info(e);
                                log.info("Could not build command, in executeNextCommands(), removing from command queue");
                                continue;
                            }
                        }
                    }
                    */
                    //TODO: Add check if another ally is also going to the same tile, then take another path


                    EmpireEvent action = command.getActions().poll();


                    if (action == null) {
                        continue;
                    }

                    sendAction(action,System.currentTimeMillis() + offset);
                    offset += 1;
                    executedCommands.add(action);

                    // If command is not empty, and it back to the queue
                    if(!command.getActions().isEmpty()){
                        queue.addFirst(command);
                    }else{
                        // Add empty command back to queue, so unit stays busy until next turn, where the empty command will be removed
                        queue.addFirst(new Command<>());
                    }



                /*
                if(!game.getPossibleActions(playerId).contains(action)){

                    // Calculate new path for MoveAction, if action is invalid for some reason, for example mountains in the way
                    if(command.getMacroAction() instanceof MoveAction<EmpireEvent> moveAction){
                        MovementStartOrder moveOrder = (MovementStartOrder) action;
                        unit = ((Empire) game).getUnit(moveOrder.getUnitId());
                        GameStateNode<EmpireEvent> advancedGameState = new GameStateNode<>(game.copy(),null);

                        MoveAction<EmpireEvent> nextMoveAction = new MoveAction<>(advancedGameState,unit,null,moveAction.getDestination(),playerId,log,false);
                        overwriteFirstCommandInCommandQueue(unitCommandQueues,nextMoveAction);

                        log.info(action + " not possible or unit was too slow to execute, recalculating the path");
                    }else{
                        log.info(action + " not possible, removing command " + command + " from queue queue");
                    }


                }else{
                    executedCommands.add(action);
                    sendAction(action,System.currentTimeMillis() + offset);

                    // If command is not empty, and it back to the queue
                    if(!command.getActions().isEmpty()){
                        queue.addFirst(command);
                    }else{
                        // Add empty command back to queue, so unit stays busy until next turn, where the empty command will be removed
                        queue.addFirst(new Command<>());
                    }

                }
                 */
                }
            }

        }

        for (var city: cityCommandQueue.keySet()) {
            Deque<Command<EmpireEvent>> queue = cityCommandQueue.get(city);

            if (!queue.isEmpty()) {
                Command<EmpireEvent> command = queue.poll();

                if(command == null || command.getActions() == null || command.getActions().isEmpty()){
                    continue;
                }

                EmpireEvent action = command.getActions().poll();

                if (action == null) {
                    continue;
                }

                // Casting should always be possible here
                BuildAction<EmpireEvent> buildAction = (BuildAction<EmpireEvent>) command.getMacroAction();

                if(!gameStateNode.getGame().getPossibleActions(playerId).contains(action)){
                    // If production order not valid yet, wait for it to be valid, maybe something else is producing right now
                    log.info("Action not possible yet, add it back to command queue");
                    command.getActions().addFirst((EmpireEvent) action);
                }else{
                    //log.info(game.getPossibleActions(playerId));
                    sendAction(action,System.currentTimeMillis() + offset);
                    executedCommands.add(action);
                    offset += 1;

                    // Change current unit type production
                    if(action instanceof ProductionStartOrder productionOrder){
                        citiesToWhichUnitTypeProducing.put(buildAction.getCityStringName(), productionOrder.getUnitTypeId());
                    }
                }
                // If command is not empty, and it back to the queue
                if(!command.getActions().isEmpty()){
                    queue.addFirst(command);
                }

            }

        }

        return executedCommands;
    }

    public void play() {
        log.info("start play()");

        Queue<EmpireEvent> lastExecutedCommands = null;
        MacroActionType lastDeterminedActionType = null;

        // Testing A Star
        /*
        while(true){
            log._info_();
            log.info("Next turn");
            var gameState = game.copy();

            // Apply event
            if(lastExecutedCommands != null){
                int c = 1;
                for (var action : lastExecutedCommands) {
                    log.info(action);
                    if(action != null && gameState.isValidAction(action, playerId)){
                        gameState.scheduleActionEvent(new GameActionEvent<>(playerId, action, gameState.getGameClock().getGameTimeMs() + c));
                        c++;
                    }
                }
            }

            // Advance the game state in time by the decision pace since this is the point in time that the next best action will be sent
            try{
                gameState.advance(DEFAULT_DECISION_PACE_MS);
            }catch(ActionException e){
                log.info(e);
                StringBuilder sb = new StringBuilder();
                for (StackTraceElement element : e.getStackTrace()) {
                    sb.append(element.toString());
                    sb.append("\n");
                }
                String stackTrace = sb.toString();
                log.info(stackTrace);
            }


            EmpireUnit unit = ((Empire)game).getUnitsByPlayer(playerId).get(0);

            var now = System.currentTimeMillis();
            var timeOfNextDecision = now + DEFAULT_DECISION_PACE_MS;
            while (System.currentTimeMillis() < timeOfNextDecision) {}


            Position chosen = null;
            if ((!unitCommandQueues.containsKey(unit) || unitCommandQueues.get(unit).isEmpty()) && turnsPassed % 2 == 0){
                log._info_();
                log.info("Path reached, next path...");
                Set<Position> knownPositions = getKnownPositions();

                Stack<Position> unknownPositions = new Stack<>();
                for (int y = 0; y < ((Empire) game).getBoard().getEmpireTiles().length; y++) {
                    for (int x = 0; x < ((Empire) game).getBoard().getEmpireTiles()[y].length; x++) {
                        var pos = new Position(x,y);
                        if(!knownPositions.contains(pos)){
                            unknownPositions.push(pos);
                        }
                    }
                }

                chosen = Util.selectRandom(unknownPositions);
                log.info("Chosen: " + chosen);

                MoveAction<EmpireEvent> moveAction = new MoveAction<>(new GameStateNode<>(gameState,null),unit,MacroActionType.EXPLORATION,chosen,playerId,log,false);

                addToCommandQueue(unitCommandQueues,cityCommandQueue,moveAction);
            }



            // Execute every other DECISION_TIME a command for infantry unit
            if(turnsPassed % 2 == 0){
                //log.info("Possible Actions in this situations");
                //log.info(((Empire) game).getPossibleActions(playerId));
                lastExecutedCommands = executeNextCommands();
            }else{
                lastExecutedCommands = null;
            }

            turnsPassed++;
        }
        */


        while (true) {
            try {
                log._info_();
                log.info("-----------------------Next game turn-------------------------");
                var startTime = System.currentTimeMillis();
                log.info(System.currentTimeMillis());
                // Copy the game state and apply the last determined actions, since those actions were not yet accepted and sent back
                // from the engine server at this point in time
                Empire gameState = (Empire) game.copy();

                // Apply event
                log.info("Advancing the game");
                if(lastExecutedCommands != null && !lastExecutedCommands.isEmpty()){
                    //log.info("Last executed Actions");
                    //log.info(lastExecutedCommands);
                    //log.info("Actually Scheduled Actions");
                    for (var action : lastExecutedCommands) {
                        if(action != null && gameState.isValidAction(action, playerId)) {
                            //log.info(action);
                            gameState.scheduleActionEvent(new GameActionEvent<>(playerId, action, gameState.getGameClock().getGameTimeMs() + 1));
                        }
                        try {
                            gameState.advance(DEFAULT_DECISION_PACE_MS / lastExecutedCommands.size());
                        } catch (ActionException e) {
                            log.info(e);
                        }
                    }
                }else{
                    try {
                        gameState.advance(DEFAULT_DECISION_PACE_MS);
                    } catch (ActionException e) {
                        log.info(e);
                    }
                }

                // Create a new tree with the game state as root
                var advancedGameState = new EmpireDoubleLinkedTree(new GameStateNode<>(gameState, null));

                var iterations = 0;
                var now = System.currentTimeMillis();
                var timeOfNextDecision = now + DEFAULT_DECISION_PACE_MS - DEFAULT_TIME_FOR_MACRO_ACTION_CALCULATIONS;

                //log.info("Before MCTS: \n" + printTree(advancedGameState,"",0));

                while (System.currentTimeMillis() < timeOfNextDecision) {
                    //lastDeterminedActionType = Util.selectRandom(actions);
                    //log.info(iterations);
                    // Select the best from the children according to the upper confidence bound
                    /*
                    var tree = selection(advancedGameState);

                    //log.info("Selected Node: \n" + printTree(tree,"",0));

                    // Expand the selected node by all actions
                    var expandedTree = expansion(tree);

                    //log.info("Selected Node expanded\n" + printTree(tree,"",0));

                    // Simulate until the simulation depth is reached and determine winners
                    var winners = simulation(expandedTree, timeOfNextDecision);

                    // Back propagate the wins of the agent
                    backPropagation(expandedTree, winners);
                    */

                    iterations++;
                }

                timeOfNextDecision = System.currentTimeMillis() + DEFAULT_TIME_FOR_MACRO_ACTION_CALCULATIONS;

                //log.info("After MCTS: \n" + printTree(advancedGameState,"",0));
                MacroActionType action = null;
                if (advancedGameState.isLeaf()) {
                    //log.info("Could not find a move! Doing nothing...");
                } else {
                    // Select the most visited node from the root node and send it to the engine server
                    log._info_();
                    log.info("Iterations: " + iterations);
                    for (var child : advancedGameState.getChildren()) {
                        //log.info(child.getNode().getResponsibleMacroActionForPlayer(playerId) + " visits:" + child.getNode().getVisits() + " wins:" + child.getNode().getWinsForPlayer(playerId));
                    }
                    var mostVisitedNode = Collections.max(advancedGameState.getChildren(), treeMoveComparator).getNode();

                    action = mostVisitedNode.getResponsibleMacroActionForPlayer(playerId);
                    log.info("Determined next action: " + action);
                }

                lastDeterminedActionType = action;
                lastDeterminedActionType = Util.selectRandom(advancedGameState.getNode().getAllPossibleMacroActionsByAllPlayer(unitCommandQueues).get(playerId));
                log.info("Determined next action: " + lastDeterminedActionType);

                if(lastDeterminedActionType != null){
                    try{
                        executeMacroAction(lastDeterminedActionType,playerId,advancedGameState.getNode(),false);
                    }catch (NoSuchElementException e){
                        log.info("[ERROR] while excuting action");
                    }
                }

                for (var unit : unitCommandQueues.keySet()) {
                    log._info_();
                    log.info("Commands in queue for " + unit.getId());
                    for (Command<EmpireEvent> command: unitCommandQueues.get(unit)) {
                        log.info(command);
                    }
                }

                //for (var city :
                //        cityCommandQueue.keySet()) {
                //    log._info_();
                //    log.info("Commands in queue for " + city);
                //    for (Command<EmpireEvent> command: cityCommandQueue.get(city)) {
                //        log.info(command);
                //    }
                //}

                log._info_();
                log.info("Cities To Unit Type Producing");
                log.info(citiesToWhichUnitTypeProducing);
                log._info_();

                // Check time to stay synced with server
                var remainingTime = timeOfNextDecision - System.currentTimeMillis();
                if (remainingTime > 0) {
                    try {
                        Thread.sleep(remainingTime);
                    } catch (InterruptedException e) {
                        log.info(e);
                    }
                }

                // Executes for each unit there next actions in their own unit action command queue
                lastExecutedCommands = executeNextCommands(advancedGameState.getNode());
                turnsPassed++;
                log.info("Time passed in this turn: " + (System.currentTimeMillis() - startTime));

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

    public static double getEuclideanDistance(Position a, Position b) {
        var diff = a.subtract(b);
        return Math.sqrt(diff.getX() * diff.getX() + diff.getY() * diff.getY());
    }

}


