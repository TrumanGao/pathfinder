package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.RoutingProperties;
import edu.northeastern.pathfinder.graph.Edge;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves an effective edge speed: maxspeed -> per-highway default -> global fallback.
 */
@Component
public class SpeedResolver {
    private static final Pattern SPEED_PATTERN = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)?)\\s*(mph|km/h|kph)?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private final RoutingProperties properties;

    public SpeedResolver(RoutingProperties properties) {
        this.properties = properties;
    }

    public double resolveMetersPerSecond(Edge edge) {
        Double parsed = parseMaxspeedMetersPerSecond(edge.getMaxspeedRaw());
        if (parsed != null && parsed > 0) {
            return parsed;
        }

        String highway = normalize(edge.getHighway());
        if (highway != null) {
            Double highwaySpeed = properties.getSpeedKphByHighway().get(highway);
            if (highwaySpeed != null && highwaySpeed > 0) {
                return kphToMetersPerSecond(highwaySpeed);
            }
        }

        return kphToMetersPerSecond(properties.getFallbackSpeedKph());
    }

    private Double parseMaxspeedMetersPerSecond(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }

        Matcher matcher = SPEED_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || unit.isBlank()) {
            return kphToMetersPerSecond(value);
        }

        String normalizedUnit = unit.toLowerCase(Locale.ROOT);
        if ("km/h".equals(normalizedUnit) || "kph".equals(normalizedUnit)) {
            return kphToMetersPerSecond(value);
        }
        if ("mph".equals(normalizedUnit)) {
            return value * 0.44704;
        }
        return null;
    }

    private double kphToMetersPerSecond(double kph) {
        return kph / 3.6;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
