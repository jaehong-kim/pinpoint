/*
 * Copyright 2020 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.collector.dao.hbase.statistics;

import com.navercorp.pinpoint.common.buffer.AutomaticBuffer;
import com.navercorp.pinpoint.common.buffer.Buffer;

/**
 * @author jaehong.kim
 */
public class PingColumnName implements ColumnName {
    private final byte prefix;
    private final String agentId;

    // WARNING - cached hash value should not be included for equals/hashCode
    private int hash;

    private long callCount;

    public PingColumnName(byte prefix, final String agentId) {
        this.prefix = prefix;
        this.agentId = agentId;
    }

    public long getCallCount() {
        return callCount;
    }

    public void setCallCount(long callCount) {
        this.callCount = callCount;
    }

    public byte[] getColumnName() {
        final Buffer buffer = new AutomaticBuffer(1 + agentId.length());
        buffer.putByte(prefix);
        buffer.put2PrefixedString(agentId);
        return buffer.getBuffer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PingColumnName that = (PingColumnName) o;
        if (!agentId.equals(that.agentId)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        // take care when modifying this method - contains hashCodes for hbasekeys
        if (hash != 0) {
            return hash;
        }
        int result = agentId.hashCode();
        hash = result;
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PingColumnName{");
        sb.append("agentId='").append(agentId).append('\'');
        sb.append(", callCount=").append(callCount);
        sb.append('}');
        return sb.toString();
    }
}