package speedNode.Utilities;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ProtectedQueue<X> {
    private Queue<X> queue = new ArrayDeque<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Condition cond = rwLock.writeLock().newCondition();

    public ProtectedQueue(){}

    public ProtectedQueue(Queue<X> queue){
        this.queue = queue;
    }

    //public X popElem(){
    //    try{
    //        rwLock.writeLock().lock();
    //        return queue.pop();
    //    }finally {
    //        rwLock.writeLock().unlock();
    //    }
    //}

    public X pollElem(){
        return pollElem(true);
    }

    public X pollElem(boolean await){
        try{
            rwLock.writeLock().lock();
            //System.out.println("Length of the queue: " + this.length());
            while (await && this.length() == 0) {
                // Fica bloqueado a espera de pacotes na queue
                try { cond.await();}
                catch (InterruptedException ignored) {}
            }
            return queue.poll();
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    public X pollElem(long time, TimeUnit timeUnit){
        try{
            rwLock.writeLock().lock();
            // Fica bloqueado a espera de pacotes na queue
            try { cond.await(time, timeUnit);}
            catch (InterruptedException ignored) {}
            return queue.poll();
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean pushElem(X elem){
        try{
            rwLock.writeLock().lock();
            boolean ret = queue.add(elem);
            cond.signal();
            return ret;
        }finally {
            rwLock.writeLock().unlock();
        }
    }
    public X peekHead(){
        try{
            rwLock.readLock().lock();
            return queue.peek();
        }finally {
            rwLock.readLock().unlock();
        }
    }

    //public void awaitPush() {
    //    while (this.length() == 0){
    //        // Fica bloqueado a espera de pacotes na queue
    //        try {
    //            cond.await();
    //        } catch (InterruptedException ignored) {}
    //    }
    //}

    public void awaitPush() {
        try {
            rwLock.writeLock().lock();
            while (this.length() == 0) {
                // Fica bloqueado a espera de pacotes na queue
                try { cond.await(); }
                catch (InterruptedException ignored) {                }
            }
        }finally {
            rwLock.writeLock().unlock();
        }
    }


    public int length(){
        try{
            rwLock.readLock().lock();
            return queue.size();
        }finally {
            rwLock.readLock().unlock();
        }
    }


    //TODO - tirar este metodo
    public String printQueue(){
        try {
            rwLock.readLock().lock();
            return queue.toString();
        }finally {
            rwLock.readLock().unlock();
        }

    }

}
