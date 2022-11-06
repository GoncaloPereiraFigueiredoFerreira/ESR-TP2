package org.example.speedNode.Nodes.Bootstrap;

import org.example.speedNode.Nodes.Bootstrap.Config.BootstrapConfig;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class Bootstrap implements Runnable{
    private ServerSocket ss;
    private final int ServerPort = 12345;
    private int soTimeout = 30000; //ServerSocket Timeout
    private int socketSoTimeout = 0; //Socket Timeout - O means infinite

    private BootstrapConfig bootstrapConfig;
    private AtomicBoolean closeServer = new AtomicBoolean(false); //Modified by thread handling a command line

    public Bootstrap(String Bootstrap_config_filename) throws FileNotFoundException {
        bootstrapConfig = BootstrapConfig.readFile(Bootstrap_config_filename);
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
                    try { new Thread(new BootstrapWorker(closeServer, s)).start(); }
                    catch (IOException ignored){}

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            ss.close();
        }catch (IOException ioe){
            System.out.println("Could not initialize server socket!");
        }
    }

    private void AcceptClientConnection(){

    }
}
