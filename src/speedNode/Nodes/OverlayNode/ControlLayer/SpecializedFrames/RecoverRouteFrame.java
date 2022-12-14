package speedNode.Nodes.OverlayNode.ControlLayer.SpecializedFrames;

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.Tags;

import java.io.*;

public class RecoverRouteFrame extends BaseFrame{
    public static int priority = 1;

    public RecoverRouteFrame(String neighbourName, long timestamp) {
        super(neighbourName, timestamp);
    }

    public RecoverRouteFrame(String neighbourName){
        super(neighbourName, System.nanoTime());
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
            in.close();
            bais.close();
            return new RecoverRouteFrame(neighbourName, timestamp) ;
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
            out.flush();
            data = baos.toByteArray();
            out.close();
            baos.close();
        } catch (IOException e) {throw new RuntimeException(e); }

        return new Frame(0, Tags.RECOVER_ROUTE, data);
    }
}
