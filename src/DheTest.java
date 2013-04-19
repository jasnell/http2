import static snell.http2.utils.RangedIntegerSupplier.forAllEvenIntegers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import snell.http2.frames.Frame;
import snell.http2.frames.HeadersFrame;
import snell.http2.frames.HeadersFrame.HeadersFrameBuilder;
import snell.http2.headers.Huffman;
import snell.http2.headers.dhe.Dhe;
import snell.http2.headers.dhe.Storage;
import snell.http2.utils.IoUtils;
import snell.http2.utils.RangedIntegerSupplier;

public class DheTest {
  
  public static void main(String... args) throws Exception {

    Storage storage = new Storage();
    
    storage.printTable();
    
    
    System.exit(0);
    
    final RangedIntegerSupplier stream_ids = 
      forAllEvenIntegers();

    Dhe encoder = Dhe.forRequest();
    Dhe decoder = Dhe.forRequest();
    
    for (int n = 0; n < 3; n++) {
      ByteArrayOutputStream out = 
        new ByteArrayOutputStream();
  
      HeadersFrameBuilder b =
        HeadersFrame.make(encoder)
        .streamId(stream_ids)
        .fin();
      
      if (n == 0) {
        b.set("foo", "bar");
      } else if (n == 1) {
        b.set("foo", "baz");
      }
      
      b.get()
        .writeTo(out);
        
      System.out.println(Arrays.toString(out.toByteArray()));
      
      ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
      
      HeadersFrame frame = 
        Frame.parse(in, decoder);
      for (String key : frame) {
        System.out.println(key + " = " + frame.get(key));
      }
    }

  }
  
}
