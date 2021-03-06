package snell.http2.headers;

import static com.google.common.primitives.UnsignedInteger.fromIntBits;
import static com.google.common.primitives.UnsignedLong.fromLongBits;
import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.uvarint2long;
import static snell.http2.utils.IoUtils.unsignedBytes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Arrays;

import snell.http2.utils.IoUtils;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

/**
 * Encodes unsigned, variable-length integers (and longs)
 */
public class NumberValueSupplier 
  extends ValueSupplier<Number> {

  private final byte[] val;
  private final int size;
  private transient int hash = 1;
  
  public NumberValueSupplier(int val) {
    super(NUMBER);
    this.size = IoUtils.size(val);
    this.val = unsignedBytes(fromIntBits(val));
  }
  
  /**
   * For Date subclass...
   */
  protected NumberValueSupplier(byte flags, long val) {
    super(flags);
    this.size = IoUtils.size(val);
    this.val = unsignedBytes(fromLongBits(val));
  }
  
  public NumberValueSupplier(long val) {
    super(NUMBER);
    this.size = IoUtils.size(val);
    this.val = unsignedBytes(fromLongBits(val));
  }
  
  public NumberValueSupplier(UnsignedInteger uint) {
    super(NUMBER);
    this.size = IoUtils.size(uint.intValue());
    this.val = unsignedBytes(uint);
  }
  
  public NumberValueSupplier(UnsignedLong ulng) {
    super(NUMBER);
    this.size = IoUtils.size(ulng.longValue());
    this.val = unsignedBytes(ulng);
  }
  
  public int size() {
    return size;
  }
  
  @SuppressWarnings("unchecked")
  public <N extends Number>N numVal() {
    switch(val.length) {
    case 0:
      return null;
    case 1:
      return (N)Byte.valueOf(val[0]);
    case 2:
      return (N)Short.valueOf(Shorts.fromByteArray(val));
    case 3:
      return (N)Integer.valueOf(Ints.fromBytes((byte)0x0, val[0], val[1], val[2]));
    case 4:
      return (N)Integer.valueOf(Ints.fromByteArray(val));
    case 5:
      return (N)Long.valueOf(
        Longs.fromBytes(
          (byte)0x0, 
          (byte)0x0, 
          (byte)0x0, 
          val[0], 
          val[1], 
          val[2], 
          val[3], 
          val[4]));
    case 6:
      return (N)Long.valueOf(
          Longs.fromBytes(
            (byte)0x0, 
            (byte)0x0, 
            val[0], 
            val[1], 
            val[2], 
            val[3], 
            val[4], 
            val[5]));
    case 7:
    return (N)Long.valueOf(
        Longs.fromBytes(
          (byte)0x0, 
          val[0], 
          val[1], 
          val[2], 
          val[3], 
          val[4], 
          val[5], 
          val[6]));
    case 8:
      return (N)Long.valueOf(
        Longs.fromByteArray(val));
    default:
      return (N)new BigInteger(val);
    }
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
    extends ValueParser<NumberValueSupplier,NumberValueParser> {
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
