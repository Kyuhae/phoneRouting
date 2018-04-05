import java.util.ArrayList;
import java.util.List;

public class Instantiator {

    private static final String HOSTNAME="localhost";

    public static void main (String [] args) {
        for (int row = 0; row < CommunicationConstants.GRID_HEIGTH; row++) {
            for (int col = 0; col < CommunicationConstants.GRID_WIDTH; col++) {
                List<NeighbourInfo_itf> neighours = new ArrayList<>();
                int left = col <= 0 ? -1 : row * CommunicationConstants.GRID_WIDTH + (col - 1);
                int right = col >= CommunicationConstants.GRID_WIDTH - 1 ? -1 : row * CommunicationConstants.GRID_WIDTH + (col + 1);
                int top = row <= 0 ? -1 : (row - 1) * CommunicationConstants.GRID_WIDTH + col;
                int bot = row >= CommunicationConstants.GRID_HEIGTH - 1 ? -1 : (row + 1) * CommunicationConstants.GRID_WIDTH + col;

                if (left >= 0) {
                    neighours.add(new NeighbourInfo(left, left + "_queue", HOSTNAME));
                }
                if (right >= 0) {
                    neighours.add(new NeighbourInfo(right, right + "_queue", HOSTNAME));
                }
                if (top >= 0) {
                    neighours.add(new NeighbourInfo(top, top + "_queue", HOSTNAME));
                }
                if (bot >= 0) {
                    neighours.add(new NeighbourInfo(bot, bot + "_queue", HOSTNAME));
                }

                Node newNode = new Node(row * CommunicationConstants.GRID_WIDTH + col, neighours);
                Thread t = new Thread(newNode);
                t.start();
            }
        }
    }
}