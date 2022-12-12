package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Nodes.OverlayNode.ControlLayer.ConnectionHandler;

import java.util.List;

public interface INeighbourTable {

    /*
     * Table that contains the columns:
     *
     *     -> IP of the neighbour
     *     -> Connection Handler (Can determine if the neighbour is connected)
     *     -> Flag: Does it want the stream
     *     -> Timestamp of last jump
     *
     */


    /**
     * Adds a new neighbour to the neighbour list if he doesnt already exist. The neighbour will be considered inactive and not connected.
     *
     * @param neighbourName Name of the new neighbour
     * @param localIP Local IP
     * @param neighbourIP Neighbour's IP
     * @return Returns true if the neighbour was added to the list, and false otherwise
     */
    boolean addNeighbour(String neighbourName, String localIP, String neighbourIP);

    /**
     * Method to get the neighbours of the node
     *
     * @return Returns a list of the neighboring ips
     */
    List<String> getNeighbours();

    /**
     * Method to get the neighbours that are connected
     *
     * @return Returns a list of the neighboring ips
     */
    List<String> getConnectedNeighbours();

    /**
     * Method to get the neighbours that do not have connection
     *
     * @return Returns a list of the neighboring ips
     */
    List<String> getUnconnectedNeighbours();

    /**
     * Returns true if the neighbour is receiving a stream through the current node
     *
     * @param ip Ip of neighbour
     * @return Returns true if the neighbour is receiving a stream through the current node, or false otherwise
     */
    boolean wantsStream(String ip);

    /**
     * Returns the state of a neighbouring node
     *
     * @param ip Ip of neighbour
     * @return Returns true if the neighbour is active, or false otherwise
     */
    boolean isConnected(String ip);


    /**
     * Returns the connection handler associated with the neighbour node.
     *
     * @param neighbourName IP of neighbour
     * @return connection handler associated with the neighbour node.
     */
    ConnectionHandler getConnectionHandler(String neighbourName);

    /**
     * Replaces the connection handler
     * @param ip IP of the neighbour
     * @param ch New connection handler
     * @return previous connection handler
     */
    ConnectionHandler updateConnectionHandler(String ip, ConnectionHandler ch);

    /**
     * Updates the table on the state
     *
     * @param ip       Ip of neighbour
     * @param wants Flag to which the state should be set to
     * @return Returns true if the operation was sucessful, or false otherwise
     */
    boolean updateWantsStream(String ip, boolean wants);


    /**
     * Returns the list of neighbours that wish to receive the stream
     *
     * @return Returns the list of neighbours that wish to receive the stream
     */
    List<String> getNeighboursWantingStream();

    /**
     * Updates the jump time to a certain neighbour
     *
     * @param neighbourName Ip of the neighbour
     * @param newTimeStamp  New time for the jump
     */
    void updateLastJumpTime(String neighbourName, long newTimeStamp);

    /**
     * Returns the time that it takes to jump from the neighbour to the current Node
     *
     * @param ip Ip of the neighbour
     * @return Returns a long value that represents the time it takes to jump from the neighbour to the current node
     */
    long getLastJumpTime(String ip);

    String getNeighbourIP(String neighbourName);

    void setNeighbourIP(String neighbourName, String interfaceIp);

    String getLocalIP(String neighbourName);

    void setLocalIP(String neighbourName, String interfaceIp);

    String getNeighbourNameByItsIP(String neighbourIP);

    /**
     * Locks write lock in case there is a need to hold the lock for multiple operations.
     */
    void writeLock();


    String anyNeighbourUsesInterfaceIP(String neighbourIP);
    /**
     * Unlocks write locks.
     */
    void writeUnlock();


    void printTable();
}
