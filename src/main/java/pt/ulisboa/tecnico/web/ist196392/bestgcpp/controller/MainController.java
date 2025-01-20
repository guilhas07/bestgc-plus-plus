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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.exceptions.BenchmarkException;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.exceptions.FileCreationException;
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

    @ExceptionHandler({ FileCreationException.class, BenchmarkException.class })
    public String fileCreationException(Model model, FileCreationException exception, HttpServletRequest request) {
        model.addAttribute("message", exception.getMessage());
        model.addAttribute("url", request.getRequestURI());
        return "fragments/error";
    }

    @GetMapping(value = { "/", "/profile_app" })
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
                e.printStackTrace();
                throw new FileCreationException();
            }

            // Create new profile request with the jar file name resolved
            profileRequest = new ProfileAppRequest(profileRequest.automaticMode(),
                    profileRequest.runApp(),
                    profileRequest.throughputWeight(), profileRequest.pauseTimeWeight(),
                    profileRequest.monitoringTime(), file.getOriginalFilename(),
                    profileRequest.args(), null);
        }

        var profileResponse = profileService.profileApp(profileRequest, dest.toString());
        var runAppRequest = new RunAppRequest(profileRequest.jar(), profileResponse.bestGC(),
                profileRequest.args(), profileResponse.heapSize(), null);

        if (profileRequest.runApp()) {
            runService.runApp(runAppRequest);
            // NOTE: to successfully redirect htmx form request inject a header
            // and use a empty template
            response.setHeader("HX-Redirect", "/dashboard");
            return "empty";
        }

        model.addAttribute("gcs", profileService.getGCs());
        model.addAttribute("jars", profileService.getJars());

        model.addAttribute("profileAppResponse", profileResponse);
        model.addAttribute("runAppRequest", runAppRequest);

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
    public String runApplication(@ModelAttribute RunAppRequest runAppRequest, Model model,
            HttpServletResponse response) {
        System.out.println(runAppRequest);

        var file = runAppRequest.file();
        var dest = Paths.get("jars").resolve(runAppRequest.jar());
        if (file != null && !file.isEmpty()) {
            dest = Paths.get("jars").resolve(file.getOriginalFilename());
            try {
                Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
                throw new FileCreationException();
            }

            // Create new profile request with the jar file name resolved
            runAppRequest = new RunAppRequest(file.getOriginalFilename(), runAppRequest.gc(), runAppRequest.args(),

                    runAppRequest.heapSize(), null);
        }

        runService.runApp(runAppRequest);

        // NOTE: to successfully redirect htmx form request inject a header
        // and use a empty template
        response.setHeader("HX-Redirect", "/dashboard");
        return "empty";
    }

    @GetMapping(value = "/dashboard")
    public String getApps(Model model) {
        model.addAttribute("apps", runService.getApps());
        return "dashboard";
    }

}
