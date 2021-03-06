package shared;

/**
 * Created by niko on 4/2/18.
 */
public class CommunicationConstants {
    public static final int GRID_WIDTH = 3;

    public static final int GRID_HEIGHT = 3;

    public static final int NUM_OF_NODES = GRID_WIDTH * GRID_HEIGHT;

    public static final int HOR_STRETCH = 100;

    public static final int VER_STRETCH = 100;

    public static final int WORLD_WIDTH = HOR_STRETCH * GRID_WIDTH;

    public static final int WORLD_HEIGTH = VER_STRETCH * GRID_HEIGHT;

    public static final String CLIENT_LOGIN_POS = "success";

    public static final String CLIENT_LOGIN_NEG = "nameInUse";
}
