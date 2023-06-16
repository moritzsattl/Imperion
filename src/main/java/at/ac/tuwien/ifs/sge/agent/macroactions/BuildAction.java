package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.Command;
import at.ac.tuwien.ifs.sge.agent.ExecutableActionFactoryException;
import at.ac.tuwien.ifs.sge.agent.GameStateNode;
import at.ac.tuwien.ifs.sge.agent.macroactions.AbstractMacroAction;
import at.ac.tuwien.ifs.sge.agent.macroactions.MacroAction;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

public class BuildAction<EmpireEvent> extends AbstractMacroAction<EmpireEvent> {

    private final String empireCityName;
    private final EmpireCity empireCity;
    private final EmpireUnit empireUnitOnCity;
    private final int unitTypeId;

    public BuildAction(GameStateNode<EmpireEvent> gameStateNode, int playerId, Logger log, boolean simulation, EmpireCity empireCity, EmpireUnit empireUnit, int unitTypeName) {
        super(gameStateNode, playerId, log, simulation);
        this.empireCityName = empireCity.toString();
        this.empireCity = empireCity;
        this.unitTypeId = unitTypeName;
        this.empireUnitOnCity = empireUnit;
    }

    public String getCityStringName() {
        // EmpireCity has no ID, its String representation is used;
        return "City " + empireCity.getPosition();
    }

    public EmpireCity getEmpireCity() {
        return empireCity;
    }

    public int getUnitTypeId() {
        return unitTypeId;
    }

    @Override
    public Deque<MacroAction<EmpireEvent>> generateExecutableAction(Map<UUID, Deque<Command<EmpireEvent>>> unitsCommandQueues) throws ExecutableActionFactoryException {
        return null;
    }

    @Override
    public Deque<EmpireEvent> getResponsibleActions(Map<UUID,Deque<Command<EmpireEvent>>> unitCommandQueues) {

        Deque<EmpireEvent> buildActions = new LinkedList<>();

        buildActions.add((EmpireEvent) new ProductionStartOrder(empireCity.getPosition(), unitTypeId));


        return buildActions;
    }



    @Override
    public String toString() {
        return "BuildAction{" +
                "empireCity=" + empireCity +
                ", unitTypeName=" + unitTypeId +
                '}';
    }
}
