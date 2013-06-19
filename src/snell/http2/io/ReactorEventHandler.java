package snell.http2.io;

import snell.http2.frames.Frame;
import snell.http2.frames.Status;

public interface ReactorEventHandler {

  void onException(Throwable t);
  
  void onShutdown(Status status);
  
  void onTimeout();
  
  void onFrame(Frame<?> frame);
  
}
