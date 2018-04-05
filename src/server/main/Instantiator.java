package server.main;

import server.NeighbourInfo;
import server.NeighbourInfo_itf;
import server.Node;
import static shared.CommunicationConstants.*;

import java.util.ArrayList;
import java.util.List;

public class Instantiator {

    private static final String HOSTNAME="localhost";

    public static void main (String [] args) {
        for (int row = 0; row < GRID_HEIGTH; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                List<NeighbourInfo_itf> neighours = new ArrayList<>();
                int left = col <= 0 ? -1 : row * GRID_WIDTH + (col - 1);
                int right = col >= GRID_WIDTH - 1 ? -1 : row * GRID_WIDTH + (col + 1);
                int top = row <= 0 ? -1 : (row - 1) * GRID_WIDTH + col;
                int bot = row >= GRID_HEIGTH - 1 ? -1 : (row + 1) * GRID_WIDTH + col;

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

                Node newNode = new Node(row * GRID_WIDTH + col, NUM_OF_NODES, neighours);
                Thread t = new Thread(newNode);
                t.start();
            }
        }
    }
}