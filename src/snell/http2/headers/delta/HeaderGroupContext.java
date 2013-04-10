package snell.http2.headers.delta;

import static com.google.common.base.Throwables.propagate;
import static snell.http2.utils.RangedIntegerSupplier.forRange;

import java.util.concurrent.Callable;

import snell.http2.utils.RangedIntegerSupplier;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class HeaderGroupContext 
  implements Supplier<HeaderGroup> {

  private final RangedIntegerSupplier c = 
    forRange(0, 0xFF);
  private Cache<Byte,HeaderGroup> groups = 
    CacheBuilder
      .newBuilder()
      .maximumSize(0xFF)
      .build();

  public void reset() {
    groups.invalidateAll();
    groups.cleanUp();
  }
  
  public HeaderGroup headerGroup(
    byte index) {
      return headerGroup(index,false);
  }
  
  public HeaderGroup headerGroup(
    final byte index, 
    boolean create) {
    try {
      return !create? 
        groups.getIfPresent(index) :
        groups.get(
          index, 
          new Callable<HeaderGroup>() {
            public HeaderGroup call() 
              throws Exception {
                return new HeaderGroup(index);
            }            
          });
    } catch (Throwable t) {
      throw propagate(t);
    }
  }
  
  public String toString() {
    return Objects.toStringHelper("HeaderGroupContext")
      .add("State",groups.asMap())
      .toString();
  }

  @Override
  public HeaderGroup get() {
    try {
      return headerGroup(c.next().byteValue(),true);
    } catch (Throwable t) {
      return null;
    }
  }
}
