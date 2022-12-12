package speedNode.Utilities.TaggedConnection;

public class Tags {
    public final static int CONNECTION_CHECK = -1;
    public final static int CONNECTION_CONFIRMATION = -2;
    public final static int REQUEST_NEIGHBOURS_EXCHANGE = 1;
    public final static int REQUEST_NEIGHBOUR_CONNECTION = 2;
    public final static int RESPONSE_NEIGHBOUR_CONNECTION = 3; // Response to the "request neighbour connection"
    public final static int INFORM_READY_STATE = 4;
    public final static int CONNECT_AS_CLIENT_EXCHANGE = 5;
    public final static int CONNECT_AS_SERVER_EXCHANGE = 6;
    public final static int FLOOD_PERMISSION_EXCHANGE = 7;
    public final static int REQUEST_STREAM = 8;
    public final static int CANCEL_STREAM = 9;
    public final static int RESPONSE_TO_REQUEST_STREAM = 10;
    public final static int FLOOD = 11;
    public final static int ACTIVATE_ROUTE = 12;
    public final static int DEACTIVATE_ROUTE = 13;
    public final static int RESPONSE_ACTIVATE_ROUTE = 14;
    public final static int RESPONSE_DEACTIVATE_ROUTE = 15;
    public final static int CLOSE_CONNECTION = 16;
    public final static int CLIENT_CLOSE_CONNECTION = 17;
    public final static int START_PERMISSION = 18;
}
