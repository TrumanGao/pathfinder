package edu.northeastern.pathfinder.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnotationRepository extends JpaRepository<Annotation, Long> {

    @Query("SELECT a FROM Annotation a WHERE " +
            "a.lat BETWEEN :minLat AND :maxLat AND " +
            "a.lon BETWEEN :minLon AND :maxLon " +
            "ORDER BY a.createdAt DESC")
    List<Annotation> findInBoundingBox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon
    );
}
