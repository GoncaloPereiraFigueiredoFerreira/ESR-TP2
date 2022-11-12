package speedNode.Nodes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

public class ProtectedQueue<X> {
    private final Deque<X> queue = new ArrayDeque<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Condition cond = rwLock.writeLock().newCondition();

    public ProtectedQueue(){}

    public X popElem(){
        try{
            rwLock.writeLock().lock();
            return queue.pop();
        }finally {
            rwLock.writeLock().unlock();
        }
    }

    public boolean pushElem(X elem){
        try{
            rwLock.writeLock().lock();
            return queue.add(elem);
        }finally {
            rwLock.writeLock().unlock();
            cond.signalAll();
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

    public void awaitPush() {
        while (this.length() == 0){
            // Fica bloqueado a espera de pacotes na queue
            try {
                cond.await();
            } catch (InterruptedException ignored) {}
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




}
