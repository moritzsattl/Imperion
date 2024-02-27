package at.ac.tuwien.ifs.sge.agent.util.UnitRole;

import at.ac.tuwien.ifs.sge.game.empire.map.Position;

/**
 * Each unit is assigned a order which the unit tries to fulfill
 */
public class UnitOrder {
    private Position destination;

    private OrderType orderType;

    public UnitOrder(Position destination, OrderType orderType) {
        this.destination = destination;
        this.orderType = orderType;
    }
}
