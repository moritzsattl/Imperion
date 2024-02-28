package at.ac.tuwien.ifs.sge.agent.util.UnitRole;

public enum OrderType {
    PRODUCE("PRODUCE"),
    MOVE("MOVE"),
    EXPANSION("EXPANSION"),
    CONQUER("CONQUER"),

    DO_NOTHING("DO_NOTHING"),

    SCHEDULE_NOTHING("SCHEDULE_NOTHING"),

    WAIT("WAIT");

    private final String type;

    OrderType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }
}
