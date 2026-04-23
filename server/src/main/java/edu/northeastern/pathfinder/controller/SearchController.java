package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.SearchResponseDto;
import edu.northeastern.pathfinder.service.SearchItem;
import edu.northeastern.pathfinder.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyword search over the normalized GeoJSON search index,
 * with optional type filtering.
 */
@RestController
@RequestMapping("/api")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SearchResponseDto search(
            @RequestParam("q") String query,
            @RequestParam(value = "types", required = false) List<String> types,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        List<SearchItem> items = searchService.search(query, normalizeTypes(types), limit);
        List<SearchResponseDto.SearchResultDto> results = items.stream()
                .map(item -> new SearchResponseDto.SearchResultDto(
                        item.id(),
                        item.name(),
                        item.displayName(),
                        item.type(),
                        item.subType(),
                        item.lat(),
                        item.lon(),
                        item.source(),
                        item.routable(),
                        item.metadata()
                ))
                .toList();

        return new SearchResponseDto(query, results.size(), results);
    }

    private List<String> normalizeTypes(List<String> rawTypes) {
        if (rawTypes == null || rawTypes.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String rawType : rawTypes) {
            if (rawType == null || rawType.isBlank()) {
                continue;
            }
            for (String part : rawType.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }
}
