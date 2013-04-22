package snell.http2.utils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Function;
import com.google.common.primitives.Longs;

public final class MiscUtilities {

  private MiscUtilities() {}
  
  private static enum Formats {
    RFC1123("EEE, dd MMM yyyy HH:mm:ss ZZZ"),
    RFC1036("EEE, dd-MMM-yy HH:mm:ss ZZZ"),
    ASCTIME("EEE MMM d HH:mm:ss yyyy");
    
    final DateTimeFormatter formatter;
    Formats(String pattern) {
      this.formatter = DateTimeFormat.forPattern(pattern);
    }
    
    DateTime toDateTime(String s) {
      return DateTime.parse(s, formatter);
    }
    
    DateTime attemptParse(String s ) {
      try {
        return toDateTime(s);
      } catch (Throwable t) {
        return null;
      }
    }
    
    static DateTime parse(String s) {
      DateTime dt = RFC1123.attemptParse(s);
      if (dt == null) dt = RFC1036.attemptParse(s);
      if (dt == null) dt = ASCTIME.attemptParse(s);
      return dt;
    }
  }

  public static Function<String,DateTime> httpDateToJodaTime = 
    new Function<String,DateTime>() {
      public DateTime apply(String input) {
        return Formats.parse(input);
      }
  };
  
  
  
  //////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////
  
  
  public static int log(long v) {
    int n = 0;
    while((v >>>= 1) != 0) {n++;}
    return n;
  }

  public static byte[] lngenc(long v) {

    if (v <= 0x7F & v >= 0) {
      return new byte[] {(byte)v};
    } else {
      BitBucket bucket = 
        new BitBucket();
      int n = log(v);
      int s = (n+1) >>> 3;
      bucket.storeBitsOn(s);
      bucket.storeBitsOff(1);
      
      if (v < 0) {
        bucket.storeBitsOff(7);
        bucket.storeBits(Longs.toByteArray(v),64);
      } else {
        v <<= ((8-(s+1))*8)+(s+1);
        int w = 8*(s+1)-(s+1);
        bucket.storeBits(Longs.toByteArray(v),w);        
      }

      return bucket.toByteArray();
    }
    
    // 0 0000000
    // 10 000000 00000000
    // 110 00000 00000000 00000000
    // 1110 0000 00000000 00000000 00000000
    // 11110 000 00000000 00000000 00000000 00000000
    // 111110 00 00000000 00000000 00000000 00000000 00000000
    // 1111110 0 00000000 00000000 00000000 00000000 00000000 00000000
    // 11111110  00000000 00000000 00000000 00000000 00000000 00000000 00000000
    // 11111111 0 0000000 00000000 00000000 00000000 00000000 00000000 00000000 00000000 
    
  }
  
}
