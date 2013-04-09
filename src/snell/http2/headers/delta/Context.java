package snell.http2.headers.delta;

import java.util.concurrent.Callable;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class Context {

  private Cache<Byte,HeaderGroup> groups = 
    CacheBuilder
      .newBuilder()
      .maximumSize(0xFF)
      .build();

  public HeaderGroup headerGroup(byte index) {
    return headerGroup(index,false);
  }
  
  public HeaderGroup headerGroup(byte index, boolean create) {
    try {
      return !create? 
        groups.getIfPresent(index) :
        groups.get(index, new Callable<HeaderGroup>() {
          public HeaderGroup call() throws Exception {
            return new HeaderGroup(Storage.create());
          }
        });
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
  
  public String toString() {
    return Objects.toStringHelper("Context")
      .add("State",groups.asMap())
      .toString();
  }
}
