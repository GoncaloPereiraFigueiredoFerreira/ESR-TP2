package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Utilities.ProtectedQueue;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;
import speedNode.Utilities.Tuple;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.Logger;

public class ConnectionHandler implements Runnable{
    private final String neighbour;
    private TaggedConnection connection;
    private boolean keepRunning = true;
    private final ProtectedQueue<Tuple<String,Frame>> framesInputQueue;
    private final Logger logger;

    public ConnectionHandler(String neighbour, TaggedConnection connection, ProtectedQueue<Tuple<String,Frame>> framesInputQueue, Logger logger) {
        this.connection = connection;
        this.neighbour = neighbour;
        this.framesInputQueue = framesInputQueue;
        this.logger = logger;
    }

    public void run() {
        logger.info(neighbour + "'s connection receiver running...");

        while (keepRunning) {
            Frame frame = null;

            try {
                frame = connection.receive();
                logger.info("Received frame from " + neighbour + " with tag " + frame.getTag());
            }
            catch (SocketException se){
                se.printStackTrace();
                logger.warning("Socket Exception in " + neighbour + "'s receiver.");
                close();
            }
            //catch (SocketTimeoutException ste) {}*/
            catch(IOException ignored) {}

            //Inserts frame in queue
            if (frame != null) {

                //Closes the connection if tag received asks for it
                if(frame.getTag() == Tags.CLOSE_CONNECTION) {
                    logger.info(neighbour + " asked to close connection!");
                    close();
                    return;
                }
                else framesInputQueue.pushElem(new Tuple<>(neighbour, frame));
            }

            //Checks if the thread has been interrupted. If so, then performs the closing procedure and terminates
            if (Thread.interrupted()) {
                logger.warning(neighbour + "'s receiver got interruped.");
                close();
                return;
            }
        }
    }

    public void close() {
        logger.info("Closing receiver for " + neighbour + "...");
        keepRunning = false;

        //Sends message to close the connection
        Socket s = getSocket();
        if(s.isConnected()) {
            try {
                connection.send(0, Tags.CLOSE_CONNECTION, new byte[]{});
                logger.info("Sent 'Close Connection' frame to " + neighbour);
            } catch (Exception ignored){}
        }

        try { connection.close(); }
        catch (Exception ignored){}

        logger.info("Closed receiver for " + neighbour + ".");
    }

    //TODO - Eliminar rotas (se estiver ativa nao esquecer de escolher outra, se nao der, avisar para tras que é preciso escolher uma nova rota) e marcar vizinho como inativo
    private void removeRoutes(){

    }

    // GETTERS
    private Socket getSocket() { return connection.getSocket(); }
    public TaggedConnection getTaggedConnection() { return connection; }
    public boolean isRunning() { return keepRunning; }

    // SETTERS
    public void setTaggedConnection(TaggedConnection connection) { this.connection = connection; }
}
