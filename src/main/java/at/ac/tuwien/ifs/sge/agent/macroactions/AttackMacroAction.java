package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.Command;
import at.ac.tuwien.ifs.sge.agent.ExecutableActionFactoryException;
import at.ac.tuwien.ifs.sge.agent.GameStateNode;
import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

import static at.ac.tuwien.ifs.sge.agent.Imperion.getEuclideanDistance;

public class AttackMacroAction<EmpireEvent> extends AbstractMacroAction<EmpireEvent>{
    private final List<EmpireCity> emptyCitiesInSight;
    public AttackMacroAction(GameStateNode<EmpireEvent> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
        this.emptyCitiesInSight = gameStateNode.knownOtherCities(playerId);
    }

    @Override
    public Deque<MacroAction<EmpireEvent>> generateExecutableAction(Map<UUID, Deque<Command<EmpireEvent>>> unitCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<EmpireEvent>> actions = new LinkedList<>();

        // Get all enemy Units
        Stack<EmpireUnit> enemyUnits = new Stack<>();
        for (int otherPlayerId = 0; otherPlayerId < game.getNumberOfPlayers(); otherPlayerId++) {
            if(otherPlayerId != playerId){
                enemyUnits.addAll(game.getUnitsByPlayer(otherPlayerId));
            }
        }

        if(enemyUnits.isEmpty()){
            throw new ExecutableActionFactoryException("No enemy units in sight");
        }


        ArrayList<EmpireCity> ourCities = new ArrayList<>();

        // Order build action for all of cities
        for (var cityPos :game.getCitiesByPosition().keySet()) {
            if(game.getCity(cityPos).getPlayerId() == playerId){
                ourCities.add(game.getCity(cityPos));
            }
        }


        List<EmpireUnit> busyWithExpandingUnits = new ArrayList<>();
        // Get empty cities
        for (var city: emptyCitiesInSight) {
            if (city.getOccupants().isEmpty()) {
                for (var unitId: unitCommandQueues.keySet()) {
                    Command<EmpireEvent> command = unitCommandQueues.get(unitId).peek();
                    if(command != null && command.getMacroAction() instanceof MoveAction<EmpireEvent> moveAction){
                        if(moveAction.getDestination().equals(city.getPosition()) && moveAction.getType() == MacroActionType.EXPANSION){
                            busyWithExpandingUnits.add(game.getUnit(unitId));
                        }
                    }
                }
            }
        }

        // Units which are not busy (so no commands are scheduled or which are last unit on city tile)
        ArrayList<EmpireUnit> notBusyUnits = new ArrayList<>();

        ArrayList<EmpireUnit> notBusyUnitsOnCities = new ArrayList<>();

        for (var unit: units) {
            var unitPosition = unit.getPosition();
            // Not busy units
            if((unitCommandQueues.get(unit.getId()) == null || unitCommandQueues.get(unit.getId()).isEmpty())){

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
        // one unit from cities should remain there, so it is actually busy and has to be removed from notBusyUnitsOnCities
        HashSet<Position> alreadyCheckedPositions = new HashSet<>();
        HashMap<Position, List<EmpireUnit>> positionToUnitsMap = new HashMap<>();
        HashSet<EmpireUnit> busyForProductionUnitsOnCity = new HashSet<>();

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
                alreadyCheckedPositions.add(entry.getKey());
            }
        }

        // Step 3: Remove the selected units
        notBusyUnitsOnCities.removeAll(busyForProductionUnitsOnCity);

        // Step 4: Add all notBusyUnitsOnCities to notBusyUnits
        notBusyUnits.addAll(notBusyUnitsOnCities);

        ArrayList<EmpireUnit> busyForProductionUnitsOnCitiesWhichAreNotProducing = new ArrayList<>();

        for (var unit : busyForProductionUnitsOnCity) {
            // Cities with units on it, which are not producing
            if(game.getCity(unit.getPosition()).getState() == EmpireProductionState.Idle){
                // Unit has to be added instead of city, because build action need units as parameter
                busyForProductionUnitsOnCitiesWhichAreNotProducing.add(unit);
            }
        }

        List<EmpireUnit> allUnitsExceptThoseLastOnCityAndThoseBusyExpanding = new ArrayList<>();

        for (var unit : this.units) {
            if (!busyForProductionUnitsOnCity.contains(unit) && !busyWithExpandingUnits.contains(unit)) {
                allUnitsExceptThoseLastOnCityAndThoseBusyExpanding.add(unit);
            }
        }

        for (var unit :
                busyForProductionUnitsOnCity) {
            BuildAction<EmpireEvent> buildAction = new BuildAction<>(gameStateNode,playerId,log,simulation,game.getCity(unit.getPosition()),unit, 3);
            actions.add(buildAction);
        }

        // Get army
        Stack<EmpireUnit> armyUnits = new Stack<>();
        for (var unit : allUnitsExceptThoseLastOnCityAndThoseBusyExpanding) {
            armyUnits.add(unit);
        }

        // If no cavalries, just send build orders
        if(armyUnits.isEmpty()){
            return actions;
        }


        // Make groups of enemies with different strength levels (just number of units)
        int DISTANT_CONSTANT = 5;
        List<List<EmpireUnit>> enemyGroups = new ArrayList<>();

        while(!enemyUnits.isEmpty()){
            List<EmpireUnit> enemyGroup = new ArrayList<>();
            var enemy = enemyUnits.pop();
            enemyGroup.add(enemy);

            for (var otherUnit : new ArrayList<>(enemyUnits)) {  // Creating a copy of enemyUnits for iteration
                if(getEuclideanDistance(enemy.getPosition(), otherUnit.getPosition()) < DISTANT_CONSTANT){
                    enemyGroup.add(otherUnit);
                    enemyUnits.remove(otherUnit);  // Remove the other unit from enemyUnits
                }
            }

            enemyGroups.add(enemyGroup);
        }

        if(!simulation){
            log.info("Enemies in Groups");
            for (var enemyGroup :
                    enemyGroups) {
                log.info(enemyGroup);
            }
        }


        // Calc for each enemyGroup the nearest unit to go there
        ArrayList<EmpireUnit> unitsWhichHaveAnAttackOrder = new ArrayList<>();
        for (var enemyGroup :
                enemyGroups) {
            // Send one more unit then enemies are there
            int unitsToSend = enemyGroup.size() + 1;

            // Sort cavalries based on the getEuclideanDistance from each unit to enemyGroup.getPosition()
            Collections.sort(armyUnits, (c1, c2) -> {
                Position enemyPos = enemyGroup.get(0).getPosition();

                double dist1 = getEuclideanDistance(c1.getPosition(), enemyPos);
                double dist2 = getEuclideanDistance(c2.getPosition(), enemyPos);

                return Double.compare(dist1, dist2);
            });

            // Select the closest units and give them an attack order
            for (int i = 0; i < unitsToSend && i < armyUnits.size(); i++) {
                EmpireUnit unit = armyUnits.get(i);
                AttackAction<EmpireEvent> moveAction = new AttackAction<>(gameStateNode,playerId,MacroActionType.ATTACK,log,simulation,unit,enemyGroup.get(0),true);
                unitsWhichHaveAnAttackOrder.add(unit);
                actions.add(moveAction);
            }

            // Remove the units which have been given an attack order from the cavalries list
            armyUnits.removeAll(unitsWhichHaveAnAttackOrder);
        }


        return actions;
    }


    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<UUID, Deque<Command<EmpireEvent>>> unitCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<EmpireEvent>> executable = generateExecutableAction(unitCommandQueues);
        Deque<EmpireEvent> events = new LinkedList<>();
        if (executable != null) {
            while (!executable.isEmpty()){
                MacroAction<EmpireEvent> action = executable.poll();
                Deque<EmpireEvent> empireEvents =  action.getResponsibleActions(unitCommandQueues);
                while (!empireEvents.isEmpty()) {
                    events.add(empireEvents.poll());
                }
            }
        }
        return events;
    }


}
