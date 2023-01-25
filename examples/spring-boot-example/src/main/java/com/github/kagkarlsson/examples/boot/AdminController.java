/**
 * Copyright (C) Gustav Karlsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.kagkarlsson.examples.boot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kagkarlsson.examples.boot.config.LongRunningJobConfiguration.PrimeGeneratorState;
import com.github.kagkarlsson.examples.boot.config.JobChainingConfiguration.JobState;
import com.github.kagkarlsson.examples.boot.config.MultiInstanceRecurringConfiguration;
import com.github.kagkarlsson.examples.boot.config.MultiInstanceRecurringConfiguration.Customer;
import com.github.kagkarlsson.examples.boot.config.MultiInstanceRecurringConfiguration.ScheduleAndCustomer;
import com.github.kagkarlsson.examples.boot.config.TaskNames;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.ScheduleAndData;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private static final Logger LOG = LoggerFactory.getLogger(AdminController.class);

    private static int ID = 1;
    private static int CHAINED_JOB_ID = 1;
    private final SchedulerClient schedulerClient;
    private final TransactionTemplate tx;

    public AdminController(SchedulerClient schedulerClient, TransactionTemplate tx) {
        this.schedulerClient = schedulerClient;
        this.tx = tx;
    }

    @GetMapping(path = "/tasks")
    public List<Scheduled> list() {
        return schedulerClient.getScheduledExecutions().stream()
            .map(e -> {
                return new Scheduled(
                    e.getTaskInstance().getTaskName(),
                    e.getTaskInstance().getId(),
                    e.getExecutionTime(),
                    e.getData());
            })
            .collect(Collectors.toList());
    }

    @PostMapping(path = "/triggerOneTime", headers = {"Content-type=application/json"})
    public void triggerOneTime(@RequestBody TriggerOneTimeRequest request) {
        LOG.info("Scheduling a basic one-time task to run 'Instant.now()+seconds'. If seconds=0, the scheduler will pick " +
            "these up immediately since it is configured with 'immediate-execution-enabled=true'"
        );

        schedulerClient.schedule(
            TaskNames.BASIC_ONE_TIME_TASK.instance(String.valueOf(ID++)),
            Instant.now().plusSeconds(request.seconds)
        );
    }


    @PostMapping(path = "/triggerTransactionallyStaged", headers = {"Content-type=application/json"})
    public void triggerTransactinallyStaged(@RequestBody TriggerOneTimeRequest request) {
        LOG.info("Scheduling a one-time task in a transaction. If the transaction rolls back, the insert of the task also " +
            "rolls back, i.e. it never runs."
        );

        tx.executeWithoutResult((TransactionStatus status) -> {
            schedulerClient.schedule(
                TaskNames.TRANSACTIONALLY_STAGED_TASK.instance(String.valueOf(ID++)),
                Instant.now().plusSeconds(request.seconds)
            );

            if (new Random().nextBoolean()) {
                throw new RuntimeException("Simulated failure happening after task was scheduled.");
            }

        });
    }

    @PostMapping(path = "/triggerChained", headers = {"Content-type=application/json"})
    public void triggerChained(@RequestBody TriggerOneTimeRequest request) {
        LOG.info("Scheduling a chained one-time task to run.");

        int id = CHAINED_JOB_ID++;
        schedulerClient.schedule(
            TaskNames.CHAINED_STEP_1_TASK.instance("chain-" + id, new JobState(id, 0)),
            Instant.now().plusSeconds(request.seconds)
        );
    }

    @PostMapping(path = "/triggerParallel", headers = {"Content-type=application/json"})
    public void triggerParallel(@RequestBody TriggerOneTimeRequest request) {
        LOG.info("Rescheduling task "+TaskNames.PARALLEL_JOB_SPAWNER.getTaskName()+" to run now. (deviating from defined schedule)");

        schedulerClient.reschedule(
            TaskNames.PARALLEL_JOB_SPAWNER.instanceId(RecurringTask.INSTANCE),
            Instant.now()
        );
    }

    @PostMapping(path = "/triggerLongRunning", headers = {"Content-type=application/json"})
    public void triggerLongRunning(@RequestBody TriggerOneTimeRequest request) {
        LOG.info("Starting long-running task "+TaskNames.LONG_RUNNING_TASK.getTaskName()+" to run 3s at a time until it " +
            "has found all prime-numbers smaller than 1.000.000.");

        PrimeGeneratorState initialState = new PrimeGeneratorState(0, 0);
        schedulerClient.schedule(
            TaskNames.LONG_RUNNING_TASK.instance("prime-generator", initialState),
            Instant.now()
        );
    }

    @PostMapping(path = "/triggerMultiInstance", headers = {"Content-type=application/json"})
    public void triggerMultiInstance(@RequestBody TriggerOneTimeRequest request) {
        CronSchedule cron = new CronSchedule(String.format("%s * * * * *", new Random().nextInt(59)));
        Customer customer = new Customer(String.valueOf(new Random().nextInt(10000)));
        ScheduleAndCustomer data = new ScheduleAndCustomer(cron, customer);

        LOG.info("Scheduling new recurring task "+TaskNames.MULTI_INSTANCE_RECURRING_TASK.getTaskName()+" with data: " + data);

        schedulerClient.schedule(
            TaskNames.MULTI_INSTANCE_RECURRING_TASK.instance(customer.id, data),
            cron.getInitialExecutionTime(Instant.now())
        );
    }

    @PostMapping(path = "/triggerStateTrackingRecurring", headers = {"Content-type=application/json"})
    public void triggerStateTrackingRecurring(@RequestBody TriggerOneTimeRequest request) {
        Integer data = 1;
        LOG.info("Starting recurring task "+TaskNames.STATE_TRACKING_RECURRING_TASK.getTaskName()+" with initial data: " + data);

        schedulerClient.schedule(
            TaskNames.STATE_TRACKING_RECURRING_TASK.instance(RecurringTask.INSTANCE, data),
            Instant.now() // start-time, will run according to schedule after this
        );
    }

    public static class TriggerOneTimeRequest {
        public final int seconds;

        public TriggerOneTimeRequest() {
            this(0);
        }

        public TriggerOneTimeRequest(int seconds) {
            this.seconds = seconds;
        }
    }

    public static class Scheduled {
        public final String taskName;
        public final String id;
        public final Instant executionTime;
        public final Object data;

        public Scheduled() {
            this(null, null, null, null);
        }

        public Scheduled(String taskName, String id, Instant executionTime, Object data) {
            this.taskName = taskName;
            this.id = id;
            this.executionTime = executionTime;
            this.data = data;
        }
    }

}