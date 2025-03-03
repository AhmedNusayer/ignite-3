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

package org.apache.ignite.internal.table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;
import org.apache.ignite.internal.schema.BinaryRow;
import org.apache.ignite.internal.schema.BinaryRowEx;
import org.apache.ignite.internal.schema.SchemaRegistry;
import org.apache.ignite.internal.schema.marshaller.TupleMarshaller;
import org.apache.ignite.internal.schema.marshaller.TupleMarshallerException;
import org.apache.ignite.internal.schema.row.Row;
import org.apache.ignite.internal.streamer.StreamerBatchSender;
import org.apache.ignite.internal.table.distributed.schema.SchemaVersions;
import org.apache.ignite.internal.tx.InternalTransaction;
import org.apache.ignite.lang.IgniteException;
import org.apache.ignite.lang.MarshallerException;
import org.apache.ignite.table.DataStreamerOptions;
import org.apache.ignite.table.RecordView;
import org.apache.ignite.table.Tuple;
import org.apache.ignite.tx.Transaction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Table view implementation for binary objects.
 */
public class RecordBinaryViewImpl extends AbstractTableView implements RecordView<Tuple> {
    private final TupleMarshallerCache marshallerCache;

    /**
     * Constructor.
     *
     * @param tbl The table.
     * @param schemaRegistry Table schema registry.
     * @param schemaVersions Schema versions access.
     */
    public RecordBinaryViewImpl(InternalTable tbl, SchemaRegistry schemaRegistry, SchemaVersions schemaVersions) {
        super(tbl, schemaVersions, schemaRegistry);

        marshallerCache = new TupleMarshallerCache(schemaRegistry);
    }

    /** {@inheritDoc} */
    @Override
    public Tuple get(@Nullable Transaction tx, Tuple keyRec) {
        return sync(getAsync(tx, keyRec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Tuple> getAsync(@Nullable Transaction tx, Tuple keyRec) {
        Objects.requireNonNull(keyRec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row keyRow = marshal(keyRec, schemaVersion, true); // Convert to portable format to pass TX/storage layer.

            return tbl.get(keyRow, (InternalTransaction) tx).thenApply(row -> wrap(row, schemaVersion));
        });
    }

    /**
     * Obtains a marshaller corresponding to the given schema version.
     *
     * @param schemaVersion Schema version for which to obtain a marshaller.
     */
    @TestOnly
    public TupleMarshaller marshaller(int schemaVersion) {
        return marshallerCache.marshaller(schemaVersion);
    }

    @Override
    public List<Tuple> getAll(@Nullable Transaction tx, Collection<Tuple> keyRecs) {
        return sync(getAllAsync(tx, keyRecs));
    }

    @Override
    public CompletableFuture<List<Tuple>> getAllAsync(@Nullable Transaction tx, Collection<Tuple> keyRecs) {
        Objects.requireNonNull(keyRecs);

        return withSchemaSync(tx, (schemaVersion) -> {
            return tbl.getAll(mapToBinary(keyRecs, schemaVersion, true), (InternalTransaction) tx)
                    .thenApply(binaryRows -> wrap(binaryRows, schemaVersion, true));
        });
    }

    /** {@inheritDoc} */
    @Override
    public void upsert(@Nullable Transaction tx, Tuple rec) {
        sync(upsertAsync(tx, rec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Void> upsertAsync(@Nullable Transaction tx, Tuple rec) {
        Objects.requireNonNull(rec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row row = marshal(rec, schemaVersion, false);

            return tbl.upsert(row, (InternalTransaction) tx);
        });
    }

    /** {@inheritDoc} */
    @Override
    public void upsertAll(@Nullable Transaction tx, Collection<Tuple> recs) {
        sync(upsertAllAsync(tx, recs));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Void> upsertAllAsync(@Nullable Transaction tx, Collection<Tuple> recs) {
        Objects.requireNonNull(recs);

        return withSchemaSync(tx, (schemaVersion) -> {
            return tbl.upsertAll(mapToBinary(recs, schemaVersion, false), (InternalTransaction) tx);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Tuple getAndUpsert(@Nullable Transaction tx, Tuple rec) {
        return sync(getAndUpsertAsync(tx, rec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Tuple> getAndUpsertAsync(@Nullable Transaction tx, Tuple rec) {
        Objects.requireNonNull(rec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row row = marshal(rec, schemaVersion, false);

            return tbl.getAndUpsert(row, (InternalTransaction) tx).thenApply(resultRow -> wrap(resultRow, schemaVersion));
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean insert(@Nullable Transaction tx, Tuple rec) {
        return sync(insertAsync(tx, rec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> insertAsync(@Nullable Transaction tx, Tuple rec) {
        Objects.requireNonNull(rec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row row = marshal(rec, schemaVersion, false);

            return tbl.insert(row, (InternalTransaction) tx);
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<Tuple> insertAll(@Nullable Transaction tx, Collection<Tuple> recs) {
        return sync(insertAllAsync(tx, recs));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<List<Tuple>> insertAllAsync(@Nullable Transaction tx, Collection<Tuple> recs) {
        Objects.requireNonNull(recs);

        return withSchemaSync(tx, (schemaVersion) -> {
            return tbl.insertAll(mapToBinary(recs, schemaVersion, false), (InternalTransaction) tx)
                    .thenApply(rows -> wrap(rows, schemaVersion, false));
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean replace(@Nullable Transaction tx, Tuple rec) {
        return sync(replaceAsync(tx, rec));
    }

    /** {@inheritDoc} */
    @Override
    public boolean replace(@Nullable Transaction tx, Tuple oldRec, Tuple newRec) {
        return sync(replaceAsync(tx, oldRec, newRec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> replaceAsync(@Nullable Transaction tx, Tuple rec) {
        Objects.requireNonNull(rec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row row = marshal(rec, schemaVersion, false);

            return tbl.replace(row, (InternalTransaction) tx);
        });
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> replaceAsync(@Nullable Transaction tx, Tuple oldRec, Tuple newRec) {
        Objects.requireNonNull(oldRec);
        Objects.requireNonNull(newRec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row oldRow = marshal(oldRec, schemaVersion, false);
            Row newRow = marshal(newRec, schemaVersion, false);

            return tbl.replace(oldRow, newRow, (InternalTransaction) tx);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Tuple getAndReplace(@Nullable Transaction tx, Tuple rec) {
        return sync(getAndReplaceAsync(tx, rec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Tuple> getAndReplaceAsync(@Nullable Transaction tx, Tuple rec) {
        Objects.requireNonNull(rec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row row = marshal(rec, schemaVersion, false);

            return tbl.getAndReplace(row, (InternalTransaction) tx).thenApply(resultRow -> wrap(resultRow, schemaVersion));
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean delete(@Nullable Transaction tx, Tuple keyRec) {
        return sync(deleteAsync(tx, keyRec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> deleteAsync(@Nullable Transaction tx, Tuple keyRec) {
        Objects.requireNonNull(keyRec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row keyRow = marshal(keyRec, schemaVersion, true);

            return tbl.delete(keyRow, (InternalTransaction) tx);
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean deleteExact(@Nullable Transaction tx, Tuple rec) {
        return sync(deleteExactAsync(tx, rec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> deleteExactAsync(@Nullable Transaction tx, Tuple rec) {
        Objects.requireNonNull(rec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row row = marshal(rec, schemaVersion, false);

            return tbl.deleteExact(row, (InternalTransaction) tx);
        });
    }

    /** {@inheritDoc} */
    @Override
    public Tuple getAndDelete(@Nullable Transaction tx, Tuple keyRec) {
        return sync(getAndDeleteAsync(tx, keyRec));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Tuple> getAndDeleteAsync(@Nullable Transaction tx, Tuple keyRec) {
        Objects.requireNonNull(keyRec);

        return withSchemaSync(tx, (schemaVersion) -> {
            Row keyRow = marshal(keyRec, schemaVersion, true);

            return tbl.getAndDelete(keyRow, (InternalTransaction) tx).thenApply(row -> wrap(row, schemaVersion));
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<Tuple> deleteAll(@Nullable Transaction tx, Collection<Tuple> keyRecs) {
        return sync(deleteAllAsync(tx, keyRecs));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<List<Tuple>> deleteAllAsync(@Nullable Transaction tx, Collection<Tuple> keyRecs) {
        Objects.requireNonNull(keyRecs);

        return withSchemaSync(tx, (schemaVersion) -> {
            return tbl.deleteAll(mapToBinary(keyRecs, schemaVersion, true), (InternalTransaction) tx)
                    .thenApply(rows -> wrapKeys(rows, schemaVersion));
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<Tuple> deleteAllExact(@Nullable Transaction tx, Collection<Tuple> recs) {
        return sync(deleteAllExactAsync(tx, recs));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<List<Tuple>> deleteAllExactAsync(@Nullable Transaction tx, Collection<Tuple> recs) {
        Objects.requireNonNull(recs);

        return withSchemaSync(tx, (schemaVersion) -> {
            return tbl.deleteAllExact(mapToBinary(recs, schemaVersion, false), (InternalTransaction) tx)
                    .thenApply(rows -> wrap(rows, schemaVersion, false));
        });
    }

    /**
     * Marshal a tuple to a row.
     *
     * @param tuple The tuple.
     * @param schemaVersion Schema version in which to marshal.
     * @param keyOnly Marshal key part only if {@code true}, otherwise marshal both, key and value parts.
     * @return Row.
     * @throws IgniteException If failed to marshal tuple.
     */
    private Row marshal(Tuple tuple, int schemaVersion, boolean keyOnly) throws IgniteException {
        TupleMarshaller marshaller = marshaller(schemaVersion);

        try {
            if (keyOnly) {
                return marshaller.marshalKey(tuple);
            } else {
                return marshaller.marshal(tuple);
            }
        } catch (TupleMarshallerException ex) {
            throw new MarshallerException(ex);
        }
    }

    /**
     * Returns table row tuple.
     *
     * @param row Binary row.
     */
    private @Nullable Tuple wrap(@Nullable BinaryRow row, int targetSchemaVersion) {
        return row == null ? null : TableRow.tuple(rowConverter.resolveRow(row, targetSchemaVersion));
    }

    /**
     * Returns table rows.
     *
     * @param rows Binary rows.
     * @param addNull {@code true} if {@code null} is added for missing rows.
     */
    private List<Tuple> wrap(Collection<BinaryRow> rows, int targetSchemaVersion, boolean addNull) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        var wrapped = new ArrayList<Tuple>(rows.size());

        for (Row row : rowConverter.resolveRows(rows, targetSchemaVersion)) {
            if (row != null) {
                wrapped.add(TableRow.tuple(row));
            } else if (addNull) {
                wrapped.add(null);
            }
        }

        return wrapped;
    }

    private List<Tuple> wrapKeys(Collection<BinaryRow> rows, int targetSchemaVersion) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        var wrapped = new ArrayList<Tuple>(rows.size());

        for (Row row : rowConverter.resolveKeys(rows, targetSchemaVersion)) {
            if (row != null) {
                wrapped.add(TableRow.tuple(row));
            }
        }

        return wrapped;
    }

    /**
     * Maps a collection of tuples to binary rows.
     *
     * @param rows Tuples.
     * @param schemaVersion Schema version in which to marshal.
     * @param key  {@code true} to marshal only a key.
     * @return List of binary rows.
     */
    private Collection<BinaryRowEx> mapToBinary(Collection<Tuple> rows, int schemaVersion, boolean key) {
        Collection<BinaryRowEx> mapped = new ArrayList<>(rows.size());

        for (Tuple row : rows) {
            mapped.add(marshal(row, schemaVersion, key));
        }

        return mapped;
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Void> streamData(Publisher<Tuple> publisher, @Nullable DataStreamerOptions options) {
        Objects.requireNonNull(publisher);

        var partitioner = new TupleStreamerPartitionAwarenessProvider(rowConverter.registry(), tbl.partitions());
        StreamerBatchSender<Tuple, Integer> batchSender = (partitionId, items) -> withSchemaSync(null, (schemaVersion) -> {
            return this.tbl.upsertAll(mapToBinary(items, schemaVersion, false), partitionId);
        });

        return DataStreamer.streamData(publisher, options, batchSender, partitioner);
    }
}
