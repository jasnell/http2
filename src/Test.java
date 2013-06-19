
import snell.http2.frames.Frame;
import snell.http2.frames.GoAwayFrame;
import snell.http2.frames.PingFrame;
import snell.http2.frames.SettingsFrame;
import snell.http2.frames.SettingsFrame.Settings;
import snell.http2.frames.Status;
import snell.http2.frames.WindowUpdateFrame;
import snell.http2.io.BasicReactor;
import snell.http2.io.Connection;

public class Test {

  public static void main(String... args) throws Exception {

    TestReactor reactor = 
      new TestReactor();
    
    Connection conn = 
      Connection.makeServer()
        .reactor(reactor)
        .get();
    
    reactor.sendSettings();
    
    reactor.sendPing();
    
    reactor.sendWindowUpdate();
    
    reactor.sendGoAway();
    
  }

  
  public static final class TestReactor extends BasicReactor {

    public void sendPing() {
      handler().onFrame(
        PingFrame
          .make()
          .payload(System.currentTimeMillis())
          .get());
    }

    public void sendGoAway() {
      handler().onFrame(GoAwayFrame.make().status(Status.NO_ERROR).get());
    }
    
    public void sendWindowUpdate() {
      handler().onFrame(WindowUpdateFrame.make().value(1).get());
    }
    
    public void sendSettings() {
      handler().onFrame(
        SettingsFrame
          .make()
          .set(Settings.DOWNLOAD_BANDWIDTH, 1000)
          .set(Settings.MAX_CONCURRENT_STREAMS, 1000)
          .get());
    }
    
    @Override
    public void enqueue(Frame<?> frame) {
      System.out.println(frame); 
    }

    @Override
    public void requestGracefulShutdown(Status status) {
      System.out.println("shutting down...");
    }
  
  }

}
