package snell.http2.headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import com.google.common.collect.ImmutableMultimap;

/**
 * Storage for headers... pretty simple
 */
public final class HeaderBlock 
  implements HeaderSet<HeaderBlock> {

  public static HeaderBlockBuilder make() {
    return new HeaderBlockBuilder();
  }
  
  public static class HeaderBlockBuilder 
    extends HeaderSetBuilder<HeaderBlock,HeaderBlockBuilder> {
    public HeaderBlock get() {
      return new HeaderBlock(this);
    }
  }
  
  private final HeaderSerializer ser;  
  private final ImmutableMultimap<String,ValueProvider> map;
  
  protected HeaderBlock(
    HeaderBlockBuilder builder) {
      this.ser = builder.ser;
      this.map = builder.map.build();
  }
    
  public void writeTo(
    OutputStream buf) 
      throws IOException {
    ser.serialize(buf, this);
  }
 
  public int size() {
    return map.size();
  }
    
  @Override
  public Iterable<ValueProvider> get(String key) {
    return map.get(key);
  }

  @Override
  public Iterator<String> iterator() {
    return map.keySet().iterator();
  }

  @Override
  public boolean contains(String key, ValueProvider val) {
    if (!map.containsKey(key)) return false;
    return map.get(key).contains(val);
  }
}
