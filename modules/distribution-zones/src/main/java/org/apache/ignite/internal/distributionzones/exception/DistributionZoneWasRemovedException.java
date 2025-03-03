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

package org.apache.ignite.internal.distributionzones.exception;

import static org.apache.ignite.lang.ErrorGroups.DistributionZones.ZONE_NOT_FOUND_ERR;

import java.util.UUID;
import org.apache.ignite.internal.lang.IgniteInternalException;

/**
 * Exception is thrown when appropriate distribution zone was removed.
 */
public class DistributionZoneWasRemovedException extends IgniteInternalException {
    /**
     * The constructor.
     *
     * @param zoneId Zone id.
     */
    public DistributionZoneWasRemovedException(int zoneId) {
        super(ZONE_NOT_FOUND_ERR, "Distribution zone is not found [zoneId=" + zoneId + ']');
    }

    /**
     * The constructor is used for creating an exception instance that is thrown from a remote server.
     *
     * @param traceId Trace id.
     * @param code Error code.
     * @param message Error message.
     * @param cause Cause exception.
     */
    public DistributionZoneWasRemovedException(UUID traceId, int code, String message, Throwable cause) {
        super(traceId, code, message, cause);
    }
}
