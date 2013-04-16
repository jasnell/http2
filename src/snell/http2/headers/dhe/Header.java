package snell.http2.headers.dhe;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.elementsEqual;
import static snell.http2.utils.IoUtils.writeLengthPrefixedString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import snell.http2.headers.ValueSupplier;
import snell.http2.headers.BinaryValueSupplier.BinaryDataValueParser;
import snell.http2.headers.DateTimeValueSupplier.DateTimeValueParser;
import snell.http2.headers.NumberValueSupplier.NumberValueParser;
import snell.http2.headers.StringValueSupplier.StringValueParser;
import snell.http2.headers.ValueSupplier.ValueParser;
import snell.http2.headers.delta.Huffman;
import snell.http2.utils.IoUtils;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.TreeRangeSet;

public abstract class Header<I extends Header.Instance> 
  implements Iterable<I> {

  public static final byte TYPE_INDEX = 0x00;
  public static final byte TYPE_RANGE = 0x40;
  public static final byte TYPE_CLONE = (byte)0x80;
  public static final byte TYPE_LITERAL = (byte)0xC0;
  
  private static final byte FLAG_EPHEMERAL = (byte)0x20;
 
  @SuppressWarnings("unchecked")
  public static <H extends Header<?>>H parse(
    InputStream in,
    Huffman huffman)
      throws IOException {
    byte[] flag = new byte[1];
    int r = in.read(flag);
    if (r == -1)
      throw new IllegalStateException();
    byte code = (byte)(flag[0] & ~0x3F);
    byte count = (byte)(flag[0] & ~0xE0);
    boolean eph = (flag[0] & FLAG_EPHEMERAL) == FLAG_EPHEMERAL;
    HeaderBuilder<?,H,?> builder = null;
    switch (code) {
    case TYPE_INDEX:
      builder = (HeaderBuilder<?, H, ?>) index();
      break;
    case TYPE_RANGE:
      builder = (HeaderBuilder<?, H, ?>) range();
      break;
    case TYPE_CLONE:
      builder = (HeaderBuilder<?, H, ?>) cloned();
      break;
    case TYPE_LITERAL:
      builder = (HeaderBuilder<?, H, ?>) literal();
      break;
    }
    return builder != null ? 
      builder
        .parse(count, in, huffman)
        .ephemeral(eph)
        .get() : 
      null;
  }
  
  private final byte code;
  private final boolean eph;
  private final ImmutableSet<I> instances;
  private transient int hash = 1;
  
  protected Header(
    byte code, 
    Header.HeaderBuilder<I,?,?> builder) {
    this.code = code;
    this.eph = builder.eph;
    this.instances = builder.instances.build();
  }
  
  protected Header(
    byte code, 
    Header.HeaderBuilder<I,?,?> builder,
    Function<ImmutableSet<I>,ImmutableSet<I>> reducer) {
    this.code = code;
    this.eph = builder.eph;
    this.instances = reducer.apply(builder.instances.build());
  }
  
  @SuppressWarnings("unchecked")
  public <H extends Header<?>>H cast() {
    return (H)this;
  }
  
  public boolean ephemeral() {
    return eph;
  }
  
  public Iterator<I> iterator() {
    return instances.iterator();
  }
  
  public byte code() {
    return code;
  }
  
  @Override
  public int hashCode() {
    if (hash == 1) {
      hash = 31 * hash + code;
      hash = 31 * hash + (eph ? 1331 : 1337);
      hash = 31 * hash + ((instances == null) ? 0 : instances.hashCode());
    }
    return hash;
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Header<I> other = (Header<I>) obj;
    return code == other.code && 
      eph == other.eph &&
      elementsEqual(instances, other.instances);
  }

  public void writeTo(OutputStream out) throws IOException {
    byte prefix = (byte)(code() | instances.size()-1);
    if (eph) prefix |= FLAG_EPHEMERAL;
    out.write(prefix);
    for (Instance i : this)
      i.writeTo(out);
  }
  
  public static IndexBuilder index() {
    return new IndexBuilder();
  }
  
  public static RangeBuilder range() {
    return new RangeBuilder();
  }
  
  public static CloneBuilder cloned() {
    return new CloneBuilder();
  }
  
  public static CloneBuilder ephemeralCloned() {
    return cloned().ephemeral();
  }
  
  public static LiteralBuilder literal() {
    return new LiteralBuilder();
  }
  
  public static LiteralBuilder ephemeralLiteral() {
    return literal().ephemeral();
  }
  
  public static interface Instance {
    public void writeTo(OutputStream out) throws IOException;
  }
  
  public static abstract class HeaderBuilder
    <I extends Instance, H extends Header<I>, B extends HeaderBuilder<I,H,B>>
    implements Supplier<H> {
    
    private final ImmutableSet.Builder<I> instances = 
      ImmutableSet.builder();
    private boolean eph = false;
    private int c = 0;
    
    @SuppressWarnings("unchecked")
    public B ephemeral(boolean on) {
      this.eph = on;
      return (B)this;
    }
    
    public B ephemeral() {
      return ephemeral(true);
    }
    
    @SuppressWarnings("unchecked")
    protected B add(I instance) {
      checkState(c <= 32);
      instances.add(instance);
      c++;
      return (B)this;
    }
    
    public int count() {
      return c;
    }
    
    protected abstract B parse(
      byte count, 
      InputStream in, 
      Huffman huffman) 
        throws IOException;
    
  }
  
  public static final class IndexInstance 
    implements Instance {
    private final byte idx;
    protected IndexInstance(byte idx) {
      this.idx = idx;
    }
    public byte index() {
      return idx;
    }
    @Override
    public void writeTo(
      OutputStream out) 
        throws IOException {
      out.write(idx);
    }
    @Override
    public int hashCode() {
      return 31 + idx;
    }
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass())
        return false;
      IndexInstance other = (IndexInstance) obj;
      return idx == other.idx;
    }
    
  }
  
  public static final class Index 
    extends Header<IndexInstance> {
      protected Index(IndexBuilder builder) {
        super(TYPE_INDEX,builder);
      }
      public Iterable<Header<?>> coallesce() {
        return INDEX_COALESCE.apply(this);
      }
  }
  
  public static final class IndexBuilder 
    extends HeaderBuilder<IndexInstance,Index,IndexBuilder> {
    public IndexBuilder set(int... idx) {
      if (idx == null) return this;
      for (int i : idx)
        set((byte)i);
      return this;
    }
    public IndexBuilder set(byte... idx) {
      checkNotNull(idx);
      checkArgument(idx.length <= 32);
      for (byte b : idx)
        add(new IndexInstance(b));
      return this;
    }
    public Index get() {
      return new Index(this);
    }
    @Override
    protected IndexBuilder parse(
      byte count, 
      InputStream in,
      Huffman huffman) 
        throws IOException {
      byte[] b = new byte[count+1];
      int r = in.read(b);
      checkState(r == count+1);
      set(b);
      return this;
    }
  
  }
  
  public static final class RangeInstance 
    implements Instance {
    private final byte start;
    private final byte end;
    protected RangeInstance(byte start, byte end) {
      this.start = start;
      this.end = end;
    }
    public byte start() {
      return start;
    }
    public byte end() {
      return end;
    }
    @Override
    public void writeTo(
      OutputStream out)
        throws IOException {
      out.write(start);
      out.write(end);
    }
    @Override
    public int hashCode() {
      int hash = 1;
      hash = 31 * hash + end;
      hash = 31 * hash + start;
      return hash;
    }
    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass())
        return false;
      RangeInstance other = (RangeInstance) obj;
      return end == other.end &&
             start == other.start;
    }
    
  }
  
  public static final class Range 
    extends Header<RangeInstance> {
      protected Range(RangeBuilder builder) {
        super(TYPE_RANGE,builder,RANGE_REDUCER);
      }
  }
  
  public static final class RangeBuilder 
    extends HeaderBuilder<RangeInstance,Range,RangeBuilder> {
    public RangeBuilder range(byte s, byte e) {
      add(new RangeInstance(s,e));
      return this;
    }
    public Range get() {
      return new Range(this);
    }
    @Override
    protected RangeBuilder parse(
      byte count, 
      InputStream in,
      Huffman huffman) 
        throws IOException {
      count++;
      byte[] b = new byte[count*2];
      int r = in.read(b);
      checkState(r == count*2);
      for (int n = 0; n < count*2; n = n + 2) 
        range(b[n],b[n+1]);
      return this;
    }
  }

  private static final 
    Function<ImmutableSet<RangeInstance>,ImmutableSet<RangeInstance>> RANGE_REDUCER = 
      new Function<ImmutableSet<RangeInstance>,ImmutableSet<RangeInstance>>() {
        public ImmutableSet<RangeInstance> apply(ImmutableSet<RangeInstance> input) {
          TreeRangeSet<Byte> set = TreeRangeSet.create();
          for (RangeInstance i : input)
            set.add(com.google.common.collect.Range.open(
              (byte)(i.start()-1),(byte)(i.end()+1)));
          ImmutableSet.Builder<RangeInstance> b = 
            ImmutableSet.builder();
          for (com.google.common.collect.Range<Byte> r : set.asRanges()) {
            b.add(new RangeInstance(
              (byte)(r.lowerEndpoint()+1),
              (byte)(r.upperEndpoint()-1)));
          }
          return b.build();
        }
    };
    
  private static final
    Function<Iterable<IndexInstance>,Iterable<Header<?>>> INDEX_COALESCE = 
      new Function<Iterable<IndexInstance>,Iterable<Header<?>>>() {
        public Iterable<Header<?>> apply(Iterable<IndexInstance> input) {
          ImmutableSet.Builder<Header<?>> ret = ImmutableSet.builder();
          TreeRangeSet<Byte> set = TreeRangeSet.create();
          TreeRangeSet<Byte> set2 = TreeRangeSet.create();
          for (IndexInstance i : input) {
            byte b = i.index();
            if (b < 0) 
              set2.add(
                com.google.common.collect.Range.open(
                  (byte)(b-1),
                  (byte)(b+1)));
            else 
              set.add(
                com.google.common.collect.Range.open(
                  (byte)(b-1),
                  (byte)(b+1)));
          }
          for (com.google.common.collect.Range<Byte> range : set.asRanges()) {
            byte h = range.upperEndpoint();
            byte l = range.lowerEndpoint();
            if (h - l == 0) 
              ret.add(
                index()
                  .set(h)
                  .get());
            else {
              ret.add(
                range()
                  .range(
                    (byte)(l+1), 
                    (byte)(h-1))
                  .get());
            }
          }

          return ret.build();
        }
  };
  
  public static final class CloneInstance
    implements Instance {
    private final byte index;
    private final ValueSupplier<?> value;
    private transient int hash = 1;
    protected CloneInstance(
      byte index, 
      ValueSupplier<?> value) {
      this.index = index;
      this.value = value;
    }
    public byte index() {
      return index;
    }
    @SuppressWarnings("unchecked")
    public <V extends ValueSupplier<?>>V value() {
      return (V)value;
    }
    @Override
    public void writeTo(
      OutputStream out) 
        throws IOException {
      out.write(index);
      out.write(value.flags());
      value.writeTo(out);
    }
    @Override
    public int hashCode() {
      if (hash == 1) {
        hash = 31 * hash + index;
        hash = 31 * hash + ((value == null) ? 0 : value.hashCode());
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
      CloneInstance other = (CloneInstance) obj;
      return index == other.index && 
        equal(value,other.value);
    }
  }
  
  public static final class Clone 
    extends Header<CloneInstance> {
      protected Clone(CloneBuilder builder) {
        super(TYPE_CLONE,builder);
      }
  }
  
  public static final class CloneBuilder 
    extends HeaderBuilder<CloneInstance,Clone,CloneBuilder> {
    public CloneBuilder value(byte idx, ValueSupplier<?> value) {
      checkNotNull(value);
      add(new CloneInstance(idx,value));
      return this;
    }
    public Clone get() {
      return new Clone(this);
    }
    @Override
    protected CloneBuilder parse(
      byte count, 
      InputStream in, 
      Huffman huffman) 
        throws IOException {
      while(count >= 0) {
        byte[] b = new byte[2];
        int r = in.read(b);
        checkState(r == 2);
        ValueSupplier<?> val = 
          selectValueParser(b[1])
            .useHuffman(huffman)
            .parse(in, b[1]);
        value(b[0],val);
        count--;
      }
      return this;
    }
  }

  public static final class LiteralInstance 
    implements Instance {
    private final String name;
    private final ValueSupplier<?> value;
    private transient int hash = 1;
    protected LiteralInstance(
      String name, 
      ValueSupplier<?> value) {
      this.name = name;
      this.value = value;
    }
    public String name() {
      return name;
    }
    @SuppressWarnings("unchecked")
    public <V extends ValueSupplier<?>>V value() {
      return (V)value;
    }
    @Override
    public void writeTo(
      OutputStream out) 
        throws IOException {
      writeLengthPrefixedString(out, name);
      out.write(value.flags());
      value.writeTo(out);
    }
    @Override
    public int hashCode() {
      if (hash == 1) {
        hash = 31 * hash + ((name == null) ? 0 : name.hashCode());
        hash = 31 * hash + ((value == null) ? 0 : value.hashCode());
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
      LiteralInstance other = (LiteralInstance) obj;
      return 
        equal(name, other.name) && 
        equal(value,other.value);
    }
    
  }
  
  public static final class Literal 
    extends Header<LiteralInstance> {
      protected Literal(LiteralBuilder builder) {
        super(TYPE_LITERAL,builder);
      }
  }
  
  public static final class LiteralBuilder
    extends HeaderBuilder<LiteralInstance,Literal,LiteralBuilder> {
    public LiteralBuilder value(String name, ValueSupplier<?> value) {
      checkNotNull(name);
      checkNotNull(value);
      add(new LiteralInstance(name,value));
      return this;
    }
    public Literal get() {
      return new Literal(this);
    }
    @Override
    protected LiteralBuilder parse(
      byte count, 
      InputStream in,
      Huffman huffman) 
        throws IOException {
      while(count >= 0) {
        String name = 
          IoUtils.readLengthPrefixedString(
            in, "ISO-8859-1");
        byte[] b = new byte[1];
        int r = in.read(b);
        checkState(r == 1);
        ValueSupplier<?> val = 
          selectValueParser(b[0])
            .useHuffman(huffman)
            .parse(in, b[0]);
        value(name,val);
        count--;
      }
      return this;
    }
  }
  
  public static final class BuilderContext {
    private IndexBuilder index;
    private RangeBuilder range;
    private CloneBuilder cloned;
    private CloneBuilder ephemeral_cloned;
    private LiteralBuilder literal;
    private LiteralBuilder ephemeral_literal;
    
    public BuilderContext() {
      reset(TYPE_INDEX,false);
      reset(TYPE_RANGE,false);
      reset(TYPE_CLONE,false);
      reset(TYPE_CLONE,true);
      reset(TYPE_LITERAL,false);
      reset(TYPE_LITERAL,true);
    }
    
    public boolean index(
      byte idx, 
      OutputStream out)
        throws IOException {
      IndexBuilder ib = builder(TYPE_INDEX,false);
      ib.set(idx);
      return writeAndResetIfFull(TYPE_INDEX,false,out);
    }
    
    public boolean range(
      byte start, 
      byte end, 
      OutputStream out)
        throws IOException {
       RangeBuilder rb = builder(TYPE_RANGE,false);
       rb.range(start, end);
       return writeAndResetIfFull(TYPE_RANGE,false,out);
    }
    
    public boolean cloned(
      byte idx, 
      ValueSupplier<?> val, 
      boolean ephemeral, 
      OutputStream out)
        throws IOException {
       CloneBuilder cb = builder(TYPE_CLONE,ephemeral);
       cb.value(idx, val);
       return writeAndResetIfFull(TYPE_CLONE,ephemeral,out);
    }
    
    public boolean literal(
      String name,
      ValueSupplier<?> val,
      boolean ephemeral,
      OutputStream out)
        throws IOException {
      LiteralBuilder lb = builder(TYPE_LITERAL,ephemeral);
      lb.value(name, val);
      return writeAndResetIfFull(TYPE_LITERAL,ephemeral,out);
    }
    
    @SuppressWarnings("unchecked")
    public <H extends HeaderBuilder<?,?,?>>H builder(
      byte code, 
      boolean ephemeral) {
        switch(code) {
        case TYPE_INDEX:
          return (H)index;
        case TYPE_RANGE:
          return (H)range;
        case TYPE_CLONE:
          return (H)(ephemeral?ephemeral_cloned:cloned);
        case TYPE_LITERAL:
          return (H)(ephemeral?ephemeral_literal:literal);
        default: 
            return null;
        }
    }
    
    public void reset(
      byte code, 
      boolean ephemeral) {
        switch(code) {
        case TYPE_INDEX:
          index = Header.index();
          break;
        case TYPE_RANGE:
          range = Header.range();
          break;
        case TYPE_CLONE:
          if (ephemeral)
            ephemeral_cloned = Header.ephemeralCloned();
          else
            cloned = Header.cloned();
          break;
        case TYPE_LITERAL:
          if (ephemeral)
            ephemeral_literal = Header.ephemeralLiteral();
          else
            literal = Header.literal();
          break;
        }
    }

    public boolean writeAndResetIfFull(
      byte code, 
      boolean ephemeral,
      OutputStream out)
        throws IOException {
      HeaderBuilder<?,?,?> b = 
        builder(code,ephemeral);
      if (b != null) {
        if (b.count() == 32) {
          b.get().writeTo(out);
          reset(code,ephemeral);
          return true;
        }
      }
      return false;
    }
    
    public int writeRemaining(
      OutputStream out) 
        throws IOException {
      int c = 0;
      if (index.count() > 0)
        for (Header<?> h : index.get().coallesce()) {
          h.writeTo(out);
          c++;
        }
      ImmutableList<HeaderBuilder<?,?,?>> list = 
        ImmutableList.<HeaderBuilder<?,?,?>>of(
          range,
          cloned,
          ephemeral_cloned,
          literal,
          ephemeral_literal);
      for (HeaderBuilder<?,?,?> b : list)
        if (b.count() > 0) {
          b.get().writeTo(out);
          c++;
        } 
      return c;
    }
  }

  static final ValueParser<?,?> selectValueParser(byte flags) {
    switch((byte)(flags & ~0x3F)) {
    case 0x0: 
      return new StringValueParser();
    case 0x40:
      return new NumberValueParser();
    case (byte)0x80: 
      return new DateTimeValueParser();
    case (byte)0xC0:
      return new BinaryDataValueParser();
    default:
      throw new IllegalArgumentException("Invalid Flags...");
    }
  }
}
