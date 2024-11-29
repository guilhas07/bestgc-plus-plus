package pt.ulisboa.tecnico.web.ist196392.bestgcpp.model;

import org.springframework.web.multipart.MultipartFile;

public record RunAppRequestBackup(
        String garbageCollector,
        String gcArgs,
        int heapSize,
        String customHeapGCPolicy,
        String jar,
        String args,
        boolean enableLog,
        MultipartFile file) {

    public RunAppRequestBackup {
        if (customHeapGCPolicy != null && customHeapGCPolicy.isEmpty())
            customHeapGCPolicy = null;

        if (gcArgs != null && gcArgs.isEmpty())
            gcArgs = null;

        if (args != null && args.isEmpty())
            args = null;

        if (heapSize <= 0) {
            throw new IllegalArgumentException(
                    "Heap size sould be a value greater than 0.");
        }
    }
}
