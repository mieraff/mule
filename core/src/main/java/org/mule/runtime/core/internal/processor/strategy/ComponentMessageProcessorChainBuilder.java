/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.processor.strategy;

import static org.mule.runtime.core.internal.util.rx.ImmediateScheduler.IMMEDIATE_SCHEDULER;
import static reactor.core.scheduler.Schedulers.fromExecutorService;

import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.core.api.event.CoreEvent;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ComponentMessageProcessorChainBuilder {

  private static final Scheduler defaultScheduler = IMMEDIATE_SCHEDULER;
  private ReactiveProcessor processor;
  private Publisher<CoreEvent> publisher;
  private int parallelism = 1;
  private ExecutionOrchestrator executionOrchestrator;

  public static ComponentMessageProcessorChainBuilder buildProcessorChainFrom(ReactiveProcessor processor,
                                                                              Publisher<CoreEvent> publisher) {
    return new ComponentMessageProcessorChainBuilder(processor, publisher);
  }

  public ComponentMessageProcessorChainBuilder(ReactiveProcessor processor,
                                               Publisher<CoreEvent> publisher) {
    this.processor = processor;
    this.publisher = publisher;
  }


  public ComponentMessageProcessorChainBuilder withParallelism(int parallelism) {
    this.parallelism = parallelism;
    return this;
  }

  public Mono<CoreEvent> doBuildFromMono() {
    return Mono.from(publisher)
        .doOnNext(e -> executionOrchestrator.traceBefore(e))
        .publishOn(fromExecutorService(executionOrchestrator.getDispatcherScheduler()))
        .transform(processor)
        .doOnNext(e -> executionOrchestrator.traceComponentProccessed(e))
        .publishOn(fromExecutorService(executionOrchestrator.getCallbackScheduler()))
        .doOnNext(e -> executionOrchestrator.traceAfter(e))
        .subscriberContext(ctx -> ctx.put(AbstractProcessingStrategy.PROCESSOR_SCHEDULER_CONTEXT_KEY,
                                          executionOrchestrator.getContextProcessorScheduler()));

  }


  public Flux<CoreEvent> doBuildFromFlux() {
    return Flux.from(publisher)
        .doOnNext(e -> executionOrchestrator.traceBefore(e))
        .publishOn(fromExecutorService(executionOrchestrator.getDispatcherScheduler()))
        .transform(processor)
        .doOnNext(e -> executionOrchestrator.traceComponentProccessed(e))
        .publishOn(fromExecutorService(executionOrchestrator.getCallbackScheduler()))
        .doOnNext(e -> executionOrchestrator.traceAfter(e))
        .subscriberContext(ctx -> ctx.put(AbstractProcessingStrategy.PROCESSOR_SCHEDULER_CONTEXT_KEY,
                                          executionOrchestrator.getContextProcessorScheduler()));
  }

  public Publisher<CoreEvent> build() {
    if (parallelism == 1) {
      return doBuildFromFlux();
    } else {
      return Flux.from(publisher)
          .flatMap(event -> doBuildFromMono(),
                   parallelism);
    }
  }

  public ComponentMessageProcessorChainBuilder withExecutionOrchestrator(ExecutionOrchestrator executionOrchestrator) {
    this.executionOrchestrator = executionOrchestrator;
    return this;
  }



}
