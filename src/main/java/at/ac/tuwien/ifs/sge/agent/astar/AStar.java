package at.ac.tuwien.ifs.sge.agent.astar;

import at.ac.tuwien.ifs.sge.agent.GameStateNode;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.map.EmpireMap;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;

import java.util.*;

public class AStar {

    private final AStarNode endNode;
    private final TreeSet<AStarNode> openList;
    private final HashSet<AStarNode> openSet;
    private final boolean[][] closedList;
    private final int[][] gCosts;
    private final int[][] hCosts;

    private final EmpireMap map;
    private final int playerId;
    private final Logger log;

    public <A> AStar(Position startPos, Position endPos, GameStateNode<A> gameStateNode, int playerId, Logger log) {
        map = gameStateNode.getGame().getBoard();

        //if(map == null){
        //    log.info("Map in AStar is null");
        //}
        Comparator<AStarNode> comparator = Comparator.comparingInt(AStarNode::getTfCost);
        openList = new TreeSet<>(comparator);
        openSet = new HashSet<>();

        //log.info("Starting Accessing map.getEmpireTiles() in A Star");
        gCosts = new int[map.getEmpireTiles().length][map.getEmpireTiles()[0].length];
        hCosts = new int[map.getEmpireTiles().length][map.getEmpireTiles()[0].length];
        closedList = new boolean[map.getEmpireTiles().length][map.getEmpireTiles()[0].length];
        //log.info("Finished accessing map.getEmpireTiles() in A Star");

        AStarNode start = new AStarNode(startPos);
        endNode = new AStarNode(endPos);

        openList.add(start);
        openSet.add(start);
        gCosts[start.getX()][start.getY()] = 0;
        hCosts[start.getX()][start.getY()] = getEstimatedDistance(start, endNode);
        start.sethCosts(hCosts[start.getX()][start.getY()]);

        this.playerId = playerId;
        this.log = log;
    }

    public AStarNode findPath() {
        while (!openList.isEmpty()) {
            AStarNode currentNode = openList.pollFirst();
            if (currentNode == null) return null;

            closedList[currentNode.getX()][currentNode.getY()] = true;

            //log.info(currentNode);
            if (isEndNode(currentNode)) {
                return currentNode;
            } else {

                addAdjacentNodes(currentNode);
            }
        }
        return null; // no path found
    }

    private void addAdjacentNodes(AStarNode currentNode) {
        // 8-directional movement
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1},{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

        for (int[] direction : directions) {
            int nextX = direction[0] + currentNode.getX();
            int nextY = direction[1] + currentNode.getY();

            if(withinBounds(nextX, nextY)){
                try{
                    var tile = map.getTile(nextX,nextY);
                    // Ignore when tiles are not visited, but don't ignore if tiles are occupied or mountains
                    if (!closedList[nextX][nextY] && (tile == null || (tile.getOccupants() != null && map.isMovementPossible(nextX,nextY,playerId)))) {
                        AStarNode adjacentNode = new AStarNode(new Position(nextX,nextY));
                        int gCost = gCosts[currentNode.getX()][currentNode.getY()] + 1;
                        hCosts[nextX][nextY] = getEstimatedDistance(adjacentNode, endNode);

                        if (!openSet.contains(adjacentNode)) {
                            adjacentNode.setPreviousNode(currentNode);
                            adjacentNode.sethCosts(hCosts[nextX][nextY]);
                            gCosts[nextX][nextY] = gCost;
                            adjacentNode.setfCosts(gCost + hCosts[nextX][nextY]);
                            openList.add(adjacentNode);
                            openSet.add(adjacentNode);
                        } else {
                            AStarNode existingNode = openList.stream().filter(n -> n.equals(adjacentNode)).findFirst().orElse(null);
                            if (existingNode != null && gCost < gCosts[existingNode.getX()][existingNode.getY()]) {
                                openList.remove(existingNode);
                                openSet.remove(existingNode);
                                existingNode.setPreviousNode(currentNode);
                                gCosts[existingNode.getX()][existingNode.getY()] = gCost;
                                existingNode.setfCosts(gCost + hCosts[nextX][nextY]);
                                openList.add(existingNode);
                                openSet.add(existingNode);
                            }
                        }
                    }
                }catch (Exception e){
                    log.info(e);
                }
            }

        }
    }

    private boolean withinBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < map.getEmpireTiles().length && y < map.getEmpireTiles()[0].length;
    }

    private boolean isEndNode(AStarNode node) {
        return node.getPosition().equals(endNode.getPosition());
    }

    private int getEstimatedDistance(AStarNode node1, AStarNode node2) {
        int dx = Math.abs(node1.getX() - node2.getX());
        int dy = Math.abs(node1.getY() - node2.getY());
        return Math.max(dx, dy);  // Chebyshev distance
    }


}
