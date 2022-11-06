package org.example.speedNode.Nodes.Bootstrap;

import org.example.speedNode.Nodes.Bootstrap.Config.BootstrapConfig;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Bootstrap implements Runnable{
    private ServerSocket ss;
    private final int ServerPort = 12345;
    private int soTimeout = 0; //ServerSocket Timeout
    private int socketSoTimeout = 0; //Socket Timeout - O means infinite

    private Map<String, List<String>> nodesMap;
    private AtomicBoolean closeServer = new AtomicBoolean(false); //Modified by thread handling a command line

    public Bootstrap(String Bootstrap_config_filename) throws FileNotFoundException {
        nodesMap = BootstrapConfig.getNodesMapFromFile(Bootstrap_config_filename);
    }

    @Override
    public void run() {
        try {
            ss = new ServerSocket(ServerPort);
            ss.setSoTimeout(soTimeout);

            while (!closeServer.get()) {
                try {
                    Socket s = ss.accept();
                    s.setSoTimeout(socketSoTimeout);

                    //TODO - see how to wake threads so they can check the closeServer bool and end on command
                    try { new Thread(new BootstrapWorker(closeServer, s, nodesMap)).start(); }
                    catch (IOException ignored){}

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            ss.close();
            closeServer.set(true);
        }catch (IOException ioe){
            System.out.println("Could not initialize server socket!");
        }
    }

    public static void main(String[] args) {
        String filename = "bootstrap.yaml";

        for(int i = 1; i < args.length; i++){
            switch (args[i]){
                case "--filename":
                    if(i + 1 < args.length){
                        filename = args[i+1];
                        i++;
                    }
                    break;

                case "--help":
                    System.out.println("Use the flag '--filename' to specify the path to the .yaml config file.");
            }
        }

        try {
            Bootstrap bootstrap = new Bootstrap(filename);
            bootstrap.run();
        }catch (IOException ioe){
            System.out.println("Config file '" + filename + "' does not exist!");
        }
    }
}
