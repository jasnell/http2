package snell.http2.utils.headerdiff;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.OutputStream;

import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.ValueSupplier;

import static com.google.common.base.Strings.commonPrefix;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;

public final class Utils {

  public static final byte[] DELTA_LIMITS = 
    new byte[] {'/', '&', '?', '=', ',', ';', ' '};
  
  private Utils() {}
  
  public static byte[] utf8bytes(String s1) {
    if (s1 == null) return new byte[0];
    try {
      return s1.getBytes("UTF-8");
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
  
  public static int utf8length(String s1) {
    return utf8bytes(s1).length;
  }
  
  public static int commonPrefixLength(String s1, String s2) {
    byte[] bytes = utf8bytes(commonPrefix(s1, s2));
    int l = bytes.length;
    boolean valid_end = false;
    while (l > 0 && !valid_end) {
      for (byte c : DELTA_LIMITS) {
        if (bytes[l-1] == c) {
          valid_end = true;
          break;
        }
      }
      if (valid_end)
        break;
      l--;
    }
    return l;
  }
  
  public static String stringFromValueSupplier(
    ValueSupplier<?> val) {
    if (val == null) return "";
    checkArgument(val instanceof StringValueSupplier);
    StringValueSupplier sval = (StringValueSupplier) val;
    Iterable<String> values = sval.get();
    checkArgument(Iterables.size(values) == 1);
    return values.iterator().next();
  }
  
  public static void writeValueSupplier(
    ValueSupplier<?> val, 
    OutputStream out)
      throws IOException {
    writeString(
      stringFromValueSupplier(val),
      out);
  }
  
  public static void writeName(
    byte current,
    Name name, 
    int prefixLen,
    boolean useIndex, 
    OutputStream out)
      throws IOException {
    if (useIndex) {
      writeInteger(
        current,
        prefixLen,
        name.index()+1,
        out);
    } else {
      writeInteger(
        current,
        prefixLen,0,out);
      writeString(
        name.value(),
        out);
    }
  }
  
  public static void writeSubstring(
    int skip, 
    String val, 
    OutputStream out)
      throws IOException {
    val = val == null ? "" : val;
    writeString(val.substring(skip),out);
  }
  
  public static void writeString(
    String val, 
    OutputStream out)
      throws IOException {
    try {
      val = val == null ? "" : val;
      byte[] bytes = val.getBytes("UTF-8");
      writeInteger((byte)0x0,0,bytes.length,out);
      out.write(bytes);
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
  
  public static void writeInteger14(
    byte current, 
    int value, 
    OutputStream out)
      throws IOException {
      writeInteger(current,14,value-64,out);
  }
  
  public static void writeInteger(
    byte current, 
    int prefixBits, 
    int value,
    OutputStream out) 
      throws IOException {
    checkArgument(value <= 0xFFFF);
    
    final int MAX_VALUE = (1 << prefixBits) - 1;
    
    if (value < MAX_VALUE) {
      if (prefixBits <= 8) {
        current |= (byte)value;
        out.write(current);
      } else {
        out.write(current | (value >> 8));
        out.write(value & 0xff);
      }
    } else {
      if (prefixBits > 0) {
        current |= MAX_VALUE;
        out.write(current);
      }
      value -= MAX_VALUE;
      if (value == 0)
        out.write(0);
      while (value > 0) {
        byte b = 0x0;
        byte q = (byte)(value >>> 7);
        byte r = (byte)(value - (q << 7));
        if (q > 0) 
          b = (byte)0x80;
        b |= r;
        out.write(b);
        value = q;
      }
    }
   
  }
}
