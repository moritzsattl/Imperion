package at.ac.tuwien.ifs.sge.agent.determinizedmap;

import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrainType;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class DeterminizedEmpireMap extends EmpireMap{

    private Set<Position> knownPositions;


    public DeterminizedEmpireMap(EmpireTerrain[][] empireTiles, Map<Position, EmpireCity> citiesByPosition, Map<UUID, EmpireUnit> unitsById, Map<Integer, List<EmpireUnit>> unitsByPlayer, int numberOfPlayers, Set<Position> knownPositions) {
        super(empireTiles, citiesByPosition, unitsById, unitsByPlayer, numberOfPlayers);
        this.knownPositions = knownPositions;
    }
}
