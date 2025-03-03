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

package org.apache.ignite.distributed;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.willCompleteSuccessfully;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.apache.ignite.internal.catalog.CatalogService;
import org.apache.ignite.internal.hlc.HybridClock;
import org.apache.ignite.internal.hlc.HybridTimestamp;
import org.apache.ignite.internal.placementdriver.PlacementDriver;
import org.apache.ignite.internal.raft.service.RaftGroupService;
import org.apache.ignite.internal.replicator.ReplicaResult;
import org.apache.ignite.internal.replicator.ReplicaService;
import org.apache.ignite.internal.replicator.message.ReplicaRequest;
import org.apache.ignite.internal.storage.MvPartitionStorage;
import org.apache.ignite.internal.table.distributed.IndexLocker;
import org.apache.ignite.internal.table.distributed.StorageUpdateHandler;
import org.apache.ignite.internal.table.distributed.TableSchemaAwareIndexStorage;
import org.apache.ignite.internal.table.distributed.replicator.PartitionReplicaListener;
import org.apache.ignite.internal.table.distributed.replicator.TransactionStateResolver;
import org.apache.ignite.internal.table.distributed.schema.SchemaSyncService;
import org.apache.ignite.internal.table.distributed.schema.Schemas;
import org.apache.ignite.internal.tx.InternalTransaction;
import org.apache.ignite.internal.tx.LockManager;
import org.apache.ignite.internal.tx.TxManager;
import org.apache.ignite.internal.tx.impl.HeapLockManager;
import org.apache.ignite.internal.tx.impl.TransactionIdGenerator;
import org.apache.ignite.internal.tx.impl.TxManagerImpl;
import org.apache.ignite.internal.tx.message.TxCleanupReplicaRequest;
import org.apache.ignite.internal.tx.storage.state.TxStateStorage;
import org.apache.ignite.internal.util.Lazy;
import org.apache.ignite.internal.util.PendingComparableValuesTracker;
import org.apache.ignite.network.ClusterNode;
import org.apache.ignite.table.Tuple;
import org.apache.ignite.tx.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * Test to Simulate missing cleanup action.
 */
public class ItTxDistributedTestSingleNodeNoCleanupMessage extends ItTxDistributedTestSingleNode {
    /** A list of background cleanup futures. */
    private final List<CompletableFuture<?>> cleanupFutures = new CopyOnWriteArrayList<>();

    /** A flag to drop async cleanup actions.  */
    private volatile boolean ignoreAsyncCleanup;

    /**
     * The constructor.
     *
     * @param testInfo Test info.
     */
    public ItTxDistributedTestSingleNodeNoCleanupMessage(TestInfo testInfo) {
        super(testInfo);
    }

    @BeforeEach
    @Override
    public void before() throws Exception {
        txTestCluster = new ItTxTestCluster(
                testInfo,
                raftConfiguration,
                gcConfig,
                workDir,
                nodes(),
                replicas(),
                startClient(),
                timestampTracker
        ) {
            @Override
            protected TxManagerImpl newTxManager(
                    ReplicaService replicaSvc,
                    HybridClock clock,
                    TransactionIdGenerator generator,
                    ClusterNode node,
                    PlacementDriver placementDriver
            ) {
                return new TxManagerImpl(
                        replicaSvc,
                        new HeapLockManager(),
                        clock,
                        generator,
                        node::id,
                        placementDriver
                ) {
                    @Override
                    public CompletableFuture<Void> executeCleanupAsync(Runnable runnable) {
                        if (ignoreAsyncCleanup) {
                            return completedFuture(null);
                        }
                        CompletableFuture<Void> cleanupFuture = super.executeCleanupAsync(runnable);

                        cleanupFutures.add(cleanupFuture);

                        return cleanupFuture;
                    }
                };
            }

            @Override
            protected PartitionReplicaListener newReplicaListener(
                    MvPartitionStorage mvDataStorage,
                    RaftGroupService raftClient,
                    TxManager txManager,
                    Executor scanRequestExecutor,
                    int partId,
                    int tableId,
                    Supplier<Map<Integer, IndexLocker>> indexesLockers,
                    Lazy<TableSchemaAwareIndexStorage> pkIndexStorage,
                    Supplier<Map<Integer, TableSchemaAwareIndexStorage>> secondaryIndexStorages,
                    HybridClock hybridClock,
                    PendingComparableValuesTracker<HybridTimestamp, Void> safeTime,
                    TxStateStorage txStateStorage,
                    TransactionStateResolver transactionStateResolver,
                    StorageUpdateHandler storageUpdateHandler,
                    Schemas schemas,
                    ClusterNode localNode,
                    SchemaSyncService schemaSyncService,
                    CatalogService catalogService,
                    PlacementDriver placementDriver
            ) {
                return new PartitionReplicaListener(
                        mvDataStorage,
                        raftClient,
                        txManager,
                        txManager.lockManager(),
                        Runnable::run,
                        partId,
                        tableId,
                        indexesLockers,
                        pkIndexStorage,
                        secondaryIndexStorages,
                        hybridClock,
                        safeTime,
                        txStateStorage,
                        transactionStateResolver,
                        storageUpdateHandler,
                        schemas,
                        localNode,
                        schemaSyncService,
                        catalogService,
                        placementDriver
                ) {
                    @Override
                    public CompletableFuture<ReplicaResult> invoke(ReplicaRequest request, String senderId) {
                        if (request instanceof TxCleanupReplicaRequest) {
                            logger().info("Dropping cleanup request: {}", request);

                            releaseTxLocks(
                                    ((TxCleanupReplicaRequest) request).txId(),
                                    txManager.lockManager()
                            );

                            return completedFuture(new ReplicaResult(null, null));
                        }
                        return super.invoke(request, senderId);
                    }
                };
            }
        };

        txTestCluster.prepareCluster();

        this.igniteTransactions = txTestCluster.igniteTransactions;

        accounts = txTestCluster.startTable(ACC_TABLE_NAME, ACC_TABLE_ID, ACCOUNTS_SCHEMA);
        customers = txTestCluster.startTable(CUST_TABLE_NAME, CUST_TABLE_ID, CUSTOMERS_SCHEMA);

        log.info("Tables have been started");
    }

    @Disabled("IGNITE-20560")
    @Test
    @Override
    public void testTransactionAlreadyRolledback() {
        super.testTransactionAlreadyRolledback();
    }

    @Disabled("IGNITE-20560")
    @Test
    @Override
    public void testTransactionAlreadyCommitted() {
        super.testTransactionAlreadyCommitted();
    }

    @Disabled("IGNITE-20395")
    @Test
    @Override
    public void testBalance() throws InterruptedException {
        super.testBalance();
    }

    @Disabled("IGNITE-20395")
    @Test
    public void testTwoReadWriteTransactions() throws TransactionException {
        Tuple key = makeKey(1);

        assertFalse(accounts.recordView().delete(null, key));
        assertNull(accounts.recordView().get(null, key));

        // Disable background cleanup to avoid a race.
        ignoreAsyncCleanup = true;

        InternalTransaction tx1 = (InternalTransaction) igniteTransactions.begin();
        accounts.recordView().upsert(tx1, makeValue(1, 100.));
        tx1.commit();

        InternalTransaction tx2 = (InternalTransaction) igniteTransactions.begin();
        accounts.recordView().upsert(tx2, makeValue(1, 200.));
        tx2.commit();

        assertEquals(200., accounts.recordView().get(null, makeKey(1)).doubleValue("balance"));
    }

    @Test
    public void testTwoReadWriteTransactionsWaitForCleanup() throws TransactionException {
        Tuple key = makeKey(1);

        assertFalse(accounts.recordView().delete(null, key));
        assertNull(accounts.recordView().get(null, key));

        // Start the first transaction. The values it changes will not be cleaned up.
        InternalTransaction tx1 = (InternalTransaction) igniteTransactions.begin();

        accounts.recordView().upsert(tx1, makeValue(1, 100.));

        tx1.commit();

        //Now start the seconds transaction and make sure write intent resolution is called  by adding a `get` operaiton.
        InternalTransaction tx2 = (InternalTransaction) igniteTransactions.begin();

        assertEquals(100., accounts.recordView().get(tx2, makeKey(1)).doubleValue("balance"));

        // Now wait for the background task to finish.
        cleanupFutures.forEach(completableFuture -> assertThat(completableFuture, willCompleteSuccessfully()));

        accounts.recordView().upsert(tx2, makeValue(1, 200.));

        tx2.commit();

        assertEquals(200., accounts.recordView().get(null, makeKey(1)).doubleValue("balance"));
    }

    private static void releaseTxLocks(UUID txId, LockManager lockManager) {
        lockManager.locks(txId).forEachRemaining(lockManager::release);
    }
}
