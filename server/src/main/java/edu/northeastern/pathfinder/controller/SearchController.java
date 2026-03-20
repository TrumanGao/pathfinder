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
 * EN: Search API controller for the current normalized GeoJSON-backed search model.
 * It exposes simple keyword search with optional type filtering and does not implement fuzzy search or ranking engines.
 * 中文：面向当前 GeoJSON 标准化搜索模型的搜索接口控制器。
 * 它只提供简单关键词搜索和可选 type 过滤，不实现模糊搜索或复杂排序引擎。
 */
@RestController
@RequestMapping("/api")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * EN: Searches the normalized SearchItem dataset by case-insensitive substring match.
     * Accepts `q`, optional `types`, and optional `limit`.
     * Current limitations: no typo tolerance, no fuzzy search, and no advanced ranking.
     * 中文：对标准化 SearchItem 数据集执行不区分大小写的子串搜索。
     * 接收 `q`、可选 `types` 和可选 `limit`。
     * 当前限制：不支持拼写纠错、不支持模糊搜索，也不支持高级排序。
     */
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
