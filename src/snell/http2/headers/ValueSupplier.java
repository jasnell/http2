package snell.http2.headers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


import com.google.common.base.Supplier;

public abstract class ValueSupplier<X>
  implements Supplier<X> {

  /**
   * Flags are the 3 most significant bits of the 
   * FLAGS+COUNT field in the header value...
   * 
   * The 5 least significant bits are reserved to
   * indicate the number of distinct values 
   * encoded in the header for header types that
   * permit multiple values (currently only
   * string headers).
   */
  protected static final byte TEXT      = (byte)0x00;
  protected static final byte NUMBER    = (byte)0x40;
  protected static final byte DATE      = (byte)0x80;
  protected static final byte BINARY    = (byte)0xC0;
  protected static final byte UTF8_TEXT = (byte)0x20;
  
  private final byte flags;
  private transient int hash = 1;
  
  protected ValueSupplier(byte flags) {
    this.flags = flags;
  }
  
  /**
   * Completely write the encoded value to the given buffer.
   */
  public abstract void writeTo(OutputStream buffer) throws IOException;

  public abstract int length();
  
  public byte flags() {
    return flags;
  }
  
  public byte count() {
    return 1;
  }
  
  @SuppressWarnings("unchecked")
  public <V extends ValueSupplier<?>>V cast() {
    return (V)this;
  }
  
  public static abstract class ValueParser
    <V extends ValueSupplier<?>,P extends ValueParser<V,P>> {
    protected Huffman huffman;
    @SuppressWarnings("unchecked")
    public P useHuffman(Huffman huffman) {
      this.huffman = huffman;
      return (P)this;
    }
    public abstract V parse(
      InputStream in, 
      byte flags) 
        throws IOException;
  }

  @Override
  public int hashCode() {
    if (hash == 1) {
      hash = 31 * hash + flags;
    }
    return hash;
  }

  protected static boolean flag(byte flags, byte f) {
    return (flags & f) == f;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ValueSupplier<X> other = 
      (ValueSupplier<X>) obj;
    if (flags != other.flags)
      return false;
    return true;
  }
  
}
