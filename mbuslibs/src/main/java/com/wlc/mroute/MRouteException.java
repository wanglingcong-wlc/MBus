package com.wlc.mroute;


public class MRouteException extends RuntimeException{
  private static final long serialVersionUID = -2912559384646531475L;

  public MRouteException(String detailMessage) {
    super(detailMessage);
  }

  public MRouteException(Throwable throwable) {
    super(throwable);
  }

  public MRouteException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }
}
