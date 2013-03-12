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
   * 0x80 - Reserved
   * 0x40 - Reserved
   * 0x20 - Text if Unset, Binary if Set
   * 0x10 - ISO-8859-1 if Unset, UTF-8 if Set (only if 0x20 is Unset)
   * 0x08 - Huffman Coded if Set (only if 0x20 is Unset)
   * 0x04 - Numeric Value (only if 0x20 is Set)
   * 0x02 - Date Value (only if 0x20 and 0x04 are Set)
   * 0x01 - Reserved
   */
  byte flags();
  
  public static interface ValueParser<V extends ValueProvider> {
    V parse(InputStream in, int flags) throws IOException;
  }
  
}
