package speedNode.Utilities;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ProtectedQueue<X> {
    private final Deque<X> queue = new ArrayDeque<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Condition cond = rwLock.writeLock().newCondition();

    public ProtectedQueue(){}

    //public X popElem(){
    //    try{
    //        rwLock.writeLock().lock();
    //        return queue.pop();
    //    }finally {
    //        rwLock.writeLock().unlock();
    //    }
    //}

    public X popElem(){
        return popElem(true);
    }

    public X popElem(boolean await){
        try{
            rwLock.writeLock().lock();
            while (await && this.length() == 0) {
                // Fica bloqueado a espera de pacotes na queue
                try { cond.await();}
                catch (InterruptedException ignored) {}
            }
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
            cond.signal();
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
                try {
                    cond.await();
                } catch (InterruptedException ignored) {
                }
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




}