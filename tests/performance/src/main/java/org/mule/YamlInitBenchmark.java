/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.vibur.objectpool.ConcurrentPool;
import org.vibur.objectpool.PoolObjectFactory;
import org.vibur.objectpool.PoolService;
import org.vibur.objectpool.util.ConcurrentLinkedQueueCollection;
import org.yaml.snakeyaml.Yaml;

@OutputTimeUnit(NANOSECONDS)
@Threads(4)
public class YamlInitBenchmark extends AbstractBenchmark {


  public static class YamlFactory implements PoolObjectFactory<Yaml> {

    @Override
    public Yaml create() {
      return new Yaml();
    }

    @Override
    public boolean readyToTake(Yaml obj) {
      return true;
    }

    @Override
    public boolean readyToRestore(Yaml obj) {
      return true;
    }

    @Override
    public void destroy(Yaml obj) {
      // Nothing to do
    }

  }

  private static Yaml staticYaml = new Yaml();
  private static PoolService<Yaml> pool =
      new ConcurrentPool<>(new ConcurrentLinkedQueueCollection<>(), new YamlFactory(), 1, 8, false);

  @Benchmark
  public Yaml shared() {
    synchronized (staticYaml) {
      Blackhole.consumeCPU(10);
      return staticYaml;
    }
  }

  @Benchmark
  public Yaml pooled() {
    final Yaml yaml = pool.take();
    try {
      Blackhole.consumeCPU(10);
      return yaml;
    } finally {
      pool.restore(yaml);
    }
  }

  @Benchmark
  public Yaml newOne() {
    Blackhole.consumeCPU(10);
    return new Yaml();
  }

}
