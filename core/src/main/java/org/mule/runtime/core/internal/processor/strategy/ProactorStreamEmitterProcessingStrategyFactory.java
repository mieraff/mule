/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.processor.strategy;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.max;
import static org.mule.runtime.core.api.config.MuleProperties.MULE_ORCHESTRATOR_MANAGER;
import static org.mule.runtime.core.api.processor.ReactiveProcessor.ProcessingType.BLOCKING;
import static org.mule.runtime.core.api.processor.ReactiveProcessor.ProcessingType.CPU_INTENSIVE;
import static org.mule.runtime.core.api.processor.ReactiveProcessor.ProcessingType.CPU_LITE;
import static org.mule.runtime.core.api.processor.ReactiveProcessor.ProcessingType.CPU_LITE_ASYNC;
import static org.mule.runtime.core.api.processor.ReactiveProcessor.ProcessingType.IO_RW;
import static org.mule.runtime.core.internal.processor.strategy.ComponentMessageProcessorChainBuilder.buildProcessorChainFrom;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.scheduler.Scheduler;
import org.mule.runtime.api.scheduler.SchedulerService;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.processor.ReactiveProcessor;
import org.mule.runtime.core.api.processor.strategy.AsyncProcessingStrategyFactory;
import org.mule.runtime.core.api.processor.strategy.ProcessingStrategy;
import org.mule.runtime.core.internal.context.MuleContextWithRegistry;
import org.mule.runtime.core.internal.orchestration.MuleOrchestratorManager;
import org.mule.runtime.core.internal.processor.strategy.StreamEmitterProcessingStrategyFactory.StreamEmitterProcessingStrategy;
import org.mule.runtime.core.internal.util.rx.ImmediateScheduler;
import org.mule.runtime.core.internal.util.rx.RetrySchedulerWrapper;
import org.slf4j.Logger;

/**
 * Creates {@link AsyncProcessingStrategyFactory} instance that implements the proactor pattern by de-multiplexing incoming events
 * onto a multiple emitter using the {@link SchedulerService#cpuLightScheduler()} to process these events from each emitter. In
 * contrast to the {@link AbstractStreamProcessingStrategyFactory} the proactor pattern treats
 * {@link ReactiveProcessor.ProcessingType#CPU_INTENSIVE} and {@link ReactiveProcessor.ProcessingType#BLOCKING} processors
 * differently and schedules there execution on dedicated {@link SchedulerService#cpuIntensiveScheduler()} and
 * {@link SchedulerService#ioScheduler()} ()} schedulers.
 * <p/>
 * This processing strategy is not suitable for transactional flows and will fail if used with an active transaction.
 *
 * @since 4.2.0
 */
public class ProactorStreamEmitterProcessingStrategyFactory extends AbstractStreamProcessingStrategyFactory {

  @Override
  public ProcessingStrategy create(MuleContext muleContext, String schedulersNamePrefix) {
    Supplier<Scheduler> cpuLightSchedulerSupplier = getCpuLightSchedulerSupplier(muleContext, schedulersNamePrefix);
    MuleOrchestratorManager orchestratorManager =
        ((MuleContextWithRegistry) muleContext).getRegistry().get(MULE_ORCHESTRATOR_MANAGER);

    return new ProactorStreamEmitterProcessingStrategy(getBufferSize(),
                                                       getSubscriberCount(),
                                                       cpuLightSchedulerSupplier,
                                                       cpuLightSchedulerSupplier,
                                                       () -> muleContext.getSchedulerService()
                                                           .ioScheduler(muleContext.getSchedulerBaseConfig()
                                                               .withName(
                                                                         schedulersNamePrefix + "." + BLOCKING.name())),
                                                       () -> muleContext.getSchedulerService()
                                                           .cpuIntensiveScheduler(muleContext.getSchedulerBaseConfig()
                                                               .withName(schedulersNamePrefix + "."
                                                                   + CPU_INTENSIVE.name())),
                                                       resolveParallelism(),
                                                       getMaxConcurrency(),
                                                       isMaxConcurrencyEagerCheck(),
                                                       () -> muleContext.getConfiguration().getShutdownTimeout(),
                                                       orchestratorManager);
  }

  @Override
  public Class<? extends ProcessingStrategy> getProcessingStrategyType() {
    return ProactorStreamEmitterProcessingStrategy.class;
  }

  static class ProactorStreamEmitterProcessingStrategy extends StreamEmitterProcessingStrategy {

    private static final Logger LOGGER = getLogger(ProactorStreamEmitterProcessingStrategy.class);

    private final Supplier<Scheduler> blockingSchedulerSupplier;
    private final Supplier<Scheduler> cpuIntensiveSchedulerSupplier;
    private Scheduler blockingScheduler;
    private Scheduler cpuIntensiveScheduler;

    private MuleOrchestratorManager orchestratorManager;



    public ProactorStreamEmitterProcessingStrategy(int bufferSize,
                                                   int subscriberCount,
                                                   Supplier<Scheduler> flowDispatchSchedulerSupplier,
                                                   Supplier<Scheduler> cpuLightSchedulerSupplier,
                                                   Supplier<Scheduler> blockingSchedulerSupplier,
                                                   Supplier<Scheduler> cpuIntensiveSchedulerSupplier,
                                                   int parallelism,
                                                   int maxConcurrency,
                                                   boolean maxConcurrencyEagerCheck,
                                                   Supplier<Long> shutdownTimeoutSupplier,
                                                   MuleOrchestratorManager orchestratorManager) {
      super(bufferSize, subscriberCount, flowDispatchSchedulerSupplier, cpuLightSchedulerSupplier, parallelism, maxConcurrency,
            maxConcurrencyEagerCheck, shutdownTimeoutSupplier);
      this.blockingSchedulerSupplier = blockingSchedulerSupplier;
      this.cpuIntensiveSchedulerSupplier = cpuIntensiveSchedulerSupplier;
      this.orchestratorManager = orchestratorManager;
    }

    @Override
    public void start() throws MuleException {
      super.start();
      this.blockingScheduler = blockingSchedulerSupplier.get();
      this.cpuIntensiveScheduler = cpuIntensiveSchedulerSupplier.get();
    }

    @Override
    protected int getSinksCount() {
      return maxConcurrency < CORES ? maxConcurrency : CORES;
    }

    @Override
    protected Scheduler createCpuLightScheduler(Supplier<Scheduler> cpuLightSchedulerSupplier) {
      return new RetrySchedulerWrapper(super.createCpuLightScheduler(cpuLightSchedulerSupplier),
                                       SCHEDULER_BUSY_RETRY_INTERVAL_MS);
    }

    @Override
    protected boolean stopSchedulersIfNeeded() {
      if (super.stopSchedulersIfNeeded()) {

        if (blockingScheduler != null) {
          blockingScheduler.stop();
          blockingScheduler = null;
        }
        if (cpuIntensiveScheduler != null) {
          cpuIntensiveScheduler.stop();
          cpuIntensiveScheduler = null;
        }

        return true;
      }

      return false;
    }

    @Override
    public ReactiveProcessor onProcessor(ReactiveProcessor processor) {
      return publisher -> buildProcessorChainFrom(processor, publisher)
          .withExecutionOrchestrator(orchestratorManager.getOrchestrator(processor, getDispatcherScheduler(processor),
                                                                         getCallbackScheduler(processor),
                                                                         getContextProcessorScheduler(processor)))
          .withParallelism(getChainParallelism(processor))
          .build();
    }

    @Override
    protected ScheduledExecutorService getCallbackScheduler(ReactiveProcessor processor) {
      if (processor.getProcessingType() != CPU_LITE_ASYNC) {
        return ImmediateScheduler.IMMEDIATE_SCHEDULER;
      }

      return super.getCallbackScheduler(processor);
    }

    @Override
    protected ScheduledExecutorService getDispatcherScheduler(ReactiveProcessor processor) {
      if (processor.getProcessingType() != CPU_LITE_ASYNC) {
        return decorateScheduler(getRetryScheduler(getContextProcessorScheduler(processor)));
      }

      return super.getDispatcherScheduler(processor);
    }

    @Override
    protected Scheduler getContextProcessorScheduler(ReactiveProcessor processor) {
      if (processor.getProcessingType() == BLOCKING || processor.getProcessingType() == IO_RW) {
        return blockingScheduler;
      } else if (processor.getProcessingType() == CPU_INTENSIVE) {
        return cpuIntensiveScheduler;
      } else {
        return super.getContextProcessorScheduler(processor);
      }
    }

    @Override
    protected int getChainParallelism(ReactiveProcessor processor) {
      // FlatMap is the way reactor has to do parallel processing. Since this proactor method is used for the processors that are
      // not CPU_LITE, parallelism is wanted when the processor is blocked to do IO or doing long CPU work.
      if (processor.getProcessingType() == CPU_LITE) {
        return super.getChainParallelism(processor);
      }

      if (maxConcurrency == 1) {
        return 1;

      } else if (maxConcurrency == MAX_VALUE) {
        if (processor instanceof ComponentInnerProcessor && !((ComponentInnerProcessor) processor).isBlocking()) {
          // For a no concurrency limit non blocking processor, the java SDK already handles parallelism internally, so no need to
          // do that here.
          return 1;
        } else {
          // For no limit, pass through the no limit meaning to Reactor's flatMap
          return MAX_VALUE;
        }
      } else {
        // Otherwise, enforce the concurrency limit from the config,
        return max(maxConcurrency / (getParallelism() * subscribers), 1);
      }
    }

    @Override
    protected Scheduler getFlowDispatcherScheduler() {
      return getCpuLightScheduler();
    }
  }

}
