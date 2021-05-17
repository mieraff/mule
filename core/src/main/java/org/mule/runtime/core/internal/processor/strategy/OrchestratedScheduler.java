/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.processor.strategy;

import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.mule.runtime.api.scheduler.Scheduler;


public class OrchestratedScheduler implements Scheduler {

  private Scheduler delegate;


  public ScheduledFuture<?> scheduleWithCronExpression(Runnable command, String cronExpression) {
    return delegate.scheduleWithCronExpression(command, cronExpression);
  }


  public ScheduledFuture<?> scheduleWithCronExpression(Runnable command, String cronExpression, TimeZone timeZone) {
    return delegate.scheduleWithCronExpression(command, cronExpression, timeZone);
  }


  public void stop() {
    delegate.stop();
  }


  public String getName() {
    return delegate.getName();
  }


  public String getThreadNameSuffix() {
    return delegate.getThreadNameSuffix();
  }


  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return delegate.schedule(command, delay, unit);
  }


  public void execute(Runnable command) {
    delegate.execute(command);
  }


  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return delegate.schedule(callable, delay, unit);
  }


  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    return delegate.scheduleAtFixedRate(command, initialDelay, period, unit);
  }


  public void shutdown() {
    delegate.shutdown();
  }


  public List<Runnable> shutdownNow() {
    return delegate.shutdownNow();
  }


  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return delegate.scheduleWithFixedDelay(command, initialDelay, delay, unit);
  }


  public boolean isShutdown() {
    return delegate.isShutdown();
  }


  public boolean isTerminated() {
    return delegate.isTerminated();
  }


  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }


  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(task);
  }


  public <T> Future<T> submit(Runnable task, T result) {
    return delegate.submit(task, result);
  }


  public Future<?> submit(Runnable task) {
    return delegate.submit(task);
  }


  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return delegate.invokeAll(tasks);
  }


  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
  }


  public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return delegate.invokeAny(tasks);
  }


  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }


  public OrchestratedScheduler(Scheduler delegate) {
    this.delegate = delegate;
  }


  public void change(Scheduler newScheduler) {
    this.delegate = newScheduler;

  }


}
