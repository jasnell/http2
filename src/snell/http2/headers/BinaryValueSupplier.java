package snell.http2.headers;

import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.uvarint2int;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Encodes a length-prefixed stream of binary octets.
 * Length is encoded as a variable length integer.
 * 
 * TODO: There are many obvious improvements to be made here.
 */
public class BinaryValueSupplier 
  extends ValueSupplier<InputStream> {

  private byte[] data;
  private transient int hash = 1;
  
  public BinaryValueSupplier(byte[] data) {
    super(BINARY);
    this.data = data;
  }
  
  @Override
  public void writeTo(
    OutputStream buffer) 
      throws IOException {
    buffer.write(
      int2uvarint(
        data.length));
    buffer.write(data);
  }

  @Override
  public int hashCode() {
    if (hash == 1) {
      hash = super.hashCode();
      hash = 31 * hash + Arrays.hashCode(data);
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
    BinaryValueSupplier other = (BinaryValueSupplier) obj;
    if (!Arrays.equals(data, other.data))
      return false;
    return true;
  }

  public String toString() {
    return Arrays.toString(data);
  }

  public static class BinaryDataValueParser 
    extends ValueParser<BinaryValueSupplier,BinaryDataValueParser> {
    public BinaryValueSupplier parse(
      InputStream in, 
      byte flags) 
        throws IOException {
      byte[] data = new byte[uvarint2int(in)];
      in.read(data);
      return new BinaryValueSupplier(data);
    }
    
  }

  @Override
  public InputStream get() {
    return new ByteArrayInputStream(data);
  }

  @Override
  public int length() {
    return data.length;
  }

}
