package speedNode.Utilities;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class which purpose it to make threads await while a bool is not set to true
 */
public class BoolWithLockCond {
    private boolean value;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();

    public BoolWithLockCond(boolean value) { this.value = value; }

    public boolean getValue(){
        try {
            lock.lock();
            return this.value;
        } finally { lock.unlock(); }
    }

    public void awaitForValue(boolean value) throws InterruptedException{
        try {
            lock.lock();
            while(this.value != value)
                cond.await();
        } finally { lock.unlock(); }
    }

    public void setAndSignalAll(boolean value){
        try {
            lock.lock();
            boolean currentValue = this.value;
            this.value = value;
            if(currentValue != value)
                cond.signalAll();
        }finally { lock.unlock(); }
    }

    public void setValue(boolean value){
        try {
            lock.lock();
            this.value = value;
        }finally { lock.unlock(); }
    }
}
