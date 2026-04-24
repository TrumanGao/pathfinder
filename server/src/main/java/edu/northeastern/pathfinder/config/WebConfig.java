package edu.northeastern.pathfinder.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Forwards non-asset, non-API paths to the SPA entry point so that
 * client-side routes survive a browser refresh when the React bundle
 * is served from Spring Boot's embedded static resources.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/")
                .setViewName("forward:/index.html");
        // Any single-segment or deeper path with no dot (i.e. not a static asset like /foo.js)
        // and not starting with /api/ gets forwarded to the SPA.
        registry.addViewController("/{path:[^\\.]*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/{path:^(?!api$).*}/{sub:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}
