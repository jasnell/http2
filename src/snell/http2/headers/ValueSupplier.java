package snell.http2.headers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.headers.delta.Huffman;

import com.google.common.base.Supplier;

public abstract class ValueSupplier<X>
  implements Supplier<X> {

  protected static final byte TEXT      = 0x0;
  protected static final byte NUMBER    = 0x1;
  protected static final byte DATE      = 0x2;
  protected static final byte BINARY    = 0x3;
  protected static final byte UTF8_TEXT = 0x4;
  protected static final byte HUFF_TEXT = 0x8;
  
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
  
  public static abstract class ValueParser<V extends ValueSupplier<?>,P extends ValueParser<V,P>> {
    protected Huffman huffman;
    @SuppressWarnings("unchecked")
    public P useHuffman(Huffman huffman) {
      this.huffman = huffman;
      return (P)this;
    }
    public abstract V parse(InputStream in, byte flags) throws IOException;
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
