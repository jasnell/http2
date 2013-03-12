package snell.http2.headers;

import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.uvarint2int;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import com.google.common.base.Objects;

/**
 * Encodes a length-prefixed stream of binary octets.
 * Length is encoded as a variable length integer.
 * 
 * TODO: There are many obvious improvements to be made here.
 */
public class BinaryDataValueProvider 
  implements ValueProvider {

  private byte[] data;
  private transient int hash = -1;
  
  public BinaryDataValueProvider(byte[] data) {
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
    if (hash == -1)
      hash = 
        Objects.hashCode(
          getClass(),
          data);
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
    BinaryDataValueProvider other = (BinaryDataValueProvider) obj;
    if (!Arrays.equals(data, other.data))
      return false;
    return true;
  }

  
  public String toString() {
    return Arrays.toString(data);
  }
  
  @Override
  public byte flags() {
    return 0x20;
  }
  
  public static class BinaryDataValueParser 
    implements ValueParser<BinaryDataValueProvider> {
    public BinaryDataValueProvider parse(
      InputStream in, 
      int flags) 
        throws IOException {
      byte[] data = new byte[uvarint2int(in)];
      in.read(data);
      return new BinaryDataValueProvider(data);
    }
    
  }

}
