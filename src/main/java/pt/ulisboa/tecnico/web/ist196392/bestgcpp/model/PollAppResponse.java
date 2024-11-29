package pt.ulisboa.tecnico.web.ist196392.bestgcpp.model;

public record PollAppResponse(
        String name,
        String command,
        float cpuUsage,
        float ioTime,
        float heapSize) {
}
