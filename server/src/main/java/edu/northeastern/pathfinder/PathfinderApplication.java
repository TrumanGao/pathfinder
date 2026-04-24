package edu.northeastern.pathfinder;

import java.util.Locale;

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
	 * Opens the default browser once Spring Boot is ready. Uses OS-native commands
	 * (rundll32 / open / xdg-open) instead of java.awt.Desktop because the AWT
	 * path reports headless=true inside jpackage console-subsystem launchers and
	 * silently no-ops.
	 */
	@Bean
	ApplicationListener<ApplicationReadyEvent> openBrowserOnStartup(
			@Value("${server.port:8080}") int port,
			@Value("${pathfinder.launch.open-browser:false}") boolean openBrowser) {
		return event -> {
			if (!openBrowser) {
				log.info("Browser auto-launch disabled (pathfinder.launch.open-browser=false)");
				return;
			}
			String url = "http://localhost:" + port + "/";
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			try {
				ProcessBuilder pb;
				if (os.contains("win")) {
					pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
				} else if (os.contains("mac")) {
					pb = new ProcessBuilder("open", url);
				} else {
					pb = new ProcessBuilder("xdg-open", url);
				}
				pb.start();
				log.info("Opened browser at {}", url);
			} catch (Exception ex) {
				log.warn("Failed to open browser at {}: {}. Please open it manually.",
						url, ex.getMessage());
			}
		};
	}

}
