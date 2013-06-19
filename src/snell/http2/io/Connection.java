package snell.http2.io;

import static com.google.common.base.Preconditions.checkNotNull;
import static snell.http2.utils.MorePreconditions.checkState;
import static snell.http2.utils.MorePreconditions.checkInstanceOf;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

import snell.http2.frames.Frame;
import snell.http2.frames.GoAwayFrame;
import snell.http2.frames.PingFrame;
import snell.http2.frames.SettingsFrame;
import snell.http2.frames.Status;
import snell.http2.frames.WindowUpdateFrame;

/**
 * Encapsulates an http/2 connection. 
 * 
 * Upon creation, the Connection object is bound to a socket and immediately
 * sends a Session Header (client or server depending on startup)...
 */
public class Connection
  implements ReactorEventHandler {
  
  public static enum Role {
    CLIENT,
    SERVER
  }
  
  public static enum State {
    STARTING,          // The Connection is open but has not yet been fully initialized (no initial settings yet)
    RUNNING,           // The Connection is open and running normally
    STOPPING_OK,       // The Connection is stopping gracefully, no error
    STOPPING_NOT_OK,   // The Connection is stopping gracefully because of an error
    BORKED,            // The Connection is not yet closed but cannot be used due to an error
    STOPPED            // The Connection is no longer working
  }
  
  public static Builder makeClient() {
    return new Builder().role(Role.CLIENT);
  }
  
  public static Builder makeServer() {
    return new Builder().role(Role.SERVER);
  }
  
  public static class Builder 
    implements Supplier<Connection> {

    private Role role;
    private Reactor reactor;
    private StateHandler stateHandler;
    
    public Builder role(Role role) {
      this.role = role;
      return this;
    }
    
    public Builder reactor(Reactor reactor) {
      this.reactor = reactor;
      return this;
    }
    
    public Builder stateHandler(StateHandler handler) {
      this.stateHandler = handler;
      return this;
    }
    
    @Override
    public Connection get() {
      return new Connection(this);
    }
    
  }

  private final Role role;
  private final Reactor reactor;
  private final StateHandler stateHandler;
  private final ConnectionContext context;
  private State state;
  
  private Connection(Builder builder) {
    this.state = State.STARTING;
    this.role = builder.role;
    this.context = new ConnectionContext(this.role);
    this.stateHandler = builder.stateHandler;
    this.reactor = builder.reactor;
    this.reactor.bind(context, this);
  }
  
  public ConnectionContext context() {
    return context;
  }
  
  public State state() {
    return state;
  }
  
  public boolean isStarting() {
    return state == State.STARTING;
  }
  
  public boolean isRunning() {
    return state == State.RUNNING;
  }
  
  public boolean isStopping() {
    return state == State.STOPPING_NOT_OK || state == State.STOPPING_OK;
  }
  
  public boolean isStopped() {
    return state == State.STOPPED;
  }
  
  private void changeState(State to) {
    State current = this.state;
    this.state = to;
    if (stateHandler != null)
      stateHandler.onStateChange(this, current, to);
  }

  @Override
  public void onFrame(Frame<?> frame) {
    FrameHandler handler = handlers.get(state);
    if (handler != null) 
      handler.onFrame(frame);
    else
      throw new IllegalStateException(); // TODO: Proper handling
  }
  
  private void handleWindowUpdate(
    Frame<?> frame) {
      checkNotNull(frame);
      checkInstanceOf(frame,WindowUpdateFrame.class);
      WindowUpdateFrame windowUpdate = frame.cast();
      if (windowUpdate.endFlowControl()) 
        context.endFlowControl(windowUpdate.id());
      else
        context().updateWindow(
          windowUpdate.unsignedValue(), 
          windowUpdate.id());
  }
  
  private void handleSettings(Frame<?> frame) {
    checkNotNull(frame);
    checkInstanceOf(frame,SettingsFrame.class);
    SettingsFrame settings = frame.cast();
    context().updateSettings(settings);
  }
  
  private void setStateFromStatus(Status status) {
    switch(status) {
    case NO_ERROR:
      changeState(State.STOPPING_OK);
      break;
    default:
      changeState(State.STOPPING_NOT_OK); // there was an error...
      // TODO: Determine proper handling and reporting
      break;
    }
  }
  
  public void requestGracefulShutdown() {
    requestGracefulShutdown(Status.NO_ERROR,0);
  }
  
  public void requestGracefulShutdown(int lastStream) {
    requestGracefulShutdown(Status.NO_ERROR,lastStream);
  }
  
  public void requestGracefulShutdown(Status status) {
    requestGracefulShutdown(status,0);
  }
  
  public void requestGracefulShutdown(
    Status status, 
    int lastStream) {
    checkNotNull(status);
    checkState(
      isRunning(),
      isStarting());
    enqueue(
      GoAwayFrame.make()
        .status(status)
        .lastStream(lastStream)
        .get());
    setStateFromStatus(status);
    gracefulShutdown(status);
  }
  
  private void handleGracefulShutdown(Frame<?> frame) {
    checkNotNull(frame);
    checkInstanceOf(frame,GoAwayFrame.class);
    GoAwayFrame goaway = frame.cast();
    setStateFromStatus(goaway.status());
    gracefulShutdown(goaway.status());
  }
  
  private void gracefulShutdown(Status status) {
    // TODO: Any other shutdown processes
    reactor.requestGracefulShutdown(status);
  }
  
  private void handlePing(Frame<?> frame) {
    checkNotNull(frame);
    checkInstanceOf(frame,PingFrame.class);
    PingFrame ping = frame.cast();
    enqueue(ping.toPongFrame());
  }
  
  protected void enqueue(Frame<?> frame) {
    checkState(
      isRunning(), 
      isStarting()); 
    reactor.enqueue(frame);
  }

  @Override
  public void onException(Throwable t) {
    // there was an error in the reactor
  }

  @Override
  public void onShutdown(Status status) {
    // the reactor has shut down... the socket is no longer available,
    // just clean up and call it a day...
    checkNotNull(status);
    state = State.STOPPED;
    // TODO: Other cleanup processes
  }

  @Override
  public void onTimeout() {
    // The reactor experienced a timeout...
  }
  
  
  private static interface FrameHandler {
    void onFrame(Frame<?> frame);
  }
  
  private final ImmutableMap<State,FrameHandler> handlers = 
    ImmutableMap
      .<State,FrameHandler>builder()
      .put(
        State.STARTING, 
        new FrameHandler() {
          @Override
          public void onFrame(Frame<?> frame) {
            switch(frame.type()) {
            case SettingsFrame.TYPE:
              handleSettings(frame);
              changeState(State.RUNNING);
              break;
            default:
              throw new IllegalStateException();
            }
          }
        }
      )
      .put(
        State.RUNNING,
        new FrameHandler() {
          @Override
          public void onFrame(Frame<?> frame) {
            switch(frame.type()) {
              case PingFrame.TYPE:
                handlePing(frame);
                break;
              case GoAwayFrame.TYPE:
                handleGracefulShutdown(frame);
                break;
              case SettingsFrame.TYPE:
                handleSettings(frame);
                break;
              case WindowUpdateFrame.TYPE:
                handleWindowUpdate(frame);
                break;
              default:
            }
          }
        }
      )
      .build();
}
