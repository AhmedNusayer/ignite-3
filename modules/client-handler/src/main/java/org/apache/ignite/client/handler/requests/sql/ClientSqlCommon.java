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

package org.apache.ignite.client.handler.requests.sql;

import java.util.List;
import org.apache.ignite.internal.binarytuple.BinaryTupleBuilder;
import org.apache.ignite.internal.client.proto.ClientMessagePacker;
import org.apache.ignite.sql.ColumnMetadata;
import org.apache.ignite.sql.ResultSetMetadata;
import org.apache.ignite.sql.SqlRow;
import org.apache.ignite.sql.async.AsyncResultSet;

/**
 * Common SQL request handling logic.
 */
class ClientSqlCommon {
    static void packCurrentPage(ClientMessagePacker out, AsyncResultSet<SqlRow> asyncResultSet) {
        ResultSetMetadata meta = asyncResultSet.metadata();
        assert meta != null : "Metadata can't be null when row set is present.";

        List<ColumnMetadata> cols = meta.columns();

        out.packInt(asyncResultSet.currentPageSize());

        for (SqlRow row : asyncResultSet.currentPage()) {
            // TODO IGNITE-18922 Avoid conversion, copy BinaryTuple from SQL to client.
            var builder = new BinaryTupleBuilder(row.columnCount());

            for (int i = 0; i < cols.size(); i++) {
                packValue(builder, cols.get(i), row, i);
            }

            out.packBinaryTuple(builder);
        }

        if (!asyncResultSet.hasMorePages()) {
            asyncResultSet.closeAsync();
        }
    }

    private static void packValue(BinaryTupleBuilder out, ColumnMetadata col, SqlRow row, int idx) {
        if (row.value(idx) == null) {
            out.appendNull();
            return;
        }

        switch (col.type()) {
            case BOOLEAN:
                out.appendByte((Boolean) row.value(idx) ? (byte) 1 : (byte) 0);
                break;

            case INT8:
                out.appendByte(row.byteValue(idx));
                break;

            case INT16:
                out.appendShort(row.shortValue(idx));
                break;

            case INT32:
                out.appendInt(row.intValue(idx));
                break;

            case INT64:
                out.appendLong(row.longValue(idx));
                break;

            case FLOAT:
                out.appendFloat(row.floatValue(idx));
                break;

            case DOUBLE:
                out.appendDouble(row.doubleValue(idx));
                break;

            case DECIMAL:
                out.appendDecimal(row.value(idx), col.scale());
                break;

            case DATE:
                out.appendDate(row.dateValue(idx));
                break;

            case TIME:
                out.appendTime(row.timeValue(idx));
                break;

            case DATETIME:
                out.appendDateTime(row.datetimeValue(idx));
                break;

            case TIMESTAMP:
                out.appendTimestamp(row.timestampValue(idx));
                break;

            case UUID:
                out.appendUuid(row.uuidValue(idx));
                break;

            case BITMASK:
                out.appendBitmask(row.bitmaskValue(idx));
                break;

            case STRING:
                out.appendString(row.stringValue(idx));
                break;

            case BYTE_ARRAY:
                out.appendBytes(row.value(idx));
                break;

            case PERIOD:
                out.appendPeriod(row.value(idx));
                break;

            case DURATION:
                out.appendDuration(row.value(idx));
                break;

            case NUMBER:
                out.appendNumber(row.value(idx));
                break;

            default:
                throw new UnsupportedOperationException("Unsupported column type: " + col.type());
        }
    }
}
