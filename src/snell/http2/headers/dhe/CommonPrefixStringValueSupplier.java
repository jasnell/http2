package snell.http2.headers.dhe;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.readLengthPrefixedData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.headers.Huffman;
import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.ValueSupplier;
import snell.http2.utils.IntTriple;
import snell.http2.utils.IoUtils;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CommonPrefixStringValueSupplier 
  extends ValueSupplier<Iterable<String>> {

  private final ImmutableList<PrefixedString> list;
  private final Huffman huffman;
  private transient int length = 0;
  private transient int hash = 1;
  
  private CommonPrefixStringValueSupplier(
    Huffman huffman, 
    String name,
    ImmutableList<PrefixedString> list, 
    Storage storage) {
    super(TEXT);
    this.huffman = huffman;
    this.list = list;
  }  
  
  public CommonPrefixStringValueSupplier(
    Huffman huffman, 
    String name,
    StringValueSupplier source, 
    Storage storage) {
    super(TEXT);
    this.huffman = huffman;
    this.list = prep(name,source,storage);
  }
  
  private static ImmutableList<PrefixedString> prep(
    String name,
    StringValueSupplier source,
    Storage storage) {
    ImmutableList.Builder<PrefixedString> list = 
      ImmutableList.builder();
    for (String s : source.get()) {
      IntTriple triple = 
        storage.findLongestCommonPrefix(name, s);
      list.add(
        new PrefixedString(
          s, triple));
    }
    return list.build();
  }

  public StringValueSupplier toStringValueSupplier() {
    return StringValueSupplier
      .create(huffman, get());
  }
  
  @Override
  public Iterable<String> get() {
    return Iterables.transform(list, EXTERN);
  }
  
  public String get(int idx) {
    return list.get(idx).value();
  }
  
  @Override
  public byte flags() {
    byte flags = super.flags();
    return (byte)(flags | (byte)(list.size() - 1));
  }
  
  @Override
  public int length() {
    if (length == 0)
      for (PrefixedString s : list)
        length += length(s.shortVal());
    return length;
  }

  private int length(String s) {
    try {
      byte[] m = s.getBytes("ISO-8859-1");
      return m.length;
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
  
  private static final Function<PrefixedString,String> EXTERN = 
    new Function<PrefixedString,String>() {
      public String apply(PrefixedString input) {
        return input.value();
      }
  };

  @Override
  public int hashCode() {
    if (hash == 1) {
      hash = 31 * hash + ((huffman == null) ? 0 : huffman.hashCode());
      hash = 31 * hash + ((list == null) ? 0 : list.hashCode());     
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
    CommonPrefixStringValueSupplier other = 
      (CommonPrefixStringValueSupplier) obj;
    return Objects.equal(huffman,other.huffman) &&
      Iterables.elementsEqual(list, other.list);
  }

  @Override
  public void writeTo(
    OutputStream buffer) 
      throws IOException {
    
    ByteArrayOutputStream buf = 
      new ByteArrayOutputStream();
    for (PrefixedString ps : list) {
      byte[] data = null;
      String string = ps.shortVal();
      int pl = Math.max(0,ps.length());
      buf.write(int2uvarint(pl));
      if (pl > 0) {
        buf.write(ps.index());
        buf.write(int2uvarint(Math.max(0,ps.position())));
      }
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
  
  public String toString() {
    return list.toString();
  }

  private static final class PrefixedString {
    private final String val;
    private final byte index;
    private final int lst;
    private final int length;
    private transient int hash = 1;
    PrefixedString(
      String val, 
      IntTriple triple) {
      checkNotNull(val);
      this.val = val;
      this.index = (byte)triple.one();
      this.length = triple.two();
      this.lst = triple.three();
      checkArgument(this.length <= val.length());
    }
    String value() {
      return val;
    }
    String shortVal() {
      return length > 0 ? val.substring(length) : val;
    }
    byte index() {
      return index;
    }
    int length() {
      return length;
    }
    int position() {
      return lst;
    }
    @Override
    public int hashCode() {
      if (hash == 1) {
        hash = 31 * hash + index;
        hash = 31 * hash + length;
        hash = 31 * hash + lst;
        hash = 31 * hash + ((val == null) ? 0 : val.hashCode());
      }
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
      PrefixedString other = (PrefixedString) obj;
      return index == other.index && 
        length == other.length &&
        lst == other.lst &&
        Objects.equal(val,other.val);
    }
    
    public String toString() {
      return Objects.toStringHelper(PrefixedString.class)
        .add("Value", val)
        .add("Index", index)
        .add("Length", length)
        .add("Pos", lst)
        .toString();
    }
  }

  public static class CommonPrefixStringValueParser 
    extends ValueParser<CommonPrefixStringValueSupplier,CommonPrefixStringValueParser> {
    
    private Storage storage;
    private String name;
    
    public CommonPrefixStringValueParser forName(String name) {
      this.name = name;
      return this;
    }
    
    public CommonPrefixStringValueParser usingStorage(Storage storage) {
      this.storage = storage;
      return this;
    }
    
    @Override
    public CommonPrefixStringValueSupplier parse(
      InputStream in, 
      byte flags) 
        throws IOException {
      checkNotNull(storage);
      checkNotNull(name);
      ImmutableList.Builder<PrefixedString> strings = 
        ImmutableList.builder();
      int c = flags & ~0xE0;
      while (c >= 0) {
        if (huffman == null)
          throw new IllegalStateException();
        ByteArrayOutputStream comp =
          new ByteArrayOutputStream();
        
        int prefix_length = IoUtils.uvarint2int(in);
        byte[] idx = new byte[] {-1};
        int position = 0;
        if (prefix_length > 0) {
          if (in.read(idx) < 0)
            throw new IllegalStateException();
          position = IoUtils.uvarint2int(in);
        }
        
        byte[] data = 
          readLengthPrefixedData(in);
        huffman.decode(data, comp);
        
        String suffix = 
          new String(
            comp.toByteArray(), 
            "UTF-8"); 
              
        String value = 
          prefix_length > 0 ?
            storage.expand(
              idx[0], 
              position, 
              prefix_length, 
              suffix) : 
            suffix;
        
        PrefixedString ps = 
          new PrefixedString(
            value,IntTriple.of(
              idx[0], 
              Math.min(prefix_length,-1), 
              Math.min(position,-1)));
        
        strings.add(ps);
        c--;
      }
      return new CommonPrefixStringValueSupplier(
        huffman,
        name,
        strings.build(),
        storage);
    }
    
  }
  
}
