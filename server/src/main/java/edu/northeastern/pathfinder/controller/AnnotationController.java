package edu.northeastern.pathfinder.controller;

import edu.northeastern.pathfinder.dto.AnnotationDto;
import edu.northeastern.pathfinder.model.Annotation;
import edu.northeastern.pathfinder.service.AnnotationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/annotations")
public class AnnotationController {

    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    @GetMapping
    public AnnotationDto.ListResponse list(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam(value = "radius", required = false, defaultValue = "2000") double radius
    ) {
        List<Annotation> annotations = annotationService.findNearby(lat, lon, radius);
        List<AnnotationDto.Response> results = annotations.stream()
                .map(a -> new AnnotationDto.Response(a.getId(), a.getLat(), a.getLon(),
                        a.getCategory(), a.getText(), a.getAuthor(), a.getCreatedAt()))
                .toList();
        return new AnnotationDto.ListResponse(results.size(), results);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnnotationDto.Response create(@RequestBody AnnotationDto.CreateRequest request) {
        Annotation a = annotationService.create(
                request.lat(), request.lon(), request.category(), request.text(), request.author());
        return new AnnotationDto.Response(a.getId(), a.getLat(), a.getLon(),
                a.getCategory(), a.getText(), a.getAuthor(), a.getCreatedAt());
    }
}
