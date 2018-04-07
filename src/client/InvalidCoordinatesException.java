package client;

/**
 * Created by niko on 4/2/18. Tiny class for semantic differences. Yay! #NotBloat
 */
public class InvalidCoordinatesException extends Exception {
    public InvalidCoordinatesException(String s) {
        super(s);
    }
}
