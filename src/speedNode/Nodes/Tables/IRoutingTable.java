package speedNode.Nodes.Tables;

public interface IRoutingTable {
    /**
     * Adds a new path to a server
     * @param ServerIp The ip for the streaming server
     * @param Provider The ip of the neighbour node that transmits the stream
     * @param Jumps The number of jumps to the server to the current node
     * @param Time The measured time from the server to the current node
     * @param active If this connection is active
     * @return Returns true if the connection was successfully added
     */
    boolean addServerPath(String ServerIp, String Provider, int Jumps, float Time, boolean active);

    /**
     * Updates the metrics to a server path
     * @param ServerIp The ip for the streaming server
     * @param Provider The ip of the neighbour node that transmits the stream
     * @param Jumps The new number of jumps
     * @param Time The new time it takes
     * @return Returns true if the connection was successfully added
     */
    boolean updateMetrics(String ServerIp,String Provider, int Jumps, float Time);

    /**
     * Updates the active state of a connection
     * @param ServerIp The ip for the streaming server
     * @param Provider The ip of the neighbour node that transmits the stream
     * @param activate Flag that indicates if the connection should be active or not
     * @return Returns true if the state was correctly updated
     */
    boolean updateActiveState(String ServerIp,String Provider, boolean activate);




}