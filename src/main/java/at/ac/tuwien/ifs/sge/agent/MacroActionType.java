package at.ac.tuwien.ifs.sge.agent;

public enum MacroActionType {
    EXPLORATION("EXPLORATION"),
    EXPANSION("EXPANSION"),

    ATTACK("ATTACK");


    private final String displayName;

    MacroActionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}