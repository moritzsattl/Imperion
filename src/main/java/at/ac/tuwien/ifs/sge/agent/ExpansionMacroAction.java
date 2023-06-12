package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class ExpansionMacroAction<A> extends AbstractMacroAction<A> {

    private final List<EmpireUnit> units;
    private List<EmpireCity> nonFriendlyCities;
    private Deque<EmpireEvent> path;

    public ExpansionMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        super(gameStateNode, playerId, log, simulation);
        this.units = game.getUnitsByPlayer(playerId);
        this.nonFriendlyCities = null;
    }


    @Override
    public Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit, Deque<Command<A>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        return null;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit, Deque<Command<A>>> unitCommandQueues) throws ExecutableActionFactoryException {

        Deque<MacroAction<A>> actions = new LinkedList<>();

        if (units.isEmpty()) {
            // No move action possible if no units are available.
            throw new ExecutableActionFactoryException();
        }

        // Get units which are not busy
        ArrayList<EmpireUnit> notBusyUnits = new ArrayList<>();

        for (var unit: units) {
            if(!unitCommandQueues.containsKey(unit) || unitCommandQueues.get(unit).isEmpty()){
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
            if (unit.getUnitTypeName().equals("Scout")) {
                EmpireCity potentialCity = game.getCitiesByPosition().get(unit.getPosition());
                if (potentialCity != null && potentialCity.getOccupants().size()==1) continue;
                log.info("Select scout");
                selectedUnit = unit;
            }
        }

        if(selectedUnit == null) {
            throw new ExecutableActionFactoryException("No Scout for Expansion found");
        }

        //TODO logic as to which city
        Position destination = Util.selectRandom(emptyCities).getPosition();

        MoveAction<A> moveAction = new MoveAction<A>(gameStateNode, selectedUnit, destination, playerId, log, simulation);
        actions.add(moveAction);


        Deque<EmpireEvent> events = new LinkedList<>();
        if (actions != null) {
            for (var action: actions){
                Deque<EmpireEvent> empireEvents = action.getResponsibleActions(unitCommandQueues);
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
