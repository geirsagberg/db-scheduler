package com.github.kagkarlsson.scheduler.exceptions;

public class TaskBatchException extends DbSchedulerException {
  private static final long serialVersionUID = -2132850112553296792L;

  public TaskBatchException(String message) {
    super(message);
  }

  public TaskBatchException(String message, Throwable cause) {
    super(message, cause);
  }
}
