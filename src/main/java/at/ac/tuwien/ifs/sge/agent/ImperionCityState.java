package at.ac.tuwien.ifs.sge.agent;

public enum ImperionCityState {
    IDLE("IDLE"),
    INFANTRY("INFANTRY"),
    SCOUT("SCOUT"),
    CAVALRY("CAVALRY");

    private final String displayName;

    ImperionCityState(String displayName) {
        this.displayName = displayName;
    }
    public static ImperionCityState mapProductionUnitTypeToCityState(int unitTypeId){
        return switch (unitTypeId) {
            case 1 -> INFANTRY;
            case 2 -> SCOUT;
            case 3 -> CAVALRY;
            default -> IDLE;
        };

    }

    @Override
    public String toString() {
        return displayName;
    }
}
