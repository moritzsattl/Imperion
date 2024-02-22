package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ProductionMacroAction extends AbstractMacroAction {

    private final ProductionStartOrder productionStartOrder;

    // This variable, when initialized, will force the simulation to do nothing until waitCounter == 0
    private final int waitCounter;
    public ProductionMacroAction(ProductionStartOrder productionStartOrder, int waitCounter) {
        super(createEvents(productionStartOrder, waitCounter));

        this.productionStartOrder = productionStartOrder;
        this.waitCounter = waitCounter;
    }

    // Just a helper class
    private static List<EmpireEvent> createEvents(ProductionStartOrder productionStartOrder, int waitCounter) {
        var actions = new ArrayList<EmpireEvent>();
        actions.add(productionStartOrder);
        actions.addAll(IntStream.range(0 , waitCounter).mapToObj(i -> new WaitEvent(productionStartOrder.getCityPosition())).toList());

        return actions;
    }

    @Override
    public String getType() {
        return "Production";
    }

    @Override
    public String toString() {
        return "ProductionMacroAction{" + getAtomicActions() + '}';
    }

    public int getWaitCounter() {
        return waitCounter;
    }

    public ProductionStartOrder getProductionStartOrder() {
        return productionStartOrder;
    }
}
