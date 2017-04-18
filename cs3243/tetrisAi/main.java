package cs3243.tetrisAi;

import jneat.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Vector;

// Controller for the learning
public class main {
    // Parameters for learning
    private final static int numInputs = 9;
    private final static int numOutputs = 1;
    private final static int numThreads = 4;
    private final static int numGenerations = 1000000;
    private final static int gamesPerGeneration = 5;
    private static Population neatPop;
    // Storage for players and their scores
    private static ArrayList<Player> players = new ArrayList<>();
    private static ArrayList<Integer> scores = new ArrayList<>();
    private static ArrayList<ArrayList<Integer>> gameScores = new ArrayList<>();
    // Highest score to decide when to print to file
    private static int highestScore;
    private static int numPlayers;

    private static LinkedList<Thread> mainQueue = new LinkedList<>();

    public static void main(String[] strings) {
        // Initialise JNeat
        Neat.initbase();
        String paramsFile = "params.ne";
        boolean rc = Neat.readParam(paramsFile);
        if(rc) System.out.println("Params read successfully");
        else System.out.println("Params read failed");
        numPlayers = Neat.p_pop_size;

        // Initialise Population
        neatPop = new Population(numPlayers, numInputs, numOutputs, 10, true, 0.5);

        // Initialise Players
        for(int i = 0; i < numPlayers; ++i){
            players.add(new Player(numInputs));
            scores.add(0);
        }
        highestScore = 0;

        // Run genetic algorithm
        for(int gen = 0; gen < numGenerations; ++gen){
            // Input the new brains into the players and reset scores
            Vector neatOrgs = neatPop.getOrganisms();
            gameScores.clear();
            for(int i = 0; i < neatOrgs.size(); ++i){
                players.get(i).setBrain(((Organism) neatOrgs.get(i)).getNet());
                scores.set(i, 0);
                gameScores.add(new ArrayList<>());
            }

            // Make all the players play games using multithreading and add up scores across the games
            for(int j = 0; j < gamesPerGeneration; ++j) {
                mainQueue.clear();
                long seed = System.currentTimeMillis() + (long) j;
                for (int i = 0; i < players.size(); ++i) {
                    players.get(i).setSeed(seed);
                    Thread t = new Thread(players.get(i));
                    mainQueue.add(t);
                    if(i < numThreads) t.start();
                }
                for (int i = 0; i < players.size(); ++i) {
                    try {
                        mainQueue.get(i).join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(i + numThreads < mainQueue.size()) mainQueue.get(i + numThreads).start();
                    scores.set(i, scores.get(i) + players.get(i).getFinalScore());
                    gameScores.get(i).add(players.get(i).getFinalScore());
                }
            }

            // Evolve the population to generate the next generation
            boolean win = false;
            for(int i = 0; i < neatOrgs.size(); ++i){
                ((Organism)neatOrgs.get(i)).setFitness((double)scores.get(i)/(double)gamesPerGeneration);
                if(scores.get(i) > highestScore) {
                    win = true;
                    highestScore = scores.get(i);
                }
            }

            // If record fitness, print the result of this generation
            if(win){
                neatPop.print_to_file_by_species_plus_score(((double) highestScore / (double) gamesPerGeneration)+"("+gen+")"+".txt", gameScores);
            }

            // Print outputs to show learning progress
            System.out.println("\n\n\n\nGeneration " + gen);
            System.out.println(new SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(new java.util.Date()));

            // Form the next generation
            neatPop.epoch(gen);
        }
    }
}
