package com.manulaiko.shinshijiru.libman;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;

@SpringBootApplication
@Slf4j
public class LibmanApplication implements ApplicationRunner {
    public static void main(String[] args) {
        SpringApplication.run(LibmanApplication.class, args);
    }

    @Autowired
    private Manager manager;

    /**
     * Callback used to run the bean.
     *
     * @param args incoming application arguments
     */
    public void run(ApplicationArguments args) {
        if (!args.containsOption("path")) {
            log.error("Specify a path to the library with --path");

            return;
        }

        log.info("Library path set to {}", args.getOptionValues("path"));

        var libs = manager.scan(
				args.getOptionValues("path")
						.stream()
						.map(Path::of)
						.toList()
		);

        log.info("Result {}", libs);
    }
}
