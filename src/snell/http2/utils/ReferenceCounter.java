package snell.http2.utils;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.util.concurrent.Monitor;


public final class ReferenceCounter<V> {
  private final Monitor monitor = 
    new Monitor();
  private final Interner<CountingReference<V>> interner = 
    Interners.newWeakInterner();
  private CountingReference<V> intern(CountingReference<V> s) {
    return (CountingReference<V>) interner.intern(s);
  }
  public CountingReference<V> acquire(V val) {
    monitor.enter();
    try {
      CountingReference<V> s = 
        new CountingReference<V>(val);
      s = intern(s);
      s.increment();
      return s;
    } finally {
      monitor.leave();
    }
  }
  public void release(V val) {
    monitor.enter();
    try {
      CountingReference<V> s = 
        new CountingReference<V>(val);
      s = intern(s);
      s.decrement();
    } finally {
      monitor.leave();
    }
  }
}