package speedNode.Nodes.OverlayNode.ControlLayer.SpecializedFrames;

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.Tags;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class ActivateRouteResponseFrame extends BaseFrame{
    public static int priority = 1;
    public boolean response;

    public ActivateRouteResponseFrame(boolean response) {
        this.response = response;
    }

    public ActivateRouteResponseFrame(String neighbourName, boolean response){
        this.neighbourName = neighbourName;
        this.timestamp = System.nanoTime();
        this.response = response;
    }

    public ActivateRouteResponseFrame(String neighbourName, long timestamp, boolean response) {
        super(neighbourName, timestamp);
        this.response = response;
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
            boolean response = in.readBoolean();
            in.close();
            bais.close();
            return new ActivateRouteResponseFrame(neighbourName, timestamp, response) ;
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
            out.writeBoolean(response);
            out.flush();
            data = baos.toByteArray();
            out.close();
            baos.close();
        } catch (IOException e) {throw new RuntimeException(e); }

        return new Frame(0, Tags.RESPONSE_ACTIVATE_ROUTE, data);
    }
}
