import static snell.http2.utils.RangedIntegerSupplier.forAllEvenIntegers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import snell.http2.frames.Frame;
import snell.http2.frames.HeadersFrame;
import snell.http2.headers.dhe.Dhe;
import snell.http2.headers.dhe.Dhe.Mode;
import snell.http2.utils.RangedIntegerSupplier;

public class DheTest {
  
  public static void main(String... args) throws Exception {

    final RangedIntegerSupplier stream_ids = 
      forAllEvenIntegers();

    final Dhe dhe = new Dhe(Mode.REQUEST);
    final Dhe dhe2 = new Dhe(Mode.REQUEST);
    
    for (int n = 0; n < 2; n++) {
      ByteArrayOutputStream out = 
        new ByteArrayOutputStream();
  
      HeadersFrame.make(dhe.ser())
        .streamId(stream_ids)
        .set(":method", "get")
        .set(":path", "/")
        .set(":host", "example.org")
        .set("foo", 123)
        .set("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
        .fin()
        .get()
        .writeTo(out);
        
      System.out.println(Arrays.toString(out.toByteArray()));
      
      ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
      
      HeadersFrame frame = 
        Frame.parse(in, dhe2.ser());
      for (String key : frame) {
        System.out.println(key + " = " + frame.get(key));
      }
    }
  }
  
}