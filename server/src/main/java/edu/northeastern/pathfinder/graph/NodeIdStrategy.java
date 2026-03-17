package edu.northeastern.pathfinder.graph;

import java.math.BigDecimal;

/**
 * Stable coordinate-based node id strategy.
 *
 * The same coordinate pair always produces the same node id string.
 */
public final class NodeIdStrategy {
    private NodeIdStrategy() {
    }

    public static String fromCoordinate(String lonRaw, String latRaw) {
        String lon = normalizeDecimalText(lonRaw, "lon");
        String lat = normalizeDecimalText(latRaw, "lat");
        return "n:" + lat + "," + lon;
    }

    private static String normalizeDecimalText(String value, String axis) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invalid coordinate input: " + axis + " is blank or null");
        }
        BigDecimal decimal = new BigDecimal(value.trim());
        return decimal.stripTrailingZeros().toPlainString();
    }
}
