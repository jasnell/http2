package snell.http2.headers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface ValueProvider {

  /**
   * Completely write the encoded value to the given buffer.
   */
  void writeTo(OutputStream buffer) throws IOException;
  
  /**
   * These may change... 
   * 
   * 00000000
   * 
   * Offsets 0..1 => type code
   *   00 => text
   *   01 => number
   *   10 => date
   *   11 => binary
   *   
   * Offset 2     => ISO-8859-1 if Unset, UTF-8 if Set (only if type code == 00)
   * Offset 3     => Huffman coded if set (only if type code == 00)
   * Offset 4..7  => Reserved
   */
  int flags();
  
  public static interface ValueParser<V extends ValueProvider> {
    V parse(InputStream in, int flags) throws IOException;
  }
  
}
