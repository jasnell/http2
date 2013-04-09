package snell.http2.frames;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.max;
import static snell.http2.utils.IoUtils.read32;
import static snell.http2.utils.IoUtils.write32;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import org.joda.time.DateTime;

import snell.http2.frames.HeadersFrame.HeadersFrameBuilder;
import snell.http2.headers.HeaderBlock;
import snell.http2.headers.HeaderBlock.HeaderBlockBuilder;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.HeaderSet;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.ValueSupplier;

public final class PushPromiseFrame 
  extends Frame<PushPromiseFrame>
  implements HeaderSet<PushPromiseFrame> {
  
  static final byte TYPE = 0x5;

  public static PushPromiseFrameBuilder make(HeaderSerializer ser) {
    return new PushPromiseFrameBuilder(ser);
  }
  
  public static final class PushPromiseFrameBuilder extends 
    FrameBuilder<PushPromiseFrame,PushPromiseFrameBuilder>
    implements HeaderSetter<PushPromiseFrameBuilder> {

    private final HeaderBlockBuilder headers = 
     HeaderBlock.make();
    private int id;
    
    protected PushPromiseFrameBuilder(HeaderSerializer ser) {
      super(TYPE);
      headers.serializer(ser);
    }

    public PushPromiseFrameBuilder useUtf8Headers() {
      return useUtf8Headers(true);
    }
    
    public PushPromiseFrameBuilder useUtf8Headers(boolean on) {
      headers.utf8(on);
      return this;
    }
    
    @Override
    protected void parseRest(
      InputStream in) 
        throws IOException {
      this.id = read32(in);
      headers.parse(in);
    }
 
    public PushPromiseFrame get() {
      return new PushPromiseFrame(this);
    }

    public PushPromiseFrameBuilder id(int id) {
      checkArgument(inRange(id,0,0x7FFFFFFF));
      this.id = id;
      return this;
    }
    
    public PushPromiseFrameBuilder set(
      String key,
      String... val) {
      headers.set(key,val);
      return this;
    }

    public PushPromiseFrameBuilder set(
      String key, 
      int val) {
      headers.set(key, val);
      return this;
    }

    public PushPromiseFrameBuilder set(
      String key, 
      long val) {
      headers.set(key,val);
      return this;
    }

    public PushPromiseFrameBuilder set(
      String key, 
      DateTime val) {
      headers.set(key,val);
      return this;
    }

    @SuppressWarnings("rawtypes")
    public PushPromiseFrameBuilder set(
      String key, 
      ValueSupplier... val) {
      headers.set(key,val);
      return this;
    }
    
  }

  private final HeaderBlock block;
  private final int id;
  
  protected PushPromiseFrame(
    PushPromiseFrameBuilder builder) {
      super(builder);
      this.block = builder.headers.get();
      this.id = max(0,builder.id);
  }
  
  public int size() {
    return block.size();
  }

  @Override
  protected byte[] preWrite() 
      throws IOException {
    ByteArrayOutputStream out = 
      new ByteArrayOutputStream();
    write32(out,id);
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
