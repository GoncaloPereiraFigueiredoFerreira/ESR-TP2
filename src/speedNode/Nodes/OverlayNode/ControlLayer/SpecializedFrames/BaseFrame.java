package speedNode.Nodes.OverlayNode.ControlLayer.SpecializedFrames;

import speedNode.Utilities.TaggedConnection.Frame;

public abstract class BaseFrame implements Comparable<BaseFrame>{
    public String neighbourName; //Node that sent the frame
    public long timestamp; //Timestamp of the time exactly before the frame was sent

    public BaseFrame() {
        timestamp = System.nanoTime();
    }

    public BaseFrame(String neighbourName, long timestamp) {
        this.neighbourName = neighbourName;
        this.timestamp = timestamp;
    }

    /**
     * @return the priority of the frame. The lower the more priority is has.
     */
    public abstract int getPriority();

    ///**
    // * Used to break the tie between objects of the same class
    // * @param bf Object of the same class
    // * @return negative value if the object itself appears first (ascending order)
    // */
    //public int getBreakTieValue(BaseFrame bf){
    //    return 0;
    //}

    /**
     * Must not be overriden.
     * @param bf the object to be compared.
     * @return negative value if the object itself should be first (in ascending order) than the 'bf' object
     */
    @Override
    public int compareTo(BaseFrame bf) {
        int ret = this.getPriority() - bf.getPriority();
        if(ret == 0) {
            ret = (int) (this.timestamp - bf.timestamp);
            if (ret == 0)
                ret = this.neighbourName.compareTo(bf.neighbourName);
        }
        return ret;
    }

    public static BaseFrame deserialize(String neighbourName, Frame frame){
        return null;
    }

    public Frame serialize(){
        return null;
    }
}
