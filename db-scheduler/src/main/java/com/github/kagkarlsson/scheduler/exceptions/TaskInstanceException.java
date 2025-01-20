/*
 * Copyright (C) Gustav Karlsson
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.kagkarlsson.scheduler.exceptions;

public class TaskInstanceException extends DbSchedulerException {
  private static final long serialVersionUID = -2132850112553296791L;
  private static final String TASK_NAME_INSTANCE_MESSAGE_PART = " (task name: %s, instance id: %s)";

  private final String taskName;
  private final String instanceId;

  public TaskInstanceException(String message, String taskName, String instanceId, Throwable ex) {
    super(message + String.format(TASK_NAME_INSTANCE_MESSAGE_PART, taskName, instanceId), ex);
    this.taskName = taskName;
    this.instanceId = instanceId;
  }

  public TaskInstanceException(String message, String taskName, String instanceId) {
    super(message + String.format(TASK_NAME_INSTANCE_MESSAGE_PART, taskName, instanceId));
    this.taskName = taskName;
    this.instanceId = instanceId;
  }

  public String getTaskName() {
    return taskName;
  }

  public String getInstanceId() {
    return instanceId;
  }
}
