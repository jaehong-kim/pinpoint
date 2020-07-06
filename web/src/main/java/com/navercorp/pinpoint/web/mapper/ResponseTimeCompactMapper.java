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

package com.navercorp.pinpoint.web.mapper;

import com.navercorp.pinpoint.common.buffer.Buffer;
import com.navercorp.pinpoint.common.buffer.FixedBuffer;
import com.navercorp.pinpoint.common.hbase.HbaseColumnFamily;
import com.navercorp.pinpoint.common.hbase.RowMapper;
import com.navercorp.pinpoint.common.trace.BaseHistogramSchema;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.navercorp.pinpoint.common.util.TimeUtils;
import com.navercorp.pinpoint.loader.service.ServiceTypeRegistryService;
import com.navercorp.pinpoint.web.vo.ResponseTime;
import com.sematext.hbase.wd.RowKeyDistributorByHashPrefix;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author emeroad
 * @author jaehong.kim
 */
@Component
public class ResponseTimeCompactMapper implements RowMapper<ResponseTime> {
    @Autowired
    private ServiceTypeRegistryService registry;
    @Autowired
    @Qualifier("statisticsSelfCompactRowKeyDistributor")
    private RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix;

    @Override
    public ResponseTime mapRow(Result result, int rowNum) throws Exception {
        if (result.isEmpty()) {
            return null;
        }

        final byte[] rowKey = getOriginalKey(result.getRow());
        final ResponseTime responseTime = createResponseTime(rowKey);
        for (Cell cell : result.rawCells()) {
            if (CellUtil.matchingFamily(cell, HbaseColumnFamily.MAP_STATISTICS_SELF_COMPACT_COUNTER.getName())) {
                final long count = Bytes.toLong(cell.getValueArray(), cell.getValueOffset());
                final Buffer buffer = new FixedBuffer(CellUtil.cloneQualifier(cell));
                final byte prefix = buffer.readByte();
                if (prefix == HbaseColumnFamily.SelfStatMapCompact.QUALIFIER_RESPONSE) {
                    final short slotNumber = buffer.readShort();
                    responseTime.addResponseTime(responseTime.getApplicationName(), slotNumber, count);
                } else if (prefix == HbaseColumnFamily.SelfStatMapCompact.QUALIFIER_PING) {
                    final String agentId = buffer.read2PrefixedString();
                    responseTime.addResponseTime(agentId, BaseHistogramSchema.PING_SLOT_TIME, count);
                }
            }
        }

        return responseTime;
    }

    private ResponseTime createResponseTime(byte[] rowKey) {
        final Buffer row = new FixedBuffer(rowKey);
        final String applicationName = row.read2PrefixedString();
        final short serviceTypeCode = row.readShort();
        final long timestamp = TimeUtils.recoveryTimeMillis(row.readLong());
        final ServiceType serviceType = registry.findServiceType(serviceTypeCode);
        return new ResponseTime(applicationName, serviceType, timestamp);
    }

    private byte[] getOriginalKey(byte[] rowKey) {
        return rowKeyDistributorByHashPrefix.getOriginalKey(rowKey);
    }
}