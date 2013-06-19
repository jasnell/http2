package snell.http2.frames;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public final class PingFrame 
  extends Frame<PingFrame> {
  
  public static final byte TYPE = 0x6;
  
  private static final byte FLAG_PONG = 0x2;
  
  public static PingFrameBuilder make() {
    return new PingFrameBuilder();
  }
  
  public static class PingFrameBuilder 
    extends FrameBuilder<PingFrame,PingFrameBuilder> {
    private byte[] payload = null;
    protected PingFrameBuilder() {
      super(TYPE);
    }
    
    @Override
    public PingFrameBuilder streamId(int id) {
      checkArgument(id == 0);
      return super.streamId(id);
    }

    @Override
    public PingFrameBuilder streamId(Supplier<Integer> s) {
      return super.streamId(s);
    }

    public PingFrameBuilder pong() {
      this.flag(FLAG_PONG, true);
      return this;
    }
    public PingFrameBuilder pong(boolean  on) {
      this.flag(FLAG_PONG, on);
      return this;
    }
    public PingFrameBuilder payload(long payload) {
      this.payload = Longs.toByteArray(payload);
      return this;
    }
    public PingFrameBuilder payload(int payload) {
      this.payload = Ints.toByteArray(payload);
      return this;
    }
    public PingFrameBuilder payload(byte[] payload) {
      checkNotNull(payload);
      checkArgument(payload.length <= 8);
      this.payload = payload;
      return this;
    }
    @Override
    public PingFrame get() {
      if (payload != null) {
        try {
          checkState(payload.length <= 8);
          this.put(payload);
        } catch (Throwable t) {
          throw Throwables.propagate(t);
        }
      }
      return new PingFrame(this);
    }
  }
  
  protected PingFrame(
    PingFrameBuilder builder) {
      super(builder);
  }
  
  public boolean pong() {
    return this.flag(FLAG_PONG);
  }

  public InputStream readPayload() throws IOException {
    return new ByteArrayInputStream(super.buffer);
  }
  
  public PingFrame toPongFrame() {
    checkState(!pong());
    return 
      make()
        .pong()
        .streamId(0)
        .payload(buffer)
        .get();
  }
  
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(pong()?"PONG":"PING");
    builder.append('[');
    for (byte b : buffer) 
      builder.append(Integer.toHexString(b & 0xFF));
    builder.append(']');
    return builder.toString();
  }
}
