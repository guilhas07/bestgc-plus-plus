package pt.ulisboa.tecnico.web.ist196392.bestgcpp.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.AppInfo;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.PollAppResponse;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.service.MatrixService;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.service.ProfileService;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.service.RunService;

@RestController
public class RunController {

    @Autowired
    ProfileService profileService;

    @Autowired
    RunService runService;

    @Autowired
    MatrixService matrixService;

    public RunController() {
    }

    @GetMapping("/poll")
    public Map<Long, PollAppResponse> pollApps(long[] ids) {
        return runService.pollApps(ids);
    }

    @GetMapping("/apps")
    public Map<Long, AppInfo> apps() {
        return runService.getApps();
    }
}
