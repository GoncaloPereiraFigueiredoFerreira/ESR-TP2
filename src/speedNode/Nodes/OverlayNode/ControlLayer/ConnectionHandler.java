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

public class ConnectionHandler implements Runnable{
    private final String neighbour;
    private TaggedConnection connection;
    private boolean keepRunning = true;
    private final ProtectedQueue<Tuple<String,Frame>> framesInputQueue;

    public ConnectionHandler(String neighbour, TaggedConnection connection, ProtectedQueue<Tuple<String,Frame>> framesInputQueue) {
        this.connection = connection;
        this.neighbour = neighbour;
        this.framesInputQueue = framesInputQueue;
    }

    public void run() {
        while (keepRunning) {
            Frame frame = null;

            try { frame = connection.receive(); }
            catch (SocketException se){ close(); }
            //catch (SocketTimeoutException ste) {}*/
            catch(IOException ignored) {}

            //Inserts frame in queue
            if (frame != null) {
                //Closes the connection if
                if(frame.getTag() == Tags.CLOSE_CONNECTION) {
                    close();
                    return;
                }

                framesInputQueue.pushElem(new Tuple<>(neighbour, frame));
            }

            //Checks if the thread has been interrupted. If so, then performs the closing procedure and terminates
            if (Thread.interrupted()) {
                close();
                return;
            }
        }
    }

    public void close() {
        keepRunning = false;

        //Sends message to close the connection
        Socket s = getSocket();
        if(s.isConnected()) {
            try { connection.send(0, Tags.CLOSE_CONNECTION, new byte[]{}); }
            catch (Exception ignored){}
        }

        try { connection.close(); }
        catch (Exception ignored){}
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
