package snell.http2.frames;

public enum Status {
  NO_ERROR,           // 0
  PROTOCOL_ERROR,     // 1
  INTERNAL_ERROR,     // 2
  FLOW_CONTROL_ERROR, // 3
  INVALID_STREAM,     // 4
  STREAM_CLOSED,      // 5
  FRAME_TOO_LARGE,    // 6
  REFUSED_STREAM,     // 7
  CANCEL              // 8
  ;
    
  public static Status get(int o) {
    return Status.values()[o];
  }
}