package snell.http2.utils;

import static com.google.common.base.Preconditions.checkArgument;

import static java.lang.Math.max;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Supplier;
import com.google.common.primitives.UnsignedInts;

/**
 * Supplier instance that supplies integers within a given range.
 * The object can be used as an Iterator and Iterable object as well.
 * 
 * For instance, the example below will print the numbers 1-10.
 * 
 * <code><pre>
 * for (int i : RangedIntegerSupplier.forRange(1,10)) {
 *   System.out.println(i);
 * }
 * </pre></code>
 * 
 * For the HTTP/2 implementation, this is primarily intended 
 * as a utility for sequencing stream ID's and header group ID's.
 * 
 */
public final class RangedIntegerSupplier 
  implements Supplier<Integer>,   
             Iterator<Integer>, 
             Iterable<Integer> {
  
  public static RangedIntegerSupplier forRange(int min, int max) {
    return new RangedIntegerSupplier(min,max,1);
  }

  public static RangedIntegerSupplier forAllOddIntegers(int min, int max) {
    return new RangedIntegerSupplier(min|0x1,max|0x1,2);
  }
  
  public static RangedIntegerSupplier forAllEvenIntegers(int min, int max) {
    if ((min & 0x1) == 0x1) min++;
    return new RangedIntegerSupplier(min,max & ~0x1,2);
  }
  
  public static RangedIntegerSupplier forAllOddIntegers(int max) {
    return new RangedIntegerSupplier(1,max|0x1,2);
  }
  
  public static RangedIntegerSupplier forAllEvenIntegers() {
    return forAllEvenIntegers(Integer.MAX_VALUE);
  }
  
  public static RangedIntegerSupplier forAllOddIntegers() {
    return forAllEvenIntegers(Integer.MAX_VALUE);
  }
  
  public static RangedIntegerSupplier forAllEvenIntegers(int max) {
    return new RangedIntegerSupplier(0,max & ~0x1,2);
  }
  
  public static RangedIntegerSupplier forRange(int min, int max, boolean wrap) {
    return new RangedIntegerSupplier(min,max,1, wrap);
  }

  public static RangedIntegerSupplier forAllOddIntegers(int min, int max, boolean wrap) {
    return new RangedIntegerSupplier(min|0x1,max|0x1,2, wrap);
  }
  
  public static RangedIntegerSupplier forAllEvenIntegers(int min, int max, boolean wrap) {
    if ((min & 0x1) == 0x1) min++;
    return new RangedIntegerSupplier(min,max & ~0x1,2,wrap);
  }
  
  public static RangedIntegerSupplier forAllOddIntegers(int max, boolean wrap) {
    return new RangedIntegerSupplier(1,max|0x1,2,wrap);
  }
  
  public static RangedIntegerSupplier forAllEvenIntegers(int max, boolean wrap) {
    return new RangedIntegerSupplier(0,max & ~0x1,2,wrap);
  }
  
  private boolean consumed = false;
  private final boolean wrap;
  
  private final AtomicInteger i = 
    new AtomicInteger();
  private final int min;
  private final int max;
  private final int step;
  
  protected RangedIntegerSupplier(int max, int step) {
    this(0,max,step,false);
  }
  
  protected RangedIntegerSupplier(int start, int max, int step) {
    this(start,max,step,false);
  }
  
  protected RangedIntegerSupplier(int max, int step, boolean wrap) {
    this(0,max,step,wrap);
  }
  
  protected RangedIntegerSupplier(int start, int max, int step, boolean wrap) {
    checkArgument(UnsignedInts.compare(start, max) < 0);
    i.set(max(0,start));
    this.max = max;
    this.min = start;
    this.step = step;
    this.wrap = wrap;
  }
  
  public int size() {
    return max - min;
  }
  
  public void reset() {
    i.set(min);
    consumed = false;
  }
  
  public boolean consumed() {
    return consumed;
  }
  
  @Override
  public Integer get() {
    if (!consumed) {
      int r = i.getAndAdd(step);
      consumed = r+1 > max || r+1 < 0;
      if (wrap && consumed)
        reset();
      return r;
    } else throw new IllegalStateException();
  }
  
  public RangedIntegerSupplier skip() {
    return skip(1);
  }
  
  public RangedIntegerSupplier skip(int c) {
    while(c-- > 0)
      skip();
    return this;
  }

  public boolean hasNext() {
    return !consumed;
  }

  public Integer next() {
    return get();
  }

  @Override
  public void remove() {
    get();
  }

  @Override
  public Iterator<Integer> iterator() {
    return new RangedIntegerSupplier(i.get(),max,step);
  }

}
