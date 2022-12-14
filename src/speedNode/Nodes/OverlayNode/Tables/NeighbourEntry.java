package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Nodes.OverlayNode.ControlLayer.ConnectionHandler;

public class NeighbourEntry {

    private String name;
    private ConnectionHandler connectionHandler;
    private boolean wantsStream;
    private long timestamp;
    private String localIP;
    private String neighbourIP;

    /*
     * Columns:
     *
     *     -> Name of the neighbour
     *     -> Connection Handler (Can determine if the neighbour is connected)
     *     -> Flag: Does it want the stream
     *     -> Timestamp of last jump
     *     -> Local IP used to contact the neighbour
     *     -> Neighbour's IP
     */

    public NeighbourEntry(String name, ConnectionHandler connectionHandler, boolean wantsStream, long timestamp, String localIP, String neighbourIP) {
        this.name = name;
        this.connectionHandler = connectionHandler;
        this.wantsStream = wantsStream;
        this.timestamp = timestamp;
        this.localIP = localIP;
        this.neighbourIP = neighbourIP;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConnectionHandler getConnectionHandler() {
        return connectionHandler;
    }

    public void setConnectionHandler(ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }

    public boolean isConnected() {
        return connectionHandler != null && connectionHandler.isRunning();
    }

    public boolean getWantsStream() {
        return wantsStream;
    }

    public void setWantsStream(boolean wantsStream) {
        this.wantsStream = wantsStream;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getLocalIP() { return localIP; }

    public String getNeighbourIP(){
        return this.neighbourIP;
    }

    public void setNeighbourIP(String neighbourIP){
        this.neighbourIP = neighbourIP;
    }

    public void setLocalIP(String localIP) { this.localIP = localIP; }

    @Override
    public String toString() {
        return "{" +
                "ip='" + name + '\'' +
                ", wantsStream=" + wantsStream +
                ", timestamp=" + timestamp +
                ", running=" + (connectionHandler != null && connectionHandler.isRunning()) +
                '}';
    }
}
