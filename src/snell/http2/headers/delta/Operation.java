package snell.http2.headers.delta;

import static snell.http2.utils.IoUtils.int2uvarint;
import static snell.http2.utils.IoUtils.uvarint2int;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.google.common.base.Objects;
import com.google.common.collect.Range;
import com.google.common.primitives.UnsignedInteger;

import snell.http2.headers.BinaryDataValueProvider.BinaryDataValueParser;
import snell.http2.headers.DateTimeValueProvider.DateTimeValueParser;
import snell.http2.headers.NumberValueProvider.NumberValueParser;
import snell.http2.headers.StringValueProvider.StringValueParser;
import snell.http2.headers.ValueProvider;
import snell.http2.headers.ValueProvider.ValueParser;

public abstract class Operation 
  implements Comparable<Operation> {

  public static enum Code {
    STOGGL((byte)0x0, "stoggl") {
      public Operation parse(InputStream in) throws IOException {
        return Toggl.parse(in);
      }
    },
    ETOGGL((byte)0x1, "etoggl") {
      public Operation parse(InputStream in) throws IOException {
        return Toggl.parse(in);
      }
    },
    STRANG((byte)0x2, "strang") {
      public Operation parse(InputStream in) throws IOException {
        return Trang.parse(in);
      }
    },
    ETRANG((byte)0x3, "etrang") {
      public Operation parse(InputStream in) throws IOException {
        return Trang.parse(in);
      }
    },
    SKVSTO((byte)0x4, "skvsto") {
      public Operation parse(InputStream in) throws IOException {
        return Kvsto.parse(in);
      }
    },
    EKVSTO((byte)0x5, "ekvsto") {
      public Operation parse(InputStream in) throws IOException {
        return Kvsto.parse(in);
      }
    },
    SCLONE((byte)0x6, "sclone") {
      public Operation parse(InputStream in) throws IOException {
        return Clone.parse(in);
      }
    },
    ECLONE((byte)0x7, "eclone") {
      public Operation parse(InputStream in) throws IOException {
        return Clone.parse(in);
      }
    }
    ;
    private final byte code;
    private final String lbl;
    Code(byte code, String lbl) {
      this.code = code;
      this.lbl = lbl;
    }
    public abstract Operation parse(InputStream in) throws IOException;
    public String label () {
      return lbl;
    }
    public byte code() {
      return code;
    }
    public static Code get(byte b) {
      try {
        return values()[b];
      } catch (Throwable t) {
        throw new IllegalArgumentException();
      }
    }
  }

  
  protected final byte opcode;
  
  protected Operation(Code opcode) {
    this.opcode = opcode.code;
  }
  
  public byte opcode() {
    return opcode;
  }
  
  @Override
  public int compareTo(Operation o) {
    if (opcode < o.opcode) return -1;
    if (opcode > o.opcode) return 1;
    return 0;
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

  public void writeTo(OutputStream buf) throws IOException {}
  
  public final static class Toggl extends Operation {
    private final int index;
    private transient int hash = -1;
    public Toggl(int index) {
      super(Code.STOGGL);
      this.index = index;
    }
    public void writeTo(OutputStream buf) throws IOException {
      super.writeTo(buf);
      buf.write(int2uvarint(index));
    }
    public int index() {
      return index;
    }
    public String toString() {
      return String.format("TOGGL[%d]",index);
    }
    public static Toggl parse(InputStream in) throws IOException {
      return new Toggl(uvarint2int(in));
    }
    @Override
    public int compareTo(Operation o) {
      int t = super.compareTo(o);
      if (t!=0) return t;
      Toggl other = (Toggl) o;
      if (index > other.index) return 1;
      return -1;
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
    
  }
  
  public final static class Trang extends Operation {
    private final int s,e;
    private transient int hash = -1;
    public Trang(int s, int e) {
      super(Code.STRANG);
      this.s = s;
      this.e = e;
    }
    public void writeTo(OutputStream buf) throws IOException {
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
    public static Trang parse(InputStream in) throws IOException {
      return new Trang(
        uvarint2int(in),
        uvarint2int(in));
    }
    private Range<Integer> asRange() {
      return Range.closed(s,e);
    }
    @Override
    public int compareTo(Operation o) {
      int t = super.compareTo(o);
      if (t!=0) return t;
      Trang other = (Trang) o;
      Range<Integer> r1 = asRange();
      Range<Integer> r2 = other.asRange();
      if (r1.lowerEndpoint() < r2.lowerEndpoint()) return -1;
      if (r1.upperEndpoint() > r2.upperEndpoint()) return 1;
      return -1;
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
    
  }
  
  public final static class Clone extends Operation {
    private final int index;
    private final ValueProvider val;
    private transient int hash = -1;
    public Clone(int index, ValueProvider val) {
      super(Code.SCLONE);
      this.index = index;
      this.val = val;
    }
    public void writeTo(OutputStream buf) throws IOException {
      super.writeTo(buf);
      buf.write(int2uvarint(index));
      buf.write(val.flags());
      val.writeTo(buf);
    }
    public int index() {
      return index;
    }
    public ValueProvider val() {
      return val;
    }
    public String toString() {
      return String.format("CLONE[%d,'%s']",index,val.toString());
    }
    public Kvsto asKvsto(String key) {
      return new Kvsto(key, val);
    }
    public static Clone parse(InputStream in) throws IOException {
      int index  = uvarint2int(in);
      int flags = in.read(); // read the flags
      return new Clone(index, selectValueParser(flags).parse(in,flags));
    }
    @Override
    public int compareTo(Operation o) {
      int t = super.compareTo(o);
      if (t!=0) return t;
      Clone other = (Clone) o;
      if (index < other.index) return -1;
      if (index > other.index) return 1;
      return -1;
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
    
  }
  
  public final static class Kvsto extends Operation {
    private final String key;
    private final ValueProvider val;
    private transient int hash = -1;
    public Kvsto(String key, ValueProvider val) {
      super(Code.SKVSTO);
      this.key = key;
      this.val = val;
    }
    public void writeTo(OutputStream buf) throws IOException {
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
    public ValueProvider val() {
      return val;
    }
    public String toString() {
      return String.format("KVSTO['%s','%s']",key,val.toString());
    }
    public static Kvsto parse(InputStream in) throws IOException {
      int c = in.read(); // length of the key
      byte[] keydata = new byte[c];
      int r = in.read(keydata);
      String key = new String(keydata,0,r,"ISO-8859-1");
      int flags = in.read(); // read in the flags
      return new Kvsto(key,selectValueParser(flags).parse(in,flags));
    }
    @Override
    public int compareTo(Operation o) {
      int t = super.compareTo(o);
      if (t!=0) return t;
      return -1;
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
    
  }
    
  public static Operation makeToggl(int index) {
    return new Toggl(index);
  }
  
  public static Operation makeTrang(int s, int e) {
    return new Trang(s,e);
  }
  
  public static Operation makeClone(int index, ValueProvider val) {
    return new Clone(index,val);
  }
  
  public static Operation makeKvsto(String key, ValueProvider val) {
    return new Kvsto(key, val);
  }
    
  static final ValueParser<?> selectValueParser(int flags) {
    int b = flags >>> 6;
    switch(b) {
    case 0: 
      return new StringValueParser();
    case 1:
      return new NumberValueParser();
    case 2: 
      return new DateTimeValueParser();
    case 3:
      return new BinaryDataValueParser();
    default:
      throw new IllegalArgumentException("Invalid Flags...");
    }
  }
}
