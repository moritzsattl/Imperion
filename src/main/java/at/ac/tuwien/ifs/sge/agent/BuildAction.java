package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

public class BuildAction<A> extends AbstractMacroAction<A> {

    private final EmpireCity empireCity;
    private final EmpireUnit empireUnitOnCity;
    private final int unitTypeName;

    public BuildAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation, EmpireCity empireCity,EmpireUnit empireUnit, int unitTypeName) {
        super(gameStateNode, playerId, log, simulation);
        this.empireCity = empireCity;
        this.unitTypeName = unitTypeName;
        this.empireUnitOnCity = empireUnit;
    }

    public EmpireCity getCity() {
        return empireCity;
    }

    public EmpireUnit getEmpireUnitOnCityPosition() {
        return empireUnitOnCity;
    }

    public int getUnitTypeName() {
        return unitTypeName;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<EmpireUnit,Deque<Command<A>>> unitCommandQueues) {

        Deque<EmpireEvent> buildActions = new LinkedList<>();

        if(game.getCity(empireCity.getPosition()).getState() == EmpireProductionState.Idle){
            buildActions.add(new ProductionStartOrder(empireCity.getPosition(),unitTypeName));
            //buildActions.add(new ProductionStopOrder(empireUnit.getPosition()));
        }


        return buildActions;
    }

    @Override
    public void simulate() throws ActionException {

    }

    @Override
    public String toString() {
        return "BuildAction{" +
                "empireUnit=" + empireCity +
                ", unitTypeName=" + unitTypeName +
                '}';
    }
}
