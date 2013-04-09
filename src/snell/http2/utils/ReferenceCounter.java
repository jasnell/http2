package snell.http2.utils;

import static snell.http2.utils.CountingReference.ref;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.util.concurrent.Monitor;

/**
 * Basic reference counter implementation that uses a Guava 
 * WeakInterner instance to manage instances of the counter.
 * 
 * Calling acquire(V) returns the interned copy of the val
 * object. Objects used need to properly adhere to provide
 * override impls of equals and hashcode to ensure they'll
 * be interned properly. 
 * 
 * Calling release releases the interned object and decrements
 * the reference counter.
 */
public final class ReferenceCounter<V> {
  private final Monitor monitor = 
    new Monitor();
  private final Interner<CountingReference<V>> interner = 
    Interners.newWeakInterner();
  private CountingReference<V> intern(V s) {
    return interner.intern(ref(s));
  }
  public CountingReference<V> acquire(V val) {
    monitor.enter();
    try {
      return intern(val).increment();
    } finally {
      monitor.leave();
    }
  }
  public void release(V val) {
    monitor.enter();
    try {
      intern(val).decrement();
    } finally {
      monitor.leave();
    }
  }
}