package snell.http2.frames;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.channels.Channels.newInputStream;
import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.long2uvarint;
import static snell.http2.utils.IoUtils.read32;
import static snell.http2.utils.IoUtils.write32;
import static snell.http2.utils.IoUtils.write16;
import static snell.http2.utils.IoUtils.writeChar;
import static java.lang.Math.max;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import snell.http2.headers.HeaderSerializer;

import com.google.common.base.Supplier;
import com.google.common.primitives.Shorts;

public abstract class Frame<F extends Frame<F>> {

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <F extends Frame<F>>F parse(InputStream in, HeaderSerializer ser)
    throws IOException {
    byte[] header = 
      new byte[4];
    int r = in.read(header);
    if (r < 4)
      throw new IOException();
    int length = Shorts.fromByteArray(header);
    byte type = header[2];
    byte flags = header[3];
    int stream_id = read32(in);
    FrameBuilder builder = null;

    switch(type) {
    case DataFrame.TYPE:
      builder = DataFrame.make();
      break;
    case GoAwayFrame.TYPE:
      builder = GoAwayFrame.make();
      break;
    case HeadersFrame.TYPE1:
      builder = HeadersFrame.make(true,ser);
      break;
    case HeadersFrame.TYPE8:
      builder = HeadersFrame.make(false,ser);
      break;
    case PingFrame.TYPE:
      builder = PingFrame.make();
      break;
    case RstStreamFrame.TYPE:
      builder = RstStreamFrame.make();
      break;
    case SettingsFrame.TYPE:
      builder = SettingsFrame.make();
      break;
    case WindowUpdateFrame.TYPE:
      builder = WindowUpdateFrame.make();
      break;
    case PushPromiseFrame.TYPE:
      builder = PushPromiseFrame.make(ser);
      break;
    }
    builder.parseRest(
      length, 
      type, 
      flags, 
      stream_id, 
      in);
    return (F)builder.get();
  }
  
  @SuppressWarnings("unchecked")
  public static abstract class FrameBuilder<X extends Frame<X>,B extends FrameBuilder<X,B>>
    implements Supplier<X> {
    
    protected int length = 0;
    protected byte type;
    protected byte flags;
    protected int stream_id;
    protected final ByteArrayOutputStream buffer = 
      new ByteArrayOutputStream();
    
    protected FrameBuilder(byte type) {
      type(type);
    }
    
    public B parse(
      InputStream in) 
        throws IOException {
      checkNotNull(in);
      byte[] header = 
        new byte[4];
      int r = in.read(header);
      if (r < 4)
        throw new IOException();
      this.length = (header[0] << 16) | header[1];
      this.type = header[2];
      this.flags = header[3];
      this.stream_id = read32(in);
      parseRest(in);
      return (B)this;
    }
    
    protected void parseRest(
      int length,
      byte type,
      byte flags,
      int stream_id,
      InputStream in) 
        throws IOException {
      this.length = length;
      this.type = type;
      this.flags = flags;
      this.stream_id = stream_id;
      parseRest(in);
    }
    
    protected void parseRest(
      InputStream in) 
        throws IOException {}
    
    protected B type(byte type) {
      this.type = type;
      return (B)this;
    }
    
    protected B flags(byte flags) {
      this.flags = flags;
      return (B)this;
    }
    
    protected B flag(byte flag, boolean on) {
      if (on)
        this.flags |= flag;
      else
        this.flags &= ~flag;
      return (B)this;
    }
    
    public B fin() {
      return fin(true);
    }
    
    public B fin(boolean on) {
      return flag(FIN_FLAG,on);
    }
    
    protected boolean inRange(int v, int l, int h) {
      return v >= l && v <= h;
    }
    
    public B streamId(int id) {
      checkArgument(inRange(id,0,0x7FFFFFFF));
      this.stream_id = id;
      return (B)this;
    }
    
    protected B put(byte b) throws IOException {
      buffer.write(b);
      return (B)this;
    }
    
    protected B put(byte[] b) throws IOException {
      buffer.write(b);
      return (B) this;
    }
    
    protected B put(byte[] b, int n, int c) throws IOException {
      buffer.write(b,n,c);
      return (B) this;
    }
    
    protected B put(ByteBuffer buf) throws IOException {
      if (!buf.isReadOnly())
        buf = (ByteBuffer) buf.flip();
      buffer.write(buf.array(),0,buf.limit());
      return (B) this;
    }
    
    protected boolean fillFrom(
      InputStream in) 
        throws IOException {
      int n = -1;
      while((n = in.read()) > -1)
        put((byte)n);
      return n != -1;
    }
    
    protected boolean fillFrom(
      InputStream in, 
      int c) 
        throws IOException {
      byte[] buf = new byte[c];
      int n = in.read(buf);
      if (n > -1)
        put(buf,0,n);
      return n >= c;
    }
    
    protected boolean fillFrom(
      ReadableByteChannel c) 
        throws IOException {
      return fillFrom(newInputStream(c));
    }
    
    protected boolean fillFrom(
      ReadableByteChannel c, 
      int n) 
        throws IOException {
      return fillFrom(newInputStream(c),n);
    }
    
    protected B putChar(char c) throws IOException {
      writeChar(buffer(), c);
      return (B) this;
    }
    
    protected B putInt(int i) throws IOException {
      write32(buffer(), i);
      return (B) this;
    }
    
    protected B putUvarint(int i) throws IOException {
      buffer().write(int2uvarint(i));
      return (B) this;
    }
    
    protected B putUvarint(long i) throws IOException {
      buffer().write(long2uvarint(i));
      return (B) this;
    }
    
    protected OutputStream buffer() {
      return buffer;
    }

  }
  
  public static final int DEFAULT_MAX_SIZE = 0xFFFF;
  private static final byte FIN_FLAG = 0x1;
  
  protected final int length;
  protected final byte type;
  protected final byte flags;
  protected final int opaque_id;
  protected final byte[] buffer;
  
  protected Frame(
    FrameBuilder<?,?> builder) {
    this.type = builder.type;
    this.flags = builder.flags;
    this.opaque_id = builder.stream_id;
    this.buffer = builder.buffer.toByteArray();
    this.length = max(builder.length,this.buffer.length);
    checkArgument(this.length <= DEFAULT_MAX_SIZE);
  }
    
  public boolean fin() {
    return flag(FIN_FLAG);
  }
  
  protected boolean flag(byte i) {
    return (flags & i) == i;
  }
    
  public byte type() {
    return type;
  }
  
  public int id() {
    return opaque_id;
  }

  protected int size() {
    return length;
  }
  
  public void writeTo(
    OutputStream out) 
      throws IOException {
    byte[] buffer = preWrite();
    if (buffer != null)
      checkArgument(buffer.length <= DEFAULT_MAX_SIZE);
    if (buffer != null)
      write16(out,buffer.length);
    else
      write16(out,length);
    out.write(type);
    out.write(flags);
    write32(out,opaque_id);
    if (buffer != null)
      out.write(buffer);
    else
      out.write(this.buffer);
    writeRest(out);
  }
  
  protected byte[] preWrite()
    throws IOException {
    return null;
  }
  
  protected void writeRest(OutputStream out)
    throws IOException {}
  
}
