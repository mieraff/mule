/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.processor.strategy;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.ScheduledExecutorService;

import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.slf4j.Logger;

import reactor.core.scheduler.Scheduler;


public class DefaultExecutionOrchestrator implements ExecutionOrchestrator {

  private static final Logger LOGGER = getLogger(DefaultExecutionOrchestrator.class);

  private ReactiveProcessor processor;

  private ScheduledExecutorService dispatcherScheduler;
  private ScheduledExecutorService callbackScheduler;
  private ScheduledExecutorService contextProcessorScheduler;

  private String location = "";

  public DefaultExecutionOrchestrator(ReactiveProcessor processor, ScheduledExecutorService dispatcherScheduler,
                                      ScheduledExecutorService callbackScheduler,
                                      ScheduledExecutorService contextProcessorScheduler) {
    this.processor = processor;
    this.dispatcherScheduler = dispatcherScheduler;
    this.callbackScheduler = callbackScheduler;
    this.contextProcessorScheduler = contextProcessorScheduler;
    location = processor.toString();
  }

  @Override
  public void traceAfter(CoreEvent event) {
    LOGGER.warn("After dispatching {} on {}", event.getCorrelationId(), location);

  }

  @Override
  public void traceComponentProccessed(CoreEvent event) {
    LOGGER.warn("After processing {} on {}", event.getCorrelationId(), location);
  }

  @Override
  public void traceBefore(CoreEvent event) {
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


}
