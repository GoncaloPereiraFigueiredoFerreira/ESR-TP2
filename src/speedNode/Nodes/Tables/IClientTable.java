package speedNode.Nodes.Tables;

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
     * Returns the type of the IP
     * @param Ip Ip of the client
     * @return Returns "Client" if it is a client, "Server" if it is a streaming server and "" if the IP is not recognized
     */
    String typeOf(String Ip);

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

    /**
     * Removes a client from the client table
     * @param IP Ip of the to be removed client
     * @return Returns true if the operation was successful or false otherwise
     */
    boolean removeClient(String IP);



}
