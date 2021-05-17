/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.processor.strategy;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.ScheduledExecutorService;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.runtime.core.internal.util.rx.ImmediateScheduler;
import org.slf4j.Logger;


public class DefaultExecutionOrchestrator implements ExecutionOrchestrator {

  private static final Logger LOGGER = getLogger(DefaultExecutionOrchestrator.class);

  private ReactiveProcessor processor;

  private OrchestratedScheduledExecutorService dispatcherScheduler;
  private OrchestratedScheduledExecutorService callbackScheduler;
  private OrchestratedScheduler contextProcessorScheduler;

  private String location = "";

  public DefaultExecutionOrchestrator(ReactiveProcessor processor, ScheduledExecutorService dispatcherScheduler,
                                      ScheduledExecutorService callbackScheduler,
                                      Scheduler contextProcessorScheduler) {
    this.processor = processor;
    this.dispatcherScheduler = new OrchestratedScheduledExecutorService(dispatcherScheduler);
    this.callbackScheduler = new OrchestratedScheduledExecutorService(callbackScheduler);
    this.contextProcessorScheduler = new OrchestratedScheduler(contextProcessorScheduler);
    location = processor.toString();
  }

  @Override
  public void traceAfter(CoreEvent event) {
    if (dispatcherScheduler.getDelegate() instanceof ImmediateScheduler) {
      return;
    }
    LOGGER.warn("After dispatching {} on {}", event.getCorrelationId(), location);

  }

  @Override
  public void traceComponentProccessed(CoreEvent event) {
    LOGGER.warn("After processing {} on {}", event.getCorrelationId(), location);
  }

  @Override
  public void traceBefore(CoreEvent event) {
    if (dispatcherScheduler.getDelegate() instanceof ImmediateScheduler) {
      return;
    }
    LOGGER.warn("Before dispaching {} on {}", event.getCorrelationId(), location);
  }

  @Override
  public ScheduledExecutorService getDispatcherScheduler() {
    return dispatcherScheduler;
  }

  @Override
  public ScheduledExecutorService getCallbackScheduler() {
    return callbackScheduler;
  }

  @Override
  public ScheduledExecutorService getContextProcessorScheduler() {
    return contextProcessorScheduler;
  }

  public void changeScheduler(Scheduler newScheduler) {
    contextProcessorScheduler.change(newScheduler);
    this.dispatcherScheduler.change(newScheduler);

  }


}
