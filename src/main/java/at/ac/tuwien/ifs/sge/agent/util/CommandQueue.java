package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.agent.util.MacroAction.*;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.ProductionStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.MovementStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.stop.ProductionStopOrder;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;

import java.util.*;
import java.util.stream.IntStream;

public class CommandQueue {
    private Map<UUID, Queue<EmpireEvent>> unitCommandQueue;
    private Map<Position, Queue<EmpireEvent>> cityCommandQueue;

    // This lets the MCTS do nothing
    public boolean doNothing = false;

    public CommandQueue(Map<UUID, Queue<EmpireEvent>> unitCommandQueue, Map<Position, Queue<EmpireEvent>> cityCommandQueue) {
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

    private void addUnitCommand(UUID uuid, EmpireEvent event){
        var val = unitCommandQueue.getOrDefault(uuid, new ArrayDeque<>());
        val.add(event);
        unitCommandQueue.put(uuid, val);
    }

    public void addCommand(EmpireEvent event){
        if(event instanceof ProductionStartOrder productionAction) addCityCommand(productionAction.getCityPosition(), productionAction);
        else if(event instanceof ProductionStopOrder productionStopOrder) addCityCommand(productionStopOrder.getCityPosition(), productionStopOrder);
        else if(event instanceof MovementStartOrder movementAction) addUnitCommand(movementAction.getUnitId(), movementAction);
        else if(event instanceof MovementStopOrder movementStopOrder) addUnitCommand(movementStopOrder.getUnitId(), movementStopOrder);
        else if(event instanceof WaitEvent waitEvent) addCityCommand(waitEvent.getEmpireCityPosition(), waitEvent);
        else Imperion.logger.debug("Unknown event: " + event);
    }

    /**
     * Adds macroAction to command queue
     */
    public void addCommand(MacroAction macroAction){
        if(macroAction instanceof DoNothingMacroAction){ doNothing = true; return;};
        if(!(macroAction instanceof ScheduleNothingMacroAction)) for (var event : macroAction.getAtomicActions()) addCommand(event);
    }

    public Map<UUID, Queue<EmpireEvent>> getUnitCommandQueue() {
        return unitCommandQueue;
    }

    public Map<Position, Queue<EmpireEvent>> getCityCommandQueue() {
        return cityCommandQueue;
    }

    @Override
    public String toString() {
        return "CommandQueue{hashCode=" + Integer.toHexString(hashCode())+
                ", unitCommandQueue=" + unitCommandQueue +
                ", cityCommandQueue=" + cityCommandQueue +
                '}';
    }
}
