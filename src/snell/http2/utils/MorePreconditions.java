package snell.http2.utils;

import com.google.common.base.Preconditions;

public final class MorePreconditions {

  private MorePreconditions() {}
  
  public static void checkState(boolean... conditions) {
    for (boolean condition : conditions)
      Preconditions.checkState(condition);
  }
  
  public static void checkInstanceOf(Object obj, Class<?> _class) {
    if (!_class.isInstance(obj))
      throw new IllegalArgumentException();
  }
}
