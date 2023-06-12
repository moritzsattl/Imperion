package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

class ImperionRealTimeGameNode<A> {

    protected RealTimeGame<A, ?> game;

    protected final Map<Integer, MacroActionType> actionsTaken;

    public ImperionRealTimeGameNode(RealTimeGame<A, ?> game, Map<Integer, MacroActionType> actionsTaken) {
        this.game = game;
        if (actionsTaken == null){
            this.actionsTaken = new HashMap<>();
        }else{
            this.actionsTaken = new HashMap<>(actionsTaken);
        }
    }


    // This method return all possible macro actions by each individual player
    public Map<Integer, Stack<MacroActionType>> getAllPossibleMacroActionsByAllPlayer(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues) {
        Map<Integer, Stack<MacroActionType>> getAllPossibleMacroActionsByPlayer = new HashMap<>();
        for (int playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
            Stack<MacroActionType> actions = new Stack<>();


            boolean allUnitsMoving = true;
            for (var unit : ((Empire) game).getUnitsByPlayer(playerId)) {

                if(unitCommandQueues.get(unit) == null || unitCommandQueues.get(unit).peek() == null) {
                    allUnitsMoving = false;
                    continue;
                }

                if(!(unitCommandQueues.get(unit).peek().getMacroAction() instanceof MoveAction<A>)){
                    allUnitsMoving = false;
                }
            }

            // Base case: At the beginning, the command queue is empty
            if(unitCommandQueues.isEmpty()){
                allUnitsMoving = false;
            }

            // If all units are moving, don't add Exploration Action
            if(!allUnitsMoving){
                actions.add(MacroActionType.EXPLORATION);
            }



            //if(!knownOtherCities(playerId).isEmpty()) {
            //    actions.add(MacroActionType.EXPANSION);
            //}

            //TODO: add only macro actions if certain Empire events exist at the moment
            // For example: Only add MOVE_UNITS MacroActionType is there is possible actions 'MovementOrder' from the game

            getAllPossibleMacroActionsByPlayer.put(playerId,actions);
        }

        return getAllPossibleMacroActionsByPlayer;
    }

    public List<EmpireCity> knownOtherCities(int playerId) {
        var citiesMap = ((Empire) game).getCitiesByPosition();
        List<EmpireCity> cities = new ArrayList<>();
        for (var city: citiesMap.values()) {
            if (city.getPlayerId() != playerId) {
                cities.add(city);
            }
        }
        return cities;
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
