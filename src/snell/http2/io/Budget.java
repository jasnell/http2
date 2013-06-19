package snell.http2.io;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Monitor;

/**
 * Flow Control monitor... the budget limit is set to the current 
 * Window Size... when flow controlled data is to be sent, the
 * number of bytes are reserved from the budget, if available. If 
 * those bytes are not available, then the reservation will not be
 * allowed unless Overdraft is allowed
 */
public class Budget {

  private final static long DEFAULT_INITIAL_WINDOW_SIZE = 0xFFFF;
  
  private final AtomicLong window_size = 
    new AtomicLong(0);
  private final boolean overdraft;
  
  private final Monitor monitor = 
    new Monitor();

  private Budget(Builder builder) {
    this.window_size.set(builder.limit);
    this.overdraft = builder.overdraft;
  }
  
  private Monitor.Guard available(final long amount) {
    return new Monitor.Guard(monitor) {
      @Override
      public boolean isSatisfied() {
        return overdraft || window_size.get() >= amount;
      }
    };
  }
  
  public void free(long amount) {
    window_size.addAndGet(amount);
  }

  public void blockingReserve(
    long amount, 
    long time, 
    TimeUnit unit) 
      throws InterruptedException {
    monitor.enterWhen(available(amount), time, unit);
    try {
      window_size.addAndGet(-amount);
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    } finally {
      monitor.leave();
    }
  }
  
  public void blockingReserve(
    long amount) 
      throws InterruptedException {
    monitor.enterWhen(available(amount));
    try {
      window_size.addAndGet(-amount);
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    } finally {
      monitor.leave();
    }
  }
  
  public long remaining() {
    return window_size.get();
  }
  
  public static Builder make() {
    return new Builder();
  }
  
  public static final class Builder 
    implements Supplier<Budget> {

    private long limit = 0xFFFF; // default limit
    private boolean overdraft = false;
    
    public Builder limit(long limit) {
      if (limit < 0)
        limit = DEFAULT_INITIAL_WINDOW_SIZE;
      this.limit = limit;
      return this;
    }
    
    public Builder allowOverdraft(boolean on) {
      this.overdraft = on;
      return this;
    }
    
    public Builder allowOverdraft() {
      return allowOverdraft(true);
    }
    
    @Override
    public Budget get() {
      return new Budget(this);
    }
   
  }
}
