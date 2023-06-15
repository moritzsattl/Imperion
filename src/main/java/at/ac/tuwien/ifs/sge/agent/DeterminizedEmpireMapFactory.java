package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.util.Util;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.configuration.EmpireConfiguration;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrain;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireTerrainType;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.*;

public class DeterminizedEmpireMapFactory {

    private Empire game;
    private Map<Character, EmpireTerrainType> terrainDictionary;
    private Set<Position> knownPositions;

    private HashMap<EmpireUnit, LastSeenInfo> enemyUnitsLastSeen;
    private EmpireConfiguration config;

    public DeterminizedEmpireMapFactory(Empire game,Set<Position> knownPositions, HashMap<EmpireUnit, LastSeenInfo> enemyUnitsLastSeen) {
        this.game = game;
        this.enemyUnitsLastSeen = enemyUnitsLastSeen;

        terrainDictionary = new HashMap<>();
        for (var terrainType : game.getGameConfiguration().getTerrainTypes()) {
            terrainDictionary.put(terrainType.getMapIdentifier(), terrainType);
        }

        this.knownPositions = knownPositions;
        this.config = game.getGameConfiguration();
    }

    public DeterminizedEmpireMap simpleDeterminzeMap(){
        EmpireConfiguration configuration = game.getGameConfiguration();

        char[][] map = new char[configuration.getMapSize().getHeight()][configuration.getMapSize().getWidth()];


        Map<Position, EmpireCity> citiesByPositions = new HashMap<>();

        configuration.getStartingCities().forEach(c -> {
            citiesByPositions.put(c, new EmpireCity(new EmpireTerrainType(terrainDictionary.get('c')),c));
            map[c.getY()][c.getX()] = 'c';
        });


        ArrayList<Position> unknownPositions = new ArrayList<>();

        for (int y = 0; y < map.length; y++) {
            for (int x = 0; x < map[y].length; x++) {
                Position pos = new Position(x,y);
                if (!configuration.getStartingCities().contains(pos) && !knownPositions.contains(pos)) {
                    unknownPositions.add(pos);
                }
            }
        }

        // Set known positions to know map identifier
        for (var knownPos: knownPositions) {
            if(game.getBoard().getEmpireTiles()[knownPos.getY()][knownPos.getX()] != null){
                map[knownPos.getY()][knownPos.getX()] = game.getBoard().getEmpireTiles()[knownPos.getY()][knownPos.getX()].getMapIdentifier();
            }
        }

        // set all unknown positions to be grass
        // TODO: Add possible enemy units and city pos
        for (var pos: unknownPositions) {
            map[pos.getY()][pos.getX()] = 'g';
        }

        config.setMap(map);


        Map<Integer, List<EmpireUnit>> unitsByPlayer = new HashMap<>();

        for (int playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
            unitsByPlayer.put(playerId,game.getUnitsByPlayer(playerId));
        }

        return new DeterminizedEmpireMap(config.getEmpireTiles(),citiesByPositions,game.getUnitsById(),unitsByPlayer,game.getNumberOfPlayers(),knownPositions);
    }
    public DeterminizedEmpireMap determinizeMap() {

        Map<Position, EmpireCity> citiesByPositions = new HashMap<>();

        // Empire Map from Game
        EmpireTerrain[][] map = game.getBoard().getEmpireTiles();

        // Newly created determinizedEmpireTiles (this will be returned)
        EmpireTerrain[][] determinizedEmpireTiles = new EmpireTerrain[map.length][];

        // We first create a map for each enemy unit and their possible positions
        Map<EmpireUnit, List<EmpireTerrain>> possibleEnemyPositions = new HashMap<>();

        Set<UUID> alreadyPlaced = new HashSet<>(); // Track if the enemy unit has been placed or not

        Random random = new Random();

        // Set starting cities
        config.getStartingCities().forEach(pos -> {
            determinizedEmpireTiles[pos.getY()] = new EmpireTerrain[map[pos.getY()].length];
            determinizedEmpireTiles[pos.getY()][pos.getX()] = new EmpireCity(new EmpireTerrainType(terrainDictionary.get('c')), new Position(pos.getX(), pos.getY()));
            citiesByPositions.put(pos,(EmpireCity) determinizedEmpireTiles[pos.getY()][pos.getX()]);
        });


        for (int y = 0; y < determinizedEmpireTiles.length; y++) {
            if(determinizedEmpireTiles[y] == null){
                determinizedEmpireTiles[y] = new EmpireTerrain[map[y].length];
            }

            for (int x = 0; x < determinizedEmpireTiles[y].length; x++) {
                Position pos = new Position(x,y);
                if(!config.getStartingCities().contains(pos) && !knownPositions.contains(pos)) {
                    // Generate the probability for the tile to be a city or mountain

                    // TODO: Make city probabilityfunction correct
                    double cityProb = cityProbabilityFunction(pos);

                    // Generate a random number between 0 and 1
                    Double rand = random.nextDouble();

                    // Assign the tile based on the probabilities
                    if (rand <= cityProb) {
                        // Set tile to type City
                        determinizedEmpireTiles[y][x] = new EmpireCity(new EmpireTerrainType(terrainDictionary.get('c')), new Position(x, y));
                        citiesByPositions.put(pos,(EmpireCity) determinizedEmpireTiles[y][x]);
                    } else {
                        // Set tile to type grass
                        determinizedEmpireTiles[y][x] = new EmpireTerrain(new EmpireTerrainType(terrainDictionary.get('g')), new Position(x, y));
                    }


                    // Consider the possibility of enemy units
                    if (determinizedEmpireTiles[y][x].getMapIdentifier() != 'm') {
                        for (EmpireUnit enemyUnit : enemyUnitsLastSeen.keySet()) {
                            if(!alreadyPlaced.contains(enemyUnit.getId())){
                                LastSeenInfo lastSeenInfo = enemyUnitsLastSeen.get(enemyUnit);
                                double distanceToTile = Imperion.getEuclideanDistance(lastSeenInfo.getPosition(), pos);
                                long secondsPassed = (game.getGameClock().getGameDurationMs() - lastSeenInfo.getTimeStamp()) / 1000;

                                // Check if the enemy unit could have reached the tile based on its speed
                                if (distanceToTile <= secondsPassed * enemyUnit.getTilesPerSecond()) {
                                    // If the tile is a city, we place the enemy unit there immediately
                                    if (determinizedEmpireTiles[y][x].getMapIdentifier() == 'c') {
                                        alreadyPlaced.add(enemyUnit.getId());
                                        enemyUnit.setPosition(pos);
                                    } else {
                                        // Else, we add this tile as a possible position for this enemy unit
                                        if (!possibleEnemyPositions.containsKey(enemyUnit)) {
                                            possibleEnemyPositions.put(enemyUnit, new ArrayList<>());
                                        }
                                        possibleEnemyPositions.get(enemyUnit).add(determinizedEmpireTiles[y][x]);
                                    }
                                }
                            }
                        }
                    }

                }

            }
        }

        // Save tiles which were already placed with enmies
        Set<EmpireTerrain> occupied = new HashSet<>();

        // Now we place each enemy in one of their possible positions
        for (EmpireUnit enemyUnit : possibleEnemyPositions.keySet()) {
            // We check if the enemy unit has already been placed (in a city)
            if (alreadyPlaced.contains(enemyUnit.getId())) continue;

            List<EmpireTerrain> possiblePositions = possibleEnemyPositions.get(enemyUnit);

            // Choose a random tile from the possible positions
            EmpireTerrain chosenTile = Util.selectRandom(possiblePositions);

            // If already occupied choose another one, try this a maximum of 10 times
            int c = 0;
            while (c < 10 || occupied.contains(chosenTile)){
                chosenTile = Util.selectRandom(possiblePositions);
                c++;
            }

            // If chosen tile is empty
            if(!occupied.contains(chosenTile)){
                enemyUnit.setPosition(chosenTile.getPosition());
                occupied.add(chosenTile);
            }
        }

        Map<Integer, List<EmpireUnit>> unitsByPlayer = new HashMap<>();

        for (int playerId = 0; playerId < game.getNumberOfPlayers(); playerId++) {
            unitsByPlayer.put(playerId,game.getUnitsByPlayer(playerId));
        }

        return new DeterminizedEmpireMap(determinizedEmpireTiles,citiesByPositions,game.getUnitsById(),unitsByPlayer,game.getNumberOfPlayers(),knownPositions);
    }


    private double cityProbabilityFunction(Position pos) {
        // We need to find the closest starting city to the current position.
        double closestCityDistance = Double.MAX_VALUE;

        for (Position cityPos : config.getStartingCities()) {
            double distance = Imperion.getEuclideanDistance(cityPos, pos);
            if (distance < closestCityDistance) {
                closestCityDistance = distance;
            }
        }

        // a * e^(k*x) + c
        var gc = config.getGeneratorConfig();
        double k = Math.log(gc.getCityY1() / gc.getCityY0()) / gc.getCityX1();

        return gc.getCityY0() * Math.exp(k * closestCityDistance) + gc.getCityC();
    }

}
