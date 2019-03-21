package com.sematext.logseneandroid;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Log handler which sends messages to Logsene.
 */
public class LogseneHandler extends Handler {
  private final Logsene logsene;

  public LogseneHandler(Logsene logsene) {
    this.logsene = logsene;
    setFormatter(new SimpleFormatter());
  }

  @Override
  public void publish(LogRecord record) {
    // first check if this record should be logged (log level and filters are checked)
    if (!isLoggable(record)) {
      return;
    }
    JSONObject obj = new JSONObject();
    try {
      obj.put("@timestamp", Utils.iso8601(record.getMillis()));
      obj.put("level", record.getLevel().toString());
      obj.put("message", getFormatter().formatMessage(record));
      obj.put("logger", record.getLoggerName());
      obj.put("seqNum", record.getSequenceNumber());
      obj.put("threadId", record.getThreadID());
      obj.put("sourceClass", record.getSourceClassName());
      obj.put("sourceMethod", record.getSourceMethodName());
      if (record.getThrown() != null) {
        obj.put("stacktrace", Utils.getStackTrace(record.getThrown()));
      }
      logsene.event(obj);
    } catch (JSONException e) {
      // should never happen, as exception is thrown when key in put() is null
      reportError("Unable to construct json object", e, ErrorManager.GENERIC_FAILURE);
    }
  }

  @Override
  public void flush() {
    // nop, messages will be sent by the log worker eventually
  }

  @Override
  public void close() throws SecurityException {
    // nop, nothing to close
  }
}
