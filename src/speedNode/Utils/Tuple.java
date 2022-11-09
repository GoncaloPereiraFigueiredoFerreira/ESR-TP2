package speedNode.Utils;

// Credit: Tuples implementation from https://stackoverflow.com/a/12328838

public class Tuple<X, Y> {
    public final X fst;
    public final Y snd;
    public Tuple(X fst, Y snd) {
        this.fst = fst;
        this.snd = snd;
    }

    @Override
    public String toString() {
        return "(" + fst + "," + snd + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof Tuple)){
            return false;
        }

        Tuple<X,Y> other_ = (Tuple<X,Y>) other;

        // this may cause NPE if nulls are valid values for x or y. The logic may be improved to handle nulls properly, if needed.
        return other_.fst.equals(this.fst) && other_.snd.equals(this.snd);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fst == null) ? 0 : fst.hashCode());
        result = prime * result + ((snd == null) ? 0 : snd.hashCode());
        return result;
    }
    @Override
    public Tuple<X,Y> clone(){
        return new Tuple<>(this.fst,this.snd);
    }


}