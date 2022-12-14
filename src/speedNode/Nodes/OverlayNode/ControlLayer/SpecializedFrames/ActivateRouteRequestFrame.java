package speedNode.Nodes.OverlayNode.ControlLayer.SpecializedFrames;

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.Tags;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ActivateRouteRequestFrame extends BaseFrame{
    public static final int priority = 2;
    public boolean noMoreRoutes; //If true, the node that sent this frame, does not have any additional routes
    public boolean updateRouteMode;
    public Set<String> contactedNodes; //All the nodes contacted by the previous nodes


    /**
     * Constructor for self activate/update route request
     * @param updateRouteMode True if the route should be updated
     */
    public ActivateRouteRequestFrame(boolean updateRouteMode){
        super();
        noMoreRoutes = false;
        neighbourName = null;
        contactedNodes = new HashSet<>();
        timestamp = 0; //needs to be 0 to have maximum priority
        this.updateRouteMode = updateRouteMode;
    }

    public ActivateRouteRequestFrame(boolean noMoreRoutes, boolean updateRouteMode, Set<String> contactedNodes) {
        this.noMoreRoutes = noMoreRoutes;
        this.contactedNodes = contactedNodes;
        this.updateRouteMode = updateRouteMode;
    }

    public ActivateRouteRequestFrame(String neighbourName, boolean noMoreRoutes, boolean updateRouteMode, Set<String> contactedNodes) {
        this.neighbourName = neighbourName;
        this.noMoreRoutes = noMoreRoutes;
        this.contactedNodes = contactedNodes;
        this.updateRouteMode = updateRouteMode;
    }

    public ActivateRouteRequestFrame(String neighbourName, long timestamp, boolean noMoreRoutes, boolean updateRouteMode, Set<String> contactedNodes) {
        super(neighbourName, timestamp);
        this.noMoreRoutes = noMoreRoutes;
        this.updateRouteMode = updateRouteMode;
        this.contactedNodes = contactedNodes;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public static BaseFrame deserialize(String neighbourName, Frame frame) {
        if(frame == null) return null;

        ByteArrayInputStream bais = new ByteArrayInputStream(frame.getData());
        try {
            ObjectInputStream in = new ObjectInputStream(bais);

            long timestamp = in.readLong();
            boolean noMoreRoutes = in.readBoolean();
            boolean updateRouteMode = in.readBoolean();
            int tamanho = in.readInt();
            Set<String> contactedNodes = new HashSet<>();
            for(int i=0;i<tamanho;i++)
                contactedNodes.add(in.readUTF());

            in.close();
            bais.close();
            return new ActivateRouteRequestFrame(neighbourName, timestamp, noMoreRoutes, updateRouteMode, contactedNodes) ;
        }catch (IOException ioe){ return null;}

    }

    @Override
    public Frame serialize() {
        byte[] data = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(baos);
            out.writeLong(timestamp);
            out.writeBoolean(noMoreRoutes);
            out.writeBoolean(updateRouteMode);
            out.writeInt(contactedNodes.size());
            for (String ip : contactedNodes)
                out.writeUTF(ip);
            out.flush();

            data = baos.toByteArray();
            out.close();
            baos.close();
        } catch (IOException e) {throw new RuntimeException(e); }

        return new Frame(0, Tags.ACTIVATE_ROUTE, data);
    }
}
