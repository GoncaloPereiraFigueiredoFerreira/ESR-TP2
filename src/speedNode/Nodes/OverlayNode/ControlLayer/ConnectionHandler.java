package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Utilities.ProtectedQueue;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;
import speedNode.Utilities.Tuple;

import java.io.EOFException;
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

    //Timeouts Control
    private int socketTimeout = 1000000;
    private int timeoutsUntilConCheck = 10; //nr of timeouts until connection check
    private int timeoutsCounter = 0;
    private boolean timeoutCheck = false;


    public ConnectionHandler(String neighbour, TaggedConnection connection, ProtectedQueue<Tuple<String,Frame>> framesInputQueue, Logger logger) {
        this.connection = connection;
        this.neighbour = neighbour;
        this.framesInputQueue = framesInputQueue;
        this.logger = logger;
    }

    public ConnectionHandler(String neighbour, TaggedConnection connection, ProtectedQueue<Tuple<String, Frame>> framesInputQueue, Logger logger, int socketTimeout) {
        this.neighbour = neighbour;
        this.connection = connection;
        this.framesInputQueue = framesInputQueue;
        this.logger = logger;
        this.socketTimeout = socketTimeout;
    }

    public void run() {
        logger.info(neighbour + "'s connection receiver running...");
        setSocketTimeout(socketTimeout);

        while (keepRunning) {
            Frame frame;

            try {
                //If the 'timeoutCheck' flag is true, then a connection check is performed
                if (timeoutCheck)
                    connection.send(0, Tags.CONNECTION_CHECK, new byte[]{});

                frame = connection.receive();
                resetTimeoutVariables();

                handleFrame(frame);
            }
            catch (SocketTimeoutException | EOFException e){
                updateTimeoutVariables();
            }
            catch (SocketException se){
                logger.warning("Socket Exception in " + neighbour + "'s receiver.");
                keepRunning = false;
            }
            catch(IOException ignored) {}

            //Checks if the thread has been interrupted. If so, then performs the closing procedure and terminates
            if (Thread.interrupted()) {
                logger.warning(neighbour + "'s receiver got interruped.");
                keepRunning = false;
            }
        }

        close();
    }

    /* ***** Auxiliary Methods ***** */

    private void resetTimeoutVariables(){
        timeoutsCounter = 0; //resets timeout counter
        timeoutCheck = false; //resets the flag to avoid performing a connection check
    }

    public void updateTimeoutVariables(){
        //Next two lines is a way of reseting the counter and avoiding the overflow
        timeoutsCounter++;
        timeoutsCounter %= timeoutsUntilConCheck;

        //When the timeout reachs 0, a connection check is needed
        if(timeoutsCounter == 0) {
            if(timeoutCheck){
                logger.warning("Connection check failed for neighbour " + neighbour);
                keepRunning = false;
            }
            timeoutCheck = true; //sets flag indicating the need to perform a timeoutCheck
        }
    }

    private void handleFrame(Frame frame) throws IOException {
        int tag = frame.getTag();

        switch (tag){
            //Sends a frame to confirm the connection is alive
            case Tags.CONNECTION_CHECK -> connection.send(0, Tags.CONNECTION_CONFIRMATION, new byte[]{});
            //Connection confirmation frame is only to confirm the connection, and serves its purpose when the
            // frame is received, since the timeout variables are reset
            case Tags.CONNECTION_CONFIRMATION -> {}
            //Closes the connection if tag received asks for it
            case Tags.CLOSE_CONNECTION -> {
                logger.info(neighbour + " asked to close connection!");
                keepRunning = false;
            }
            //Inserts frame in queue
            default -> {
                framesInputQueue.pushElem(new Tuple<>(neighbour, frame));
                logger.info("Received frame from " + neighbour + " with tag " + frame.getTag());
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

        removeRoutes();

        logger.info("Closed receiver for " + neighbour + ".");
    }

    //TODO - Eliminar rotas (se estiver ativa nao esquecer de escolher outra, se nao der, avisar para tras que é preciso escolher uma nova rota) e marcar vizinho como inativo
    private void removeRoutes(){

    }


    /* ***** Getters ***** */

    private Socket getSocket() { return connection.getSocket(); }
    public TaggedConnection getTaggedConnection() { return connection; }
    public boolean isRunning() { return keepRunning; }

    /* ***** Setters ***** */

    public void setTaggedConnection(TaggedConnection connection) { this.connection = connection; }

    public boolean setSocketTimeout(int socketTimeout) {
        boolean ret;
        this.socketTimeout = socketTimeout;

        try {
            connection.getSocket().setSoTimeout(socketTimeout);
            logger.info("Socket Timeout set to " + socketTimeout + "ms.");
            ret = true;
        } catch (Exception e) {
            logger.warning("Exception occurred while trying to set socket timeout. " +
                    "Could not start connection receiver for neighbour " + neighbour);
            ret = false;
        }

        //If there was a problem setting the socket timeout, then the receiver should be closed.
        if(!ret) keepRunning = false;

        return ret;
    }
}
