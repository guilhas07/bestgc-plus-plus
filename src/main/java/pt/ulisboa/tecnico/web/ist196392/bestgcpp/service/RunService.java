package pt.ulisboa.tecnico.web.ist196392.bestgcpp.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.PollAppResponse;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.RunAppRequest;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.RunAppResponse;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.exceptions.RunAppException;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.AppInfo;

@Service
public class RunService {

    Map<Long, AppInfo> runningApps = new ConcurrentHashMap<>();

    @Autowired
    ProfileService profileService;

    public RunService() {
        // NOTE: To test frontend application with fake apps
        // runningApps.put(0L, new AppInfo("teste", "teste 1 2 3 4"));
        // runningApps.put(1L, new AppInfo("teste2", "giro hello"));
    }

    public RunAppResponse runApp(RunAppRequest runAppRequest) {

        try {
            var cmdArray = getExecJarCommand(runAppRequest);
            Process appProcess = Runtime.getRuntime().exec(cmdArray);

            long pid = appProcess.pid();
            String command = String.join(" ", cmdArray);

            // NOTE: Updating AppInfo if its present due to the operating system guarantee
            // of process Id uniqueness i.e., a previous app with this same pid is already
            // finished.
            runningApps.put(pid, new AppInfo(runAppRequest.jar(), command));
            return new RunAppResponse();

        } catch (IOException e) {
            e.printStackTrace();
            throw new RunAppException();
        }
    }

    public Map<Long, AppInfo> getApps() {
        runningApps.entrySet().removeIf((entry) -> {
            var id = entry.getKey();
            int exitCode;
            try {
                // NOTE: Test if app is alive sending a -0 signal. If it is alive a exit code of
                // 0 will be returned.
                Process aliveCheck = Runtime.getRuntime().exec(new String[] { "kill", "-0", Long.toString(id) });
                exitCode = aliveCheck.waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                // don't remove
                return false;
            }

            return exitCode != 0;
        });

        return runningApps;
    }

    public Map<Long, AppInfo> getApps(Set<Long> filterIds) {
        return runningApps.entrySet().stream().filter((entry) -> filterIds.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map<Long, PollAppResponse> pollApps(long[] pids) {
        Map<Long, PollAppResponse> response = new HashMap<>();
        for (long id : pids) {
            response.put(id, pollApp(id));
        }
        return response;
    }

    public PollAppResponse pollApp(long pid) {

        if (!runningApps.containsKey(pid))
            return null;

        // NOTE: for debugging purposes use reserved pids to test. Uncomment to test.
        // if (pid == 1 || pid == 0) {
        // if (pid == 0) {
        // var val = runningApps.get(pid);
        // runningApps.put(pid, new AppInfo(val.command(), val.name()));
        // }
        // var info = runningApps.get(pid);
        // return new PollAppResponse(info.name(), info.command(), 0, 0, 0);
        // }

        var info = runningApps.get(pid);
        TopCmdResponse top = profileService.executeTop(pid);
        HeapCmdResponse heap = profileService.executeHeapCommand(pid);

        if (top == null || heap == null) {
            System.out.printf("App %s is not alive. Removing it.", info.name());
            runningApps.remove(pid);
            return null;
        }

        return new PollAppResponse(info.name(), info.command(), top.cpuUsage(), top.ioTime(), heap.heapSize());
    }

    private String[] getExecJarCommand(RunAppRequest request) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("java");
        String gc = request.gc();
        sb.append(gc != null ? " -XX:+Use" + gc + "GC" : "");

        sb.append(" -Xms" + request.heapSize() + "m");
        sb.append(" -Xmx" + request.heapSize() + "m");

        // enable log
        System.out.println("Jar: " + request.jar());

        var index = request.jar().lastIndexOf(".");

        sb.append(" -Xlog:gc*,safepoint:file=" + request.jar().substring(0, index) + ".log::filecount=0");
        sb.append(" -jar " + "jars/" + request.jar() + " " + request.args());

        String command = sb.toString();
        System.out.println("Command: " + command);
        return command.split(" ");
    }

}
