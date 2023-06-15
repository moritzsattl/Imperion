package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class ExpansionMacroAction<A> extends AbstractMacroAction<A> {
    private final static List<EmpireCity> citiesAlreadyVisiting = new ArrayList<>();
    private final List<EmpireCity> nonFriendlyCities;

    public ExpansionMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
        this.nonFriendlyCities = gameStateNode.knownOtherCities(playerId);
    }


    @Override
    public Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit, Deque<Command<A>>> unitCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<A>> actions = new LinkedList<>();
        EmpireUnit selectedUnit = null;
        EmpireCity selectedCity = null;

        // Find City to Expand to
        log.info("Not Friendly Cities: " +  nonFriendlyCities);

        // Get empty cities
        List<EmpireCity> emptyCities = new LinkedList<>();
        for (var city: nonFriendlyCities) {
            if (city.getOccupants().isEmpty() && !citiesAlreadyVisiting.contains(city)) {
                emptyCities.add(city);
            }
        }

        log.info("Empty Cities: " + emptyCities);

        if (emptyCities.isEmpty()) {
            throw new ExecutableActionFactoryException("No empty cities not already visiting found.");
        }

        // Units which are not busy (so no commands are scheduled or which are last unit on city tile)
        ArrayList<EmpireUnit> notBusyUnits = new ArrayList<>();

        ArrayList<EmpireUnit> notBusyUnitsOnCities = new ArrayList<>();

        for (var unit: units) {
            var unitPosition = unit.getPosition();
            // Not busy units
            if((unitCommandQueues.get(unit) == null || unitCommandQueues.get(unit).isEmpty())){

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
        log.info("Not busy units: " + notBusyUnits);

        ArrayList<EmpireUnit> busyForProductionUnitsOnCitiesWhichAreNotProducing = new ArrayList<>();

        for (var unit : busyForProductionUnitsOnCity) {
            // Cities with units on it, which are not producing
            if(game.getCity(unit.getPosition()).getState() == EmpireProductionState.Idle){
                // Unit has to be added instead of city, because build action need units as parameter
                busyForProductionUnitsOnCitiesWhichAreNotProducing.add(unit);
            }
        }

        List<EmpireUnit> allUnitsExceptThoseLastOnCity = new ArrayList<>();

        // Force nearest unit to city, to expand (except units which are on cities)
        for (var unit : this.units) {
            if (!busyForProductionUnitsOnCity.contains(unit)) {
                allUnitsExceptThoseLastOnCity.add(unit);
            }
        }

        // If there is a unit, then select one
        if(!allUnitsExceptThoseLastOnCity.isEmpty()){
            Object[] selectedPair = findClosestPair(emptyCities, allUnitsExceptThoseLastOnCity);
            selectedCity = (EmpireCity) selectedPair[0];
            selectedUnit = (EmpireUnit) selectedPair[1];
        }

        // If there are no infantry units or all are infantry units are busy, then build one
        if(selectedUnit == null){
            BuildAction<A> buildAction;

            EmpireUnit unitOnCity = null;
            // Select a random non producing city with units on it and produce infantry
            if(!busyForProductionUnitsOnCitiesWhichAreNotProducing.isEmpty()) {
                unitOnCity = Util.selectRandom(busyForProductionUnitsOnCitiesWhichAreNotProducing);
            }else {
                // In this case all cities are producing, so we just want to queue a build order for a random City
                if(!busyForProductionUnitsOnCity.isEmpty()){
                    unitOnCity = Util.selectRandom(busyForProductionUnitsOnCity);
                }
            }

            // Schedule production order
            if(unitOnCity != null) {
                buildAction = new BuildAction<>(gameStateNode,playerId,log,simulation,game.getCity(unitOnCity.getPosition()),unitOnCity, 1);
                actions.add(buildAction);
            }

            // If no units are free and buildAction could not be scheduled, throw exception
            if(selectedUnit == null){
                if(actions.isEmpty()){
                    throw new ExecutableActionFactoryException("All units busy or last on city tile, can't even order build action");
                }
                // If there are no units, which are free (not last unit on a city tile) just order buildAction
                return actions;
            }
        }


        MoveAction<A> moveAction = new MoveAction<>(gameStateNode, selectedUnit, MacroActionType.EXPANSION, selectedCity.getPosition(), playerId, log, simulation, true);
        actions.add(moveAction);
        citiesAlreadyVisiting.add(selectedCity);

        return actions;
    }

    private static Object[] findClosestPair(List<EmpireCity> cities, List<EmpireUnit> units) {
        Object[] result = {null, null};
        double closest = Double.MAX_VALUE;
        for (var city: cities) {
            for (var unit: units) {
                double temp = Imperion.getEuclideanDistance(city.getPosition(), unit.getPosition());
                if (temp < closest) {
                    result[0] = city;
                    result[1] = unit;
                }
            }
        }

        return result;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit, Deque<Command<A>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<A>> actions = generateExecutableAction(unitsCommandQueues);

        Deque<EmpireEvent> events = new LinkedList<>();
        if (actions != null) {
            for (var action: actions){
                Deque<EmpireEvent> empireEvents = action.getResponsibleActions(unitsCommandQueues);
                while (!empireEvents.isEmpty()) {
                    events.add(empireEvents.poll());
                }
            }
        }

        return events;
    }

}
