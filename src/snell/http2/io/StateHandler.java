package snell.http2.io;

import snell.http2.io.Connection.State;

public interface StateHandler {

  void onStateChange(
    Connection connection, 
    State from, 
    State to);
  
}
