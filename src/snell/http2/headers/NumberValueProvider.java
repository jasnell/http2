package snell.http2.headers;

import static com.google.common.primitives.UnsignedInteger.fromIntBits;
import static com.google.common.primitives.UnsignedLong.fromLongBits;
import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.uvarint2long;
import static snell.http2.utils.IoUtils.unsignedBytes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

/**
 * Encodes unsigned, variable-length integers (and longs)
 */
public class NumberValueProvider 
  implements ValueProvider {

  private final byte[] val;
  private transient int hash = -1;
  
  public NumberValueProvider(int val) {
    this.val = unsignedBytes(fromIntBits(val));
  }
  
  public NumberValueProvider(long val) {
    this.val = unsignedBytes(fromLongBits(val));
  }
  
  public NumberValueProvider(UnsignedInteger uint) {
    this.val = unsignedBytes(uint);
  }
  
  public NumberValueProvider(UnsignedLong ulng) {
    this.val = unsignedBytes(ulng);
  }
  
  public int size() {
    return val.length;
  }
  
  public long longVal() {
    return Longs.fromByteArray(val);
  }
  
  public int intVal() {
    return Ints.fromByteArray(val);
  }
  
  public UnsignedLong ulongVal() {
    return fromLongBits(longVal());
  }
  
  public UnsignedInteger uintVal() {
    return fromIntBits(intVal());
  }
  
  @Override
  /**
   * Format is [num_items][uvarint]...[uvarint]
   */
  public void writeTo(OutputStream buffer) throws IOException {
    buffer.write(int2uvarint(val));
  } 

  @Override
  public int hashCode() {
    if (hash == -1) 
      hash = Objects.hashCode(val);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    NumberValueProvider other = (NumberValueProvider) obj;
    if (!Arrays.equals(val, other.val))
      return false;
    return true;
  }

  @Override
  public int flags() {
    return 0x40;
  }
  
  public String toString() {
    return val.length == 4 ?
      fromIntBits(Ints.fromByteArray(val)).toString() :
      fromLongBits(Longs.fromByteArray(val)).toString();
  }
  
  public static class NumberValueParser 
    implements ValueParser<NumberValueProvider> {
    private static final UnsignedLong MAXUINT = 
      fromLongBits(0xFFFFFFFFL);
    @Override
    public NumberValueProvider parse(
      InputStream in, 
      int flags) 
        throws IOException {
      long l = uvarint2long(in);
      UnsignedLong ulng = 
        fromLongBits(l);
      return ulng.compareTo(MAXUINT) > 0 ?
        new NumberValueProvider(ulng) :
        new NumberValueProvider((int)l);
    }
  }
  
  public static NumberValueProvider create(int val) {
    return new NumberValueProvider(val);
  }
  
  public static NumberValueProvider create(long val) {
    return new NumberValueProvider(val);
  }
}
