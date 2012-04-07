package com.marklogic.developer;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

class BareTextFormatter extends Formatter
{
  public String format(LogRecord rec)
  {
    return formatMessage(rec) + "\n";
  }
}
