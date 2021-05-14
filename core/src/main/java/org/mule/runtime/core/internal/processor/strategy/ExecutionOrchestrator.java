/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.processor.strategy;

import java.util.concurrent.ScheduledExecutorService;

import org.mule.runtime.core.api.event.CoreEvent;

import reactor.core.scheduler.Scheduler;
import reactor.util.context.Context;

public interface ExecutionOrchestrator {

  void traceBefore(CoreEvent event);

  void traceComponentProccessed(CoreEvent event);

  void traceAfter(CoreEvent event);

  ScheduledExecutorService getDispatcherScheduler();

  ScheduledExecutorService getCallbackScheduler();

  ScheduledExecutorService getContextProcessorScheduler();
}
