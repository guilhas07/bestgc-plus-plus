package pt.ulisboa.tecnico.web.ist196392.bestgcpp.model;

import org.springframework.web.multipart.MultipartFile;

// @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProfileAppRequest(
        boolean automaticMode,
        boolean runApp,
        double throughputWeight,
        double pauseTimeWeight,
        int monitoringTime,
        String jar,
        String args,
        MultipartFile file) {
    // String userAvailableMemory,

    // NOTE: compact constructor runs after canonical constructor
    public ProfileAppRequest {
        if (pauseTimeWeight < 0 || throughputWeight < 0 || throughputWeight + pauseTimeWeight != 1) {
            throw new IllegalArgumentException(
                    "Throughput and Pause Weight sould be positive and their sum equal to 1.");
        }
        if (monitoringTime <= 0)
            throw new IllegalArgumentException("Monitoring Time must be a positive value");
    }
}
