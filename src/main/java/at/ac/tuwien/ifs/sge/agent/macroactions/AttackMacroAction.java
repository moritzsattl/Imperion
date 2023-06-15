package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.Command;
import at.ac.tuwien.ifs.sge.agent.ExecutableActionFactoryException;
import at.ac.tuwien.ifs.sge.agent.GameStateNode;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class AttackMacroAction<EmpireEvent> extends AbstractMacroAction<EmpireEvent>{

    public AttackMacroAction(GameStateNode<EmpireEvent> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
    }

    @Override
    public Deque<MacroAction<EmpireEvent>> generateExecutableAction(Map<EmpireUnit, Deque<Command<EmpireEvent>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<EmpireEvent>> actions = new LinkedList<>();

        HashSet<EmpireUnit> enemyUnits = new HashSet<>();
        // Get all enemy Units
        for (var unit : units) {
            if(unit.getPlayerId() != playerId){
                enemyUnits.add(unit);
            }
        }

        if(enemyUnits.isEmpty()){
            throw new ExecutableActionFactoryException("No enemy units in sight");
        }


        ArrayList<EmpireCity> ourCities = new ArrayList<>();

        // Order build action for all cities
        for (var cityPos :game.getCitiesByPosition().keySet()) {
            if(game.getCity(cityPos).getPlayerId() == playerId){
                ourCities.add(game.getCity(cityPos));
            }
        }

        // Get units which are not busy (so no commands are scheduled or which are last unit on city tile)
        HashSet<EmpireUnit> notBusyUnits = new HashSet<>();
        HashSet<EmpireUnit> notBusyUnitsOnCities = new HashSet<>();

        for (var unit: units) {
            var unitPosition = unit.getPosition();
            // Unit which are not busy
            if((unitsCommandQueues.get(unit) == null || unitsCommandQueues.get(unit).isEmpty())){

                if(!game.getCitiesByPosition().containsKey(unitPosition)){
                    // Units which are not on cities
                    notBusyUnits.add(unit);
                }else{
                    // Units on city
                    notBusyUnitsOnCities.add(unit);
                }
            }
        }

        // Error happens, when multiple units are on city tile, because then all are free in the current scenario, so
        // one unit from cities with multiple Occupants should remain there, so it is actually busy and has to be removed from notBusyUnitsOnCities
        // Error happens, when multiple units are on city tile, because then all are free in the current scenario, so
        // one unit from cities should remain there, so it is actually busy and has to be removed from notBusyUnitsOnCities
        HashSet<Position> alreadyCheckedPositions = new HashSet<>();
        HashMap<Position, List<EmpireUnit>> positionToUnitsMap = new HashMap<>();

        HashSet<EmpireUnit> busyForProductionUnitsOnCity = new HashSet<>();
        HashSet<EmpireUnit> unitsToRemove = new HashSet<>();

        // Step 1: Fill the map
        for (EmpireUnit unit : notBusyUnitsOnCities) {
            Position position = unit.getPosition();
            if (!positionToUnitsMap.containsKey(position)) {
                positionToUnitsMap.put(position, new ArrayList<>());
            }
            positionToUnitsMap.get(position).add(unit);
        }

        // Step 2: Select units to remove
        for (Map.Entry<Position, List<EmpireUnit>> entry : positionToUnitsMap.entrySet()) {
            List<EmpireUnit> unitsAtPosition = entry.getValue();
            if (!alreadyCheckedPositions.contains(entry.getKey())) {

                EmpireUnit unitToRemove = null;
                // Try to remove the infantry unit
                for (var unit : unitsAtPosition) {
                    if(unit.getUnitTypeName().equals("Infantry")){
                        unitToRemove = unit;
                    }
                }

                // If no Infantry unit there, select other unit
                if(unitToRemove == null){
                    unitToRemove = Util.selectRandom(unitsAtPosition);
                }

                busyForProductionUnitsOnCity.add(unitToRemove);
                unitsToRemove.add(unitToRemove);
                alreadyCheckedPositions.add(entry.getKey());
            }
        }

        // Step 3: Remove the selected units
        notBusyUnitsOnCities.removeAll(unitsToRemove);

        // Step 4: Add all notBusyUnitsOnCities to notBusyUnits
        notBusyUnits.addAll(notBusyUnitsOnCities);
        log.info("Not busy units: " + notBusyUnits);

        // Produce on every city
        for (var unit: busyForProductionUnitsOnCity) {
            BuildAction<EmpireEvent> buildAction = new BuildAction<>(gameStateNode,playerId,log,false,game.getCity(unit.getPosition()),unit,3);
            actions.add(buildAction);
        }

        log.info("Attack Build Actions");
        log.info(actions);




        return actions;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit, Deque<Command<EmpireEvent>>> unitCommandQueues) throws ExecutableActionFactoryException {
        return null;
    }
}
