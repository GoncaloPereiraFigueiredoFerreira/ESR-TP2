package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Utilities.Tuple;

import java.util.Collection;
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
     * @param provider The ip of the neighbour node that transmits the stream
     * @param Jumps The number of jumps to the server to the current node
     * @param Time The measured time from the server to the current node
     * @param active If the route is active
     * @return Returns true if the connection was successfully added
     */
    boolean addServerPath(String ServerIp, String provider, int Jumps, long Time, boolean active);

    /**
     * Updates the metrics to a server path
     * @param ServerIp The ip for the streaming server
     * @param provider The ip of the neighbour node that transmits the stream
     * @param Jumps The new number of jumps
     * @param Time The new time it takes
     * @return Returns true if the connection was successfully added
     */
    boolean updateMetrics(String ServerIp,String provider, int Jumps, long Time);

    /**
     *
     * @param ServerIp
     * @param provider
     * @param Jumps
     * @param Time
     * @return true if a delay was detected
     */
    boolean updateMetricsAndCheckDelay(String ServerIp, String provider, int Jumps, long Time);

    void signalDelay();

    /**
     * Updates the active state of a connection
     * @param ServerIp The ip for the streaming server
     * @param provider The ip of the neighbour node that transmits the stream
     * @return Returns true if the state was correctly updated
     */
    boolean activateRoute(String ServerIp, String provider);
    boolean activateRoute(Tuple<String,String> route);

    void deactivateRoute();

    void deactivateRoute(String provider);


    boolean isRouteActive(String ServerIp, String provider);


    Tuple<Integer,Long> getMetrics(String ServerIp, String provider);

    Tuple<String,String> getActiveRoute();
    Tuple<String, String> activateBestRoute();

    Tuple<String, String> activateBestRoute(Set<String> excluded);

    Tuple<String, String> getBestRoute(Set<String> excluded);

    boolean existsInRoutingTable(String ServerIp,String provider);

    void printTables();

    boolean verifyDelay(String serverIP, String provider,int jumps ,long newTime);

    boolean checkDelay();

    void removeRoutes(String neighbourname);

    boolean containsRoutes();

    /**
     * Checks if there are routes that do not use the neighbours in the "excluded" collection as providers.
     * @param excluded
     * @return
     */
    boolean containsRoutes(Collection<String> excluded);

    /**
     * Gets all the providers that are not in the "excluded" collection
     * @param excluded
     * @return
     */
    Set<String> additionalProviders(Collection<String> excluded);

    void removeSpecificRoute(Tuple<String, String> route);

    Set<Tuple<String, String>> getRoutesToServer(String server);
}
