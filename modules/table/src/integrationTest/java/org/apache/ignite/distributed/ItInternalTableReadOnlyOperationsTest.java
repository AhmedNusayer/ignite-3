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

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static org.apache.ignite.internal.testframework.matchers.CompletableFutureMatcher.willBe;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.apache.ignite.internal.hlc.HybridClock;
import org.apache.ignite.internal.hlc.HybridClockImpl;
import org.apache.ignite.internal.hlc.HybridTimestamp;
import org.apache.ignite.internal.replicator.ReplicaService;
import org.apache.ignite.internal.schema.BinaryRow;
import org.apache.ignite.internal.schema.BinaryRowConverter;
import org.apache.ignite.internal.schema.Column;
import org.apache.ignite.internal.schema.ColumnsExtractor;
import org.apache.ignite.internal.schema.SchemaDescriptor;
import org.apache.ignite.internal.schema.row.Row;
import org.apache.ignite.internal.schema.row.RowAssembler;
import org.apache.ignite.internal.storage.MvPartitionStorage;
import org.apache.ignite.internal.storage.PartitionTimestampCursor;
import org.apache.ignite.internal.table.InternalTable;
import org.apache.ignite.internal.table.distributed.replication.request.ReadOnlyMultiRowPkReplicaRequest;
import org.apache.ignite.internal.table.distributed.replication.request.ReadOnlySingleRowPkReplicaRequest;
import org.apache.ignite.internal.table.impl.DummyInternalTableImpl;
import org.apache.ignite.internal.testframework.IgniteAbstractTest;
import org.apache.ignite.internal.tx.InternalTransaction;
import org.apache.ignite.internal.type.NativeTypes;
import org.apache.ignite.network.ClusterNode;
import org.apache.ignite.tx.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link InternalTable} read-only operations.
 */
@ExtendWith(MockitoExtension.class)
public class ItInternalTableReadOnlyOperationsTest extends IgniteAbstractTest {
    private static final SchemaDescriptor SCHEMA = new SchemaDescriptor(
            1,
            new Column[]{new Column("key", NativeTypes.INT64, false)},
            new Column[]{new Column("value", NativeTypes.INT64, false)}
    );

    private static final HybridClock CLOCK = new HybridClockImpl();

    private static final Row ROW_1 = createKeyValueRow(1, 1001);

    private static final Row ROW_2 = createKeyValueRow(2, 1002);

    private static final ColumnsExtractor KEY_EXTRACTOR = BinaryRowConverter.keyExtractor(SCHEMA);

    /** Mock partition storage. */
    @Mock
    private MvPartitionStorage mockStorage;

    /** Transaction mock. */
    @Mock
    private InternalTransaction readOnlyTx;

    @Mock
    private ReplicaService replicaService;

    /** Internal table to test. */
    private InternalTable internalTbl;

    /**
     * Prepare test environment using DummyInternalTableImpl and Mocked storage.
     */
    @BeforeEach
    public void setUp(TestInfo testInfo) {
        when(mockStorage.scan(any(HybridTimestamp.class))).thenReturn(mock(PartitionTimestampCursor.class));

        internalTbl = new DummyInternalTableImpl(replicaService, mockStorage, SCHEMA);

        lenient().when(readOnlyTx.isReadOnly()).thenReturn(true);
        lenient().when(readOnlyTx.readTimestamp()).thenReturn(CLOCK.now());
    }

    @Test
    public void testReadOnlyGetNonExistingKeyWithReadTimestamp() {
        mockReadOnlySingleRowRequest();

        assertNull(internalTbl.get(createKeyRow(0), CLOCK.now(), mock(ClusterNode.class)).join());
    }

    @Test
    public void testReadOnlyGetNonExistingKeyWithTx() {
        mockReadOnlySingleRowRequest();

        assertNull(internalTbl.get(createKeyRow(0), readOnlyTx).join());
    }

    @Test
    public void testReadOnlyGetExistingKeyWithReadTimestamp() {
        mockReadOnlySingleRowRequest();

        assertEquals(ROW_2, internalTbl.get(createKeyRow(2), CLOCK.now(), mock(ClusterNode.class)).join());
    }

    @Test
    public void testReadOnlyGetExistingKeyWithTx() {
        mockReadOnlySingleRowRequest();

        assertEquals(ROW_2, internalTbl.get(createKeyRow(2), readOnlyTx).join());
    }


    @Test
    public void testReadOnlyGetAllNonExistingKeysWithReadTimestamp() {
        mockReadOnlyMultiRowRequest();

        assertThat(
                internalTbl.getAll(List.of(createKeyRow(0)), CLOCK.now(), mock(ClusterNode.class)),
                willBe(contains(nullValue()))
        );
    }

    @Test
    public void testReadOnlyGetAllMixExistingKeysWithTx() {
        mockReadOnlyMultiRowRequest();

        assertThat(
                internalTbl.getAll(
                        List.of(createKeyRow(0), createKeyRow(1), createKeyRow(2), createKeyRow(3)),
                        readOnlyTx
                ),
                willBe(contains(nullValue(), equalTo(ROW_1), equalTo(ROW_2), nullValue()))
        );
    }

    @Test
    public void testReadOnlyGetAllMixExistingKeysWithReadTimestamp() {
        mockReadOnlyMultiRowRequest();

        assertThat(
                internalTbl.getAll(
                        List.of(createKeyRow(0), createKeyRow(1), createKeyRow(2), createKeyRow(3)),
                        CLOCK.now(),
                        mock(ClusterNode.class)
                ),
                willBe(contains(nullValue(), equalTo(ROW_1), equalTo(ROW_2), nullValue()))
        );
    }

    @Test
    public void testReadOnlyGetAllNonExistingKeysWithTx() {
        mockReadOnlyMultiRowRequest();

        assertThat(
                internalTbl.getAll(List.of(createKeyRow(0)), readOnlyTx),
                willBe(contains(nullValue()))
        );
    }

    @Test
    public void testReadOnlyGetAllPartiallyExistingKeysWithReadTimestamp() {
        mockReadOnlyMultiRowRequest();

        assertEquals(
                Collections.singletonList(ROW_2),
                internalTbl.getAll(Collections.singleton(createKeyRow(2)), CLOCK.now(), mock(ClusterNode.class)).join()
        );
    }

    @Test
    public void testReadOnlyGetAllPartiallyExistingKeysWithTx() {
        mockReadOnlyMultiRowRequest();

        assertEquals(
                Collections.singletonList(ROW_2),
                internalTbl.getAll(Collections.singleton(createKeyRow(2)), readOnlyTx).join()
        );
    }

    @Test
    public void testReadOnlyGetAllExistingKeysWithReadTimestamp() {
        mockReadOnlyMultiRowRequest();

        assertEquals(
                List.of(ROW_1, ROW_2),
                internalTbl.getAll(List.of(createKeyRow(1), createKeyRow(2)), CLOCK.now(), mock(ClusterNode.class)).join()
        );
    }

    @Test
    public void testReadOnlyGetAllExistingKeysWithTx() {
        mockReadOnlyMultiRowRequest();

        assertEquals(
                List.of(ROW_1, ROW_2),
                internalTbl.getAll(List.of(createKeyRow(1), createKeyRow(2)), readOnlyTx).join()
        );
    }

    @Test()
    public void testEnlistingReadWriteOperationIntoReadOnlyTransactionThrowsAnException() {
        InternalTransaction tx = mock(InternalTransaction.class);
        when(tx.isReadOnly()).thenReturn(true);

        List<Executable> executables = List.of(
                () -> internalTbl.delete(null, tx).get(),
                () -> internalTbl.deleteAll(null, tx).get(),
                () -> internalTbl.deleteExact(null, tx).get(),
                () -> internalTbl.deleteAllExact(null, tx).get(),
                () -> internalTbl.getAndDelete(null, tx).get(),
                () -> internalTbl.getAndReplace(null, tx).get(),
                () -> internalTbl.getAndUpsert(null, tx).get(),
                () -> internalTbl.upsert(null, tx).get(),
                () -> internalTbl.upsertAll(null, tx).get(),
                () -> internalTbl.insert(null, tx).get(),
                () -> internalTbl.insertAll(null, tx).get(),
                () -> internalTbl.replace(null, tx).get(),
                () -> internalTbl.replace(null, null, tx).get()
        );

        executables.forEach(executable -> {
            ExecutionException ex = assertThrows(ExecutionException.class, executable);

            assertThat(ex.getCause(), is(instanceOf(TransactionException.class)));
            assertThat(
                    ex.getCause().getMessage(),
                    containsString("Failed to enlist read-write operation into read-only transaction"));
        });

        TransactionException ex = assertThrows(TransactionException.class, () -> internalTbl.scan(0, tx));
        assertThat(
                ex.getMessage(),
                containsString("Failed to enlist read-write operation into read-only transaction"));
    }

    /**
     * Creates a {@link Row} with the supplied key.
     *
     * @param id Key.
     * @return Row.
     */
    private static Row createKeyRow(long id) {
        RowAssembler rowBuilder = RowAssembler.keyAssembler(SCHEMA);

        rowBuilder.appendLong(id);

        return Row.wrapKeyOnlyBinaryRow(SCHEMA, rowBuilder.build());
    }

    /**
     * Creates a {@link Row} with the supplied key and value.
     *
     * @param id    Key.
     * @param value Value.
     * @return Row.
     */
    private static Row createKeyValueRow(long id, long value) {
        RowAssembler rowBuilder = new RowAssembler(SCHEMA);

        rowBuilder.appendLong(id);
        rowBuilder.appendLong(value);

        return Row.wrapBinaryRow(SCHEMA, rowBuilder.build());
    }

    private void mockReadOnlyMultiRowRequest() {
        List<BinaryRow> rowStore = List.of(ROW_1, ROW_2);

        when(replicaService.invoke(any(ClusterNode.class), any(ReadOnlyMultiRowPkReplicaRequest.class))).thenAnswer(args -> {
            List<ByteBuffer> primaryKeys = args.getArgument(1, ReadOnlyMultiRowPkReplicaRequest.class).primaryKeys();

            return primaryKeys.stream()
                    .map(pk -> rowStore.stream().filter(row -> rowMatchesPk(row, pk)).findFirst().orElse(null))
                    .collect(collectingAndThen(toList(), CompletableFuture::completedFuture));
        });
    }

    private void mockReadOnlySingleRowRequest() {
        List<BinaryRow> rowStore = List.of(ROW_1, ROW_2);

        when(replicaService.invoke(any(ClusterNode.class), any(ReadOnlySingleRowPkReplicaRequest.class))).thenAnswer(args -> {
            ByteBuffer primaryKey = args.getArgument(1, ReadOnlySingleRowPkReplicaRequest.class).primaryKey();

            return CompletableFuture.completedFuture(rowStore.stream()
                    .filter(row -> rowMatchesPk(row, primaryKey))
                    .findFirst()
                    .orElse(null));
        });
    }

    private static boolean rowMatchesPk(BinaryRow row, ByteBuffer pk) {
        return KEY_EXTRACTOR.extractColumns(row).byteBuffer().equals(pk);
    }
}
