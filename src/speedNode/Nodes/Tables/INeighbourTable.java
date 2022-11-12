package speedNode.Nodes.Tables;

import java.util.List;

public interface INeighbourTable {

    /**
     * Table that contains the columns:
     *
     *     IP of the neighbour    |   Flag: Is it active      |   Flag: Does it want the stream |  Time it takes to jump
     *
     */


    /**
     * Adds a new neighbour to the neighbour list. The neighbour will be considered inactive and not connected.
     * @param ip IP of the new neighbour
     * @return Returns true if the neighbour was added to the list, and false otherwise
     */
    boolean addNeighbour(String ip);

    /**
     * Adds a list of new neighbours to the neighbour tabel. The neighbours will be considered inactive and not connected.
     * @param ip IP of the new neighbour
     * @return Returns true if all neighbours were added to the list, or false otherwise
     */
    boolean addNeighbours(List<String> ip);

    /**
     * Method to get the neighbours of the node
     * @return Returns a list of the neighboring ips
     */
    List<String> getNeighbours();

    /**
     * Returns the state of a neighbouring node
     * @param ip Ip of neighbour
     * @return Returns true if the neighbour is active, or false otherwise
     */
    boolean isActive(String ip);

    /**
     * Returns true if the neighbour is receiving a stream through the current node
     * @param ip Ip of neighbour
     * @return Returns true if the neighbour is receiving a stream through the current node, or false otherwise
     */
    boolean isConnected(String ip);


    /**
     * Updates the table to connect the current node to a neighbour
     * @param ip Ip of neighbour
     * @param connected Flag to which the connection state will be set to
     * @return Returns true if the operation was sucessful, or false otherwise
     */
    boolean updateConnectionNeighbour(String ip,boolean connected);

    /**
     * Updates the table on the state
     * @param ip Ip of neighbour
     * @param activate Flag to which the state should be set to
     * @return Returns true if the operation was sucessful, or false otherwise
     */
    boolean updateActiveState(String ip,boolean activate);


    /**
     * Returns the list of neighbours that wish to receive the stream
     * @return Returns the list of neighbours that wish to receive the stream
     */
    List<String> getConnectedNeighbours();

    /**
     * Updates the jump time to a certain neighbour
     * @param ip Ip of the neighbour
     * @param newTimeStamp New time for the jump
     */
    void updateLastJumpTime(String ip,long newTimeStamp);

    /**
     * Returns the time that it takes to jump from the neighbour to the current Node
     * @param ip Ip of the neighbour
     * @return Returns a long value that represents the time it takes to jump from the neighbour to the current node
     */
    long getLastJumpTime(String ip);
}
