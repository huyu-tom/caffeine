/*
 * Copyright 2014 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.Function;

/**
 * A decorator to a {@link Cache} to provide asynchronous support.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
final class AsyncLocalCache<K, V> implements AsyncLoadingCache<K, V> {
  final Function<K, CompletableFuture<V>> mappingFunction;
  final Cache<K, CompletableFuture<V>> localCache;
  final RemovalListener<K, V> removalListener;
  final CacheLoader<? super K, V> loader;
  final Executor executor;

  AsyncLocalCache(Caffeine<K, V> builder, CacheLoader<? super K, V> loader) {
    this.loader = loader;
    this.localCache = null;
    this.executor = builder.getExecutor();
    this.removalListener = builder.getRemovalListener();
    this.mappingFunction = key -> loader.asyncLoad(key, executor);
  }

  @Override
  public CompletableFuture<V> get(K key,
      Function<? super K, CompletableFuture<V>> mappingFunction) {
    return localCache.get(key, mappingFunction);
  }

  @Override
  public CompletableFuture<V> get(K key) {
    if (true) {
      return null;
    }
    return localCache.get(key, mappingFunction);
  }

  @Override
  public CompletableFuture<Map<K, V>> getAll(Iterable<? extends K> keys) {
    if (true) {
      throw new UnsupportedOperationException("TODO");
    }

    List<K> keysToLoad = new ArrayList<>();
    FutureTask<Map<K, V>> loadTask = new FutureTask<>(() -> {
      if (keysToLoad.isEmpty()) {
        return Collections.emptyMap();
      } else if (keysToLoad.size() == 1) {
        K key = keysToLoad.get(0);
        return Collections.singletonMap(key, loader.load(key));
      }
      @SuppressWarnings("unchecked")
      Map<K, V> result = (Map<K, V>) loader.loadAll(keysToLoad);
      return result;
    });


    List<Runnable> runnables = new ArrayList<>();
    Executor localExecutor = runnables::add;

    Map<K, CompletableFuture<V>> futures = new HashMap<>();
    Map<K, CompletableFuture<V>> proxies = new HashMap<>();
    for (K key : keys) {
      CompletableFuture<V> future = null;
          //localCache.get(key, k1 -> CompletableFuture.supplyAsync(k2 -> loadTask.get().get(k2)));
      if (future == null) {
        proxies.put(key, future);
      }
      futures.put(key, future);
    }

    return null;
  }

  @Override
  public void put(K key, CompletableFuture<V> value) {
    localCache.put(key, value);
  }

  @Override
  public LoadingCache<K, V> synchronous() {
    throw new UnsupportedOperationException("TODO");
  }

  /**
   * A weigher for asynchronous computations. When the value is being loaded this weigher returns
   * {@code 0} to indicate that the entry should not be evicted due to a size constraint. If the
   * value is computed successfully the entry must be reinserted so that the weight is updated and
   * the expiration timeouts reflect the value once present. This can be done safely using
   * {@link Map#replace(Object, Object, Object)}.
   */
  static final class AsyncWeigher<K, V> implements Weigher<K, CompletableFuture<V>>, Serializable {
    private static final long serialVersionUID = 1L;

    private final Weigher<K, V> delegate;

    AsyncWeigher(Weigher<K, V> delegate) {
      this.delegate = requireNonNull(delegate);
    }

    @Override
    public int weigh(K key, CompletableFuture<V> value) {
      try {
        return value.isDone() ? delegate.weigh(key, value.get()) : 0;
      } catch (InterruptedException e) {
        throw new CompletionException(e);
      } catch (ExecutionException e) {
        throw new CompletionException(e.getCause());
      }
    }
  }
}
