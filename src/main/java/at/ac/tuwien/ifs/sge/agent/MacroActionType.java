package at.ac.tuwien.ifs.sge.agent;

public enum MacroActionType {
    EXPLORATION("EXPLORATION"),
    EXPANSION("EXPANSION");


    private final String displayName;

    MacroActionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}