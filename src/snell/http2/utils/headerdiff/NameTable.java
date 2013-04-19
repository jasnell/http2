package snell.http2.utils.headerdiff;

import java.util.List;

import com.google.common.collect.Lists;

public final class NameTable {

  private final List<String> names = 
    Lists.newArrayListWithExpectedSize(255);
  
  public Name nameFor(String name) {
    if (!names.contains(name)) {
       names.add(name);
      return new Name(this,names.size()-1);
    } else {
      return new Name(this,names.indexOf(name));
    }
  }

  public Name nameFor(int idx) {
    if (idx < 0 || idx >= names.size())
      throw new IllegalArgumentException();
    return new Name(this,idx);
  }
  
  protected String value(int idx) {
    return names.get(idx);
  }

}
