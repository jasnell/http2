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
  
  static final byte EXPERIMENTAL_FLAG_PERSIST   = 0x1;
  static final byte EXPERIMENTAL_FLAG_CLEAR     = 0x2;
  static final byte EXPERIMENTAL_FLAG_EPHEMERAL = 0x4;
  static final byte EXPERIMENTAL_FLAG_HTTPONLY  = 0x8;
  static final byte EXPERIMENTAL_FLAG_SECURE    = 0x10;

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

    private final HeaderBlockBuilder headers;
    private boolean experimental_enabled = false;
    private int priority = 0;
    
    protected HeadersFrameBuilder(
      byte type, 
      HeaderSerializer ser) {
      super(type);
      this.headers = HeaderBlock.make(ser);
    }
    
    public HeadersFrameBuilder enableExperimentalFlags() {
      this.experimental_enabled = true;
      return this;
    }
    
    public HeadersFrameBuilder disableExperimentalFlags() {
      this.experimental_enabled = false;
      return this;
    }
    
    public HeadersFrameBuilder experimentalPersist(boolean on) {
      checkState(experimental_enabled);
      this.flag(EXPERIMENTAL_FLAG_PERSIST, on);
      return this;
    }
    
    public HeadersFrameBuilder experimentalPersist() {
      return experimentalPersist(true);
    }

    public HeadersFrameBuilder experimentalClear(boolean on) {
      checkState(experimental_enabled);
      this.flag(EXPERIMENTAL_FLAG_CLEAR, on);
      return this;
    }
    
    public HeadersFrameBuilder experimentalClear() {
      return experimentalClear(true);
    }
    
    public HeadersFrameBuilder experimentalEphemeral(boolean on) {
      checkState(experimental_enabled);
      this.flag(EXPERIMENTAL_FLAG_EPHEMERAL, on);
      return this;
    }
    
    public HeadersFrameBuilder experimentalEphemeral() {
      return experimentalEphemeral(true);
    }
    
    public HeadersFrameBuilder experimentalHttpOnly(boolean on) {
      checkState(experimental_enabled);
      this.flag(EXPERIMENTAL_FLAG_HTTPONLY, on);
      return this;
    }
    
    public HeadersFrameBuilder experimentalHttpOnly() {
      return experimentalHttpOnly(true);
    }
    
    public HeadersFrameBuilder experimentalSecure(boolean on) {
      checkState(experimental_enabled);
      this.flag(EXPERIMENTAL_FLAG_SECURE, on);
      return this;
    }
    
    public HeadersFrameBuilder experimentalSecure() {
      return experimentalSecure(true);
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

    @Override
    public HeadersFrameBuilder set(
      String key, 
      byte[] val) {
      headers.set(key,val);
      return this;
    }

    @Override
    public HeadersFrameBuilder set(
      String key, 
      InputStream in, 
      int c)
        throws IOException {
      headers.set(key, in, c);
      return this;
    }
    
  }

  private final HeaderBlock block;
  private final int priority;
  
  private final boolean experimental_enabled;
  
  protected HeadersFrame(
    HeadersFrameBuilder builder) {
      super(builder);
      this.block = builder.headers.get();
      this.priority = max(0,builder.priority);
      this.experimental_enabled = builder.experimental_enabled;
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
  
  public boolean experimentalPersist() {
    checkState(experimental_enabled);
    return this.flag(EXPERIMENTAL_FLAG_PERSIST);
  }
  
  public boolean experimentalClear() {
    checkState(experimental_enabled);
    return this.flag(EXPERIMENTAL_FLAG_CLEAR);
  }
  
  public boolean experimentalEphemeral() {
    checkState(experimental_enabled);
    return this.flag(EXPERIMENTAL_FLAG_EPHEMERAL);
  }
  
  public boolean experimentalHttpOnly() {
    checkState(experimental_enabled);
    return this.flag(EXPERIMENTAL_FLAG_HTTPONLY);
  }
  
  public boolean experimentalSecure() {
    checkState(experimental_enabled);
    return this.flag(EXPERIMENTAL_FLAG_SECURE);
  }
  
}
