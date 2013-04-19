package snell.http2.utils.headerdiff;

import static snell.http2.utils.headerdiff.Utils.writeInteger;
import static snell.http2.utils.headerdiff.Utils.writeInteger14;
import static snell.http2.utils.headerdiff.Utils.writeName;
import static snell.http2.utils.headerdiff.Utils.writeString;
import static snell.http2.utils.headerdiff.Utils.writeSubstring;

import java.io.IOException;
import java.io.OutputStream;

import com.google.common.base.Supplier;

@SuppressWarnings("unchecked")
public abstract class Header {

  public static LiteralHeaderBuilder literal() {
    return new LiteralHeaderBuilder();
  }
  
  public static LiteralHeaderBuilder literal(Name name) {
    return literal().name(name);
  }
  
  public static IndexedHeaderBuilder indexed() {
    return new IndexedHeaderBuilder();
  }
  
  public static IndexedHeaderBuilder indexed(Name name) {
    return indexed().name(name);
  }
  
  public static DeltaHeaderBuilder delta() {
    return new DeltaHeaderBuilder();
  }
  
  public static DeltaHeaderBuilder delta(Name name) {
    return delta().name(name);
  }
  
  public static abstract class HeaderBuilder
    <H extends Header, B extends HeaderBuilder<H,B>>
    implements Supplier<H> {
    
    private Name name;
    
    public B name(Name name) {
      this.name = name;
      return (B)this;
    }
    
  }
  
  private final Name name;
  private transient int hash = 1;
  
  protected Header(HeaderBuilder<?,?> builder) {
    this.name = builder.name;
  }
  
  public Name name() {
    return name;
  }
  
  @Override
  public int hashCode() {
    if (hash == 1)
      hash = 31 * hash + ((name == null) ? 0 : name.hashCode());
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
    Header other = (Header) obj;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    return true;
  }
  
  public <H extends Header>H update(HeaderTable table) {
    return (H)this;
  }
  
  public void writeTo(OutputStream out) throws IOException {}

  public static enum IndexingMode {
    NONE,
    INCREMENTAL,
    SUBSTITUTION;
  }
  
  public final static class LiteralHeader 
    extends Header {
    
      private final String val;
      private final boolean useIndex;
      private final IndexingMode mode;
      private final int existingIdx;
      private transient int hash = 1;
    
      protected LiteralHeader(LiteralHeaderBuilder builder) {
        super(builder);
        this.val = builder.value;
        this.useIndex = builder.useIndex;
        this.mode = builder.mode;
        this.existingIdx = builder.existingIdx;
      }
      
      public <H extends Header>H update(HeaderTable table) {
        switch(mode) {
        case INCREMENTAL:
          table.store(name(), val);
          break;
        case SUBSTITUTION:
          table.replace(existingIdx, name(), val);
          break;
        case NONE:
          break;
        }
        return (H)this;
      }
      
      public boolean usingIndex() {
        return useIndex;
      }
      
      public IndexingMode mode() {
        return mode;
      }
      
      public int substitutionIndex() {
        return existingIdx;
      }

      public String value() {
        return val;
      }

      @Override
      public int hashCode() {
        if (hash == 1) {
          hash = 31 * super.hashCode() + ((val == null) ? 0 : val.hashCode());
          hash = 31 * hash + ((mode == null) ? 0 : mode.hashCode());
          hash = 31 * hash + (useIndex ? 1331 : 1337);
          hash = 31 * hash + existingIdx;
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
        LiteralHeader other = (LiteralHeader) obj;
        if (val == null) {
          if (other.val != null)
            return false;
        } else if (!val.equals(other.val))
          return false;
        if (mode != other.mode)
          return false;
        if (useIndex != other.useIndex)
          return false;
        if (existingIdx != other.existingIdx)
          return false;
        return true;
      }

      @Override
      public void writeTo(
        OutputStream out) 
          throws IOException {
        switch(mode) {
          case NONE:
            writeName((byte)0x0,name(),5,useIndex,out);
            break;
          case INCREMENTAL:
            writeName((byte)0x20,name(),4,useIndex,out);
            break;
          case SUBSTITUTION:
            writeName((byte)0x30,name(),4,useIndex,out);
            writeInteger((byte)0x0,0,existingIdx,out);
            break;
        }
        writeString(val,out);
      }
      
  }
  
  public final static class LiteralHeaderBuilder 
    extends HeaderBuilder<LiteralHeader,LiteralHeaderBuilder> {

    private String value;
    private boolean useIndex = false;
    private int existingIdx = -1;
    private IndexingMode mode = IndexingMode.NONE;
    
    public LiteralHeaderBuilder substitution(int idx) {
      this.existingIdx = idx;
      return this;
    }
    
    public LiteralHeaderBuilder mode(IndexingMode mode) {
      this.mode = mode;
      return this;
    }
    
    public LiteralHeaderBuilder useIndex(boolean on) {
      this.useIndex = on;
      return this;
    }
    
    public LiteralHeaderBuilder useIndex() {
      return useIndex(true);
    }
    
    public LiteralHeaderBuilder value(String val) {
      this.value = val;
      return this;
    }
    
    @Override
    public LiteralHeader get() {
      return new LiteralHeader(this);
    }
    
  }
  
  public final static class IndexedHeader 
    extends Header {
    
    private final int idx;
    private transient int hash = 1;
    
    protected IndexedHeader(IndexedHeaderBuilder builder) {
      super(builder);
      this.idx = builder.index;
    }
    
    public int index() {
      return idx;
    }

    @Override
    public int hashCode() {
      if (hash == 1)
        hash = 31 * super.hashCode() + idx;
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
      IndexedHeader other = (IndexedHeader) obj;
      if (idx != other.idx)
        return false;
      return true;
    }

    @Override
    public void writeTo(OutputStream out) 
      throws IOException {
        if (idx < 64)
          out.write((byte)0x80 | (byte)idx);
        else
          writeInteger14(
            (byte)0xC0, 
            idx, 
            out);
    }
    
  }
  
  public final static class IndexedHeaderBuilder
    extends HeaderBuilder<IndexedHeader,IndexedHeaderBuilder> {
    
    private int index;
    
    public IndexedHeaderBuilder index(int idx) {
      this.index = idx;
      return this;
    }
    
    @Override
    public IndexedHeader get() {
      return new IndexedHeader(this);
    }
    
  }
  
  public final static class DeltaHeader 
    extends Header {
    
    private final int index;
    private final String val;
    private final IndexingMode mode;
    private final int existingIdx;
    private final int commonPrefixLength;
    private transient int hash = 1;
    
    protected DeltaHeader(DeltaHeaderBuilder builder) {
      super(builder);
      this.index = builder.index;
      this.val = builder.val;
      this.mode = builder.mode;
      this.existingIdx = builder.existingIdx;
      this.commonPrefixLength = builder.commonPrefixLength;
    }
    
    public int commonPrefixLength() {
      return commonPrefixLength;
    }
    
    public int index() {
      return index;
    }
    
    public String value() {
      return val;
    }

    @Override
    public int hashCode() {
      if (hash == 1) {
        hash = 31 * super.hashCode() + index;
        hash = 31 * hash + ((val == null) ? 0 : val.hashCode());
        hash = 31 * hash + ((mode == null) ? 0 : mode.hashCode());
        hash = 31 * hash + existingIdx;
        hash = 31 * hash + commonPrefixLength;
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
      DeltaHeader other = (DeltaHeader) obj;
      if (index != other.index)
        return false;
      if (val == null) {
        if (other.val != null)
          return false;
      } else if (!val.equals(other.val))
        return false;
      if (mode != other.mode)
        return false;
      if (existingIdx != other.existingIdx)
        return false;
      if (commonPrefixLength != other.commonPrefixLength)
        return false;
      return true;
    }

    @Override
    public void writeTo(
      OutputStream out) 
        throws IOException {
      byte cur = 
        mode == IndexingMode.NONE ? 
          (byte)0x40 : 
          (byte)0x60;
      writeInteger(
        cur,
        4,
        existingIdx,
        out);
      writeInteger(
        (byte)0x0,
        0,
        commonPrefixLength,
        out); // length of shared prefix;
      writeSubstring(
        commonPrefixLength,
        val,
        out);
    }

    public <H extends Header>H update(HeaderTable table) {
      switch(mode) {
      case INCREMENTAL:
        table.store(name(), val);
        break;
      case SUBSTITUTION:
        table.replace(existingIdx, name(), val);
        break;
      case NONE:
        break;
      }
      return (H)this;
    }
  }
  
  public final static class DeltaHeaderBuilder
    extends HeaderBuilder<DeltaHeader,DeltaHeaderBuilder> {
    
    private int index;
    private String val;
    private int existingIdx = -1;
    private int commonPrefixLength = 0;
    private IndexingMode mode = IndexingMode.NONE;
    
    public DeltaHeaderBuilder commonPrefixLength(int len) {
      this.commonPrefixLength = len;
      return this;
    }
    
    public DeltaHeaderBuilder referenceIndex(int idx) {
      this.existingIdx = idx;
      return this;
    }
    
    public DeltaHeaderBuilder mode(IndexingMode mode) {
      this.mode = mode;
      return this;
    }
    
    public DeltaHeaderBuilder value(String val) {
      this.val = val;
      return this;
    }
    
    public DeltaHeaderBuilder index(int idx) {
      this.index = idx;
      return this;
    }
    
    @Override
    public DeltaHeader get() {
      return new DeltaHeader(this);
    }
    
  }
    
}
