package snell.http2.headers;

import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.readLengthPrefixedString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

public class StringValueProvider 
  implements ValueProvider {

  private final String[] strings;
  private transient int hash = -1;
  private final boolean utf8 = true; // for now
  private final boolean huff = false; // for now
  
  public StringValueProvider(String... strings) {
    if (strings == null || 
        strings.length == 0 || 
        strings.length > 0xFF) 
      throw new IllegalArgumentException();
    this.strings = strings;
  }
  
  @Override
  /**
   * Format is: [num_items][item_len][item_data](...[item_len][item_data])
   */
  public void writeTo(
    OutputStream buffer) 
      throws IOException {
    // Obviously we still need to huffman encode things... for now, just dump it
    ByteArrayOutputStream buf = 
      new ByteArrayOutputStream();
    if (strings.length == 0) 
      throw new IllegalArgumentException();
    else {
      buf.write(strings.length-1);
      for (int n = 0; n < strings.length; n++) {
        byte[] data = strings[n].getBytes("UTF-8");
        buf.write(int2uvarint(data.length));
        buf.write(data);
      }
    }
    buffer.write(buf.toByteArray());
  }

  private static final Joiner joiner = Joiner.on(", ");
  
  public String toString() {
    return joiner.join(strings);
  }
  
  @Override
  public int hashCode() {
    if (hash == -1)
      hash = Objects.hashCode((Object[])strings);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    StringValueProvider other = (StringValueProvider) obj;
    if (!Arrays.equals(strings, other.strings))
      return false;
    return true;
  }

  @Override
  public int flags() {
    int f = 0x00;
    if (utf8) f |= 0x20;
    if (huff) f |= 0x10;
    return f;
  }
 
  public static class StringValueParser 
    implements ValueParser<StringValueProvider> {
    @Override
    public StringValueProvider parse(
      InputStream in, 
      int flags) 
        throws IOException {
      List<String> strings = 
        new ArrayList<String>();
      int c = in.read();
      while (c >= 0) {
        // todo: handle huffman coding
        strings.add(
          readLengthPrefixedString(
            in, 
            ((flags & 0x20) == 0x20) ? 
              "UTF-8" : 
              "ISO8859-1"));
        c--;
      }
      String[] _strings = 
        strings.toArray(
          new String[strings.size()]);
      return new StringValueProvider(_strings);
    }
    
  }
  
  public static StringValueProvider create(String...strings) {
    return new StringValueProvider(strings);
  }
}
