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

package org.apache.ignite.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.client.fakes.FakeCompute;
import org.apache.ignite.internal.testframework.IgniteTestUtils;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.Session;
import org.apache.ignite.sql.SqlRow;
import org.apache.ignite.sql.Statement;
import org.apache.ignite.tx.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests client handler metrics. See also {@code org.apache.ignite.client.handler.ItClientHandlerMetricsTest}.
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "rawtypes", "unchecked"})
public class ServerMetricsTest extends AbstractClientTest {
    @AfterEach
    public void resetCompute() {
        FakeCompute.future = null;
    }

    @BeforeEach
    public void enableMetrics() {
        testServer.metrics().enable();
    }

    @Test
    public void testTxMetrics() {
        assertEquals(0, testServer.metrics().transactionsActive());

        Transaction tx1 = client.transactions().begin();
        assertEquals(1, testServer.metrics().transactionsActive());

        Transaction tx2 = client.transactions().begin();
        assertEquals(2, testServer.metrics().transactionsActive());

        tx1.rollback();
        assertEquals(1, testServer.metrics().transactionsActive());

        tx2.rollback();
        assertEquals(0, testServer.metrics().transactionsActive());
    }

    @Test
    public void testSqlMetrics() {
        Statement statement = client.sql().statementBuilder()
                .property("hasMorePages", true)
                .query("select 1")
                .build();

        assertEquals(0, testServer.metrics().cursorsActive());

        try (Session session = client.sql().createSession()) {
            ResultSet<SqlRow> resultSet = session.execute(null, statement);
            assertEquals(1, testServer.metrics().cursorsActive());

            resultSet.close();
            assertEquals(0, testServer.metrics().cursorsActive());
        }
    }

    @Test
    public void testRequestsActive() throws Exception {
        assertEquals(0, testServer.metrics().requestsActive());

        CompletableFuture computeFut = new CompletableFuture();
        FakeCompute.future = computeFut;

        client.compute().executeAsync(getClusterNodes("s1"), List.of(), "job");
        client.compute().executeAsync(getClusterNodes("s1"), List.of(), "job");

        assertTrue(
                IgniteTestUtils.waitForCondition(() -> testServer.metrics().requestsActive() == 2, 1000),
                () -> "requestsActive: " + testServer.metrics().requestsActive());

        computeFut.complete("x");

        assertTrue(
                IgniteTestUtils.waitForCondition(() -> testServer.metrics().requestsActive() == 0, 1000),
                () -> "requestsActive: " + testServer.metrics().requestsActive());
    }

    @Test
    public void testRequestsProcessed() throws Exception {
        long processed = testServer.metrics().requestsProcessed();

        client.compute().executeAsync(getClusterNodes("s1"), List.of(), "job");

        assertTrue(
                IgniteTestUtils.waitForCondition(() -> testServer.metrics().requestsProcessed() == processed + 1, 1000),
                () -> "requestsProcessed: " + testServer.metrics().requestsProcessed());
    }

    @Test
    public void testRequestsFailed() throws Exception {
        assertEquals(0, testServer.metrics().requestsFailed());

        FakeCompute.future = CompletableFuture.failedFuture(new RuntimeException("test"));

        client.compute().executeAsync(getClusterNodes("s1"), List.of(), "job");

        assertTrue(
                IgniteTestUtils.waitForCondition(() -> testServer.metrics().requestsFailed() == 1, 1000),
                () -> "requestsFailed: " + testServer.metrics().requestsFailed());
    }

    @Test
    public void testMetricsDisabled() {
        testServer.metrics().disable();

        assertFalse(testServer.metrics().enabled());
        assertEquals(0, testServer.metrics().requestsProcessed());

        client.compute().executeAsync(getClusterNodes("s1"), List.of(), "job").join();

        assertEquals(0, testServer.metrics().requestsProcessed());
        assertFalse(testServer.metrics().enabled());
    }

    @Test
    public void testEnabledMetricsTwiceReturnsSameMetricSet() {
        var metrics = testServer.metrics();
        var set1 = metrics.enable();
        var set2 = metrics.enable();

        assertSame(set1, set2);
    }
}
