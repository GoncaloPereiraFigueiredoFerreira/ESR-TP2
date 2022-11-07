package speedNode.Nodes.OverlayNode;

import speedNode.Nodes.Serialize;
import speedNode.TaggedConnection.Frame;
import speedNode.TaggedConnection.TaggedConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class OverlayNode implements Runnable{
    private Socket s;
    private final int bootstrapPort = 12345;
    private final String bootstrapIP;
    TaggedConnection tc;

    public OverlayNode(String bootstrapIP) {
        this.bootstrapIP = bootstrapIP;
    }

    public void run(){

        byte[] b = {};
        try {
            this.s = new Socket(this.bootstrapIP, this.bootstrapPort);
            tc = new TaggedConnection(this.s);

            tc.send(0,1,b); //Send request with tag 1
            Frame frame = tc.receive();
            List<String> ips = Serialize.deserialize(frame.getData());
            System.out.println(ips); //TODO - remover print
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void launch(List<String> args) {
        if (args == null || args.size() == 0) {
            System.out.println("No arguments were given!");
            return;
        }
        OverlayNode node = new OverlayNode(args.get(0));
        node.run();
    }
}
