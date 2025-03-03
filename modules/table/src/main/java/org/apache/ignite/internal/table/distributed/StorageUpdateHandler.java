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

import static org.apache.ignite.internal.util.CollectionUtils.nullOrEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.ignite.internal.hlc.HybridTimestamp;
import org.apache.ignite.internal.replicator.TablePartitionId;
import org.apache.ignite.internal.schema.BinaryRow;
import org.apache.ignite.internal.schema.configuration.GcConfiguration;
import org.apache.ignite.internal.storage.ReadResult;
import org.apache.ignite.internal.storage.RowId;
import org.apache.ignite.internal.table.distributed.gc.GcUpdateHandler;
import org.apache.ignite.internal.table.distributed.index.IndexUpdateHandler;
import org.apache.ignite.internal.table.distributed.raft.PartitionDataStorage;
import org.apache.ignite.internal.table.distributed.replication.request.BinaryRowMessage;
import org.apache.ignite.internal.table.distributed.replicator.PendingRows;
import org.apache.ignite.internal.util.Cursor;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for storage updates that can be performed on processing of primary replica requests and partition replication requests.
 */
public class StorageUpdateHandler {
    /** Partition id. */
    private final int partitionId;

    /** Partition storage with access to MV data of a partition. */
    private final PartitionDataStorage storage;

    /** Garbage collector configuration. */
    private final GcConfiguration gcConfig;

    /** Low watermark. */
    private final LowWatermark lowWatermark;

    /** Partition index update handler. */
    private final IndexUpdateHandler indexUpdateHandler;

    /** Partition gc update handler. */
    private final GcUpdateHandler gcUpdateHandler;

    /** A container for rows that were inserted, updated or removed. */
    private final PendingRows pendingRows = new PendingRows();

    /**
     * The constructor.
     *
     * @param partitionId Partition id.
     * @param storage Partition data storage.
     * @param gcConfig Garbage collector configuration.
     * @param indexUpdateHandler Partition index update handler.
     * @param gcUpdateHandler Partition gc update handler.
     */
    public StorageUpdateHandler(
            int partitionId,
            PartitionDataStorage storage,
            GcConfiguration gcConfig,
            LowWatermark lowWatermark,
            IndexUpdateHandler indexUpdateHandler,
            GcUpdateHandler gcUpdateHandler
    ) {
        this.partitionId = partitionId;
        this.storage = storage;
        this.gcConfig = gcConfig;
        this.lowWatermark = lowWatermark;
        this.indexUpdateHandler = indexUpdateHandler;
        this.gcUpdateHandler = gcUpdateHandler;
    }

    /**
     * Returns partition ID of the storage.
     */
    public int partitionId() {
        return partitionId;
    }

    /**
     * Handles single update.
     *
     * @param txId Transaction id.
     * @param rowUuid Row UUID.
     * @param commitPartitionId Commit partition id.
     * @param row Row.
     * @param trackWriteIntent If {@code true} then write intent should be tracked.
     * @param onApplication Callback on application.
     * @param commitTs Commit timestamp to use on autocommit.
     * @param lastCommitTs The timestamp of last known committed entry.
     */
    public void handleUpdate(
            UUID txId,
            UUID rowUuid,
            TablePartitionId commitPartitionId,
            @Nullable BinaryRow row,
            boolean trackWriteIntent,
            @Nullable Runnable onApplication,
            @Nullable HybridTimestamp commitTs,
            @Nullable HybridTimestamp lastCommitTs
    ) {
        indexUpdateHandler.waitIndexes();

        storage.runConsistently(locker -> {
            RowId rowId = new RowId(partitionId, rowUuid);
            int commitTblId = commitPartitionId.tableId();
            int commitPartId = commitPartitionId.partitionId();

            locker.lock(rowId);

            performStorageCleanupIfNeeded(txId, rowId, lastCommitTs);

            if (commitTs != null) {
                storage.addWriteCommitted(rowId, row, commitTs);
            } else {
                BinaryRow oldRow = storage.addWrite(rowId, row, txId, commitTblId, commitPartId);

                if (oldRow != null) {
                    assert commitTs == null : String.format("Expecting explicit txn: [txId=%s]", txId);
                    // Previous uncommitted row should be removed from indexes.
                    tryRemovePreviousWritesIndex(rowId, oldRow);
                }
            }

            indexUpdateHandler.addToIndexes(row, rowId);

            if (trackWriteIntent) {
                pendingRows.addPendingRowId(txId, rowId);
            }

            if (onApplication != null) {
                onApplication.run();
            }

            return null;
        });

        executeBatchGc();
    }

    /**
     * Handle multiple updates.
     *
     * @param txId Transaction id.
     * @param rowsToUpdate Collection of rows to update.
     * @param commitPartitionId Commit partition id.
     * @param trackWriteIntent If {@code true} then write intent should be tracked.
     * @param onApplication Callback on application.
     * @param commitTs Commit timestamp to use on autocommit.
     * @param lastCommitTsMap A map(Row Id -> timestamp) of timestamps of the most recent commits to the affected rows.
     */
    public void handleUpdateAll(
            UUID txId,
            Map<UUID, BinaryRowMessage> rowsToUpdate,
            TablePartitionId commitPartitionId,
            boolean trackWriteIntent,
            @Nullable Runnable onApplication,
            @Nullable HybridTimestamp commitTs,
            Map<UUID, HybridTimestamp> lastCommitTsMap
    ) {
        indexUpdateHandler.waitIndexes();

        storage.runConsistently(locker -> {
            int commitTblId = commitPartitionId.tableId();
            int commitPartId = commitPartitionId.partitionId();

            if (!nullOrEmpty(rowsToUpdate)) {
                List<RowId> rowIds = new ArrayList<>();

                // Sort IDs to prevent deadlock. Natural UUID order matches RowId order within the same partition.
                SortedMap<UUID, BinaryRowMessage> sortedRowsToUpdateMap = new TreeMap<>(rowsToUpdate);

                for (Map.Entry<UUID, BinaryRowMessage> entry : sortedRowsToUpdateMap.entrySet()) {
                    RowId rowId = new RowId(partitionId, entry.getKey());
                    BinaryRow row = entry.getValue() == null ? null : entry.getValue().asBinaryRow();

                    locker.lock(rowId);

                    performStorageCleanupIfNeeded(txId, rowId, lastCommitTsMap.get(entry.getKey()));

                    if (commitTs != null) {
                        storage.addWriteCommitted(rowId, row, commitTs);
                    } else {
                        BinaryRow oldRow = storage.addWrite(rowId, row, txId, commitTblId, commitPartId);

                        if (oldRow != null) {
                            assert commitTs == null : String.format("Expecting explicit txn: [txId=%s]", txId);
                            // Previous uncommitted row should be removed from indexes.
                            tryRemovePreviousWritesIndex(rowId, oldRow);
                        }
                    }

                    rowIds.add(rowId);
                    indexUpdateHandler.addToIndexes(row, rowId);
                }

                if (trackWriteIntent) {
                    pendingRows.addPendingRowIds(txId, rowIds);
                }

                if (onApplication != null) {
                    onApplication.run();
                }
            }

            return null;
        });

        executeBatchGc();
    }

    private void performStorageCleanupIfNeeded(UUID txId, RowId rowId, @Nullable HybridTimestamp lastCommitTs) {
        // No previously committed value, this action might be an insert. No need to cleanup.
        if (lastCommitTs == null) {
            return;
        }

        try (Cursor<ReadResult> cursor = storage.scanVersions(rowId)) {
            // Okay, lastCommitTs is not null. It means that we are changing the previously committed data.
            // However, we could have previously called cleanup for the same row.
            // If the previous operation was "delete" and it was executed successfully, no data will be present in the storage.
            if (!cursor.hasNext()) {
                return;
            }

            ReadResult item = cursor.next();
            // If there is a write intent in the storage and this intent was created by a different transaction
            // then check the previous entry.
            // Otherwise exit the check - everything's fine.
            if (item.isWriteIntent() && !txId.equals(item.transactionId())) {
                if (!cursor.hasNext()) {
                    // No more data => the write intent we have is actually the first version of this row
                    // and lastCommitTs is the commit timestamp of it.
                    // Action: commit this write intent.
                    performCommitWrite(item.transactionId(), Set.of(rowId), lastCommitTs);
                    return;
                }
                // Otherwise there are other versions in the chain.
                ReadResult committedItem = cursor.next();

                // They should be regular entries, not write intents.
                assert !committedItem.isWriteIntent() : "Cannot have more than one write intent per row";

                assert lastCommitTs.compareTo(committedItem.commitTimestamp()) >= 0 :
                        "Primary commit timestamp " + lastCommitTs + " is earlier than local commit timestamp "
                                + committedItem.commitTimestamp();

                if (lastCommitTs.compareTo(committedItem.commitTimestamp()) > 0) {
                    // We see that lastCommitTs is later than the timestamp of the committed value => we need to commit the write intent.
                    // Action: commit this write intent.
                    performCommitWrite(item.transactionId(), Set.of(rowId), lastCommitTs);
                } else {
                    // lastCommitTs == committedItem.commitTimestamp()
                    // So we see a write intent from a different transaction, which was not committed on primary.
                    // Because of transaction locks we cannot have two transactions creating write intents for the same row.
                    // So if we got up to here, it means that the previous transaction was aborted,
                    // but the storage was not cleaned after it.
                    // Action: abort this write intent.
                    performAbortWrite(item.transactionId(), Set.of(rowId));
                }
            }
        }
    }

    void executeBatchGc() {
        HybridTimestamp lwm = lowWatermark.getLowWatermark();

        if (lwm == null || gcUpdateHandler.getSafeTimeTracker().current().compareTo(lwm) < 0) {
            return;
        }

        gcUpdateHandler.vacuumBatch(lwm, gcConfig.onUpdateBatchSize().value(), false);
    }

    /**
     * Tries to remove a previous write from index.
     *
     * @param rowId Row id.
     * @param previousRow Previous write value.
     */
    private void tryRemovePreviousWritesIndex(RowId rowId, BinaryRow previousRow) {
        try (Cursor<ReadResult> cursor = storage.scanVersions(rowId)) {
            if (!cursor.hasNext()) {
                return;
            }

            indexUpdateHandler.tryRemoveFromIndexes(previousRow, rowId, cursor);
        }
    }

    /**
     * Handles the read of a write-intent.
     *
     * @param txId Transaction id.
     * @param rowId Row id.
     */
    public void handleWriteIntentRead(UUID txId, RowId rowId) {
        pendingRows.addPendingRowId(txId, rowId);
    }

    /**
     * Handles the cleanup of a transaction. The transaction is either committed or rolled back.
     *
     * @param txId Transaction id.
     * @param commit Commit flag. {@code true} if transaction is committed, {@code false} otherwise.
     * @param commitTimestamp Commit timestamp. Not {@code null} if {@code commit} is {@code true}.
     */
    public void handleTransactionCleanup(UUID txId, boolean commit, @Nullable HybridTimestamp commitTimestamp) {
        handleTransactionCleanup(txId, commit, commitTimestamp, null);
    }

    /**
     * Handles the cleanup of a transaction. The transaction is either committed or rolled back.
     *
     * @param txId Transaction id.
     * @param commit Commit flag. {@code true} if transaction is committed, {@code false} otherwise.
     * @param commitTimestamp Commit timestamp. Not {@code null} if {@code commit} is {@code true}.
     * @param onApplication On application callback.
     */
    public void handleTransactionCleanup(
            UUID txId,
            boolean commit,
            @Nullable HybridTimestamp commitTimestamp,
            @Nullable Runnable onApplication) {
        Set<RowId> pendingRowIds = pendingRows.removePendingRowIds(txId);

        // `pendingRowIds` might be empty when we have already cleaned up the storage for this transaction,
        // for example, when primary (PartitionReplicaListener) is collocated with the raft node (PartitionListener)
        // and one of them has already processed the cleanup request, since they share the instance of this class.
        // Or the cleanup might have been done asynchronously.
        // However, we still need to run `onApplication` if it is not null, e.g. called in TxCleanupCommand handler in PartitionListener
        // to update indexes. In this case it should be executed under `runConsistently`.
        if (!pendingRowIds.isEmpty() || onApplication != null) {
            storage.runConsistently(locker -> {
                pendingRowIds.forEach(locker::lock);

                if (commit) {
                    performCommitWrite(txId, pendingRowIds, commitTimestamp);
                } else {
                    performAbortWrite(txId, pendingRowIds);
                }

                if (onApplication != null) {
                    onApplication.run();
                }

                return null;
            });
        }
    }

    /**
     * Commit write intents created by the provided transaction.
     *
     * @param txId Transaction id
     * @param pendingRowIds Row ids of write-intents to be committed.
     * @param commitTimestamp Commit timestamp.
     */
    private void performCommitWrite(UUID txId, Set<RowId> pendingRowIds, HybridTimestamp commitTimestamp) {
        assert commitTimestamp != null : "Commit timestamp is null";

        // Please note: `pendingRowIds` might not contain the complete set of rows that were changed by this transaction:
        // Pending rows are stored in memory and will be lost in case a node restarts.
        // This method might be called by a write intent resolving transaction that will find only those rows that it needs itself.
        List<RowId> rowIds = new ArrayList<>();

        for (RowId pendingRowId : pendingRowIds) {

            // Here we check that the write intent we are going to commit still belongs to the provided transaction.
            //
            // This check is required to cover the following case caused by asynchronous cleanup of write intents:
            // 1. RO Transaction A sees a write intent for a row1, resolves it and schedules a cleanup for it.
            // 2. RW Transaction B sees the same write intent for a row1, resolves it and schedules a cleanup for it.
            // This cleanup action finishes first. Then Transaction B adds its own write intent for the row1.
            // 3. Transaction A starts executing the cleanup action.
            // Without this check it would commit the write intent from a different transaction.
            //
            // This is just a workaround. The proper fix is to check the transaction id for the row in the storage.
            // TODO: https://issues.apache.org/jira/browse/IGNITE-20347 to check transaction id in the storage
            ReadResult result = storage.getStorage().read(pendingRowId, HybridTimestamp.MAX_VALUE);
            if (result.isWriteIntent() && txId.equals(result.transactionId())) {
                // In case of an asynchronous cleanup of write intents, we might get into a situation when some of the
                // write intents were already cleaned up. In this case, we just ignore them.
                rowIds.add(pendingRowId);
            }
        }

        rowIds.forEach(rowId -> storage.commitWrite(rowId, commitTimestamp));
    }

    /**
     * Abort write intents created by the provided transaction.
     *
     * @param txId Transaction id
     * @param pendingRowIds Row ids of write-intents to be aborted.
     */
    private void performAbortWrite(UUID txId, Set<RowId> pendingRowIds) {
        List<RowId> rowIds = new ArrayList<>();

        for (RowId rowId : pendingRowIds) {
            try (Cursor<ReadResult> cursor = storage.scanVersions(rowId)) {
                if (!cursor.hasNext()) {
                    continue;
                }

                ReadResult item = cursor.next();

                // TODO: https://issues.apache.org/jira/browse/IGNITE-20124 Prevent double storage updates within primary
                if (item.isWriteIntent()) {
                    // We are aborting only those write intents that belong to the provided transaction.
                    // TODO: https://issues.apache.org/jira/browse/IGNITE-20347 to check transaction id in the storage
                    if (!txId.equals(item.transactionId())) {
                        continue;
                    }
                    rowIds.add(rowId);

                    BinaryRow rowToRemove = item.binaryRow();

                    if (rowToRemove == null) {
                        continue;
                    }

                    indexUpdateHandler.tryRemoveFromIndexes(rowToRemove, rowId, cursor);
                }
            }
        }

        rowIds.forEach(storage::abortWrite);
    }

    /**
     * Returns partition index update handler.
     */
    public IndexUpdateHandler getIndexUpdateHandler() {
        return indexUpdateHandler;
    }
}
