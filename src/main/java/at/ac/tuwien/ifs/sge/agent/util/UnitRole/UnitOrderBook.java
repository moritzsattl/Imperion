package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.agent.util.MacroAction.*;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.CombatStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.MovementStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;
import java.util.stream.Collectors;

public class CommandQueue {
    private Map<UUID, ArrayDeque<EmpireEvent>> unitCommandQueue;
    private Map<Position, ArrayDeque<EmpireEvent>> cityCommandQueue;

    // This lets the MCTS do nothing
    public boolean doNothing = false;

    public CommandQueue(Map<UUID, ArrayDeque<EmpireEvent>> unitCommandQueue, Map<Position, ArrayDeque<EmpireEvent>> cityCommandQueue) {
        this.unitCommandQueue = unitCommandQueue;
        this.cityCommandQueue = cityCommandQueue;
    }

    public CommandQueue(CommandQueue commandQueue){
        this.unitCommandQueue = new HashMap<>();
        for (var command: commandQueue.unitCommandQueue.entrySet()){
            this.unitCommandQueue.put(command.getKey(), new ArrayDeque<>(command.getValue()));
        }

        this.cityCommandQueue = new HashMap<>();
        for (var command: commandQueue.cityCommandQueue.entrySet()){
            this.cityCommandQueue.put(command.getKey(), new ArrayDeque<>(command.getValue()));
        }
    }

    public CommandQueue() {
        unitCommandQueue = new HashMap<>();
        cityCommandQueue = new HashMap<>();
    }

    public boolean isEmpty(){
        return (cityCommandQueue.values().stream().mapToInt(Collection::size).sum() + unitCommandQueue.values().stream().mapToInt(Collection::size).sum()) == 0;
    }

    private void addCityCommand(Position pos, EmpireEvent event){
        var val = cityCommandQueue.getOrDefault(pos, new ArrayDeque<>());
        val.add(event);
        cityCommandQueue.put(pos, val);
    }

    /**
     * Adds command to queue, if inFront is true, then command will be added in front of other commands in queue
     */
    private void addUnitCommand(UUID uuid, EmpireEvent event, boolean inFront){
        var val = unitCommandQueue.getOrDefault(uuid, new ArrayDeque<>());
        if(inFront) val.addFirst(event); else val.add(event);
        unitCommandQueue.put(uuid, val);
    }


    public void addCommand(EmpireEvent event, boolean inFront){
        if(event instanceof ProductionStartOrder productionAction) addCityCommand(productionAction.getCityPosition(), productionAction);
        else if(event instanceof ProductionStopOrder productionStopOrder) addCityCommand(productionStopOrder.getCityPosition(), productionStopOrder);
        else if(event instanceof MovementStartOrder movementStartOrder) addUnitCommand(movementStartOrder.getUnitId(), movementStartOrder, inFront);
        else if(event instanceof MovementStopOrder movementStopOrder) addUnitCommand(movementStopOrder.getUnitId(), movementStopOrder, inFront);
        else if(event instanceof WaitEvent waitEvent) addCityCommand(waitEvent.getEmpireCityPosition(), waitEvent);
        else if(event instanceof CombatStartOrder combatStartOrder) addUnitCommand(combatStartOrder.getAttackerId(), combatStartOrder, inFront);
        else Imperion.logger.debug("Unknown event: " + event);
    }

    /**
     * Adds macroAction to command queue
     */
    public void addCommand(MacroAction macroAction, boolean inFront){
        if(macroAction instanceof DoNothingMacroAction){ doNothing = true; return;};
        if(!(macroAction instanceof ScheduleNothingMacroAction)) for (var event : macroAction.getAtomicActions()) addCommand(event, inFront);
    }

    /**
     * Remove dead units from command queue
     * Dead units could still have commands in queue, that's why we have to remove them
     */
    public void removeDeadUnits(List<EmpireUnit> unitsByPlayer) {
        var aliveUnitIds = unitsByPlayer.stream().map(EmpireUnit::getId).collect(Collectors.toSet());
        var deadUnitsIds = unitCommandQueue.keySet().stream()
                .filter(id -> !aliveUnitIds.contains(id))
                .collect(Collectors.toSet());

        for(var deadUnitId : deadUnitsIds) unitCommandQueue.remove(deadUnitId);
    }

    public Map<UUID, ArrayDeque<EmpireEvent>> getUnitCommandQueue() {
        return unitCommandQueue;
    }

    public Map<Position, ArrayDeque<EmpireEvent>> getCityCommandQueue() {
        return cityCommandQueue;
    }

    @Override
    public String toString() {
        return "CommandQueue{" +
                "   unitCommandQueue=" + unitCommandQueue +
                "   cityCommandQueue=" + cityCommandQueue +
                '}';
    }

    private String prettyPrint(Map<?, ArrayDeque<EmpireEvent>> map){
        StringBuilder s = new StringBuilder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            s.append(entry.getKey()).append(" : ").append(entry.getValue()).append("\n");
        }

        return s.toString();
    }

}
