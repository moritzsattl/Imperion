package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.GameConfiguration;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.HashMap;
import java.util.Set;

public class DeterminizedEmpireGame extends Empire {

    DeterminizedEmpireMap map;

    public DeterminizedEmpireGame(Empire game, Set<Position> knownLocations, HashMap<EmpireUnit, LastSeenInfo> enemyUnitsLastSeen) {
        super(game);

        map = new DeterminizedEmpireMapFactory(game,knownLocations,enemyUnitsLastSeen).simpleDeterminzeMap();
    }

    public DeterminizedEmpireMap getDeterminizedMap() {
        return map;
    }
}
