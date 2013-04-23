import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import snell.http2.utils.IoUtils;



public class Test {

  public static void main(String... args) throws Exception {
    

    final long c = 4294967296000L;
    
    //DateTime dt = DateTime.now(DateTimeZone.UTC);
    DateTime dt = DateTime.parse("2106-02-07T06:28:16.000Z");
    long m = dt.getMillis();
    
    System.out.println(Arrays.toString(IoUtils.long2uvarint(m)));
    
    byte e = (byte)(m / c);    
    long n = m % c;
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    out.write(e);
    out.write(IoUtils.long2uvarint(n));

    System.out.println(Arrays.toString(out.toByteArray()));
    
    long r = (e&0x00FF) * c + n;

    dt = new DateTime(r, DateTimeZone.UTC);

    System.out.println(dt);
//   
  }
  

}
