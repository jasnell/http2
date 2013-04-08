
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import snell.http2.frames.Frame;
import snell.http2.frames.HeadersFrame;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.delta.DeltaHeaderSerializer;

public class Test {
  
  public static void main(String... args) throws Exception {

    Delta delta = new Delta((byte)0x0);
    HeaderSerializer ser = 
      new DeltaHeaderSerializer(delta);
      
    ByteArrayOutputStream out = 
      new ByteArrayOutputStream();

    HeadersFrame frame = 
      HeadersFrame.make(ser)
        .streamId(6)
        .set(":method", "get")
        .set(":path", "/")
        .set(":host", "example.org")
        .set("user-agent", "This is the user agent")
        .get();

    frame.writeTo(out);
    Frame.parse(new ByteArrayInputStream(out.toByteArray()), ser);
    
    out = new ByteArrayOutputStream();
    
    frame = 
        HeadersFrame.make(ser)
          .streamId(6)
          .set(":method", "get")
          .set(":path", "/")
          .set(":host", "example.org")
          .set("user-agent", "This is the user agent")
          .get();

      frame.writeTo(out);
  
    System.out.println(Arrays.toString(out.toByteArray()));

    ByteArrayInputStream in = 
        new ByteArrayInputStream(
          out.toByteArray());

    frame = Frame.parse(in, ser);
    
    for (String s : frame) {
      System.out.println(s + "\t" + frame.get(s));
    }

  }

  
}
