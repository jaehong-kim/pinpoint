/*
 * Copyright 2022 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.mongodb4;

import com.navercorp.pinpoint.it.plugin.jdbc.DriverProperties;
import com.navercorp.pinpoint.it.plugin.jdbc.testcontainers.DatabaseContainers;
import com.navercorp.pinpoint.test.plugin.shared.SharedTestBeforeAllResult;

import java.util.Properties;

/**
 * @author Roy Kim
 */
public abstract class MongoDBITBase {
    protected static DriverProperties driverProperties;

    abstract Class<?> getMongoDatabaseClazz() throws ClassNotFoundException;

    @SharedTestBeforeAllResult
    public static void setBeforeAllResult(Properties beforeAllResult) {
        driverProperties = DatabaseContainers.readDriverProperties(beforeAllResult);
    }

    public static DriverProperties getDriverProperties() {
        return driverProperties;
    }

}
