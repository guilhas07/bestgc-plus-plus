package pt.ulisboa.tecnico.web.ist196392.bestgcpp.model;

import java.util.List;

public record ProfileAppResponse(
        String bestGC,
        int heapSize,
        float maxHeapSize,
        List<Float> cpuUsage,
        List<Float> ioTime,
        List<Float> cpuTime,
        float avgCpuUsage,
        float avgCpuTime,
        float avgIoTime,
        boolean cpuIntensive) {

}
