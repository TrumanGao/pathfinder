package edu.northeastern.pathfinder.service;

import edu.northeastern.pathfinder.model.Annotation;
import edu.northeastern.pathfinder.model.AnnotationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * EN: Manages community annotations. Bounding-box query approximates a radius search
 * using a simple lat/lon offset (accurate enough at DC latitudes).
 * 中文：管理社区标注。使用简单的经纬度偏移量做包围盒查询来近似半径搜索。
 */
@Service
public class AnnotationService {

    private static final Set<String> VALID_CATEGORIES = Set.of("recommendation", "warning", "tip");

    private final AnnotationRepository repository;

    public AnnotationService(AnnotationRepository repository) {
        this.repository = repository;
    }

    public List<Annotation> findNearby(double lat, double lon, double radiusMeters) {
        double latOffset = radiusMeters / 111_320.0;
        double lonOffset = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(lat)));
        return repository.findInBoundingBox(
                lat - latOffset, lat + latOffset,
                lon - lonOffset, lon + lonOffset
        );
    }

    public Annotation create(double lat, double lon, String category, String text, String author) {
        if (!VALID_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Invalid category: " + category + ". Must be one of: " + VALID_CATEGORIES);
        }
        if (text == null || text.isBlank() || text.length() > 500) {
            throw new IllegalArgumentException("Text must be non-empty and at most 500 characters");
        }
        return repository.save(new Annotation(lat, lon, category, text.trim(), author == null ? "Anonymous" : author.trim()));
    }
}
