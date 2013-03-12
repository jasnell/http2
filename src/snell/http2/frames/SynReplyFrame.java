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

public class SynReplyFrame 
  extends Frame<SynReplyFrame>
  implements HeaderSet<SynReplyFrame> {
  
  static final byte TYPE = 0x2;
  private final HeaderBlock block;
  
  protected SynReplyFrame(
    HeaderSerializer ser,
    boolean fin, 
    int opaque_id, 
    int max_size) {
      super(fin, TYPE, opaque_id, max_size);
      this.block = new HeaderBlock(ser);
  }
  
  public int size() {
    return block.size();
  }

  public static SynReplyFrame create(
    HeaderSerializer ser, 
    boolean fin, 
    int id, 
    int max_size) {
    return new SynReplyFrame(
      ser, 
      fin, 
      id, 
      max_size);
  }
  
  public static SynReplyFrame create(
    HeaderSerializer ser, 
    boolean fin, 
    int id) {
      return create(
        ser, 
        fin, 
        id, 
        Frame.DEFAULT_MAX_SIZE);
  }
  
  public static SynReplyFrame create(
    HeaderSerializer ser, 
    int id) {
      return create(
        ser, 
        false, 
        id);
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    block.writeTo(buffer());
    super.writeTo(out);
  }
  
  static SynReplyFrame parse(
    HeaderSerializer ser,
    boolean fin, 
    boolean con, 
    int opaque_id, 
    int size, 
    InputStream rest) 
      throws IOException {
    SynReplyFrame frame = 
      SynReplyFrame.create(ser, fin, opaque_id);
    frame.con(con);
    ser.deserialize(rest, frame);
    return frame;
  }

  @Override
  public SynReplyFrame set(String key, String... val) {
    block.set(key,val);
    return this;
  }

  @Override
  public SynReplyFrame set(String key, int val) {
    block.set(key,val);
    return this;
  }

  @Override
  public SynReplyFrame set(String key, long val) {
    block.set(key,val);
    return this;
  }
  
  @Override
  public SynReplyFrame set(String key, DateTime val) {
    block.set(key,val);
    return this;
  }
  
  @Override
  public SynReplyFrame set(String key, ValueProvider... val) {
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
