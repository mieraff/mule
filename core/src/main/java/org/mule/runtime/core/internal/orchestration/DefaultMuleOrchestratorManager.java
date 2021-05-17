/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.orchestration;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.mule.runtime.api.lifecycle.Initialisable;
import org.mule.runtime.api.lifecycle.InitialisationException;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerConfig;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.runtime.core.internal.processor.strategy.ComponentInnerProcessor;
import org.mule.runtime.core.internal.processor.strategy.DefaultExecutionOrchestrator;
import org.mule.runtime.core.internal.processor.strategy.ExecutionOrchestrator;

public class DefaultMuleOrchestratorManager implements Initialisable, MuleOrchestratorManager {

  Map<String, DefaultExecutionOrchestrator> orchestrators = new HashMap<String, DefaultExecutionOrchestrator>();

  @Inject
  private SchedulerService schedulerService;

  @Inject
  private SchedulerConfig schedulerConfig;

  private AtomicInteger counter = new AtomicInteger(0);

  @Override
  public void initialise() throws InitialisationException {
    ObjectName beanName = null;
    try {
      beanName = new ObjectName("org.mule.orchestrator:type=basic,name=orchestrator");
      MBeanServer server = ManagementFactory.getPlatformMBeanServer();
      server.registerMBean(new OrchestratorBean(this), beanName);
    } catch (Exception e) {

    }
  }

  @Override
  public ExecutionOrchestrator getOrchestrator(ReactiveProcessor processor, ScheduledExecutorService dispatcherScheduler,
                                               ScheduledExecutorService callbackScheduler,
                                               Scheduler contextProcessorScheduler) {
    DefaultExecutionOrchestrator orchestrator = new DefaultExecutionOrchestrator(processor, dispatcherScheduler,
                                                                                 callbackScheduler,
                                                                                 contextProcessorScheduler);
    orchestrators.put(processor.toString(), orchestrator);

    return orchestrator;
  }

  public void ownSchedulers(String processor) {
    Scheduler newScheduler = schedulerService.customScheduler(schedulerConfig
        .withName("MAXWELL_DAEMON" + counter.getAndIncrement()).withMaxConcurrentTasks(5));

    if (orchestrators.containsKey(processor)) {
      orchestrators.get(processor).changeScheduler(newScheduler);
    }

  }

}
