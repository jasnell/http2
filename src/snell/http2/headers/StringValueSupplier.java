package snell.http2.headers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Iterables.size;
import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.readLengthPrefixedString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class StringValueSupplier 
  extends ValueSupplier<Iterable<String>> {

  private final ImmutableList<String> strings;
  private transient int length = 0;
  private transient int hash = 1;
  private final boolean utf8;
  private final boolean huff;
  
  public StringValueSupplier(
    String... strings) {
      this(copyOf(strings));
  }
  
  public StringValueSupplier(
    boolean huff, 
    boolean utf8, 
    String... strings) {
    this(huff,utf8,copyOf(strings));
  }
  
  public StringValueSupplier(
    Iterable<String> strings) {
    this(false,true,strings);
  }
  
  public StringValueSupplier(
    boolean huff, 
    boolean utf8, 
    Iterable<String> strings) {
    super(determineFlags(huff,utf8));
    checkNotNull(strings);
    int size = size(strings);
    checkArgument(size > 0 && size <= 0xFF);
    this.utf8 = utf8;
    this.huff = huff;
    this.strings = copyOf(strings);
  }
  
  private static byte determineFlags(
    boolean huff, 
    boolean utf8) {
      byte flags = TEXT;
      if (huff) flags |= HUFF_TEXT;
      if (utf8) flags |= UTF8_TEXT;
      return flags;
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
    buf.write(strings.size()-1);
    for (String string : strings) {
      byte[] data = 
        string.getBytes(utf8?"UTF-8":"ISO-5589-1");
      buf.write(int2uvarint(data.length));
      buf.write(data);
    }
    buffer.write(buf.toByteArray());
  }

  private static final Joiner joiner = Joiner.on(", ");
  
  public String toString() {
    return joiner.join(strings);
  }
  
  @Override
  public int hashCode() {
    if (hash == 1) {
      hash = super.hashCode();
      hash = 31 * hash + (huff ? 1231 : 1237);
      hash = 31 * hash + (utf8 ? 1231 : 1237);
      hash = 31 * hash + ((strings == null) ? 0 : strings.hashCode());
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
    StringValueSupplier other = (StringValueSupplier) obj;
    if (huff != other.huff)
      return false;
    if (strings == null) {
      if (other.strings != null)
        return false;
    } else if (!strings.equals(other.strings))
      return false;
    if (utf8 != other.utf8)
      return false;
    return true;
  }


  public static class StringValueParser 
    implements ValueParser<StringValueSupplier> {
    @Override
    public StringValueSupplier parse(
      InputStream in, 
      byte flags) 
        throws IOException {
      ImmutableList.Builder<String> strings = 
        ImmutableList.builder();
      boolean huff = flag(flags,HUFF_TEXT);
      boolean utf8 = flag(flags,UTF8_TEXT);
      int c = in.read();
      while (c >= 0) {
        // todo: handle huffman coding
        strings.add(
          readLengthPrefixedString(
            in, 
            utf8 ? 
              "UTF-8" : 
              "ISO8859-1"));
        c--;
      }
      return new StringValueSupplier(
        huff,
        utf8,
        strings.build());
    }
    
  }

  public static StringValueSupplier create(
    boolean huff, 
    boolean utf8, 
    Iterable<String> strings) {
    return new StringValueSupplier(
      huff,
      utf8,
      strings);
  }
    
  public static StringValueSupplier create(
    Iterable<String> strings) {
    return new StringValueSupplier(strings);
  }
  
  public static StringValueSupplier create(
    boolean huff, 
    boolean utf8, 
    String... strings) {
    return new StringValueSupplier(
      huff,
      utf8,
      strings);
  }
  
  public static StringValueSupplier create(
    String...strings) {
    return new StringValueSupplier(strings);
  }

  @Override
  public Iterable<String> get() {
    return strings;
  }

  @Override
  public int length() {
    if (length == 0)
      for (String s : strings)
        length += length(s);
    return length;
  }

  private int length(String s) {
    try {
      byte[] m = s.getBytes(utf8?"UTF-8":"ISO-8859-1");
      return m.length;
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
}
