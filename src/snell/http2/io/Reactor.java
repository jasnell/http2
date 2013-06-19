package snell.http2.io;

import snell.http2.frames.Frame;
import snell.http2.frames.Status;

public interface Reactor {

  void bind(ConnectionContext context, ReactorEventHandler handler);
  
  void requestGracefulShutdown(Status status);
 
  void enqueue(Frame<?> frame);
  
}
