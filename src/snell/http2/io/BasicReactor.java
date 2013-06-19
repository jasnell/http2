package snell.http2.io;

import snell.http2.frames.Frame;
import snell.http2.frames.Status;

public class BasicReactor implements Reactor {

  private ReactorEventHandler handler;
  private ConnectionContext context;
  
  protected ReactorEventHandler handler() {
    return handler;
  }
  
  protected ConnectionContext context() {
    return context;
  }
  
  @Override
  public void requestGracefulShutdown(Status status) {
    handler.onShutdown(status);
  }

  @Override
  public void enqueue(Frame<?> frame) {
    // send the frame intelligently
  }

  @Override
  public void bind(
    ConnectionContext context, 
    ReactorEventHandler handler) {
      this.context = context;
      this.handler = handler;
      enqueue(context.sessionHeader());
  }

}
