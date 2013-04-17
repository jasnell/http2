import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import snell.http2.headers.HeaderBlock;
import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.dhe.Storage;
import snell.http2.headers.headerdiff.HeaderDiff;

public class TestHeaderDiff {

  public static void main(String... strings) throws Exception {
    
    Storage storage = new Storage();
    System.out.println(storage.indexOf(":method", StringValueSupplier.create("get")));
    
    System.exit(0);
    
    HeaderDiff hd = new HeaderDiff(HeaderDiff.Mode.REQUEST);
    //Delta hd = Delta.forRequest();

    for (int n = 0; n < 2; n++) {
      HeaderBlock headers = 
        HeaderBlock
          .make(hd.ser())
          .set("A", "B;" + n)
          .set("B", "A;" + n)
          .get();
      
      ByteArrayOutputStream out = 
        new ByteArrayOutputStream();
      
      headers.writeTo(out);
      
      System.out.println(Arrays.toString(out.toByteArray()));
    }
    
  }
  
}
