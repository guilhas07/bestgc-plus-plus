package pt.ulisboa.tecnico.web.ist196392.bestgcpp.model;

import org.springframework.web.multipart.MultipartFile;

public record RunAppRequest(
        String jar,
        String gc,
        String args,
        int heapSize,
        MultipartFile file) {

    public RunAppRequest {
    }
}
