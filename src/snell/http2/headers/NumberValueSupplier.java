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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

/**
 * Encodes unsigned, variable-length integers (and longs)
 */
public class NumberValueSupplier 
  extends ValueSupplier<Number> {

  private final byte[] val;
  private transient int hash = 1;
  
  public NumberValueSupplier(int val) {
    super(NUMBER);
    this.val = unsignedBytes(fromIntBits(val));
  }
  
  protected NumberValueSupplier(byte flags, long val) {
    super(flags);
    this.val = unsignedBytes(fromLongBits(val));
  }
  
  public NumberValueSupplier(long val) {
    super(NUMBER);
    this.val = unsignedBytes(fromLongBits(val));
  }
  
  public NumberValueSupplier(UnsignedInteger uint) {
    super(NUMBER);
    this.val = unsignedBytes(uint);
  }
  
  public NumberValueSupplier(UnsignedLong ulng) {
    super(NUMBER);
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
    if (hash == 1) {
      hash = super.hashCode();
      hash = 31 * hash + Arrays.hashCode(val);
    }
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!super.equals(obj))
      return false;
    if (getClass() != obj.getClass())
      return false;
    NumberValueSupplier other = (NumberValueSupplier) obj;
    if (!Arrays.equals(val, other.val))
      return false;
    return true;
  }

  public String toString() {
    return val.length == 4 ?
      fromIntBits(Ints.fromByteArray(val)).toString() :
      fromLongBits(Longs.fromByteArray(val)).toString();
  }
  
  public static class NumberValueParser 
    implements ValueParser<NumberValueSupplier> {
    private static final UnsignedLong MAXUINT = 
      fromLongBits(0xFFFFFFFFL);
    @Override
    public NumberValueSupplier parse(
      InputStream in, 
      byte flags) 
        throws IOException {
      long l = uvarint2long(in);
      UnsignedLong ulng = 
        fromLongBits(l);
      return ulng.compareTo(MAXUINT) > 0 ?
        new NumberValueSupplier(ulng) :
        new NumberValueSupplier((int)l);
    }
  }
  
  public static NumberValueSupplier create(int val) {
    return new NumberValueSupplier(val);
  }
  
  public static NumberValueSupplier create(long val) {
    return new NumberValueSupplier(val);
  }

  @Override
  public Number get() {
    return longVal();
  }

  @Override
  public int length() {
    return val.length;
  }
}
