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

package org.apache.ignite.internal.table.distributed;

import static java.util.Collections.emptySet;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.ignite.internal.catalog.CatalogService.DEFAULT_SCHEMA_NAME;
import static org.apache.ignite.internal.catalog.events.CatalogEvent.TABLE_CREATE;
import static org.apache.ignite.internal.catalog.events.CatalogEvent.TABLE_DROP;
import static org.apache.ignite.internal.testframework.IgniteTestUtils.assertThrowsWithCause;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureExceptionMatcher.willThrow;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.willCompleteSuccessfully;
import static org.apache.ignite.sql.ColumnType.INT64;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Phaser;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import org.apache.ignite.internal.affinity.AffinityUtils;
import org.apache.ignite.internal.catalog.CatalogManager;
import org.apache.ignite.internal.catalog.CatalogTestUtils;
import org.apache.ignite.internal.catalog.commands.ColumnParams;
import org.apache.ignite.internal.catalog.descriptors.CatalogTableDescriptor;
import org.apache.ignite.internal.catalog.descriptors.CatalogZoneDescriptor;
import org.apache.ignite.internal.configuration.ConfigurationRegistry;
import org.apache.ignite.internal.configuration.testframework.ConfigurationExtension;
import org.apache.ignite.internal.configuration.testframework.InjectConfiguration;
import org.apache.ignite.internal.distributionzones.DistributionZoneManager;
import org.apache.ignite.internal.distributionzones.DistributionZonesTestUtil;
import org.apache.ignite.internal.hlc.HybridClock;
import org.apache.ignite.internal.hlc.HybridClockImpl;
import org.apache.ignite.internal.lang.ByteArray;
import org.apache.ignite.internal.lang.IgniteBiTuple;
import org.apache.ignite.internal.lang.NodeStoppingException;
import org.apache.ignite.internal.metastorage.MetaStorageManager;
import org.apache.ignite.internal.metastorage.dsl.Operation;
import org.apache.ignite.internal.metastorage.impl.StandaloneMetaStorageManager;
import org.apache.ignite.internal.metastorage.server.SimpleInMemoryKeyValueStorage;
import org.apache.ignite.internal.placementdriver.TestPlacementDriver;
import org.apache.ignite.internal.raft.Loza;
import org.apache.ignite.internal.raft.Peer;
import org.apache.ignite.internal.raft.client.TopologyAwareRaftGroupService;
import org.apache.ignite.internal.raft.client.TopologyAwareRaftGroupServiceFactory;
import org.apache.ignite.internal.raft.service.RaftGroupService;
import org.apache.ignite.internal.raft.storage.impl.LocalLogStorageFactory;
import org.apache.ignite.internal.replicator.ReplicaManager;
import org.apache.ignite.internal.schema.CatalogSchemaManager;
import org.apache.ignite.internal.schema.SchemaDescriptor;
import org.apache.ignite.internal.schema.SchemaUtils;
import org.apache.ignite.internal.schema.configuration.GcConfiguration;
import org.apache.ignite.internal.storage.DataStorageManager;
import org.apache.ignite.internal.storage.DataStorageModules;
import org.apache.ignite.internal.storage.MvPartitionStorage;
import org.apache.ignite.internal.storage.PartitionTimestampCursor;
import org.apache.ignite.internal.storage.engine.MvTableStorage;
import org.apache.ignite.internal.storage.pagememory.PersistentPageMemoryDataStorageModule;
import org.apache.ignite.internal.storage.pagememory.PersistentPageMemoryStorageEngine;
import org.apache.ignite.internal.storage.pagememory.configuration.schema.PersistentPageMemoryStorageEngineConfiguration;
import org.apache.ignite.internal.table.TableImpl;
import org.apache.ignite.internal.table.TableTestUtils;
import org.apache.ignite.internal.table.distributed.raft.snapshot.outgoing.OutgoingSnapshotsManager;
import org.apache.ignite.internal.table.distributed.schema.AlwaysSyncedSchemaSyncService;
import org.apache.ignite.internal.testframework.IgniteAbstractTest;
import org.apache.ignite.internal.tx.HybridTimestampTracker;
import org.apache.ignite.internal.tx.TxManager;
import org.apache.ignite.internal.tx.storage.state.TxStateStorage;
import org.apache.ignite.internal.tx.storage.state.TxStateTableStorage;
import org.apache.ignite.internal.util.CursorUtils;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.internal.vault.VaultManager;
import org.apache.ignite.internal.vault.inmemory.InMemoryVaultService;
import org.apache.ignite.network.ClusterNode;
import org.apache.ignite.network.ClusterNodeImpl;
import org.apache.ignite.network.ClusterService;
import org.apache.ignite.network.MessagingService;
import org.apache.ignite.network.NetworkAddress;
import org.apache.ignite.network.TopologyService;
import org.apache.ignite.table.Table;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests scenarios for table manager.
 */
@ExtendWith({MockitoExtension.class, ConfigurationExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class TableManagerTest extends IgniteAbstractTest {
    /** The name of the table which is preconfigured. */
    private static final String PRECONFIGURED_TABLE_NAME = "T1";

    /** The name of the table which will be configured dynamically. */
    private static final String DYNAMIC_TABLE_NAME = "T2";

    /** The name of table to drop it. */
    private static final String DYNAMIC_TABLE_FOR_DROP_NAME = "T3";

    /** Table partitions. */
    private static final int PARTITIONS = 32;

    /** Node name. */
    private static final String NODE_NAME = "node1";

    /** Count of replicas. */
    private static final int REPLICAS = 1;

    /** Zone name. */
    private static final String ZONE_NAME = "zone1";

    /** Topology service. */
    @Mock
    private TopologyService ts;

    /** Raft manager. */
    @Mock
    private Loza rm;

    /** Replica manager. */
    @Mock
    private ReplicaManager replicaMgr;

    /** TX manager. */
    @Mock
    private TxManager tm;

    /** Meta storage manager. */
    @Mock
    private MetaStorageManager msm;

    /** Mock cluster service. */
    @Mock
    private ClusterService clusterService;

    private volatile MvTableStorage mvTableStorage;

    private volatile TxStateTableStorage txStateTableStorage;

    /** Revision updater. */
    private Consumer<LongFunction<CompletableFuture<?>>> revisionUpdater;

    /** Garbage collector configuration. */
    @InjectConfiguration
    private GcConfiguration gcConfig;

    @InjectConfiguration
    private PersistentPageMemoryStorageEngineConfiguration storageEngineConfig;

    @Mock
    private ConfigurationRegistry configRegistry;

    private DataStorageManager dsm;

    private CatalogSchemaManager sm;

    private DistributionZoneManager distributionZoneManager;

    /** Test node. */
    private final ClusterNode node = new ClusterNodeImpl(
            UUID.randomUUID().toString(),
            NODE_NAME,
            new NetworkAddress("127.0.0.1", 2245)
    );

    /** The future will be completed after each tests of this class. */
    private CompletableFuture<TableManager> tblManagerFut;

    /** Hybrid clock. */
    private final HybridClock clock = new HybridClockImpl();

    /** Catalog vault. */
    private VaultManager catalogVault;

    /** Catalog metastore. */
    private MetaStorageManager catalogMetastore;

    /** Catalog manager. */
    private CatalogManager catalogManager;

    @BeforeEach
    void before() throws NodeStoppingException {
        catalogVault = new VaultManager(new InMemoryVaultService());
        catalogMetastore = StandaloneMetaStorageManager.create(catalogVault, new SimpleInMemoryKeyValueStorage(NODE_NAME));
        catalogManager = CatalogTestUtils.createTestCatalogManager(NODE_NAME, clock, catalogMetastore);

        catalogVault.start();
        catalogMetastore.start();
        catalogManager.start();

        revisionUpdater = (LongFunction<CompletableFuture<?>> function) -> catalogMetastore.registerRevisionUpdateListener(function::apply);

        assertThat(catalogMetastore.deployWatches(), willCompleteSuccessfully());

        when(clusterService.messagingService()).thenReturn(mock(MessagingService.class));

        TopologyService topologyService = mock(TopologyService.class);

        when(clusterService.topologyService()).thenReturn(topologyService);
        when(topologyService.localMember()).thenReturn(node);

        distributionZoneManager = mock(DistributionZoneManager.class);

        when(distributionZoneManager.dataNodes(anyLong(), anyInt())).thenReturn(completedFuture(emptySet()));

        when(replicaMgr.stopReplica(any())).thenReturn(completedFuture(true));

        tblManagerFut = new CompletableFuture<>();

        mockMetastore();
    }

    @AfterEach
    void after() throws Exception {
        IgniteUtils.closeAll(
                () -> {
                    assertTrue(tblManagerFut.isDone());

                    tblManagerFut.join().beforeNodeStop();
                    tblManagerFut.join().stop();
                },
                dsm == null ? null : dsm::stop,
                sm == null ? null : sm::stop,
                catalogManager == null ? null : catalogManager::stop,
                catalogMetastore == null ? null : catalogMetastore::stop,
                catalogVault == null ? null : catalogVault::stop
        );
    }

    /**
     * Tests a table which was preconfigured.
     */
    @Test
    public void testPreconfiguredTable() throws Exception {
        when(rm.startRaftGroupService(any(), any(), any())).thenAnswer(mock -> completedFuture(mock(TopologyAwareRaftGroupService.class)));

        TableManager tableManager = createTableManager(tblManagerFut);

        tblManagerFut.complete(tableManager);

        createZone(PARTITIONS, REPLICAS);

        createTable(PRECONFIGURED_TABLE_NAME);

        assertEquals(1, tableManager.tables().size());

        assertNotNull(tableManager.table(PRECONFIGURED_TABLE_NAME));

        checkTableDataStorage(allTableDescriptors(), PersistentPageMemoryStorageEngine.ENGINE_NAME);
    }

    /**
     * Tests create a table through public API.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testCreateTable() throws Exception {
        Table table = mockManagersAndCreateTable(DYNAMIC_TABLE_NAME, tblManagerFut);

        assertNotNull(table);

        assertSame(table, tblManagerFut.join().table(DYNAMIC_TABLE_NAME));

        checkTableDataStorage(allTableDescriptors(), PersistentPageMemoryStorageEngine.ENGINE_NAME);
    }

    /**
     * Tests drop a table through public API.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testDropTable() throws Exception {
        mockManagersAndCreateTable(DYNAMIC_TABLE_FOR_DROP_NAME, tblManagerFut);

        TableManager tableManager = tblManagerFut.join();

        dropTable(DYNAMIC_TABLE_FOR_DROP_NAME);

        verify(mvTableStorage).destroy();
        verify(txStateTableStorage).destroy();
        verify(replicaMgr, times(PARTITIONS)).stopReplica(any());

        assertNull(tableManager.table(DYNAMIC_TABLE_FOR_DROP_NAME));

        assertEquals(0, tableManager.tables().size());
    }

    /**
     * Tests a work of the public API for Table manager {@see org.apache.ignite.table.manager.IgniteTables} when the manager is stopping.
     */
    @Test
    public void testApiTableManagerOnStop() {
        createTableManager(tblManagerFut);

        TableManager tableManager = tblManagerFut.join();

        tableManager.beforeNodeStop();
        tableManager.stop();

        assertThrowsWithCause(tableManager::tables, NodeStoppingException.class);
        assertThrowsWithCause(() -> tableManager.table(DYNAMIC_TABLE_FOR_DROP_NAME), NodeStoppingException.class);

        assertThat(tableManager.tablesAsync(), willThrow(NodeStoppingException.class));
        assertThat(tableManager.tableAsync(DYNAMIC_TABLE_FOR_DROP_NAME), willThrow(NodeStoppingException.class));
    }

    /**
     * Tests a work of the public API for Table manager {@see org.apache.ignite.internal.table.IgniteTablesInternal} when the manager is
     * stopping.
     */
    @Test
    public void testInternalApiTableManagerOnStop() {
        createTableManager(tblManagerFut);

        TableManager tableManager = tblManagerFut.join();

        tableManager.beforeNodeStop();
        tableManager.stop();

        int fakeTblId = 1;

        assertThrowsWithCause(() -> tableManager.table(fakeTblId), NodeStoppingException.class);
        assertThat(tableManager.tableAsync(fakeTblId), willThrow(NodeStoppingException.class));
    }

    /**
     * Checks that all RAFT nodes will be stopped when Table manager is stopping and an exception that was thrown by one of the
     * components will not prevent stopping other components.
     *
     * @throws Exception If failed.
     */
    @Test
    public void tableManagerStopTest1() throws Exception {
        IgniteBiTuple<TableImpl, TableManager> tblAndMnr = startTableManagerStopTest();

        endTableManagerStopTest(tblAndMnr.get1(), tblAndMnr.get2(),
                () -> {
                    try {
                        doThrow(new NodeStoppingException()).when(rm).stopRaftNodes(any());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Checks that all RAFT nodes will be stopped when Table manager is stopping and an exception that was thrown by one of the
     * components will not prevent stopping other components.
     *
     * @throws Exception If failed.
     */
    @Test
    public void tableManagerStopTest2() throws Exception {
        IgniteBiTuple<TableImpl, TableManager> tblAndMnr = startTableManagerStopTest();

        endTableManagerStopTest(tblAndMnr.get1(), tblAndMnr.get2(),
                () -> {
                    try {
                        doThrow(new NodeStoppingException()).when(replicaMgr).stopReplica(any());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Checks that all RAFT nodes will be stopped when Table manager is stopping and an exception that was thrown by one of the
     * components will not prevent stopping other components.
     *
     * @throws Exception If failed.
     */
    @Test
    public void tableManagerStopTest3() throws Exception {
        IgniteBiTuple<TableImpl, TableManager> tblAndMnr = startTableManagerStopTest();

        endTableManagerStopTest(tblAndMnr.get1(), tblAndMnr.get2(),
                () -> {
                    try {
                        doThrow(new RuntimeException("Test exception")).when(tblAndMnr.get1().internalTable().storage()).close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Checks that all RAFT nodes will be stopped when Table manager is stopping and an exception that was thrown by one of the
     * components will not prevent stopping other components.
     *
     * @throws Exception If failed.
     */
    @Test
    public void tableManagerStopTest4() throws Exception {
        IgniteBiTuple<TableImpl, TableManager> tblAndMnr = startTableManagerStopTest();

        endTableManagerStopTest(tblAndMnr.get1(), tblAndMnr.get2(),
                () -> doThrow(new RuntimeException()).when(tblAndMnr.get1().internalTable().txStateStorage()).close());
    }

    private IgniteBiTuple<TableImpl, TableManager> startTableManagerStopTest() throws Exception {
        TableImpl table = mockManagersAndCreateTable(DYNAMIC_TABLE_FOR_DROP_NAME, tblManagerFut);

        verify(rm, times(PARTITIONS)).startRaftGroupService(any(), any(), any());

        TableManager tableManager = tblManagerFut.join();

        return new IgniteBiTuple<>(table, tableManager);
    }

    private void endTableManagerStopTest(TableImpl table, TableManager tableManager, Runnable mockDoThrow) throws Exception {
        mockDoThrow.run();

        tableManager.stop();

        verify(rm, times(PARTITIONS)).stopRaftNodes(any());
        verify(replicaMgr, times(PARTITIONS)).stopReplica(any());

        verify(table.internalTable().storage()).close();
        verify(table.internalTable().txStateStorage()).close();
    }

    /**
     * Instantiates a table and prepares Table manager.
     */
    @Test
    public void testGetTableDuringCreation() {
        Phaser phaser = new Phaser(2);

        CompletableFuture<Table> createFut = CompletableFuture.supplyAsync(() -> {
            try {
                return mockManagersAndCreateTableWithDelay(DYNAMIC_TABLE_FOR_DROP_NAME, tblManagerFut, phaser);
            } catch (Exception e) {
                fail(e.getMessage());
            }

            return null;
        });

        CompletableFuture<Table> getFut = CompletableFuture.supplyAsync(() -> {
            phaser.awaitAdvance(0);

            return tblManagerFut.join().table(DYNAMIC_TABLE_FOR_DROP_NAME);
        });

        CompletableFuture<Collection<Table>> getAllTablesFut = CompletableFuture.supplyAsync(() -> {
            phaser.awaitAdvance(0);

            return tblManagerFut.join().tables();
        });

        assertFalse(createFut.isDone());
        assertFalse(getFut.isDone());
        assertFalse(getAllTablesFut.isDone());

        phaser.arrive();

        assertSame(createFut.join(), getFut.join());

        assertEquals(1, getAllTablesFut.join().size());
    }

    @Test
    void testStoragesGetClearedInMiddleOfFailedTxStorageRebalance() throws Exception {
        testStoragesGetClearedInMiddleOfFailedRebalance(true);
    }

    @Test
    void testStoragesGetClearedInMiddleOfFailedPartitionStorageRebalance() throws Exception {
        testStoragesGetClearedInMiddleOfFailedRebalance(false);
    }

    /**
     * Emulates a situation, when either a TX state storage or partition storage were stopped in a middle of a rebalance. We then expect
     * that these storages get cleared upon startup.
     *
     * @param isTxStorageUnderRebalance When {@code true} - TX state storage is emulated as being under rebalance, when {@code false} -
     *         partition storage is emulated instead.
     */
    private void testStoragesGetClearedInMiddleOfFailedRebalance(boolean isTxStorageUnderRebalance) throws NodeStoppingException {
        when(rm.startRaftGroupService(any(), any(), any())).thenAnswer(mock -> completedFuture(mock(TopologyAwareRaftGroupService.class)));
        when(rm.raftNodeReadyFuture(any())).thenReturn(completedFuture(1L));

        createZone(1, 1);

        var txStateStorage = mock(TxStateStorage.class);
        var mvPartitionStorage = mock(MvPartitionStorage.class);

        if (isTxStorageUnderRebalance) {
            // Emulate a situation when TX state storage was stopped in a middle of rebalance.
            when(txStateStorage.lastAppliedIndex()).thenReturn(TxStateStorage.REBALANCE_IN_PROGRESS);
        } else {
            // Emulate a situation when partition storage was stopped in a middle of rebalance.
            when(mvPartitionStorage.lastAppliedIndex()).thenReturn(MvPartitionStorage.REBALANCE_IN_PROGRESS);
        }

        doReturn(mock(PartitionTimestampCursor.class)).when(mvPartitionStorage).scan(any());
        when(txStateStorage.clear()).thenReturn(completedFuture(null));

        when(msm.recoveryFinishedFuture()).thenReturn(completedFuture(1L));

        // For some reason, "when(something).thenReturn" does not work on spies, but this notation works.
        createTableManager(tblManagerFut, (mvTableStorage) -> {
            doReturn(completedFuture(mvPartitionStorage)).when(mvTableStorage).createMvPartition(anyInt());
            doReturn(mvPartitionStorage).when(mvTableStorage).getMvPartition(anyInt());
            doReturn(completedFuture(null)).when(mvTableStorage).clearPartition(anyInt());
        }, (txStateTableStorage) -> {
            doReturn(txStateStorage).when(txStateTableStorage).getOrCreateTxStateStorage(anyInt());
            doReturn(txStateStorage).when(txStateTableStorage).getTxStateStorage(anyInt());
        });

        createTable(PRECONFIGURED_TABLE_NAME);

        verify(txStateStorage, timeout(1000)).clear();
        verify(mvTableStorage, timeout(1000)).clearPartition(anyInt());
    }

    /**
     * Instantiates Table manager and creates a table in it.
     *
     * @param tableName Table name.
     * @param tblManagerFut Future for table manager.
     * @return Table.
     * @throws Exception If something went wrong.
     */
    private TableImpl mockManagersAndCreateTable(String tableName, CompletableFuture<TableManager> tblManagerFut) throws Exception {
        return mockManagersAndCreateTableWithDelay(tableName, tblManagerFut, null);
    }

    /** Dummy metastore activity mock. */
    private void mockMetastore() {
        when(msm.prefix(any())).thenReturn(subscriber -> {
            subscriber.onSubscribe(mock(Subscription.class));

            subscriber.onComplete();
        });

        when(msm.invoke(any(), any(Operation.class), any(Operation.class))).thenReturn(completedFuture(null));
        when(msm.invoke(any(), any(List.class), any(List.class))).thenReturn(completedFuture(null));
        when(msm.get(any())).thenReturn(completedFuture(null));

        when(msm.recoveryFinishedFuture()).thenReturn(completedFuture(1L));

        when(msm.prefixLocally(any(), anyLong())).thenReturn(CursorUtils.emptyCursor());
    }

    /**
     * Instantiates a table and prepares Table manager. When the latch would open, the method completes.
     *
     * @param tableName Table name.
     * @param tblManagerFut Future for table manager.
     * @param phaser Phaser for the wait.
     * @return Table manager.
     * @throws Exception If something went wrong.
     */
    private TableImpl mockManagersAndCreateTableWithDelay(
            String tableName,
            CompletableFuture<TableManager> tblManagerFut,
            @Nullable Phaser phaser
    ) throws Exception {
        String consistentId = "node0";

        when(rm.startRaftGroupService(any(), any(), any())).thenAnswer(mock -> {
            RaftGroupService raftGrpSrvcMock = mock(TopologyAwareRaftGroupService.class);

            when(raftGrpSrvcMock.leader()).thenReturn(new Peer(consistentId));

            return completedFuture(raftGrpSrvcMock);
        });

        when(ts.getByConsistentId(any())).thenReturn(new ClusterNodeImpl(
                UUID.randomUUID().toString(),
                consistentId,
                new NetworkAddress("localhost", 47500)
        ));

        try (MockedStatic<SchemaUtils> schemaServiceMock = mockStatic(SchemaUtils.class)) {
            schemaServiceMock.when(() -> SchemaUtils.prepareSchemaDescriptor(any()))
                    .thenReturn(mock(SchemaDescriptor.class));
        }

        try (MockedStatic<AffinityUtils> affinityServiceMock = mockStatic(AffinityUtils.class)) {
            ArrayList<List<ClusterNode>> assignment = new ArrayList<>(PARTITIONS);

            for (int part = 0; part < PARTITIONS; part++) {
                assignment.add(new ArrayList<>(Collections.singleton(node)));
            }

            affinityServiceMock.when(() -> AffinityUtils.calculateAssignments(any(), anyInt(), anyInt()))
                    .thenReturn(assignment);
        }

        TableManager tableManager = createTableManager(tblManagerFut);

        int tablesBeforeCreation = tableManager.tables().size();

        if (phaser != null) {
            catalogManager.listen(TABLE_CREATE, (parameters, exception) -> {
                phaser.arriveAndAwaitAdvance();

                return completedFuture(false);
            });

            catalogManager.listen(TABLE_DROP, (parameters, exception) -> {
                phaser.arriveAndAwaitAdvance();

                return completedFuture(false);
            });
        }

        createZone(PARTITIONS, REPLICAS);

        createTable(tableName);

        TableImpl tbl2 = tableManager.tableImpl(tableName);

        assertNotNull(tbl2);

        assertEquals(tablesBeforeCreation + 1, tableManager.tables().size());

        return tbl2;
    }

    private TableManager createTableManager(CompletableFuture<TableManager> tblManagerFut) {
        return createTableManager(tblManagerFut, unused -> {}, unused -> {});
    }

    /**
     * Creates Table manager.
     *
     * @param tblManagerFut Future to wrap Table manager.
     * @param tableStorageDecorator Table storage spy decorator.
     * @param txStateTableStorageDecorator Tx state table storage spy decorator.
     *
     * @return Table manager.
     */
    private TableManager createTableManager(CompletableFuture<TableManager> tblManagerFut, Consumer<MvTableStorage> tableStorageDecorator,
            Consumer<TxStateTableStorage> txStateTableStorageDecorator) {
        VaultManager vaultManager = mock(VaultManager.class);

        when(vaultManager.get(any(ByteArray.class))).thenReturn(completedFuture(null));
        when(vaultManager.put(any(ByteArray.class), any(byte[].class))).thenReturn(completedFuture(null));

        TableManager tableManager = new TableManager(
                NODE_NAME,
                revisionUpdater,
                gcConfig,
                clusterService,
                rm,
                replicaMgr,
                null,
                null,
                ts,
                tm,
                dsm = createDataStorageManager(configRegistry, workDir, storageEngineConfig),
                workDir,
                msm,
                sm = new CatalogSchemaManager(revisionUpdater, catalogManager, msm),
                budgetView -> new LocalLogStorageFactory(),
                clock,
                new OutgoingSnapshotsManager(clusterService.messagingService()),
                mock(TopologyAwareRaftGroupServiceFactory.class),
                vaultManager,
                distributionZoneManager,
                new AlwaysSyncedSchemaSyncService(),
                catalogManager,
                new HybridTimestampTracker(),
                new TestPlacementDriver(NODE_NAME)
        ) {

            @Override
            protected MvTableStorage createTableStorage(CatalogTableDescriptor tableDescriptor, CatalogZoneDescriptor zoneDescriptor) {
                mvTableStorage = spy(super.createTableStorage(tableDescriptor, zoneDescriptor));

                tableStorageDecorator.accept(mvTableStorage);

                return mvTableStorage;
            }

            @Override
            protected TxStateTableStorage createTxStateTableStorage(
                    CatalogTableDescriptor tableDescriptor,
                    CatalogZoneDescriptor zoneDescriptor
            ) {
                txStateTableStorage = spy(super.createTxStateTableStorage(tableDescriptor, zoneDescriptor));

                txStateTableStorageDecorator.accept(txStateTableStorage);

                return txStateTableStorage;
            }
        };

        sm.start();

        tableManager.start();

        tblManagerFut.complete(tableManager);

        return tableManager;
    }

    private DataStorageManager createDataStorageManager(
            ConfigurationRegistry mockedRegistry,
            Path storagePath,
            PersistentPageMemoryStorageEngineConfiguration config
    ) {
        when(mockedRegistry.getConfiguration(PersistentPageMemoryStorageEngineConfiguration.KEY)).thenReturn(config);

        DataStorageModules dataStorageModules = new DataStorageModules(List.of(new PersistentPageMemoryDataStorageModule()));

        DataStorageManager manager = new DataStorageManager(
                dataStorageModules.createStorageEngines(NODE_NAME, mockedRegistry, storagePath, null)
        );

        manager.start();

        return manager;
    }

    private void checkTableDataStorage(Collection<CatalogTableDescriptor> tableDescriptors, String expDataStorage) {
        assertFalse(tableDescriptors.isEmpty());

        for (CatalogTableDescriptor tableDescriptor : tableDescriptors) {
            assertEquals(getZoneDataStorage(tableDescriptor.zoneId()), expDataStorage, tableDescriptor.name());
        }
    }

    private void createZone(int partitions, int replicas) {
        DistributionZonesTestUtil.createZone(catalogManager, ZONE_NAME, partitions, replicas);
    }

    private @Nullable String getZoneDataStorage(int zoneId) {
        CatalogZoneDescriptor zoneDescriptor = DistributionZonesTestUtil.getZoneById(catalogManager, zoneId, clock.nowLong());

        return zoneDescriptor == null ? null : zoneDescriptor.dataStorage().engine();
    }

    private void createTable(String tableName) {
        TableTestUtils.createTable(
                catalogManager,
                DEFAULT_SCHEMA_NAME,
                ZONE_NAME,
                tableName,
                List.of(
                        ColumnParams.builder().name("key").type(INT64).build(),
                        ColumnParams.builder().name("val").type(INT64).nullable(true).build()
                ),
                List.of("key")
        );
    }

    private void dropTable(String tableName) {
        TableTestUtils.dropTable(catalogManager, DEFAULT_SCHEMA_NAME, tableName);
    }

    private Collection<CatalogTableDescriptor> allTableDescriptors() {
        return catalogManager.tables(catalogManager.latestCatalogVersion());
    }
}
