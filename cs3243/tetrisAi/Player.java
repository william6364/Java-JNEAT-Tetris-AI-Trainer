package cs3243.tetrisAi;

import jneat.Network;
import java.util.ArrayList;
import java.util.Random;

// Class that simulates the tetris games based on the AIs given to them
public class Player implements Runnable {

    /* Shape Descriptions */
    //the next several arrays define the piece vocabulary in detail
    //width of the pieces [piece ID][rotationIndexation]
    private static int[][] pWidth = {
            {2},
            {1, 4},
            {2, 3, 2, 3},
            {2, 3, 2, 3},
            {2, 3, 2, 3},
            {3, 2},
            {3, 2}
    };

    //height of the pieces [piece ID][rotationIndexation]
    private static int[][] pHeight = {
            {2},
            {4, 1},
            {3, 2, 3, 2},
            {3, 2, 3, 2},
            {3, 2, 3, 2},
            {2, 3},
            {2, 3}
    };

    private static int[] pRotations = {
            1, 2, 4, 4, 4, 2, 2
    };

    // [piece type][rotationIndexation][bottom position from left to right]
    private static int[][][] pBottom = {
            {{0, 0}},
            {{0}, {0, 0, 0, 0}},
            {{0, 0}, {0, 1, 1}, {2, 0}, {0, 0, 0}},
            {{0, 0}, {0, 0, 0}, {0, 2}, {1, 1, 0}},
            {{0, 1}, {1, 0, 1}, {1, 0}, {0, 0, 0}},
            {{0, 0, 1}, {1, 0}},
            {{1, 0, 0}, {0, 1}}
    };

    // [piece type][rotationIndexation][top position from left to right]
    private static int[][][] pTop = {
            {{2, 2}},
            {{4}, {1, 1, 1, 1}},
            {{3, 1}, {2, 2, 2}, {3, 3}, {1, 1, 2}},
            {{1, 3}, {2, 1, 1}, {3, 3}, {2, 2, 2}},
            {{3, 2}, {2, 2, 2}, {2, 3}, {1, 2, 1}},
            {{1, 2, 2}, {3, 2}},
            {{2, 2, 1}, {2, 3}}
    };

    // Number of distinct pieces
    private final static int NUM_PIECES = 7;

    // Dimensions of board
    private final static int NUM_ROWS = 20;
    private final static int NUM_COLS = 10;

    // Maximum number of pieces to be played before force end
    private final static int TOTAL_GAME_COUNT = 1000000000;
    private int gameCount = TOTAL_GAME_COUNT;

    // Brain of the AI that stores the neural network
    private Brain brain;

    // Number of inputs to be passed into the nerual network
    private int numInputs;

    // Array of inputs to be passed into the neural network
    private double[] inputs;

    // Array to store the board state as an array of integers
    private int[] rows = new int[NUM_ROWS];

    // Array to store original rows array for resetting purposes after each possible move
    private int[] currentRows = new int[NUM_ROWS];

    // Array to store height of each column
    private int[] top = new int[NUM_COLS];

    // Array to store original top array for resetting purposes after each possible move
    private int[] currentTop = new int[NUM_COLS];

    // ArrayList to store the coordinates taken up by the new piece
    private ArrayList<Integer> filledCoordinates = new ArrayList<>();

    // Tile specific feature values
    private int wallContact = 0;
    private int floorContact = 0;
    private int pieceContact = 0;

    // Number of lines cleared
    private int score = 0;

    // Random piece generator
    private long seed;
    private Random random = new Random();

    // For multi-threading
    private Thread t;

    // Constructor
    Player(int numInputs) {
        this.numInputs = numInputs;
        inputs = new double[numInputs];
        brain = new Brain();
    }

    // Accessor functions
    public void setSeed(long seed){
        this.seed = seed;
    }

    public void setBrain(Network network){
        brain.setNetwork(network);
    }

    @Override
    public void run() {
        // Reset game
        reset();

        // Stop if maximum number of pieces is reached
        while (gameCount-- > 0) {
            // Generate random piece
            int tileChosen = (int) (Math.random() * NUM_PIECES);

            // Find and perform best move
            int[] nextMove = findNextMove(tileChosen);
            int numRowsCleared = performMove(tileChosen, nextMove[0], nextMove[1]);

            if (numRowsCleared == -1) {
                // If game is lost
                return;
            } else {
                // Add number of lines cleared to score
                score += numRowsCleared;
            }
        }
    }

    private int performMove(int pieceIndex, int rotationIndex, int leftPosition) {
        // Reset tile specific feature values
        wallContact = 0;
        floorContact = 0;
        pieceContact = 0;

        // Height if the first column makes contact
        int height = top[leftPosition] - pBottom[pieceIndex][rotationIndex][0];

        // For each column beyond the first in the piece
        for (int c = 0; c < pWidth[pieceIndex][rotationIndex]; c++) {
            // Height if this column makes contact
            height = Math.max(height, top[leftPosition + c] - pBottom[pieceIndex][rotationIndex][c]);

            // Floor Contact
            if (top[leftPosition + c] == -1 && pBottom[pieceIndex][rotationIndex][c] == 0) {
                floorContact++;
            }
        }

        // Check if game ends after performing this move
        if (height + pHeight[pieceIndex][rotationIndex] >= NUM_ROWS) {
            return -1;
        }

        // For each column in the piece - fill in the appropriate blocks
        filledCoordinates.clear();
        for (int i = 0; i < pWidth[pieceIndex][rotationIndex]; i++) {
            // From bottom to top of brick
            for (int h = height + pBottom[pieceIndex][rotationIndex][i] + 1; h <= height + pTop[pieceIndex][rotationIndex][i]; h++) {
                rows[h] |= (1 << (i + leftPosition)); // Fill the square in the board
                filledCoordinates.add(h * NUM_COLS + i + leftPosition); // Store the coordinates filled by the new piece by converting to integer
            }
        }

        // Wall Contact
        // If piece hugs left wall
        if (leftPosition == 0) {
            wallContact += pTop[pieceIndex][rotationIndex][0] - pBottom[pieceIndex][rotationIndex][0];
        }
        // If piece hugs right wall
        if (leftPosition + pWidth[pieceIndex][rotationIndex] - 1 == NUM_COLS - 1) {
            wallContact += pTop[pieceIndex][rotationIndex][pWidth[pieceIndex][rotationIndex] - 1] -
                    pBottom[pieceIndex][rotationIndex][pWidth[pieceIndex][rotationIndex] - 1];
        }

        // Piece Contact
        for (int coordinate : filledCoordinates) {
            // Find original coordinate from integer
            int row = coordinate / NUM_COLS;
            int col = coordinate % NUM_COLS;

            // Check if the squares next to each piece square were already filled
            int left = row * NUM_COLS + col - 1;
            int right = row * NUM_COLS + col + 1;
            int down = (row - 1) * NUM_COLS + col;
            // left side
            if (col != 0 && !filledCoordinates.contains(left) && ((rows[row] & (1 << (col - 1))) > 0)) {
                pieceContact++;
            }
            // right side
            if (col != NUM_COLS - 1 && !filledCoordinates.contains(right) && ((rows[row] & (1 << (col + 1))) > 0)) {
                pieceContact++;
            }
            // down side
            if (row != 0 && !filledCoordinates.contains(down) && ((rows[row - 1] & (1 << col)) > 0)) {
                pieceContact++;
            }
        }

        // Calculate new board after rows are cleared
        int rowsCleared = 0;
        for (int r = height + 1; r < NUM_ROWS; r++) {
            // If row is full
            if (rows[r] + 1 == (1 << NUM_COLS)) {
                rowsCleared++;
            }
            // Otherwise, shift row down by number of lines cleared
            else if (rowsCleared > 0) {
                rows[r - rowsCleared] = rows[r];
            }
        }

        // Reset top rows based on number of rows cleared
        for (int r = NUM_ROWS - 1; r >= NUM_ROWS - rowsCleared; r--) {
            rows[r] = 0;
        }

        // Reset top array then calculate new values of top array
        int hasBlocked = 0;
        for (int c = 0; c < NUM_COLS; c++) {
            top[c] = -1;
        }

        // Search downwards
        for (int r = NUM_ROWS - 1; r >= 0; r--) {
            // Find which columns are filled in this row that have not been filled before
            int topSquares = (rows[r] & ~hasBlocked);
            // Update top array for each column that is now filled but was not filled before
            while (topSquares > 0) {
                top[Integer.numberOfTrailingZeros(topSquares)] = r;
                topSquares ^= Integer.lowestOneBit(topSquares);
            }
            // Update which columns have already been filled
            hasBlocked |= rows[r];
        }
        return rowsCleared;
    }

    private int[] findNextMove(int tileType) {
        // Store best move and its score
        int[] bestMove = new int[]{-1, -1};
        double bestOutput = 0.0;
        //Save current board
        for (int i = 0; i < NUM_COLS; i++) {
            currentTop[i] = top[i];
        }
        for (int r = 0; r < NUM_ROWS; r++) {
            currentRows[r] = rows[r];
        }
        //Try all possible moves
        for(int i = 0; i < pRotations[tileType]; ++i){
            for(int j = 0; j <= NUM_COLS - pWidth[tileType][i]; ++j){

                // If losing state, number of lines cleared is -10000.
                int linesCleared = performMove(tileType, i, j);
                if (linesCleared < 0) continue; // losing move, do not consider.

                // Reset feature values then proceed to calculate all the feature values
                for (int k = 0; k < numInputs; k++) inputs[k] = 0.0;

                // 0. 2 Power of Lines Cleared.
                inputs[0] = (double) (1 << linesCleared);
                for (int c = 0; c < NUM_COLS; c++) {
                    // 1. Sum of Heights of each column
                    inputs[1] += (double) ((top[c] + 1));

                    // 2. Bumpiness
                    if (c > 0) {
                        inputs[2] += (double) ((top[c] - top[c - 1]) * (top[c] - top[c - 1]));
                    }
                }
                int hasBlocked = 0;
                for (int r = NUM_ROWS - 1; r >= 0; r--) {
                    // 3. Sum of Heights of each block
                    inputs[3] += (double) ((r + 1) * Integer.bitCount(rows[r]));

                    // 5. Number of Holes
                    inputs[5] += (double) (Integer.bitCount(hasBlocked & ~rows[r]));
                    hasBlocked |= rows[r];
                }
                int hasHoles = 0;
                for (int r = 0; r < NUM_ROWS; r++) {
                    // 4. Blockades
                    inputs[4] += (double) (Integer.bitCount(hasHoles & rows[r]));
                    hasHoles |= (~rows[r]);
                }

                // Piece-Specific Features
                inputs[6] = (double) (wallContact);
                inputs[7] = (double) (floorContact);
                inputs[8] = (double) (pieceContact);

                // Get evaluation of board from neural network and update if move is better or if it is first legal move
                double output = brain.Update(inputs);
                if (bestMove[0] == -1 || output > bestOutput) {
                    bestMove = new int[]{i, j};
                    bestOutput = output;
                }

                // Reset board for simulation of next move
                for (int r = 0; r < NUM_ROWS; r++) {
                    rows[r] = currentRows[r];
                }
                for (int c = 0; c < NUM_COLS; c++) {
                    top[c] = currentTop[c];
                }

            }
        }
        // If all moves are losing moves, return first move otherwise return best move
        if(bestMove[0] == -1) bestMove = new int[]{0,0};
        return bestMove;
    }

    public int getFinalScore(){
        // Return number of lines cleared
        return score;
    }

    private void reset() {
        // Reset seed
        random.setSeed(seed);

        // Reset board
        for(int i = 0; i < NUM_ROWS; i++){
            rows[i] = 0;
        }
        for (int i = 0; i < NUM_COLS; i++) {
            top[i] = -1;
        }

        // Reset score
        score = 0;

        // Reset pieces played
        gameCount = TOTAL_GAME_COUNT;
    }
}