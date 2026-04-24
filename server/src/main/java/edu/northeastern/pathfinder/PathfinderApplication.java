package edu.northeastern.pathfinder;

import java.awt.Desktop;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PathfinderApplication {

	private static final Logger log = LoggerFactory.getLogger(PathfinderApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PathfinderApplication.class, args);
	}

	/**
	 * Open the default browser on the server URL when the app finishes booting.
	 * Disabled automatically in headless environments or when
	 * {@code pathfinder.launch.open-browser=false}.
	 */
	@Bean
	ApplicationListener<ApplicationReadyEvent> openBrowserOnStartup(
			@Value("${server.port:8080}") int port,
			@Value("${pathfinder.launch.open-browser:false}") boolean openBrowser) {
		return event -> {
			if (!openBrowser) {
				return;
			}
			if (java.awt.GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
				return;
			}
			Desktop desktop = Desktop.getDesktop();
			if (!desktop.isSupported(Desktop.Action.BROWSE)) {
				return;
			}
			URI uri = URI.create("http://localhost:" + port + "/");
			try {
				desktop.browse(uri);
				log.info("Opened browser at {}", uri);
			} catch (Exception ex) {
				log.warn("Failed to open browser at {}: {}", uri, ex.getMessage());
			}
		};
	}

}
