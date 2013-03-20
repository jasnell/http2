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
public class DateTimeValueProvider 
  extends NumberValueProvider {

  public DateTimeValueProvider(long v) {
    super(v);
  }
  
  public DateTimeValueProvider(DateTime dt) {
    super(calc(dt));
  }
  
  private static long calc(DateTime dt) {
    if (dt == null) return -1;
    return dt.getMillis() / 1000;
  }
  
  public int flags() {
    return 0x80;
  }

  public DateTime dateTimeValue() {
    return new DateTime((longVal() * 1000));
  }
  
  public String toString() {
    return dateTimeValue().toString();
  }
  
  public static class DateTimeValueParser 
    implements ValueParser<DateTimeValueProvider> {
    @Override
    public DateTimeValueProvider parse(
      InputStream in, 
      int flags) 
        throws IOException {     
      return new DateTimeValueProvider(uvarint2long(in));
    }    
  }
  
  public static DateTimeValueProvider create(DateTime dt) {
    return new DateTimeValueProvider(dt);
  }
}
