package snell.http2.frames;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import org.joda.time.DateTime;

import snell.http2.headers.HeaderBlock;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.HeaderSet;
import snell.http2.headers.ValueProvider;

public class HeadersFrame 
  extends Frame<HeadersFrame>
  implements HeaderSet<HeadersFrame> {
  
  static final byte TYPE = 0x8;
  
  private final HeaderBlock block;
  
  protected HeadersFrame(
    HeaderSerializer ser,
    int opaque_id) {
      super(true, TYPE, opaque_id);
      this.block = new HeaderBlock(ser);
  }
  
  public int size() {
    return block.size();
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    block.writeTo(buffer());
    super.writeTo(out);
  }

  static HeadersFrame parse(
    HeaderSerializer ser,
    boolean fin, 
    boolean con, 
    int opaque_id, 
    int size, 
    InputStream rest) 
      throws IOException {
    HeadersFrame frame = 
      HeadersFrame.create(ser, opaque_id);
    frame.con(con);
    frame.fin(fin);
    ser.deserialize(rest, frame);
    return frame;
  }
  
  public static HeadersFrame create(HeaderSerializer ser, int id) {
    return new HeadersFrame(ser, id);
  }
  
  @Override
  public HeadersFrame set(String key, String... val) {
    block.set(key,val);
    return this;
  }

  @Override
  public HeadersFrame set(String key, int val) {
    block.set(key,val);
    return this;
  }

  @Override
  public HeadersFrame set(String key, long val) {
    block.set(key,val);
    return this;
  }
  
  @Override
  public HeadersFrame set(String key, DateTime val) {
    block.set(key,val);
    return this;
  }

  @Override
  public HeadersFrame set(String key, ValueProvider... val) {
    block.set(key, val);
    return this;
  }

  @Override
  public Iterable<ValueProvider> get(String key) {
    return block.get(key);
  }

  @Override
  public Iterator<String> iterator() {
    return block.iterator();
  }

  @Override
  public boolean contains(String key, ValueProvider val) {
    return block.contains(key,val);
  }
}
