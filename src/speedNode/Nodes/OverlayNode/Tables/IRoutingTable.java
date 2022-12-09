package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Utilities.Tuple;

import java.util.List;
import java.util.Set;

public interface IRoutingTable {

    /**
     * Table that contains the columns:
     *
     *     IP of the server | IP of the Providing Neighbour | Nº Jumps till server |   Time till Server   |  Flag : Is route active
     *
     */


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
     * @return Returns true if the state was correctly updated
     */
    boolean activateRoute(String ServerIp, String Provider);


    void deactivateRoute();

    void deactivateRoute(String Provider);


    boolean isRouteActive(String ServerIp, String Provider);


    Tuple<Integer,Float> getMetrics(String ServerIp, String Provider);

    Tuple<String,String> getActiveRoute();

    Tuple<String, String> activateBestRoute();

    boolean existsInRoutingTable(String ServerIp,String Provider);

    void printTables();

    boolean verifyDelay(String serverIP, String Provider,int jumps ,long newTime);

    boolean checkDelay();

    Tuple<String, String> activateBestRoute(Set<String> excluded);
}
