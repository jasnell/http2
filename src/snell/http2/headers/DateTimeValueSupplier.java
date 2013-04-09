package snell.http2.headers;

import static snell.http2.utils.IoUtils.uvarint2long;
import java.io.IOException;
import java.io.InputStream;
import org.joda.time.DateTime;

/**
 * Encodes a Date-Time as a uvarint representing the 
 * total number of seconds that have elapsed since 
 * the NEW_EPOCH (1990-01-01T00:00:00Z)
 */
public class DateTimeValueSupplier 
  extends NumberValueSupplier {

  public DateTimeValueSupplier(long v) {
    super(DATE,v);
  }
  
  public DateTimeValueSupplier(DateTime dt) {
    super(DATE,calc(dt));
  }
  
  private static long calc(DateTime dt) {
    if (dt == null) return -1;
    return dt.getMillis() / 1000;
  }

  public DateTime dateTimeValue() {
    return new DateTime((longVal() * 1000));
  }
  
  public String toString() {
    return dateTimeValue().toString();
  }
  
  public static class DateTimeValueParser 
    extends ValueParser<DateTimeValueSupplier,DateTimeValueParser> {
    @Override
    public DateTimeValueSupplier parse(
      InputStream in, 
      byte flags) 
        throws IOException {     
      return new DateTimeValueSupplier(uvarint2long(in));
    }    
  }
  
  public static DateTimeValueSupplier create(DateTime dt) {
    return new DateTimeValueSupplier(dt);
  }
}
