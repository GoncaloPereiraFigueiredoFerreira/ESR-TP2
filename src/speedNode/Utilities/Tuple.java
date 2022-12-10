package speedNode.Utilities;

// Credit: Tuples implementation from https://stackoverflow.com/a/12328838

import java.util.Objects;

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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple<?, ?> tuple = (Tuple<?, ?>) o;
        return Objects.equals(fst, tuple.fst) && Objects.equals(snd, tuple.snd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fst, snd);
    }

    @Override
    public Tuple<X,Y> clone(){
        return new Tuple<>(this.fst,this.snd);
    }


}