package speedNode.Nodes.Tables;

import speedNode.Nodes.OverlayNode.ControlLayer.ConnectionHandler;

public class NeighbourEntry {

    private String ip;
    private ConnectionHandler connectionHandler;
    private boolean wantsStream;
    private long timestamp;

    /*
     * Columns:
     *
     *     -> IP of the neighbour
     *     -> Connection Handler (Can determine if the neighbour is connected)
     *     -> Flag: Does it want the stream
     *     -> Timestamp of last jump
     *
     */

    public NeighbourEntry(String ip, ConnectionHandler connectionHandler, boolean wantsStream, long timestamp) {
        this.ip = ip;
        this.connectionHandler = connectionHandler;
        this.wantsStream = wantsStream;
        this.timestamp = timestamp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
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
}
