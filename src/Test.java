
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.collect.Range;

import snell.http2.frames.Frame;
import snell.http2.frames.HeadersFrame;
import snell.http2.frames.SettingsFrame;
import snell.http2.frames.SettingsFrame.Settings;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.delta.DeltaHeaderSerializer;
import snell.http2.headers.delta.Operation;
import snell.http2.utils.IntMap;

public class Test {
  
  public static void main(String... args) throws Exception {

    Delta delta = new Delta((byte)0x0);
    HeaderSerializer ser = 
      new DeltaHeaderSerializer(delta);
      
    ByteArrayOutputStream out = 
      new ByteArrayOutputStream();

    HeadersFrame frame = 
      HeadersFrame.make(ser)
        .set(":method", "post")
        .set(":method", "get")
        .set(":method", "delete")
        .set("foo", "bar")
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
