package snell.http2.headers.delta;

import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.uvarint2int;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.primitives.UnsignedInteger;

import snell.http2.headers.BinaryValueSupplier.BinaryDataValueParser;
import snell.http2.headers.DateTimeValueSupplier.DateTimeValueParser;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.NumberValueSupplier.NumberValueParser;
import snell.http2.headers.StringValueSupplier.StringValueParser;
import snell.http2.headers.ValueSupplier;
import snell.http2.headers.ValueSupplier.ValueParser;
import snell.http2.utils.Pair;

public abstract class Operation {

  public static enum Code {
    TOGGL((byte)0x0) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Toggl.parse(in);
      }
    },
    ETOGGL((byte)0x1) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Toggl.parse(in);
      }
    },
    TRANG((byte)0x2) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Trang.parse(in);
      }
    },
    ETRANG((byte)0x3) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Trang.parse(in);
      }
    },
    KVSTO((byte)0x4) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Kvsto.parse(
          in, huffman);
      }
    },
    EKVSTO((byte)0x5) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Kvsto.parse(
          in, huffman);
      }
    },
    CLONE((byte)0x6) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Clone.parse(
          in,huffman);
      }
    },
    ECLONE((byte)0x7) {
      public Operation parse(
        InputStream in, 
        Huffman huffman) 
          throws IOException {
        return Clone.parse(
          in, huffman);
      }
    }
    ;
    private final byte code;
    Code(byte code) {
      this.code = code;
    }
    public abstract Operation parse(
      InputStream in, 
      Huffman huffman) 
        throws IOException;
    public byte code() {
      return code;
    }
    public static Code get(byte opcode) {
      try { 
        return values()[opcode];
      } catch (Throwable t) {
        throw new IllegalArgumentException();
      }
    }
  }

  
  protected final Code opcode;
  
  protected Operation(Code opcode) {
    this.opcode = opcode;
  }
  
  protected abstract void execute(
    Storage storage, 
    HeaderGroup group);
  
  @SuppressWarnings("rawtypes")
  protected abstract void ephemeralExecute(
    HeaderGroup group,
    Set<Pair<String,ValueSupplier>> keys_to_turn_off,
    HeaderSetter set);
  
  public final Code code() {
    return opcode;
  }
  
  @Override
  public int hashCode() {
    return Objects.hashCode(opcode);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Operation other = (Operation) obj;
    if (opcode != other.opcode)
      return false;
    return true;
  }

  public void writeTo(
    OutputStream buf) 
      throws IOException {}
  
  public final static class Toggl 
    extends Operation {
    private final int index;
    private transient int hash = -1;
    public Toggl(int index) {
      super(Code.TOGGL);
      this.index = index;
    }
    public void writeTo(
      OutputStream buf) 
        throws IOException {
      super.writeTo(buf);
      buf.write(int2uvarint(index));
    }
    public int index() {
      return index;
    }
    public String toString() {
      return String.format("TOGGL[%d]",index);
    }
    public static Toggl parse(
      InputStream in) 
        throws IOException {
      return new Toggl(uvarint2int(in));
    }
    @Override
    public int hashCode() {
      if (hash == -1)
        hash = Objects.hashCode(opcode,index);
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
      Toggl other = (Toggl) obj;
      if (index != other.index)
        return false;
      return true;
    }
    @Override
    protected void execute(
      Storage storage, 
      HeaderGroup group) {
        group.toggle(index());
    }    
    
    @Override
    @SuppressWarnings("rawtypes")
    protected void ephemeralExecute(
      HeaderGroup group,
      Set<Pair<String,ValueSupplier>> keys_to_turn_off,
      HeaderSetter set) {
      Pair<String,ValueSupplier> pair = 
          group.storage().lookup(index());
        if (pair == null)
          throw new InvalidOperationException();
      if (group.hasEntry(index())) {
        keys_to_turn_off.add(pair);
      } else {
        set.set(pair.one(),pair.two());
      }
    }
  }
  
  public final static class Trang 
    extends Operation {
    private final int s,e;
    private transient int hash = -1;
    public Trang(int s, int e) {
      super(Code.TRANG);
      this.s = s;
      this.e = e;
    }
    public void writeTo(
      OutputStream buf) 
        throws IOException {
      super.writeTo(buf);
      buf.write(int2uvarint(s));
      buf.write(int2uvarint(e));
    }
    public int start() {
      return s;
    }
    public int end() {
      return e;
    }
    public String toString() {
      return String.format("TRANG[%d,%d]",s,e);
    }
    public static Trang parse(
      InputStream in) 
        throws IOException {
      return new Trang(
        uvarint2int(in),
        uvarint2int(in));
    }
    @Override
    public int hashCode() {
      if (hash == -1)
        hash = Objects.hashCode(opcode,e,s);
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
      Trang other = (Trang) obj;
      if (e != other.e)
        return false;
      if (s != other.s)
        return false;
      return true;
    }
    @Override
    protected void execute(
      Storage storage, 
      HeaderGroup group) {
      for (int n = start(); n <= end(); n++)
        group.toggle(n);
    }
    @Override
    @SuppressWarnings("rawtypes")
    protected void ephemeralExecute(
      HeaderGroup group,
      Set<Pair<String,ValueSupplier>> keys_to_turn_off,
      HeaderSetter set) {
      for (int n = start(); n <= end(); n++) {
        Pair<String,ValueSupplier> pair = 
          group.storage().lookup(n);
        if (pair == null)
          throw new InvalidOperationException();
        if (group.hasEntry(n)) {
          keys_to_turn_off.add(pair);
        } else {
          set.set(pair.one(),pair.two());
        }
      }
    }
    
  }
  
  public final static class Clone 
    extends Operation {
    private final int index;
    private final ValueSupplier<?> val;
    private transient int hash = -1;
    public Clone(int index, ValueSupplier<?> val) {
      super(Code.CLONE);
      this.index = index;
      this.val = val;
    }
    public void writeTo(
      OutputStream buf) 
        throws IOException {
      super.writeTo(buf);
      buf.write(int2uvarint(index));
      buf.write(val.flags());
      val.writeTo(buf);
    }
    public int index() {
      return index;
    }
    @SuppressWarnings("unchecked")
    public <V extends ValueSupplier<?>>V val() {
      return (V)val;
    }
    public String toString() {
      return String.format("CLONE[%d,'%s']",index,val.toString());
    }
    public Kvsto asKvsto(String key) {
      return new Kvsto(key, val);
    }
    public static Clone parse(
      InputStream in, 
      Huffman huffman) 
        throws IOException {
      int index  = uvarint2int(in);
      byte flags = (byte)in.read(); // read the flags
      return new Clone(
        index, 
        selectValueParser(flags)
          .useHuffman(huffman)
          .parse(in,flags));
    }
    @Override
    public int hashCode() {
      if (hash == -1)
        hash = Objects.hashCode(opcode, index, val);
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
      Clone other = (Clone) obj;
      if (index != other.index)
        return false;
      if (val == null) {
        if (other.val != null)
          return false;
      } else if (!val.equals(other.val))
        return false;
      return true;
    }
    @Override
    protected void execute(
      Storage storage, 
      HeaderGroup group) {
      String key = 
        storage.lookupKey(index());
      if (key == null)
        throw new InvalidOperationException();
      asKvsto(key).execute(storage, group);
    }
    @SuppressWarnings("rawtypes")
    @Override
    protected void ephemeralExecute(
      HeaderGroup group,
      Set<Pair<String, ValueSupplier>> keys_to_turn_off, 
      HeaderSetter set) {
      String key = 
        group.storage()
          .lookupKey(index());
      if (key == null)
        throw new InvalidOperationException();
      set.set(key, val());   
    }
    
  }
  
  public final static class Kvsto 
    extends Operation {
    private final String key;
    private final ValueSupplier<?> val;
    private transient int hash = -1;
    public Kvsto(String key, ValueSupplier<?> val) {
      super(Code.KVSTO);
      this.key = key;
      this.val = val;
    }
    public void writeTo(
      OutputStream buf) 
        throws IOException {
      super.writeTo(buf);
      if (key.length() >= 256)
        throw new IllegalArgumentException();
      buf.write(UnsignedInteger.fromIntBits(key.length()).byteValue());
      buf.write(key.getBytes("ISO-8859-1"));
      // TODO: filter out unwanted characters in key name
      buf.write(val.flags());
      val.writeTo(buf);
    }
    public String key() {
      return key;
    }
    @SuppressWarnings("unchecked")
    public <V extends ValueSupplier<?>>V val() {
      return (V)val;
    }
    public String toString() {
      return String.format("KVSTO['%s','%s']",key,val.toString());
    }
    public static Kvsto parse(
      InputStream in, 
      Huffman huffman) 
        throws IOException {
      int c = in.read(); // length of the key
      byte[] keydata = new byte[c];
      int r = in.read(keydata);
      String key = new String(keydata,0,r,"ISO-8859-1");
      byte flags = (byte)in.read(); // read in the flags
      return new Kvsto(
        key,
        selectValueParser(flags)
          .useHuffman(huffman)
          .parse(in,flags));
    }
    @Override
    public int hashCode() {
      if (hash == -1)
        hash = Objects.hashCode(opcode,key,val);
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
      Kvsto other = (Kvsto) obj;
      if (key == null) {
        if (other.key != null)
          return false;
      } else if (!key.equals(other.key))
        return false;
      if (val == null) {
        if (other.val != null)
          return false;
      } else if (!val.equals(other.val))
        return false;
      return true;
    }
    @Override
    protected void execute(
      Storage storage, 
      HeaderGroup group) {
      int idx = storage.store(key(),val());
      if (idx > 0)
        group.toggle(idx);
    }
    @SuppressWarnings("rawtypes")
    @Override
    protected void ephemeralExecute(
      HeaderGroup group,
      Set<Pair<String, ValueSupplier>> keys_to_turn_off, 
      HeaderSetter set) {
      set.set(key(), val());
    }
    
  }
    
  public static Operation makeToggl(
    int index) {
      return new Toggl(index);
  }
  
  public static Operation makeTrang(
    int s, 
    int e) {
      return new Trang(s,e);
  }
  
  public static Operation makeClone(
    int index, 
    ValueSupplier<?> val) {
      return new Clone(index,val);
  }
  
  public static Operation makeKvsto(
    String key, 
    ValueSupplier<?> val) {
      return new Kvsto(key, val);
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
