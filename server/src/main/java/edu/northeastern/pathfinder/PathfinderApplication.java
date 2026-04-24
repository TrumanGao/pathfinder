package edu.northeastern.pathfinder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PathfinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(PathfinderApplication.class, args);
	}

}
