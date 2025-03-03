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

package org.apache.ignite.internal.security.authentication.basic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import org.apache.ignite.configuration.annotation.Secret;
import org.apache.ignite.configuration.validation.NotBlank;
import org.apache.ignite.internal.security.authentication.basic.BasicAuthenticationProviderConfigurationSchema;
import org.junit.jupiter.api.Test;

class BasicAuthenticationProviderConfigurationSchemaTest {
    @Test
    public void usernameIsNotBlank() {
        Field username = Arrays.stream(BasicAuthenticationProviderConfigurationSchema.class.getDeclaredFields())
                .filter(it -> it.getName().equals("username"))
                .findFirst()
                .orElseThrow();

        assertTrue(username.isAnnotationPresent(NotBlank.class));
    }

    @Test
    public void passwordIsSecretAndNotBlank() {
        Field password = Arrays.stream(BasicAuthenticationProviderConfigurationSchema.class.getDeclaredFields())
                .filter(it -> it.getName().equals("password"))
                .findFirst()
                .orElseThrow();

        assertTrue(password.isAnnotationPresent(Secret.class));
        assertTrue(password.isAnnotationPresent(NotBlank.class));
    }
}
