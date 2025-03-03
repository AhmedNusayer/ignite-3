/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.metastorage.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.apache.ignite.internal.testframework.flow.TestFlowUtils.subscribeToList;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureExceptionMatcher.willThrowFast;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.will;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.willBe;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.willCompleteSuccessfully;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.willSucceedFast;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.willSucceedIn;
import static org.apache.ignite.utils.ClusterServiceTestUtils.clusterService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.ignite.internal.cluster.management.ClusterManagementGroupManager;
import org.apache.ignite.internal.cluster.management.topology.api.LogicalTopologyService;
import org.apache.ignite.internal.configuration.testframework.ConfigurationExtension;
import org.apache.ignite.internal.configuration.testframework.InjectConfiguration;
import org.apache.ignite.internal.hlc.HybridClock;
import org.apache.ignite.internal.hlc.HybridClockImpl;
import org.apache.ignite.internal.hlc.HybridTimestamp;
import org.apache.ignite.internal.lang.ByteArray;
import org.apache.ignite.internal.manager.IgniteComponent;
import org.apache.ignite.internal.metastorage.Entry;
import org.apache.ignite.internal.metastorage.RevisionUpdateListener;
import org.apache.ignite.internal.metastorage.WatchEvent;
import org.apache.ignite.internal.metastorage.WatchListener;
import org.apache.ignite.internal.metastorage.configuration.MetaStorageConfiguration;
import org.apache.ignite.internal.metastorage.dsl.Conditions;
import org.apache.ignite.internal.metastorage.dsl.Operations;
import org.apache.ignite.internal.metastorage.server.KeyValueStorage;
import org.apache.ignite.internal.metastorage.server.persistence.RocksDbKeyValueStorage;
import org.apache.ignite.internal.metastorage.server.time.ClusterTime;
import org.apache.ignite.internal.raft.Loza;
import org.apache.ignite.internal.raft.client.TopologyAwareRaftGroupServiceFactory;
import org.apache.ignite.internal.raft.configuration.RaftConfiguration;
import org.apache.ignite.internal.testframework.IgniteAbstractTest;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.internal.vault.VaultManager;
import org.apache.ignite.internal.vault.inmemory.InMemoryVaultService;
import org.apache.ignite.network.ClusterService;
import org.apache.ignite.network.NetworkAddress;
import org.apache.ignite.network.StaticNodeFinder;
import org.apache.ignite.raft.jraft.rpc.impl.RaftGroupEventsClientListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

/**
 * Integration tests for {@link MetaStorageManagerImpl}.
 */
@ExtendWith(ConfigurationExtension.class)
public class ItMetaStorageManagerImplTest extends IgniteAbstractTest {
    private VaultManager vaultManager;

    private ClusterService clusterService;

    private Loza raftManager;

    private KeyValueStorage storage;

    private MetaStorageManagerImpl metaStorageManager;

    @BeforeEach
    void setUp(
            TestInfo testInfo,
            @InjectConfiguration RaftConfiguration raftConfiguration,
            @InjectConfiguration("mock.idleSyncTimeInterval = 100") MetaStorageConfiguration metaStorageConfiguration
    ) {
        var addr = new NetworkAddress("localhost", 10_000);

        clusterService = clusterService(testInfo, addr.port(), new StaticNodeFinder(List.of(addr)));

        HybridClock clock = new HybridClockImpl();

        var raftGroupEventsClientListener = new RaftGroupEventsClientListener();

        raftManager = new Loza(clusterService, raftConfiguration, workDir.resolve("loza"), clock, raftGroupEventsClientListener);

        var logicalTopologyService = mock(LogicalTopologyService.class);

        var topologyAwareRaftGroupServiceFactory = new TopologyAwareRaftGroupServiceFactory(
                clusterService,
                logicalTopologyService,
                Loza.FACTORY,
                raftGroupEventsClientListener
        );

        vaultManager = new VaultManager(new InMemoryVaultService());

        ClusterManagementGroupManager cmgManager = mock(ClusterManagementGroupManager.class);

        when(cmgManager.metaStorageNodes()).thenReturn(completedFuture(Set.of(clusterService.nodeName())));

        storage = new RocksDbKeyValueStorage(clusterService.nodeName(), workDir.resolve("metastorage"));

        metaStorageManager = new MetaStorageManagerImpl(
                vaultManager,
                clusterService,
                cmgManager,
                logicalTopologyService,
                raftManager,
                storage,
                clock,
                topologyAwareRaftGroupServiceFactory,
                metaStorageConfiguration
        );

        vaultManager.start();
        clusterService.start();
        raftManager.start();
        metaStorageManager.start();

        assertThat("Watches were not deployed", metaStorageManager.deployWatches(), willCompleteSuccessfully());
    }

    @AfterEach
    void tearDown() throws Exception {
        List<IgniteComponent> components = List.of(metaStorageManager, raftManager, clusterService, vaultManager);

        IgniteUtils.closeAll(Stream.concat(
                components.stream().map(c -> c::beforeNodeStop),
                components.stream().map(c -> c::stop)
        ));
    }

    /**
     * Tests a corner case when a prefix request contains a max unsigned byte value.
     */
    @Test
    void testPrefixOverflow() {
        byte[] value = "value".getBytes(UTF_8);

        var key1 = new ByteArray(new byte[]{1, (byte) 0xFF, 0});
        var key2 = new ByteArray(new byte[]{1, (byte) 0xFF, 1});
        var key3 = new ByteArray(new byte[]{1, (byte) 0xFF, (byte) 0xFF});
        // Contains the next lexicographical prefix
        var key4 = new ByteArray(new byte[]{1, 0, 1});
        // Contains the previous lexicographical prefix
        var key5 = new ByteArray(new byte[]{1, (byte) 0xFE, 1});

        CompletableFuture<Boolean> invokeFuture = metaStorageManager.invoke(
                Conditions.notExists(new ByteArray("foo")),
                List.of(
                        Operations.put(key1, value),
                        Operations.put(key2, value),
                        Operations.put(key3, value),
                        Operations.put(key4, value),
                        Operations.put(key5, value)
                ),
                List.of(Operations.noop())
        );

        assertThat(invokeFuture, willBe(true));

        CompletableFuture<List<byte[]>> actualKeysFuture =
                subscribeToList(metaStorageManager.prefix(new ByteArray(new byte[]{1, (byte) 0xFF})))
                        .thenApply(entries -> entries.stream().map(Entry::key).collect(toList()));

        assertThat(actualKeysFuture, will(contains(key1.bytes(), key2.bytes(), key3.bytes())));
    }

    private static class NoOpListener implements WatchListener {
        @Override
        public CompletableFuture<Void> onUpdate(WatchEvent event) {
            return completedFuture(null);
        }

        @Override
        public void onError(Throwable e) {}
    }

    @Test
    void testMetaStorageStopClosesRaftService() throws Exception {
        MetaStorageServiceImpl svc = metaStorageManager.metaStorageServiceFuture().join();

        metaStorageManager.stop();

        CompletableFuture<Entry> fut = svc.get(ByteArray.fromString("ignored"));

        assertThat(fut, willThrowFast(CancellationException.class));
    }

    @Test
    void testMetaStorageStopBeforeRaftServiceStarted() throws Exception {
        metaStorageManager.stop(); // Close MetaStorage that is created in setUp.

        ClusterManagementGroupManager cmgManager = mock(ClusterManagementGroupManager.class);

        Set<String> msNodes = Set.of(clusterService.nodeName());
        CompletableFuture<Set<String>> cmgFut = new CompletableFuture<>();

        when(cmgManager.metaStorageNodes()).thenReturn(cmgFut);

        metaStorageManager = new MetaStorageManagerImpl(
                vaultManager,
                clusterService,
                cmgManager,
                mock(LogicalTopologyService.class),
                raftManager,
                storage,
                new HybridClockImpl(),
                mock(TopologyAwareRaftGroupServiceFactory.class)
        );

        metaStorageManager.stop();

        // Unblock the future so raft service can be initialized. Although the future should be cancelled already by the
        // stop method.
        cmgFut.complete(msNodes);

        assertThat(metaStorageManager.metaStorageServiceFuture(), willThrowFast(CancellationException.class));
    }

    @Test
    void testUpdateRevisionListener() {
        ArgumentCaptor<Long> revisionCapture = ArgumentCaptor.forClass(Long.class);

        RevisionUpdateListener listener = mock(RevisionUpdateListener.class);

        when(listener.onUpdated(revisionCapture.capture())).thenReturn(completedFuture(null));

        long revision = metaStorageManager.appliedRevision();

        metaStorageManager.registerRevisionUpdateListener(listener);

        assertThat(metaStorageManager.put(ByteArray.fromString("test"), "test".getBytes(UTF_8)), willSucceedFast());

        // Watches are processed asynchronously.
        // Timeout is big just in case there's a GC pause. Test's duration doesn't really depend on it.
        verify(listener, timeout(5000).atLeast(1)).onUpdated(anyLong());

        assertThat(revisionCapture.getAllValues(), is(List.of(revision + 1)));
    }

    /**
     * Tests that idle safe time propagation does not advance safe time while watches of a normal command are being executed.
     */
    @Test
    void testIdleSafeTimePropagationAndNormalSafeTimePropagationInteraction(TestInfo testInfo) throws Exception {
        var key = new ByteArray("foo");
        byte[] value = "bar".getBytes(UTF_8);

        AtomicBoolean watchCompleted = new AtomicBoolean(false);
        CompletableFuture<HybridTimestamp> watchEventTsFuture = new CompletableFuture<>();

        metaStorageManager.registerExactWatch(key, new WatchListener() {
            @Override
            public CompletableFuture<Void> onUpdate(WatchEvent event) {
                watchEventTsFuture.complete(event.timestamp());

                // The future will set the flag and complete after 300ms to allow idle safe time mechanism (which ticks each 100ms)
                // to advance SafeTime (if there is still a bug for which this test is written).
                return waitFor(300, TimeUnit.MILLISECONDS)
                        .whenComplete((res, ex) -> watchCompleted.set(true));
            }

            @Override
            public void onError(Throwable e) {
            }
        });

        metaStorageManager.put(key, value);

        ClusterTime clusterTime = metaStorageManager.clusterTime();

        assertThat(watchEventTsFuture, willSucceedIn(5, TimeUnit.SECONDS));

        HybridTimestamp watchEventTs = watchEventTsFuture.join();
        assertThat(clusterTime.waitFor(watchEventTs), willCompleteSuccessfully());

        assertThat("Safe time is advanced too early", watchCompleted.get(), is(true));
    }

    private static CompletableFuture<Void> waitFor(int timeout, TimeUnit unit) {
        return new CompletableFuture<Void>()
                .orTimeout(timeout, unit)
                .exceptionally(ex -> null);
    }
}
