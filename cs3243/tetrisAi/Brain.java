package cs3243.tetrisAi;

import jneat.NNode;
import jneat.Network;

// Class that stores and processes the neural network
public class Brain {
    // Neural network
    private Network network;

    // Setter function
    public void setNetwork(Network network){
        this.network = network;
    }

    // Get output based in given inputs
    public double Update(double[] inputs){
        // Load these inputs into the neural network.
        network.load_sensors(inputs);

        int net_depth = network.max_depth();
        // First activate from sensor to next layer
        network.activate();

        // Next activate each layer until the last level is reached
        for (int relax = 0; relax <= net_depth; relax++) {
            network.activate();
        }

        // Retrieve outputs from the final layer.
        return ((NNode) network.getOutputs().elementAt(0)).getActivation();
    }
}
