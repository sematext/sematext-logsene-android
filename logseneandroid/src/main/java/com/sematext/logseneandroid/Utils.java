package com.sematext.logseneandroid;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public enum Utils {
  INSTANCE, Utils;

  private static final SimpleDateFormat ISO8601_FORMAT;

  static {
    TimeZone tz = TimeZone.getTimeZone("UTC");
    ISO8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SZ");
    ISO8601_FORMAT.setTimeZone(tz);
  }

  public static final Locale DEFAULT_LOCALE = new Locale("en", "US");

  public static String iso8601() {
    return ISO8601_FORMAT.format(new Date());
  }

  public static String iso8601(long millis) {
    return ISO8601_FORMAT.format(new Date(millis));
  }

  public static String getStackTrace(Throwable throwable) {
    assert throwable != null;
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);
    throwable.printStackTrace(pw);
    return sw.getBuffer().toString();
  }

  public static void requireNonNull(Object o) {
    if (o == null) {
      throw new NullPointerException();
    }
  }
}
