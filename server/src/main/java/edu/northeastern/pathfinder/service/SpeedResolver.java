package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.config.RoutingProperties;
import edu.northeastern.pathfinder.graph.Edge;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EN: Resolves an effective edge speed for time-based routing.
 * Resolution order is intentionally simple and explainable:
 * 1) parse a small supported subset of maxspeed formats,
 * 2) fall back to highway-type defaults,
 * 3) fall back to one global speed.
 * It does not try to implement country-specific traffic rules in this phase.
 * 中文：为基于时间的路由解析边的有效速度。
 * 解析顺序刻意保持简单、可解释：
 * 1）先解析少量受支持的 maxspeed 格式，
 * 2）再回退到 highway 类型默认速度，
 * 3）最后回退到全局默认速度。
 * 本阶段不尝试实现国家或地区级别的交通规则。
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
