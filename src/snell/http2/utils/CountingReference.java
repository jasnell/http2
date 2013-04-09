package snell.http2.utils;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Supplier;

/**
 * Maintains a reference count for a given value. The range of 
 * allowed references are capped at a maximum of 0xFFFF distinct 
 * references. Any attempt to increment the counter > 0xFFFF will 
 * have zero effect. Any attempt to decrement the counter < 0 will
 * have zero effect.
 */
public final class CountingReference<V> 
  implements Supplier<V> {
    
  public static <V>CountingReference<V> ref(V item) {
    return new CountingReference<V>(item);
  }
  
  private transient final AtomicInteger c =
    new AtomicInteger(0);
  private transient int hash = 1;
  private final V val;
  
  CountingReference(V val) {
    this.val = val;
  }
  public V get() {
    return val;
  }
  CountingReference<V> increment() {
    if (c.get() < 0xFFFF)
      c.incrementAndGet();
    return this;
  }
  CountingReference<V> decrement() {
    if (c.get() > 0)
      c.decrementAndGet();
    return this;
  }
  public int count() {
    return c.get();
  }
  @Override
  public int hashCode() {
    if (hash == 1)
      hash = 31 * hash + ((val == null) ? 0 : val.hashCode());
    return hash;
  }
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    CountingReference<V> other = (CountingReference<V>) obj;
    if (val == null) {
      if (other.val != null)
        return false;
    } else if (!val.equals(other.val))
      return false;
    return true;
  }
  
}