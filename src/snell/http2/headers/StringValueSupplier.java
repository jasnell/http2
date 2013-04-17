package snell.http2.headers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Iterables.size;
import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.readLengthPrefixedData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class StringValueSupplier 
  extends ValueSupplier<Iterable<String>> {

  private final ImmutableList<String> strings;
  private final Huffman huffman;
  private transient int length = 0;
  private transient int hash = 1;
  
  public StringValueSupplier(
    String... strings) {
      this(copyOf(strings));
  }
  
  public StringValueSupplier(
    Huffman huffman, 
    String... strings) {
    this(huffman,copyOf(strings));
  }
  
  public StringValueSupplier(
    Iterable<String> strings) {
    this(null,strings);
  }
  
  public StringValueSupplier(
    Huffman huffman,
    Iterable<String> strings) {
    super(TEXT);
    checkNotNull(strings);
    int size = size(strings);
    checkArgument(size > 0 && size <= 32);
    this.huffman = huffman;
    this.strings = copyOf(strings);
  }
  
  @Override
  public byte flags() {
    byte flags = super.flags();
    return (byte)(flags | (byte)(strings.size() - 1));
  }

  @Override
  /**
   * Format is: [num_items][item_len][item_data](...[item_len][item_data])
   */
  public void writeTo(
    OutputStream buffer) 
      throws IOException {
    ByteArrayOutputStream buf = 
    new ByteArrayOutputStream();
    for (String string : strings) {
      byte[] data = null;
      if (huffman != null) {
        ByteArrayOutputStream comp = 
          new ByteArrayOutputStream();
        huffman.encode(string, comp);
        data = comp.toByteArray();
      } else
        data = string.getBytes("UTF-8");
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
    if (hash == 1)
      hash = 31 * super.hashCode() + ((strings == null) ? 0 : strings.hashCode());
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
    return Iterables.elementsEqual(strings, other.strings);
  }


  public static class StringValueParser 
    extends ValueParser<StringValueSupplier,StringValueParser> {
    @Override
    public StringValueSupplier parse(
      InputStream in, 
      byte flags) 
        throws IOException {
      ImmutableList.Builder<String> strings = 
        ImmutableList.builder();
      int c = flags & ~0xE0;
      while (c >= 0) {
        if (huffman == null)
          throw new IllegalStateException();
        ByteArrayOutputStream comp =
          new ByteArrayOutputStream();
        byte[] data = 
          readLengthPrefixedData(in);
        huffman.decode(data, comp);
        strings.add(
          new String(
            comp.toByteArray(), 
            "UTF-8")); 
        c--;
      }
      return new StringValueSupplier(
        huffman,
        strings.build());
    }
    
  }

  public static StringValueSupplier create(
    Huffman huff, 
    Iterable<String> strings) {
    return new StringValueSupplier(
      huff,
      strings);
  }
  
  public static StringValueSupplier create(
    Iterable<String> strings) {
    return new StringValueSupplier(strings);
  }
  
  public static StringValueSupplier create(
    String... strings) {
      return create(null,strings);
  }
  
  public static StringValueSupplier create(
    Huffman huff, 
    String... strings) {
    return new StringValueSupplier(
      huff,
      strings);
  }

  @Override
  public Iterable<String> get() {
    return strings;
  }
  
  public String get(int idx) {
    return strings.get(idx);
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
      byte[] m = s.getBytes("UTF-8");
      return m.length;
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
}
