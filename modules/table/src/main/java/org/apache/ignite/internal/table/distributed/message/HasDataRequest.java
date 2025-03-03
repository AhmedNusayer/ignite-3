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

package org.apache.ignite.internal.table.distributed.message;

import org.apache.ignite.internal.table.distributed.TableMessageGroup;
import org.apache.ignite.network.NetworkMessage;
import org.apache.ignite.network.annotations.Transferable;

/**
 * A message that queries a node whether it has data for the partition of a table.
 */
@Transferable(TableMessageGroup.HAS_DATA_REQUEST)
public interface HasDataRequest extends NetworkMessage {
    /** ID of the table. */
    int tableId();

    /** ID of the partition. */
    int partitionId();
}
