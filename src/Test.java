
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import snell.http2.frames.DataFrame;
import snell.http2.frames.Frame;
import snell.http2.frames.HeadersFrame;
import snell.http2.frames.SettingsFrame;
import snell.http2.frames.SettingsFrame.SettingFlags;
import snell.http2.frames.SettingsFrame.Settings;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.delta.DeltaHeaderSerializer;

public class Test {
  
  public static void main(String... args) throws Exception {
    
    Delta delta = new Delta(1);
    HeaderSerializer ser = 
        new DeltaHeaderSerializer(delta);
    DateTime dt = DateTime.now(DateTimeZone.UTC);
      
    ByteArrayOutputStream out = 
      new ByteArrayOutputStream();

    SettingsFrame frame = 
      SettingsFrame.make()
        .persisted(Settings.CURRENT_CWND, 100)
        .get();

//    DataFrame frame =
//      DataFrame.make()
//        .streamId(1)
//        .fill(new ByteArrayInputStream(new byte[] {1,2,3}))
//        .get();
//
    frame.writeTo(out);
  
    System.out.println(Arrays.toString(out.toByteArray()));
    
    ByteArrayInputStream in = 
      new ByteArrayInputStream(
        out.toByteArray());
    frame = Frame.parse(in, ser);
    

  }

  
}
