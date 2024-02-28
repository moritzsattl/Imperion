package at.ac.tuwien.ifs.sge.agent.util.MacroAction;

import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;

// Does nothing, but wait if scheduled
public class WaitEvent implements EmpireEvent {

    private final Position empireCityPosition;

    public WaitEvent(Position empireCityPosition) {
        this.empireCityPosition = empireCityPosition;
    }

    public Position getEmpireCityPosition() {
        return empireCityPosition;
    }

    @Override
    public String toString() {
        return "city at "+ empireCityPosition + " wait";
    }
}
