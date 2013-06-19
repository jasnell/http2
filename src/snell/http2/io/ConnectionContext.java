package snell.http2.io;

import snell.http2.frames.SettingsFrame;
import snell.http2.frames.SettingsFrame.Settings;
import snell.http2.io.Connection.Role;
import snell.http2.utils.IntMap;

import com.google.common.primitives.UnsignedInteger;

/**
 * The Connection Context maintains the current details
 * about the Connection Settings, Flow Control and 
 * operational parameters... The ConnectionContext 
 * object instance itself is owned by the Connection
 * object and shared with the Reactor and any other
 * component that requires access to the Settings
 * and Flow Control mechanisms.
 */
public final class ConnectionContext {

  private final Connection.Role role;
  private final IntMap WINDOW_SIZE = new IntMap();
  private final IntMap SETTINGS = new IntMap();
  
  public ConnectionContext(Role role) {
    this.role = role;
  }
  
  public Role role() {
    return role;
  }
  
  public SettingsFrame sessionHeader() {
    SettingsFrame.SettingsFrameBuilder sf = null;
    switch(role) {
    case CLIENT:
      sf = SettingsFrame.make(true);
    case SERVER:
      sf = SettingsFrame.make(false);
    }
    // set the initial settings frames
    return sf.get();
  }
  
  public void updateWindow(int size) {
    updateWindow(UnsignedInteger.fromIntBits(size));
  }
  
  public void updateWindow(int size, int stream) {
    updateWindow(UnsignedInteger.fromIntBits(size),stream);
  }
  
  public void updateWindow(long size) {
    updateWindow(UnsignedInteger.valueOf(size));
  }
  
  public void updateWindow(long size, int stream) {
    updateWindow(UnsignedInteger.valueOf(size),stream);
  }  
  
  public void updateWindow(UnsignedInteger size) {
    updateWindow(size,0);
  }
  
  public void updateWindow(UnsignedInteger size, int stream) {
    WINDOW_SIZE.put(size.intValue(),stream);
  }
  
  public void updateSettings(Iterable<SettingsFrame.Entry> settings) {
    for (SettingsFrame.Entry entry : settings) {
      Settings setting = entry.setting();
      SETTINGS.put(setting.id(), entry.value());
    }
  }
  
  public int get(SettingsFrame.Settings setting) {
    return SETTINGS.get(setting.id(),-1);
  }
  
  public UnsignedInteger getWindow() {
    return getWindow(0);
  }
  
  public UnsignedInteger getWindow(int stream) {
    return UnsignedInteger.fromIntBits(WINDOW_SIZE.get(stream,0));
  }
  
  public void endFlowControl() {
    endFlowControl(0);
  }
  
  public void endFlowControl(int stream) {
    WINDOW_SIZE.put(stream,-1);
  }
  
  
}
