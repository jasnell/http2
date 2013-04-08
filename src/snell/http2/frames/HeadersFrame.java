package snell.http2.frames;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.max;
import static snell.http2.utils.IoUtils.read32;
import static snell.http2.utils.IoUtils.write32;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.joda.time.DateTime;

import snell.http2.headers.HeaderBlock;
import snell.http2.headers.HeaderBlock.HeaderBlockBuilder;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.HeaderSet;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.ValueSupplier;

public final class HeadersFrame 
  extends Frame<HeadersFrame>
  implements HeaderSet<HeadersFrame> {
  
  static final byte TYPE1 = 0x1;
  static final byte TYPE8 = 0x8;

  public static HeadersFrameBuilder make(
    HeaderSerializer ser) {
      return new HeadersFrameBuilder(TYPE8, ser);
  }
  
  public static HeadersFrameBuilder make(
    boolean withPriority, 
    HeaderSerializer ser) {
    return new HeadersFrameBuilder(
      withPriority?TYPE1:TYPE8, ser);
  }
  
  public static final class HeadersFrameBuilder extends 
    FrameBuilder<HeadersFrame,HeadersFrameBuilder>
    implements HeaderSetter<HeadersFrameBuilder> {

    private final HeaderBlockBuilder headers = 
     HeaderBlock.make();
    private int priority = 0;
    
    protected HeadersFrameBuilder(byte type, HeaderSerializer ser) {
      super(type);
      headers.serializer(ser);
    }

    @Override
    protected void parseRest(
      InputStream in) 
        throws IOException {
      if (type == TYPE1)
        this.priority = read32(in);
      headers.parse(in);
    }
 
    public HeadersFrame get() {
      
      return new HeadersFrame(this);
    }

    public HeadersFrameBuilder priority(int p) {
      checkArgument(inRange(p,0,0x7FFFFFFF));
      checkState(this.type == TYPE1);
      this.priority = p;
      return this;
    }
    
    public HeadersFrameBuilder set(
      String key,
      String... val) {
      headers.set(key,val);
      return this;
    }

    public HeadersFrameBuilder set(
      String key, 
      int val) {
      headers.set(key, val);
      return this;
    }

    public HeadersFrameBuilder set(
      String key, 
      long val) {
      headers.set(key,val);
      return this;
    }

    public HeadersFrameBuilder set(
      String key, 
      DateTime val) {
      headers.set(key,val);
      return this;
    }

    @SuppressWarnings("rawtypes")
    public HeadersFrameBuilder set(
      String key, 
      ValueSupplier... val) {
      headers.set(key,val);
      return this;
    }
    
  }

  private final HeaderBlock block;
  private final int priority;
  
  protected HeadersFrame(
    HeadersFrameBuilder builder) {
      super(builder);
      this.block = builder.headers.get();
      this.priority = max(0,builder.priority);
  }
  
  public int size() {
    return block.size();
  }

  @Override
  protected byte[] preWrite() 
      throws IOException {
    ByteArrayOutputStream out = 
      new ByteArrayOutputStream();
    if (type == TYPE1)
      write32(out,priority);
    block.writeTo(out);
    return out.toByteArray();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Iterable<ValueSupplier> get(String key) {
    return block.get(key);
  }

  @Override
  public Iterator<String> iterator() {
    return block.iterator();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public boolean contains(String key, ValueSupplier val) {
    return block.contains(key,val);
  }
}
