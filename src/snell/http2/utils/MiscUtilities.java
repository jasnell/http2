package snell.http2.utils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Function;

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
        t.printStackTrace();
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
  
}
