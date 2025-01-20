package pt.ulisboa.tecnico.web.ist196392.bestgcpp.exceptions;

public class FileCreationException extends RuntimeException {
    public FileCreationException() {
        super("Couldn't create file.");
    }

}
