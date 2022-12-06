package speedNode.Nodes.Server;

import speedNode.Utilities.LoggingToFile;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;

public class TestServer {
    private final String ipNode;
    private final int ssPort = 54321;
    private final Logger logger;

    public TestServer(String ipNode){
        this.ipNode = ipNode;
        this.logger = LoggingToFile.createLogger("Server" + ipNode + ".txt", "", true);
    }

    public void run(){
        try{
            Socket s = new Socket(ipNode, ssPort);
            TaggedConnection tc = new TaggedConnection(s);
            logger.info("Connected to overlay node " + ipNode);

            tc.send(0, Tags.CONNECT_AS_SERVER_EXCHANGE, new byte[]{});
            logger.info("Sent request to overlay node " + ipNode + " to connect as server.");

            Frame frame = tc.receive();
            if(frame.getTag() == Tags.CONNECT_AS_SERVER_EXCHANGE){
                logger.info("OverlayNode " + ipNode + " accepted request to connect as server.");
            }

        }catch (IOException ioe){
            //TODO - handle exception
            ioe.printStackTrace();
        }
    }

    public static void launch(List<String> args){
        if (args == null || args.size() == 0) {
            System.out.println("No arguments were given!");
            return;
        }

        TestServer server = new TestServer(args.get(0));
        server.run();
    }
}
