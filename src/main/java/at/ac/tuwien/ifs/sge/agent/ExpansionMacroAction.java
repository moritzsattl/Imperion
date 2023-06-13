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
    public Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit, Deque<Command<A>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        Deque<MacroAction<A>> actions = new LinkedList<>();

        if (units.isEmpty()) {
            // No move action possible if no units are available.
            throw new ExecutableActionFactoryException();
        }

        // Get units which are not busy
        ArrayList<EmpireUnit> notBusyUnits = new ArrayList<>();

        for (var unit: units) {
            if(!unitsCommandQueues.containsKey(unit) || unitsCommandQueues.get(unit).isEmpty()){
                notBusyUnits.add(unit);
            }
        }


        List<EmpireCity> emptyCities = new LinkedList<>();
        for (var city: nonFriendlyCities) {
            if (city.getOccupants().isEmpty()) {
                emptyCities.add(city);
            }
        }

        if (emptyCities.isEmpty()) {
            throw new ExecutableActionFactoryException("No empty cities found.");
        }

        // Select scout unit
        EmpireUnit selectedUnit = null;
        for (var unit : notBusyUnits) {
            // Select Scout
            var city = game.getCitiesByPosition().get(unit.getPosition());
            if (unit.getUnitTypeName().equals("Infantry")
                    && (city == null || city.getOccupants().size()>1) //TODO add check for only own units
            ) {
                EmpireCity potentialCity = game.getCitiesByPosition().get(unit.getPosition());
                if (potentialCity != null && potentialCity.getOccupants().size()==1) continue;
                log.info("Select Infantry");
                selectedUnit = unit;
            }
        }

        BuildAction<A> buildAction;

        if(selectedUnit == null) {
            for (var unit : notBusyUnits) {
                // If we have a unit (not a scout) on a city, make production order for scout (if not already producing)
                if(game.getCitiesByPosition().containsKey(unit.getPosition())){
                    if(game.getCity(unit.getPosition()).getState() == EmpireProductionState.Idle && unit.getUnitTypeId() != 1){
                        buildAction = new BuildAction<>(gameStateNode,playerId,log,simulation,game.getCity(unit.getPosition()),unit, 1);
                        actions.add(buildAction);
                    }
                }
            }

            throw new ExecutableActionFactoryException("No Infantry for Expansion found, scheduling production order.");
        }

        //TODO logic as to which city
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
