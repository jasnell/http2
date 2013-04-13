import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import snell.http2.headers.HeaderBlock;
import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.headerdiff.Header;
import snell.http2.headers.headerdiff.Header.IndexingMode;
import snell.http2.headers.headerdiff.HeaderDiff;
import snell.http2.headers.headerdiff.HeaderTable;
import snell.http2.headers.headerdiff.NameTable;
import snell.http2.headers.headerdiff.Utils;

public class TestHeaderDiff {

  public static void main(String... strings) throws Exception {
    
    //HeaderDiff hd = new HeaderDiff(HeaderDiff.Mode.REQUEST);
    Delta hd = Delta.forRequest();

    for (int n = 0; n < 2; n++) {
      HeaderBlock headers = 
        HeaderBlock
          .make(hd.ser())
          .utf8()
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
