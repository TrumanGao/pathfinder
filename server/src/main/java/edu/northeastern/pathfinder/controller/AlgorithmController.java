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
import java.util.List;

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

    @PostMapping("/route")
    public AlgorithmResponse route(@RequestBody AlgorithmRequest request) {
        return algorithmService.route(request);
    }

    @GetMapping("/map-info")
    public Map<String, Object> mapInfo() {
        return algorithmService.getMapInfo();
    }

    @GetMapping("/poi-search")
    public List<Map<String, Object>> poiSearch(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return algorithmService.searchPoi(q, limit);
    }

    @GetMapping("/nearest-node")
    public Map<String, Object> nearestNode(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon
    ) {
        return algorithmService.nearestNode(lat, lon);
    }
}
