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
 * EN: Search API controller providing keyword search and nearby search.
 * 中文：搜索接口控制器，提供关键词搜索和附近搜索。
 */
@RestController
@RequestMapping("/api")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * EN: Keyword search with scored ranking, fuzzy matching, and optional type filtering.
     * 中文：带评分排序、模糊匹配和可选类型过滤的关键词搜索。
     */
    @GetMapping("/search")
    public SearchResponseDto search(
            @RequestParam("q") String query,
            @RequestParam(value = "types", required = false) List<String> types,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        List<SearchItem> items = searchService.search(query, normalizeTypes(types), normalizeTypes(tags), limit);
        List<SearchResponseDto.SearchResultDto> results = items.stream()
                .map(item -> toDto(item, null))
                .toList();

        return new SearchResponseDto(query, results.size(), results);
    }

    /**
     * EN: Nearby search: finds places within a radius of the given coordinates,
     * filtered by type, sorted by distance ascending.
     * 中文：附近搜索：在给定坐标半径内查找地点，按类型过滤，按距离升序排序。
     */
    @GetMapping("/search/nearby")
    public SearchResponseDto nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam(value = "types", required = false) List<String> types,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "radius", required = false, defaultValue = "1000") double radiusMeters,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        List<SearchService.NearbyResult> nearbyResults =
                searchService.searchNearby(lat, lon, normalizeTypes(types), normalizeTypes(tags), radiusMeters, limit);

        List<SearchResponseDto.SearchResultDto> results = nearbyResults.stream()
                .map(nr -> toDto(nr.item(), nr.distanceM()))
                .toList();

        String queryDesc = String.format("nearby(%.5f,%.5f,r=%.0fm)", lat, lon, radiusMeters);
        return new SearchResponseDto(queryDesc, results.size(), results);
    }

    private SearchResponseDto.SearchResultDto toDto(SearchItem item, Double distanceM) {
        return new SearchResponseDto.SearchResultDto(
                item.id(),
                item.name(),
                item.displayName(),
                item.type(),
                item.subType(),
                item.lat(),
                item.lon(),
                item.source(),
                item.routable(),
                item.metadata(),
                distanceM,
                item.studentTags()
        );
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
