package pt.ulisboa.tecnico.web.ist196392.bestgcpp.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.ProfileAppRequest;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.ProfileAppResponse;

import pt.ulisboa.tecnico.web.ist196392.bestgcpp.exceptions.BenchmarkException;

@Service
public class ProfileService {

    @Autowired
    MatrixService matrixService;

    final private int profileInterval = 1;

    final private int CPU_CORES;
    final private int CPU_INTENSIVE = 60;

    public ProfileService() {
        CPU_CORES = Runtime.getRuntime().availableProcessors();
        System.out.println("CPU CORES: " + CPU_CORES);
    }

    /**
     * Profile application in order to discover the best Garbage Collector and the
     * minimal heap Size required to run it.
     * Method is synchronized so the benchmark is executed with minimal
     * interference.
     *
     * @param profileAppRequest
     * @param appPath           Path for application jar
     * @return ProfileAppResponse
     */
    public synchronized ProfileAppResponse profileApp(ProfileAppRequest profileAppRequest, String appPath) {

        Process appProcess = null;
        ProfileAppResponse response = null;

        final int monitoringTime = profileAppRequest.monitoringTime();
        System.out.println("Monitoring App with " + monitoringTime);

        List<Float> cpuUsages;
        List<Float> ioTimes;
        List<Float> cpuTimes;
        List<Float> heapSizes;

        int[] matrixHeapSizes = matrixService.getHeapSizes();
        int runId = -1;
        while (true) {
            runId++;
            int heapSize = matrixHeapSizes[runId];
            try {
                cpuUsages = new ArrayList<>();
                ioTimes = new ArrayList<>();
                cpuTimes = new ArrayList<>();
                heapSizes = new ArrayList<>();

                appProcess = Runtime.getRuntime()
                        .exec(getProfileJarCommand(appPath, profileAppRequest.args(), heapSize));
                long pid = appProcess.pid();
                System.out.println(pid);

                Thread.sleep(1000);
                long startTime = System.currentTimeMillis();
                while ((System.currentTimeMillis() - startTime) / 1000 < monitoringTime && appProcess.isAlive()) {

                    var topResponse = executeTop(pid);
                    if (topResponse == null)
                        break;

                    var heapResponse = executeHeapCommand(pid);
                    if (heapResponse == null)
                        break;

                    cpuTimes.add(topResponse.cpuTime());
                    cpuUsages.add(topResponse.cpuUsage());
                    ioTimes.add(topResponse.ioTime());
                    heapSizes.add(heapResponse.heapSize());
                }

                // The app is still running or finished with success
                if (appProcess.isAlive() || appProcess.exitValue() == 0) {
                    System.out.println("Profile ended. Process is Alive ?" + appProcess.isAlive());
                    break;
                }

                // App failed for every available heap size
                if (appProcess.exitValue() != 0 && runId + 1 == matrixHeapSizes.length) {
                    System.out.println("App failed for every available heap size");
                    System.out.println("Error: " + new String(appProcess.getErrorStream().readAllBytes()));
                    System.out.println("Output: " + new String(appProcess.getInputStream().readAllBytes()));
                    throw new BenchmarkException("Application failed for every available heap size.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new BenchmarkException("An error occurred during the Benchmark.");
            }
            System.out.println("Retrying...");

        }
        Optional<Float> optMaxHeap = heapSizes.stream().max(Float::compare);
        if (optMaxHeap.isEmpty()) {
            System.out.println("[Error]: Application run successfully however no data was collected.");
            throw new BenchmarkException("Application run successfully however no data was collected.");
        }

        float maxHeap = optMaxHeap.get() / 1024;
        float maxHeapUsage = maxHeap * 1.2f; // NOTE: recommend heapSize

        float totalCpuUsage = 0;
        float totalCpuTime = 0;
        float totalIoTime = 0;
        for (int i = 0; i < cpuUsages.size(); i++) {
            totalCpuUsage += cpuUsages.get(i);
            totalCpuTime += cpuTimes.get(i);
            totalIoTime += ioTimes.get(i);
        }
        float avgCpuUsage = (float) Math.round(totalCpuUsage / cpuUsages.size() * 100) / 100;
        float avgCpuTime = (float) Math.round(totalCpuTime / cpuUsages.size() * 100) / 100;
        float avgIoTime = (float) Math.round(totalIoTime / cpuUsages.size() * 100) / 100;

        boolean isCpuIntensive = totalCpuUsage / cpuUsages.size() >= CPU_INTENSIVE;
        BestGC bestGC = null;
        if (profileAppRequest.automaticMode()) {
            bestGC = matrixService.getBestGC(avgCpuUsage, maxHeapUsage);
        } else {
            bestGC = matrixService.getBestGC(maxHeapUsage,
                    profileAppRequest.throughputWeight(), profileAppRequest.pauseTimeWeight());
        }

        response = new ProfileAppResponse(bestGC.gc(), bestGC.heapSize(), maxHeap, cpuUsages, ioTimes,
                cpuTimes, avgCpuUsage, avgCpuTime, avgIoTime, isCpuIntensive);
        System.out.println(response);

        if (appProcess != null && appProcess.isAlive())
            appProcess.destroy();

        return response;

    }

    HeapCmdResponse executeHeapCommand(long pid) {
        try {

            var heapCommand = getHeapCommand(pid);
            Process p = Runtime.getRuntime().exec(heapCommand);
            try (var b = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                // NOTE: If process doesn't exist this returns 0.00
                String line = b.readLine();
                float heapSize = Float.valueOf(line);
                if (heapSize == 0)
                    return null;
                return new HeapCmdResponse(heapSize);
            }
        } catch (IOException e) {
            // Intentionally empty
            e.printStackTrace();
        }
        return null;
    }

    TopCmdResponse executeTop(long pid) {
        try {
            Process p = Runtime.getRuntime().exec(getTopCommand(pid));

            try (var b = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                var lines = b.lines().collect(Collectors.toList());
                if (lines.size() != 8) {
                    // NOTE: correct top output has 8 lines. When it doesn't probably means the
                    // process already died
                    System.out.println("Warning: Couldn't execute top for app with pid: " + pid);
                    return null;
                }

                // NOTE: from man top(1)
                // us, user : time running un-niced user processes
                // wa, IO-wait : time waiting for I/O completion
                // %Cpu(s): 15.1 us, 2.2 sy, 0.0 ni, 81.2 id, 0.0 wa, 0.0 hi, 1.6 si, 0.0 st
                Pattern pattern = Pattern.compile("(\\d+.\\d+) us.*(\\d+.\\d+) wa");
                Matcher matcher = pattern.matcher(lines.get(2));
                if (!matcher.find()) {
                    System.out.println("Error: couldn't match cpu us time and IO-wait time in " + lines.get(2));
                    System.exit(1);
                }

                float us = (float) Math.round(Float.valueOf(matcher.group(1)) * 100) / 100;
                float wa = (float) Math.round(Float.valueOf(matcher.group(2)) * 100) / 100;

                lines = lines.subList(lines.size() - 2, lines.size());
                if (!lines.get(0).trim().split("\\s+")[8].equals("%CPU")) {
                    System.out.println("Error: Top command with wrong format");
                    System.exit(1);
                }

                float cpuUsage = (float) Math.round(Float.valueOf(lines.get(1).trim().split("\\s+")[8]) * 100)
                        / 100
                        / CPU_CORES;

                return new TopCmdResponse(us, cpuUsage, wa);
            }
        } catch (IOException e) {
            // Intentionally empty
            e.printStackTrace();
        }
        return null;
    }

    public String[] getGCs() {
        // TODO: find / implement a way to get available GCs
        return new String[] { "G1", "Parallel", "Z" };
    }

    public String[] getJars() {
        File jarsFolder = new File("jars");
        FilenameFilter filter = (dir, name) -> name.endsWith(".jar");
        Optional<File[]> files = Optional.ofNullable(jarsFolder.listFiles(filter));
        List<String> fileNames = new ArrayList<>();

        files.ifPresent((_files) -> Arrays.stream(_files).forEach(f -> fileNames.add(f.getName())));

        fileNames.sort(Comparator.naturalOrder());
        return fileNames.toArray(String[]::new);
    }

    private String[] getProfileJarCommand(String app, String args, int heapSize) {
        String minHeapSize = "-Xms" + heapSize + "m";
        String maxHeapSize = "-Xmx" + heapSize + "m";
        System.out.println("java -jar " + maxHeapSize + " " + minHeapSize + " " + app + " " + args);

        return ("java -jar " + maxHeapSize + " " + minHeapSize + " " + app + " " + args).split(" ");
    }

    private String[] getTopCommand(long pid) {
        return ("top -bn 1 -p " + pid).split(" ");
    }

    private String[] getHeapCommand(long pid) {
        // NOTE: Using /bin/bash -c to use pipes. \\n to prevent early expansion.
        return new String[] { "/bin/bash", "-c",
                "jstat -gc " + pid + " | awk 'END {sum = $4 + $6 + $8 + $10 + $12; printf \"%.2f\\n\", sum}'" };
    }

}

record TopCmdResponse(float cpuTime, float cpuUsage, float ioTime) {
};

record HeapCmdResponse(float heapSize) {
};
