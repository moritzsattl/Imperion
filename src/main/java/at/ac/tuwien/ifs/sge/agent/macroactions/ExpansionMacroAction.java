package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.*;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class ExpansionMacroAction<EmpireEvent> extends AbstractMacroAction<EmpireEvent> {
    private final static List<EmpireCity> citiesAlreadyVisiting = new ArrayList<>();
    private final List<EmpireCity> emptyCitiesInSight;

    public ExpansionMacroAction(GameStateNode<EmpireEvent> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
        this.emptyCitiesInSight = gameStateNode.knownOtherCities(playerId);
    }


    @Override
    public Deque<MacroAction<EmpireEvent>> generateExecutableAction(Map<UUID, Deque<Command<EmpireEvent>>> unitCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<EmpireEvent>> actions = new LinkedList<>();
        EmpireUnit selectedUnit = null;
        EmpireCity selectedCity = null;

        // Find City to Expand to
        log.debug("Not Friendly Cities: " + emptyCitiesInSight);


        List<EmpireUnit> busyWithExpandingUnits = new ArrayList<>();
        // Get empty cities
        List<EmpireCity> emptyWhichAreNotAlreadyBeenVisited = new LinkedList<>();
        for (var city: emptyCitiesInSight) {
            if (city.getOccupants().isEmpty()) {
                boolean otherUnitExpandingToCity = false;
                for (var unitId: unitCommandQueues.keySet()) {
                    Command<EmpireEvent> command = unitCommandQueues.get(unitId).peek();
                    if(command != null && command.getMacroAction() instanceof MoveAction<EmpireEvent> moveAction){
                        if(moveAction.getDestination().equals(city.getPosition()) && moveAction.getType() == MacroActionType.EXPANSION){
                            busyWithExpandingUnits.add(game.getUnit(unitId));
                            otherUnitExpandingToCity = true;
                        }
                    }
                }
                if(!otherUnitExpandingToCity){
                    emptyWhichAreNotAlreadyBeenVisited.add(city);
                }
            }
        }

        log.debug("Empty Cities: " + emptyWhichAreNotAlreadyBeenVisited);

        if (emptyWhichAreNotAlreadyBeenVisited.isEmpty()) {
            throw new ExecutableActionFactoryException("No empty cities in sight.");
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
        // Force nearest unit to city, to expand (except units which are on cities)
        for (var unit : this.units) {
            if (!busyForProductionUnitsOnCity.contains(unit) && !busyWithExpandingUnits.contains(unit)) {
                allUnitsExceptThoseLastOnCityAndThoseBusyExpanding.add(unit);
            }
        }
        log.info("All Units Except Those Last On City And Those Busy Expanding: " + allUnitsExceptThoseLastOnCityAndThoseBusyExpanding);
        // If there is a unit, then select one
        if(!allUnitsExceptThoseLastOnCityAndThoseBusyExpanding.isEmpty()){
            Object[] selectedPair = findClosestPair(emptyWhichAreNotAlreadyBeenVisited, allUnitsExceptThoseLastOnCityAndThoseBusyExpanding);
            selectedCity = (EmpireCity) selectedPair[0];
            selectedUnit = (EmpireUnit) selectedPair[1];
        }

        // If there are no infantry units or all are infantry units are busy, then build one
        if(selectedUnit == null){
            BuildAction<EmpireEvent> buildAction;

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
            if(actions.isEmpty()){
                throw new ExecutableActionFactoryException("All units busy or last on city tile, even order build action");
            }
            // If there are no units, which are free (not last unit on a city tile) just order buildAction
            return actions;
        }




        MoveAction<EmpireEvent> moveAction = new MoveAction<>(gameStateNode, selectedUnit, MacroActionType.EXPANSION, selectedCity.getPosition(), playerId, log, simulation, true);
        actions.add(moveAction);

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
    public Deque<EmpireEvent> getResponsibleActions(Map<UUID, Deque<Command<EmpireEvent>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<EmpireEvent>> actions = generateExecutableAction(unitsCommandQueues);

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
