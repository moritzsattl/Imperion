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
        switch (unitTypeId){
            case 1:
                return INFANTRY;
            case 2:
                return SCOUT;
            case 3:
                return CAVALRY;
        }

        return IDLE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
