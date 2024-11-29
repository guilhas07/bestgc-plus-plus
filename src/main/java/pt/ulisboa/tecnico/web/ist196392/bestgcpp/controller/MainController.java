package pt.ulisboa.tecnico.web.ist196392.bestgcpp.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletResponse;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.ProfileAppRequest;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.RunAppRequest;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.service.MatrixService;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.service.ProfileService;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.service.RunService;

@Controller
public class MainController {

    @Autowired
    ProfileService profileService;

    @Autowired
    RunService runService;

    @Autowired
    MatrixService matrixService;

    @Value("${monitoring-time}")
    int monitoringTime;

    public MainController() {
    }

    @GetMapping("/")
    public String index(Model model) {
        System.out.println(monitoringTime);
        model.addAttribute("profile", new ProfileAppRequest(true, true, 1, 0, monitoringTime, null, null, null));
        model.addAttribute("jars", profileService.getJars());
        return "index";
    }

    @PostMapping(value = "/profile_app", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public String profileApplication(@ModelAttribute ProfileAppRequest profileRequest, Model model,
            HttpServletResponse response) {

        System.out.println(profileRequest);
        var file = profileRequest.file();
        var dest = Paths.get("jars").resolve(profileRequest.jar());
        if (file != null && !file.isEmpty()) {
            dest = Paths.get("jars").resolve(file.getOriginalFilename());
            try {
                Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            // Create new profile request with the jar file name resolved
            profileRequest = new ProfileAppRequest(profileRequest.automaticMode(), profileRequest.runApp(),
                    profileRequest.throughputWeight(), profileRequest.pauseTimeWeight(),
                    profileRequest.monitoringTime(), file.getOriginalFilename(), profileRequest.args(), null);
        }

        var updateProfileRequest = profileRequest;
        var optProfileResponse = profileService.profileApp(profileRequest, dest.toString());
        var wrapper = new Object() {
            boolean redirect = false;
        };
        optProfileResponse.ifPresentOrElse(
                profileResponse -> {
                    var runAppRequest = new RunAppRequest(updateProfileRequest.jar(), profileResponse.bestGC(),
                            updateProfileRequest.args(), profileResponse.heapSize(), null);
                    if (updateProfileRequest.runApp()) {
                        runService.runApp(runAppRequest);
                        wrapper.redirect = true;
                        return;
                    }

                    model.addAttribute("gcs", profileService.getGCs());
                    model.addAttribute("jars", profileService.getJars());

                    model.addAttribute("profileAppResponse", profileResponse);
                    model.addAttribute("runAppRequest", runAppRequest);
                },
                () -> model.addAttribute("profileAppResponse", null));

        if (wrapper.redirect) {
            // NOTE: to successfully redirect htmx form request inject a header
            // and use a empty template
            response.setHeader("HX-Redirect", "/dashboard");
            return "empty";
        }
        return "fragments/profileApp";
    }

    @GetMapping(value = "/run_app")
    public String runApp(Model model) {
        model.addAttribute("run", new RunAppRequest(null, null, null, 0, null));
        model.addAttribute("gcs", profileService.getGCs());
        model.addAttribute("jars", profileService.getJars());
        model.addAttribute("heapSizes", matrixService.getHeapSizes());
        return "runApp";
    }

    @PostMapping(value = "/run_app", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public String runApplication(@ModelAttribute RunAppRequest runAppRequest, Model model) {
        System.out.println(runAppRequest);

        var file = runAppRequest.file();
        var dest = Paths.get("jars").resolve(runAppRequest.jar());
        if (file != null && !file.isEmpty()) {
            dest = Paths.get("jars").resolve(file.getOriginalFilename());
            try {
                Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        var response = runService.runApp(runAppRequest);
        model.addAttribute("runAppResponse", response);
        return "runAppResponse";
    }

    @GetMapping(value = "/dashboard")
    public String getApps(Model model) {
        model.addAttribute("apps", runService.getApps());
        return "dashboard";
    }

}
