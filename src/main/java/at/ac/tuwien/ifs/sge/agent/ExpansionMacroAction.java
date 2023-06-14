package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class ExpansionMacroAction<A> extends AbstractMacroAction<A> {

    private final List<EmpireUnit> units;
    private final List<EmpireCity> nonFriendlyCities;
    private Deque<EmpireEvent> path;

    public ExpansionMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
        this.units = game.getUnitsByPlayer(playerId);
        this.nonFriendlyCities = gameStateNode.knownOtherCities(playerId);
    }


    @Override
    public Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit, Deque<Command<A>>> unitCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<A>> actions = new LinkedList<>();

        // Units which are not busy (so no commands are scheduled or which are last unit on city tile)
        ArrayList<EmpireUnit> notBusyUnits = new ArrayList<>();

        ArrayList<EmpireUnit> notBusyUnitsOnCities = new ArrayList<>();

        ArrayList<EmpireUnit> notBusyUnitsOnCitiesWhichAreNotProducing = new ArrayList<>();

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


                    // Cities with units on it, which are not producing
                    if(game.getCity(unit.getPosition()).getState() == EmpireProductionState.Idle){
                        // Unit has to be added instead of city, because build action need units as parameter
                        notBusyUnitsOnCitiesWhichAreNotProducing.add(unit);
                    }
                }
            }
        }

        // Error happens, when multiple units are on city tile, because then all are free in the current scenario, so
        // one unit from cities should remain there, so it is actually busy and has to be removed from notBusyUnitsOnCities
        HashSet<Position> alreadyCheckedPositions = new HashSet<>();
        HashMap<Position, List<EmpireUnit>> positionToUnitsMap = new HashMap<>();
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
                // select one unit to remove at random
                EmpireUnit unitToRemove = Util.selectRandom(unitsAtPosition);
                unitsToRemove.add(unitToRemove);
                alreadyCheckedPositions.add(entry.getKey());
            }
        }

        // Step 3: Remove the selected units
        notBusyUnitsOnCities.removeAll(unitsToRemove);

        // Step 4: Add all notBusyUnitsOnCities to notBusyUnits
        notBusyUnits.addAll(notBusyUnitsOnCities);



        EmpireUnit selectedUnit = null;
        List<EmpireUnit> infantries = new ArrayList<>();
        // Get all free Infantry units
        for (var unit : notBusyUnits) {
            // Select Infantry
            if (unit.getUnitTypeName().equals("Infantry")) {
                infantries.add(unit);
            }
        }

        // TODO: Maybe select the infantry unit which is closest to the city
        // If there is a free infantry unit, then select one
        if(!infantries.isEmpty()){
            selectedUnit = Util.selectRandom(infantries);
        }

        BuildAction<A> buildAction;

        // If there are no infantry units or all are infantry units are busy, then build one
        if(selectedUnit == null){

            EmpireUnit unitOnCity = null;
            // Select a random non producing city with units on it and produce infantry
            if(!notBusyUnitsOnCitiesWhichAreNotProducing.isEmpty()) {
                unitOnCity = Util.selectRandom(notBusyUnitsOnCitiesWhichAreNotProducing);
            }else {
                // In this case all cities are producing, so we just want to queue a build order for a random City
                if(!notBusyUnitsOnCities.isEmpty()){
                    unitOnCity = Util.selectRandom(notBusyUnitsOnCities);
                }
            }

            // Schedule production order
            if(unitOnCity != null) {
                buildAction = new BuildAction<>(gameStateNode,playerId,log,simulation,game.getCity(unitOnCity.getPosition()),unitOnCity, 1);
                actions.add(buildAction);
            }

            // Select other unit for expansion instead of infantry for MoveAction
            selectedUnit = Util.selectRandom(notBusyUnits);
        }

        log.info("Not busy units: " + notBusyUnits);

        // If no units are free and buildAction could not be scheduled, throw exception
        if(selectedUnit == null){
            if(actions.isEmpty()){
                throw new ExecutableActionFactoryException("All units busy or last on city tile, just ordered build action");
            }
            // If there are no units, which are free (not last unit on a city tile) just order buildAction
            return actions;
        }


        log.info("Not Friendly Cities: " +  nonFriendlyCities);

        // Get empty cities
        List<EmpireCity> emptyCities = new LinkedList<>();
        for (var city: nonFriendlyCities) {
            if (city.getOccupants().isEmpty()) {
                emptyCities.add(city);
            }
        }

        log.info("Empty Cities: " + emptyCities);

        if (emptyCities.isEmpty()) {
            throw new ExecutableActionFactoryException("No empty cities found.");
        }

        // Select random city for expansion order
        Position destination = Util.selectRandom(emptyCities).getPosition();

        MoveAction<A> moveAction = new MoveAction<>(gameStateNode, selectedUnit, MacroActionType.EXPANSION, destination, playerId, log, simulation);
        actions.add(moveAction);

        return actions;
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

    @Override
    public void simulate() throws ActionException {

    }
}
