package snell.http2.frames;

import static java.nio.channels.Channels.newInputStream;
import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.long2uvarint;
import static snell.http2.utils.IoUtils.write24;
import static snell.http2.utils.IoUtils.write32;
import static snell.http2.utils.IoUtils.writeChar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public abstract class Frame<F extends Frame<F>> {

  public static final int DEFAULT_MAX_SIZE = 0xFFFFFF;
  
  protected byte flags_type_length;
  protected final int opaque_id;
  protected final ByteArrayOutputStream buffer;
  protected boolean flipped;
  
  protected Frame(
    boolean fin,
    byte type,
    int opaque_id) {
      this(fin,type,opaque_id,DEFAULT_MAX_SIZE);
  }
  
  protected Frame(
    boolean fin, 
    byte type,
    int opaque_id,
    int max_size) {
      this.flags_type_length = 
        init_flags(fin,type);
      this.opaque_id = opaque_id;
      if (max_size > DEFAULT_MAX_SIZE)
        throw new IllegalArgumentException();
      this.buffer = new ByteArrayOutputStream();
  }
  
  private byte init_flags(
    boolean fin, 
    byte type) {
      byte b= 0x0;
      if (fin) b |= 0x80;
      if (type > 0x3F) 
    	throw new IllegalArgumentException();
      b |= type;
      return b;
  }
  
  public boolean fin() {
    return (flags_type_length & 0x80) == 0x80;
  }
  
  public boolean con() {
    return (flags_type_length & 0x40) == 0x40;
  }
  
  @SuppressWarnings("unchecked")
  public F con(boolean b) {
    if (b) this.flags_type_length |= 0x40;
    else this.flags_type_length = (byte)(this.flags_type_length & ~0x40);
    return (F)this;
  }
  
  @SuppressWarnings("unchecked")
  public F fin(boolean b) {
    if (b) this.flags_type_length |= 0x80;
    else this.flags_type_length = (byte)(this.flags_type_length & ~0x80);
    return (F)this;
  }
  
  public byte type() {
    return (byte)(flags_type_length & ~0xC0);
  }
  
  public int id() {
    return opaque_id;
  }
  
  @SuppressWarnings("unchecked")
  protected F put(byte b) throws IOException {
    if (consumed()) throw new IllegalStateException();
    buffer.write(b);
    return (F)this;
  }
  
  @SuppressWarnings("unchecked")
  protected F put(byte[] b) throws IOException {
    if (consumed()) throw new IllegalStateException();
    buffer.write(b);
    return (F) this;
  }
  
  @SuppressWarnings("unchecked")
  protected F put(byte[] b, int n, int c) throws IOException {
    if (consumed()) throw new IllegalStateException();
    buffer.write(b,n,c);
    return (F) this;
  }
  
  @SuppressWarnings("unchecked")
  protected F put(ByteBuffer buf) throws IOException {
    if (consumed()) throw new IllegalStateException();
    if (!buf.isReadOnly())
      buf = (ByteBuffer) buf.flip();
    buffer.write(buf.array(),0,buf.limit());
    return (F) this;
  }
  
  protected boolean fillFrom(InputStream in) throws IOException {
    int n = -1;
    while((n = in.read()) > -1)
      put((byte)n);
    return n != -1;
  }
  
  protected boolean fillFrom(InputStream in, int c) throws IOException {
    byte[] buf = new byte[c];
    int n = in.read(buf);
    if (n > -1)
      put(buf,0,n);
    return n != -1;
  }
  
  protected boolean fillFrom(ReadableByteChannel c) throws IOException {
    return fillFrom(newInputStream(c));
  }
  
  protected boolean fillFrom(ReadableByteChannel c, int n) throws IOException {
    return fillFrom(newInputStream(c),n);
  }
  
  @SuppressWarnings("unchecked")
  protected F putChar(char c) throws IOException {
    if (consumed()) throw new IllegalStateException();
    writeChar(buffer(), c);
    return (F) this;
  }
  
  @SuppressWarnings("unchecked")
  protected F putInt(int i) throws IOException {
    if (consumed()) throw new IllegalStateException();
    write32(buffer(), i);
    return (F) this;
  }
  
  @SuppressWarnings("unchecked")
  protected F putUvarint(int i) throws IOException {
    if (consumed()) throw new IllegalStateException();
    buffer().write(int2uvarint(i));
    return (F) this;
  }
  
  @SuppressWarnings("unchecked")
  protected F putUvarint(long i) throws IOException {
    if (consumed()) throw new IllegalStateException();
    buffer().write(long2uvarint(i));
    return (F) this;
  }
  
  protected int size() {
    return buffer.toByteArray().length;
  }
  
  protected OutputStream buffer() {
    return buffer;
  }
  
  public void writeTo(OutputStream out) throws IOException {
    if (!flipped) {
      flipped = true;
      out.write(flags_type_length);
      byte[] oid = int2uvarint(opaque_id);
      write24(out,size() + 4 + oid.length);
      out.write(oid);
      out.write(buffer.toByteArray());
    } else {
      throw new IllegalStateException();
    }
  }
  
  protected boolean consumed() {
    return flipped;
  }
  
}
