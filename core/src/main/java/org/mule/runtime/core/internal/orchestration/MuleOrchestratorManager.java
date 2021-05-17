/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.orchestration;

import java.util.concurrent.ScheduledExecutorService;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.runtime.core.internal.processor.strategy.ExecutionOrchestrator;

public interface MuleOrchestratorManager {

  ExecutionOrchestrator getOrchestrator(ReactiveProcessor processor,
                                        ScheduledExecutorService dispatcherScheduler,
                                        ScheduledExecutorService callbackScheduler,
                                        Scheduler contextProcessorScheduler);
}
