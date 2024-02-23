package at.ac.tuwien.ifs.sge.agent.util;


import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.action.MovementAction;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.order.start.MovementStartOrder;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Breadth First Search for Path-Finding
 */
public class BFS {

    /**
     * Returns null if path was not found
     * Returns all movement actions that are possible which lead towards the destination
     * // TODO: What happens if a certain action is rejected, because enemy or ally is in the pathway
     * // Solution: Check if movement is possible before scheduling movementAction
     */
    public static List<EmpireEvent> findShortestPath(EmpireUnit unit, Position destination, Empire game, int playerId){
        Imperion.logger.trace("Start findShortestPath " + unit + " to " + destination);

        var actions = new ArrayDeque<MovementStartOrder>();

        var node = bfs(unit.getPosition(), destination, game, playerId);
        Imperion.logger.trace("End bfs");
        Imperion.logAssertWithMessage(node != null, "No path was found for " + unit + " to " + destination);

        if(node == null) return null;

        // Build movement actions from path
        while (node.parent != null){
            actions.addFirst(new MovementStartOrder(unit, node.position));
            node = node.parent;
        }


        Imperion.logger.trace("Path is :" + actions);
        Imperion.logger.trace("End findShortestPath " + unit + " to " + destination);

        // Only return movement actions for visible tiles
        return new ArrayList<>(actions.stream().filter(order -> game.getBoard().getEmpireTiles()[order.getDestination().getY()][order.getDestination().getX()] != null).toList());
    }

    private static Node bfs(Position source, Position destination, Empire game, int playerId) {
        Imperion.logger.trace("Start bfs");

        var queue = new ArrayDeque<Node>();

        // Keeps track of already discovered positions
        var discovered = new ArrayDeque<Position>();

        var root = new Node(source);

        queue.add(root);
        discovered.add(root.position);

        while (!queue.isEmpty()){
            var node = queue.poll();
            Imperion.logger.trace(node);

            if(node.position.equals(destination)) return node;

            var neighbours = node.getNeighbours(game, playerId);

            for(var neighbour : neighbours){
                if(discovered.contains(neighbour.position)) continue;

                Imperion.logger.trace("Neighbour:" + neighbour);

                discovered.add(neighbour.position);
                neighbour.parent = node;
                queue.add(neighbour);
            }
        }

        // Not path was found
        return null;
    }

    private static class Node {
        public Position position;

        public Node parent;

        public Node(Position position) {
            this.position = position;
        }

        /**
         * Returns a list of positions which are neighbours of this node
         * neighbours are positions where its corresponding tile is null (not discovered yet)
         */
        public List<Node> getNeighbours(Empire game, int playerId) {
            Imperion.logger.trace("Start getNeighbours");
            var neighbours = new ArrayList<Node>();

            // 8-directional movement
            int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1},{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

            for (int[] direction : directions) {
                int nextX = direction[0] + position.getX();
                int nextY = direction[1] + position.getY();

                if (!game.getBoard().isInside(nextX, nextY)) continue;

                var tile = game.getBoard().getEmpireTiles()[nextY][nextX];

                Imperion.assertWithMessage(tile != null, "Tile is null Pos: (" + nextX + " " + nextY+")");

                // tile is not visible yet, just imagine it is possible to move there
                if(tile == null) {neighbours.add(new Node(new Position(nextX, nextY))); continue;}

                // tile is mountain, do not add
                if(tile.getMapIdentifier() == 'm') continue;

                //Imperion.logger.info(tile);
                boolean enemyTerritory = tile.getPlayerId() != playerId && tile.getPlayerId() != -1;
                boolean remainingSpace = tile.getMaxOccupants() > tile.getOccupants().size() || tile.getMaxOccupants() == -1;

                if(!enemyTerritory && tile.getMaxOccupants() != 0 && remainingSpace) neighbours.add(new Node(tile.getPosition()));
            }

            Imperion.logger.trace("Neighbours" + neighbours);
            Imperion.logger.trace("End getNeighbours");
            return neighbours;
        }

        @Override
        public String toString() {
            return "Node(" + position + ')';
        }

    }
}
