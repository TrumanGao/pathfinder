package edu.northeastern.pathfinder.dto;

import java.time.Instant;
import java.util.List;

public class AnnotationDto {

    public record CreateRequest(
            double lat,
            double lon,
            String category,
            String text,
            String author
    ) {
    }

    public record Response(
            long id,
            double lat,
            double lon,
            String category,
            String text,
            String author,
            Instant createdAt
    ) {
    }

    public record ListResponse(
            int count,
            List<Response> annotations
    ) {
    }
}
