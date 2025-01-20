package pt.ulisboa.tecnico.web.ist196392.bestgcpp.exceptions;

public class RunAppException extends RuntimeException {
    public RunAppException() {
        super("An error occurred when trying to run the application.");
    }

}
