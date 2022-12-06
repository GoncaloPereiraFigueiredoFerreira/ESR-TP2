package speedNode.Nodes.Client;

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;

import java.io.IOException;
import java.net.Socket;

public class TestClient {
    private final String ipNode;
    private final int ssPort = 54321;

    public TestClient(String ipNode){
        this.ipNode=ipNode;
    }

    public void run(){
        try{
            Socket s = new Socket(ipNode, ssPort);
            TaggedConnection tc = new TaggedConnection(s);

            tc.send(0, Tags.CONNECT_AS_CLIENT_EXCHANGE,new byte[]{});

            Frame frame=  tc.receive();
            if(frame.getTag()==Tags.CONNECT_AS_CLIENT_EXCHANGE){
                System.out.println("fixe");
            }

        }catch (IOException ioe){
            //TODO - handle exception
            ioe.printStackTrace();
        }
    }
}
