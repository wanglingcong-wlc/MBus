package com.wlc.mbuslibs;

public class MBusException extends RuntimeException{
  private static final long serialVersionUID = -2912559384646531479L;

  public MBusException(String detailMessage) {
    super(detailMessage);
  }

  public MBusException(Throwable throwable) {
    super(throwable);
  }

  public MBusException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }
}
