package com.roottrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
public class RootTraceApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(RootTraceApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        List<Path> candidatePaths = List.of(Path.of(".env"), Path.of("../.env"), Path.of("../../.env"));
        for (Path path : candidatePaths) {
            if (Files.isRegularFile(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                        int eqIdx = trimmed.indexOf('=');
                        if (eqIdx > 0) {
                            String key = trimmed.substring(0, eqIdx).trim();
                            String value = trimmed.substring(eqIdx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    System.out.println("[RootTrace] Loaded environment variables from: " + path.toAbsolutePath());
                    break;
                } catch (IOException e) {
                    System.err.println("[RootTrace] Could not read .env at " + path + ": " + e.getMessage());
                }
            }
        }
    }
}
