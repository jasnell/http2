package snell.http2.headers.dhe;

public final class Dhe {

  public static enum Mode {
    REQUEST, 
    RESPONSE;
  }
  
  private final Storage table = 
    new Storage();
  private final Mode mode;
  private final DheHeaderSerializer ser;
  
  public Dhe(Mode mode) {
    ser = new DheHeaderSerializer(this);
    this.mode = mode;
  }
  
  public Mode mode() {
    return mode;
  }
  
  public Storage storage() {
    return table;
  }
  
  public DheHeaderSerializer ser() {
    return ser;
  }
}
