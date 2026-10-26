package com.groupnine.bumberoos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling // NotificationBroadcaster's keep-alive
public class BumberoosApplication {

	// Timestamps are zone-less LocalDateTime values, so the JVM clock decides what they mean.
	// Pinning it to UTC makes every stored time UTC, whatever the host machine's zone is.
	// The UI converts to Philippine time for display.
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(BumberoosApplication.class, args);
	}

}
