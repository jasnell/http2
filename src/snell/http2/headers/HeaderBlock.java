package snell.http2.headers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import com.google.common.collect.ImmutableMultimap;


/**
 * Storage for headers... pretty simple
 */
@SuppressWarnings("rawtypes")
public final class HeaderBlock 
  implements HeaderSet<HeaderBlock> {

  public static HeaderBlockBuilder make(HeaderSerializer ser) {
    return new HeaderBlockBuilder(ser);
  }
  
  public static class HeaderBlockBuilder 
    extends HeaderSetBuilder<HeaderBlock,HeaderBlockBuilder> {
    HeaderBlockBuilder(HeaderSerializer ser) {
      super(ser);
    }
    public HeaderBlock get() {
      return new HeaderBlock(this);
    }
  }
  
  private final HeaderSerializer ser;  
  private final ImmutableMultimap<String,ValueSupplier> map;
  
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
    
  protected static String tlc(String key) {
    checkNotNull(key);
    return key.toLowerCase();
  }
  
  @Override
  public Iterable<ValueSupplier> get(String key) {
    return map.get(tlc(key));
  }

  @Override
  public Iterator<String> iterator() {
    return map.keySet().iterator();
  }

  @Override
  public boolean contains(String key, ValueSupplier val) {
    key = tlc(key);
    if (!map.containsKey(key)) return false;
    return map.get(key).contains(val);
  }
}
