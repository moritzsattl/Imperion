package at.ac.tuwien.ifs.sge.agent;

public enum MacroActionType {
    MOVE_UNITS("MOVE UNITS");


    private final String displayName;

    MacroActionType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}