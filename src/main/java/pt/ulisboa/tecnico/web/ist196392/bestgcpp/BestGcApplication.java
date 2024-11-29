package pt.ulisboa.tecnico.web.ist196392.bestgcpp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import pt.ulisboa.tecnico.web.ist196392.bestgcpp.model.ProfileAppRequest;
import pt.ulisboa.tecnico.web.ist196392.bestgcpp.service.ProfileService;

@SpringBootApplication
public class BestGcApplication {

    @Autowired
    private ProfileService profileService;

    @Value("${monitoring-time}")
    private int monitoringTime;

    public static void main(String... args) {
        SpringApplication.run(BestGcApplication.class, args);
    }

    @Profile("!test")
    @Component
    @ConditionalOnNotWebApplication
    class ConsoleRunner implements CommandLineRunner {

        @Override
        public void run(String... args) {

            if (args.length < 2) {
                System.err.println("Please specify the application jar and the --wp or --wt");
                System.exit(1);
            }

            String pathToJar = args[0];
            float throughputWeight = -1;
            float pauseTimeWeight = -1;
            String jarArgs = "";
            boolean automaticMode = false;
            boolean runApp = true;

            for (int i = 1; i < args.length; i++) {
                System.out.println("Parsing: '" + args[i] + "'");
                switch (args[i]) {
                    case String s when s.contains("--wt=") -> {
                        throughputWeight = Float.valueOf(s.substring(5));
                        pauseTimeWeight = 1 - throughputWeight;
                    }
                    case String s when s.contains("--wp=") -> {
                        pauseTimeWeight = Float.valueOf(s.substring(5));
                        throughputWeight = 1 - pauseTimeWeight;
                    }
                    case String s when s.contains("--monitoringTime=") ->
                        monitoringTime = Integer.valueOf(s.substring("--monitoringTime=".length()));
                    case String s when s.contains("--args=") ->
                        jarArgs = s.substring("--args=".length());
                    case String s when s.contains("--automatic") ->
                        automaticMode = true;
                    case String s when s.contains("--no-run") ->
                        runApp = false;
                    default -> System.out.println("Option " + args[i] + " not recognized.");
                }
            }

            if (automaticMode) {
                System.out.println("Running BestGC with automaticMode. Weights will be ignored.");
                throughputWeight = 1;
                pauseTimeWeight = 0;
            }

            if (pauseTimeWeight < 0 || throughputWeight < 0 || throughputWeight + pauseTimeWeight != 1) {
                System.err.println("The sum of throughputWeight and pauseTimeWeight should be equal to 1");
                System.exit(1);
            }

            var response = profileService.profileApp(
                    new ProfileAppRequest(automaticMode, runApp, throughputWeight, pauseTimeWeight, monitoringTime,
                            pathToJar,
                            jarArgs,
                            null),
                    pathToJar);
            System.out.println(response);
        }
    }
}
