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
import snell.http2.utils.IoUtils;

public class SynStreamFrame 
  extends Frame<SynStreamFrame>
  implements HeaderSet<SynStreamFrame> {

  public static enum Priority {
    ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN;
    
    public byte pri() {
      return (byte)ordinal();
    }
    
    public static Priority get(byte o) {
      return Priority.values()[o];
    }
  }
  
  public static final byte MAX_PRIORITY = 0x7;
  static final byte TYPE = 0x1;
  private int associated_id = 0;
  private byte priority = 0;
  private final HeaderBlock block; // TODO: Figure out passing state
  
  protected SynStreamFrame(
    HeaderSerializer ser,
    boolean fin, 
    int opaque_id, 
    int max_size,
    int associated_id,
    byte priority) {
      super(fin, TYPE, opaque_id, max_size);
      this.block = new HeaderBlock(ser);
      this.associated_id = associated_id;
      if (priority > MAX_PRIORITY)
        throw new IllegalArgumentException();
      this.priority = priority;
  }
  
  public int size() {
    return block.size();
  }
  
  public int associatedId() {
    return associated_id;
  }
  
  public Priority priority() {
    return Priority.get(priority); 
  }

  public static SynStreamFrame create(
    HeaderSerializer ser, 
    boolean fin, 
    int id, 
    int max_size, 
    int associated_id, 
    Priority priority) {
    return new SynStreamFrame(
      ser, 
      fin, 
      id, 
      max_size, 
      associated_id, 
      priority.pri());
  }
  
  public static SynStreamFrame create(
    HeaderSerializer ser, 
    boolean fin, 
    int id, 
    int max_size) {
    return create(
      ser, 
      fin, 
      id, 
      max_size, 
      0, 
      Priority.ZERO);
  }
  
  public static SynStreamFrame create(
    HeaderSerializer ser, 
    boolean fin, 
    int id, 
    int associated_id, 
    Priority priority) {
      return create(
        ser,
        fin, 
        id, 
        Frame.DEFAULT_MAX_SIZE, 
        associated_id, 
        priority);
  }
  
  public static SynStreamFrame create(
    HeaderSerializer ser, 
    boolean fin, 
    int id) {
    return create(
      ser, 
      fin, 
      id, 
      Frame.DEFAULT_MAX_SIZE, 
      0, Priority.ZERO);
  }
  
  public static SynStreamFrame create(
    HeaderSerializer ser, 
    int id, 
    int associated_id, 
    Priority priority) {
      return create(
        ser, 
        false, 
        id, 
        associated_id, 
        priority);
  }
  
  public static SynStreamFrame create(
    HeaderSerializer ser,
    int id) {
      return create(ser, false, id);
  }
  
  @Override
  public void writeTo(OutputStream out) throws IOException {
    //putInt(associated_id);
    putUvarint(associated_id);
    put((byte)(priority << 5));
    block.writeTo(buffer());
    super.writeTo(out);
  }
  
  static SynStreamFrame parse(
    HeaderSerializer ser,
    boolean fin, 
    boolean con, 
    int opaque_id, 
    int size, 
    InputStream rest) 
      throws IOException {
    SynStreamFrame frame = 
      SynStreamFrame.create(ser, fin, opaque_id);
    frame.con(con);
    frame.associated_id = IoUtils.uvarint2int(rest);
    int flags = rest.read();
    if (flags > -1)
      frame.priority = (byte)(flags >> 5);
    ser.deserialize(rest, frame);
    return frame;
  }

  @Override
  public SynStreamFrame set(String key, String... val) {
    block.set(key,val);
    return this;
  }

  @Override
  public SynStreamFrame set(String key, int val) {
    block.set(key,val);
    return this;
  }
  
  @Override
  public SynStreamFrame set(String key, long val) {
    block.set(key,val);
    return this;
  }

  @Override
  public SynStreamFrame set(String key, DateTime val) {
    block.set(key,val);
    return this;
  }
  
  @Override
  public SynStreamFrame set(String key, ValueProvider... val) {
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
    return block.contains(key, val);
  }
}
