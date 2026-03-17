package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.model.AlgorithmRequest;
import edu.northeastern.pathfinder.model.AlgorithmResponse;
import edu.northeastern.pathfinder.service.AlgorithmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Frontend API bridge.
 */
@RestController
@RequestMapping("/api")
public class AlgorithmController {
    private final AlgorithmService algorithmService;

    public AlgorithmController(AlgorithmService algorithmService) {
        this.algorithmService = algorithmService;
    }

    /** Compatibility endpoint kept from previous integration pass. */
    @PostMapping("/route")
    public AlgorithmResponse route(@RequestBody AlgorithmRequest request) {
        return algorithmService.findRoute(request);
    }

    @GetMapping("/map-info")
    public Map<String, Object> mapInfo() {
        return algorithmService.getMapInfo();
    }

    @PostMapping("/path-finding/compare")
    public Map<String, Object> compare(@RequestBody AlgorithmRequest request) {
        return algorithmService.comparePath(request);
    }

    @GetMapping("/poi-search")
    public Map<String, Object> poiSearch(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return algorithmService.searchPoi(q, limit);
    }

    @GetMapping("/node-info")
    public Map<String, Object> nodeInfo(
            @RequestParam("lng") double lng,
            @RequestParam("lat") double lat
    ) {
        return algorithmService.nearestNodeInfo(lat, lng);
    }
}
