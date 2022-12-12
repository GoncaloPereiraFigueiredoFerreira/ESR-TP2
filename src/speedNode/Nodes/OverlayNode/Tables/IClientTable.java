package speedNode.Nodes.OverlayNode.Tables;

import java.util.List;

public interface IClientTable {
    /**
     * Table that contains the columns:
     *                 IP address of client     |        Type of client (either "Server" or "Client")
     *
     */



    /**
     * Adds a new client to the client table
     * @param Ip Ip of the client
     * @return Returns true if the operation was concluded successfully
     */
    boolean addNewClient(String Ip);
    /**
     * Adds a new server to the client table
     * @param Ip Ip of the server
     * @return Returns true if the operation was concluded successfully
     */
    boolean addNewServer(String Ip);


    /**
     * Returns the list of all the connected clients
     * @return Returns a list with the Ips of all connected clients
     */
    List<String> getAllClients();

    /**
     * Returns the list of all the connected servers
     * @return Returns a list with the Ips of all connected servers
     */
    List<String> getAllServers();

    boolean hasClients();

    boolean hasServers();

    /**
     * Removes a client from the client table
     * @param IP Ip of the to be removed client
     * @return Returns true if the operation was successful or false otherwise
     */
    boolean removeClient(String IP);

    boolean containsServer(String IP);

    void printTable();
}
