public class Instantiator {
}

/*
        int left = row * numCols + ((col - 1 + numCols) % numCols);
        int right = row * numCols + ((col + 1) % numCols);
        int top = ((row - 1 + numRows) % numRows) * numCols + col;
        int bottom = ((row + 1) % numRows) * numCols + col;
        int neighborIds[] = {left, right, top, bottom};

 */