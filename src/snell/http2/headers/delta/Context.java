package snell.http2.headers.delta;

import java.util.concurrent.Callable;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class Context {

  private Cache<Integer,HeaderGroup> groups = 
    CacheBuilder.newBuilder().build();
  
  public HeaderGroup headerGroup(int index) {
    return headerGroup(index,false);
  }
  
  public HeaderGroup headerGroup(int index, boolean create) {
    try {
      return !create? 
        groups.getIfPresent(index) :
        groups.get(index, new Callable<HeaderGroup>() {
          public HeaderGroup call() throws Exception {
            Storage storage = new Storage();
            return new HeaderGroup(storage);
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
