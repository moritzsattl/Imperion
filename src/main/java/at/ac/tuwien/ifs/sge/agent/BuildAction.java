package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

public class BuildAction<A> extends AbstractMacroAction<A> {

    private final EmpireUnit empireUnit;
    private final int unitTypeName;

    public BuildAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation, EmpireUnit empireUnit, int unitTypeName) {
        super(gameStateNode, playerId, log, simulation);
        this.empireUnit = empireUnit;
        this.unitTypeName = unitTypeName;
    }

    public EmpireUnit getUnitOnCity() {
        return empireUnit;
    }

    public int getUnitTypeName() {
        return unitTypeName;
    }

    @Override
    public Deque<MacroAction<A>> generateExecutableAction(Map<EmpireUnit, Deque<Command<A>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        return null;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues) {

        Deque<EmpireEvent> buildActions = new LinkedList<>();

        if(game.getCity(empireUnit.getPosition()).getState() == EmpireProductionState.Idle){
            buildActions.add(new ProductionStartOrder(empireUnit.getPosition(),unitTypeName));
        }

        return buildActions;
    }

    @Override
    public void simulate() throws ActionException {

    }

    @Override
    public String toString() {
        return "BuildAction{" +
                "empireUnit=" + empireUnit +
                ", unitTypeName=" + unitTypeName +
                '}';
    }
}
