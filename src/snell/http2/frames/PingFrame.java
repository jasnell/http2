package snell.http2.frames;

public final class PingFrame 
  extends Frame<PingFrame> {
  
  static final byte TYPE = 0x6;
  
  private static final byte FLAG_PONG = 0x2;
  
  public static PingFrameBuilder make() {
    return new PingFrameBuilder();
  }
  
  public static class PingFrameBuilder 
    extends FrameBuilder<PingFrame,PingFrameBuilder> {
    protected PingFrameBuilder() {
      super(TYPE);
    }
    public PingFrameBuilder pong() {
      this.flag(FLAG_PONG, true);
      return this;
    }
    public PingFrameBuilder ping(boolean  on) {
      this.flag(FLAG_PONG, on);
      return this;
    }
    @Override
    public PingFrame get() {
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

}
