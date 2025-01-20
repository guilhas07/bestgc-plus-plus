package pt.ulisboa.tecnico.web.ist196392.bestgcpp.service;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class MatrixService {

    Matrix matrix;

    @Autowired
    public MatrixService(@Value("${gc-matrix-file}") String matrixFile) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            System.out.println("Loading matrix file: \"" + matrixFile + "\".");
            var matrixData = objectMapper.readValue(new File(matrixFile), FullMatrixData.class);

            this.matrix = matrixData.matrix();
            System.out.println("Matrix: " + matrix);
        } catch (Exception e) {
            // NOTE: If matrix file doesn't load successfully abort execution.
            e.printStackTrace();
            System.exit(1);
        }
    }

    public int[] getHeapSizes() {
        return matrix.keySet().stream().mapToInt(Integer::valueOf).sorted().toArray();
    }

    public BestGC getBestGC(float cpuAvgPercentage, float maxHeapUsed) {

        float throughputWeight = 0;
        float pauseTimeWeight = 0;

        // NOTE: Mapping the range [30,90] to [0,1] with this equation: y = x/60 - 0.5
        // and clamping values <30 to 0 and >90 to 1
        if (cpuAvgPercentage < 30)
            throughputWeight = 0;
        else if (cpuAvgPercentage > 90)
            throughputWeight = 1;
        else
            throughputWeight = (float) Math.round((cpuAvgPercentage / 60 - 0.5f) * 100) / 100;

        pauseTimeWeight = (float) Math.round((1 - throughputWeight) * 100) / 100;
        System.out.printf("CpuAvgPercentage=%s\tCalculated weights: throughput weight=%s, pause_time weight=%s\n",
                cpuAvgPercentage, throughputWeight,
                pauseTimeWeight);

        return getBestGC(maxHeapUsed, throughputWeight, pauseTimeWeight);
    }

    public BestGC getBestGC(float maxHeapUsed, double throughputWeight, double pauseTimeWeight) {
        int min = Integer.MAX_VALUE;
        for (String key : this.matrix.keySet()) {
            var heapSize = Integer.valueOf(key);
            if (heapSize >= maxHeapUsed && heapSize < min)
                min = heapSize;
        }
        System.out.printf("Weights: throughput weight=%s, pause_time weight=%s\n", throughputWeight, pauseTimeWeight);
        System.out.println("Selected Heap Size: " + min);

        var gcMetrics = this.matrix.get(String.valueOf(min));

        String bestgc = "";
        double value = Double.MAX_VALUE;
        System.out.println("Selecting best gc:");
        for (var entry : gcMetrics.entrySet()) {
            var perfMetric = entry.getValue();
            var gc = entry.getKey();
            double score = perfMetric.throughput() * throughputWeight + perfMetric.pause_time() * pauseTimeWeight;
            System.out.printf("GC %s score: %s\n", gc, score);
            if (score < value) {
                bestgc = entry.getKey();
                value = score;
            }

        }
        return new BestGC(bestgc, min);
    }
}

record BestGC(String gc, int heapSize) {
}

record FullMatrixData(Matrix matrix, List<String> garbage_collectors, Map<String, List<String>> benchmarks) {
}

class Matrix extends HashMap<String, Map<String, PerformanceMetrics>> {
};

record PerformanceMetrics(double throughput, double pause_time) {
}

record GCValue(String gc, double value) {
}
