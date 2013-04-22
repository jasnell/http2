package snell.http2.utils;

import static com.google.common.primitives.Ints.toByteArray;
import static com.google.common.primitives.Longs.toByteArray;
import static com.google.common.primitives.Shorts.toByteArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;

public final class IoUtils {

  /**
   * Calculate the size of a serialized uvarint
   */
  public static int size(int t) {
    int est = 0;
    if (t < 0) est = 5; // negative will always be 5-bytes
    else {
      do { 
        est++; 
      } while ((t>>=7) > 0);
    }
    return est;
  }
  
  /**
   * Calculate the size of a serialized uvarint
   */
  public static int size(long t) {
    int est = 0;
    if (t < 0) est = 10; // negative will always be 10-bytes
    else {
      do { 
        est++; 
      } while ((t>>=7) > 0);
    }
    return est;
  }
  
  public static byte[] unsignedBytes(UnsignedInteger uint) {
    int intval = uint.intValue();
    byte[] bytes = toByteArray(intval);
    return bytes;
  }
  
  public static byte[] unsignedBytes(UnsignedLong ulng) {
    long lngval = ulng.longValue();
    byte[] bytes = toByteArray(lngval);
    return bytes;
  }
  
  public static byte[] long2uvarint(long num) {
    if (num == 0) return new byte[] {0};
    byte[] buf = new byte[size(num)];
    int pos = 0;
    while(num != 0) {
      long m = num >>> 7; // unsigned shift
      buf[pos++] = (byte)((num & ~0x80) | (m > 0?0x80:0x00));
      num = m;
    }
    return buf;
  }

  public static byte[] int2uvarint(int num) {
    if (num == 0) return new byte[] {0};
    byte[] buf = new byte[size(num)];
    int pos = 0;
    while(num != 0) {
      int m = num >>> 7; // unsigned shift
      buf[pos++] = (byte)((num & ~0x80) | (m > 0?0x80:0x00));
      num = m;
    }
    return buf;
  }
  
  public static byte[] int2uvarint(byte[] bytes) {
    if (bytes.length == 0) return new byte[] {0};
    return bytes.length == 4 ?
      int2uvarint(Ints.fromByteArray(bytes)) :
      long2uvarint(Longs.fromByteArray(bytes));
  }
  
  public static int uvarint2int(InputStream in) throws IOException {
    return (int)uvarint2long(in);
  }
  
  public static long uvarint2long(InputStream in) throws IOException {
    long l = 0;
    int r = -1, pos = 0;
    while((r = in.read()) > -1 && pos < 10) { // make sure we never read more than 10-bytes to prevent overflow
      l |= ((long)(r & 0x7F)) << (7 * pos);
      if ((r & 0x80) != 0x80) break;
      pos++;
    }
    return l;
  }
    
  public static void write24(
    OutputStream out, 
    int val) 
      throws IOException {
    out.write(toByteArray(val),1,3);
  }
  
  public static void write32(
    OutputStream out,
    int val) 
      throws IOException {
       out.write(toByteArray(val));
  }
  
  public static int read32(
    InputStream in)
      throws IOException {
    byte[] buf = new byte[4];
    int r = in.read(buf);
    if (r < 4)
      throw new IOException();
    return Ints.fromByteArray(buf);
  }
  
  public static long read64(
    InputStream in)
      throws IOException {
    byte[] buf = new byte[8];
    int r = in.read(buf);
    if (r < 8)
      throw new IOException();
    return Longs.fromByteArray(buf);
  }
  
  public static void write64(
    OutputStream out,
    long val)
      throws IOException {
        out.write(toByteArray(val));
  }
  
  public static void write16(
    OutputStream out, 
    short val) 
      throws IOException {
        out.write(toByteArray(val));
  }
  
  public static void write16(
    OutputStream out,
    int val)
      throws IOException {
    byte[] bytes = toByteArray(val);
    // only write two lsb's
    out.write(bytes[2]);
    out.write(bytes[3]);
  }
  
  public static void writeChar(
    OutputStream out,
    char c) 
      throws IOException {
    write16(out,(short)c);
  }
  
  public static byte[] ascii(String s) {
    try {
      return s.getBytes("ISO-8859-1");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  public static byte[] readLengthPrefixedData(InputStream in) throws IOException {
    int length = uvarint2int(in);
    byte[] data = new byte[length];
    if (length > 0)
      if (in.read(data) < length)
        throw new IllegalStateException(); // obviously this doesn't work long term
    return data;
  }
  
  public static String readLengthPrefixedString(InputStream in, String charset) throws IOException {
    byte[] data = readLengthPrefixedData(in);
    return new String(data,charset);
  }
  
  public static void writeLengthPrefixedString(OutputStream buffer, String s) throws IOException {
    if (s == null) 
      buffer.write(0);
    else {
      byte[] data = ascii(s);
      buffer.write(int2uvarint(data.length));
      buffer.write(data);
    }
  }
}
