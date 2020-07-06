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

package com.navercorp.pinpoint.collector.dao.hbase;

import com.navercorp.pinpoint.collector.dao.MapStatisticsCallerCompactDao;
import com.navercorp.pinpoint.collector.dao.hbase.statistics.BulkIncrementer;
import com.navercorp.pinpoint.collector.dao.hbase.statistics.CallRowKey;
import com.navercorp.pinpoint.collector.dao.hbase.statistics.CalleeColumnCompactName;
import com.navercorp.pinpoint.collector.dao.hbase.statistics.ColumnName;
import com.navercorp.pinpoint.collector.dao.hbase.statistics.RowKey;
import com.navercorp.pinpoint.common.hbase.HbaseColumnFamily;
import com.navercorp.pinpoint.common.hbase.HbaseOperations2;
import com.navercorp.pinpoint.common.hbase.TableDescriptor;
import com.navercorp.pinpoint.common.profiler.util.ApplicationMapStatisticsUtils;
import com.navercorp.pinpoint.common.server.util.AcceptedTimeService;
import com.navercorp.pinpoint.common.server.util.TimeSlot;
import com.navercorp.pinpoint.common.trace.ServiceType;
import com.sematext.hbase.wd.RowKeyDistributorByHashPrefix;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Increment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Update statistics of caller node
 *
 * @author netspider
 * @author emeroad
 * @author HyunGil Jeong
 * @author jaehong.kim
 */
@Repository
public class HbaseMapStatisticsCallerCompactDao implements MapStatisticsCallerCompactDao {
    private final HbaseOperations2 hbaseTemplate;
    private final TableDescriptor<HbaseColumnFamily.CalleeStatMapCompact> descriptor;
    private final AcceptedTimeService acceptedTimeService;
    private final TimeSlot timeSlot;
    private final BulkIncrementer bulkIncrementer;
    private final RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix;
    private final boolean useBulk;

    @Autowired
    public HbaseMapStatisticsCallerCompactDao(HbaseOperations2 hbaseTemplate,
                                              TableDescriptor<HbaseColumnFamily.CalleeStatMapCompact> descriptor,
                                              @Qualifier("statisticsCallerCompactRowKeyDistributor") RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix,
                                              AcceptedTimeService acceptedTimeService,
                                              TimeSlot timeSlot,
                                              @Qualifier("callerCompactBulkIncrementer") BulkIncrementer bulkIncrementer) {
        this(hbaseTemplate, descriptor, rowKeyDistributorByHashPrefix, acceptedTimeService, timeSlot, bulkIncrementer, true);
    }

    public HbaseMapStatisticsCallerCompactDao(HbaseOperations2 hbaseTemplate,
                                              TableDescriptor<HbaseColumnFamily.CalleeStatMapCompact> descriptor,
                                              RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix,
                                              AcceptedTimeService acceptedTimeService,
                                              TimeSlot timeSlot,
                                              BulkIncrementer bulkIncrementer, boolean useBulk) {
        this.hbaseTemplate = Objects.requireNonNull(hbaseTemplate, "hbaseTemplate");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.rowKeyDistributorByHashPrefix = Objects.requireNonNull(rowKeyDistributorByHashPrefix, "rowKeyDistributorByHashPrefix");
        this.acceptedTimeService = Objects.requireNonNull(acceptedTimeService, "acceptedTimeService");
        this.timeSlot = Objects.requireNonNull(timeSlot, "timeSlot");
        this.bulkIncrementer = Objects.requireNonNull(bulkIncrementer, "bulkIncrementer");
        this.useBulk = useBulk;
    }

    @Override
    public void update(String callerApplicationName, ServiceType callerServiceType, String calleeApplicationName, ServiceType calleeServiceType, String calleeHost, int elapsed, boolean isError) {
        Objects.requireNonNull(callerApplicationName, "callerApplicationName");
        Objects.requireNonNull(calleeApplicationName, "calleeApplicationName");


        // make row key. rowkey is me
        final long acceptedTime = acceptedTimeService.getAcceptedTime();
        final long rowTimeSlot = timeSlot.getTimeSlot(acceptedTime);
        final RowKey callerRowKey = new CallRowKey(callerApplicationName, callerServiceType.getCode(), rowTimeSlot);

        // there may be no endpoint in case of httpclient
        final String host = StringUtils.defaultString(calleeHost);
        final short slotNumber = ApplicationMapStatisticsUtils.getSlotNumber(calleeServiceType, elapsed, isError);
        final ColumnName calleeColumnName = new CalleeColumnCompactName(calleeServiceType.getCode(), calleeApplicationName, host, slotNumber);
        if (useBulk) {
            TableName tableName = descriptor.getTableName();
            bulkIncrementer.increment(tableName, callerRowKey, calleeColumnName);
        } else {
            final byte[] rowKey = getDistributedKey(callerRowKey.getRowKey());
            // column name is the name of caller app.
            byte[] columnName = calleeColumnName.getColumnName();
            increment(rowKey, columnName, 1L);
        }
    }

    private void increment(byte[] rowKey, byte[] columnName, long increment) {
        if (rowKey == null) {
            throw new NullPointerException("rowKey");
        }
        if (columnName == null) {
            throw new NullPointerException("columnName");
        }
        TableName tableName = descriptor.getTableName();
        hbaseTemplate.incrementColumnValue(tableName, rowKey, descriptor.getColumnFamilyName(), columnName, increment);
    }

    @Override
    public void flushAll() {
        if (!useBulk) {
            throw new IllegalStateException();
        }
        // update statistics by rowkey and column for now. need to update it by rowkey later.
        Map<TableName, List<Increment>> incrementMap = bulkIncrementer.getIncrements(rowKeyDistributorByHashPrefix);
        for (Map.Entry<TableName, List<Increment>> e : incrementMap.entrySet()) {
            TableName tableName = e.getKey();
            List<Increment> increments = e.getValue();
            hbaseTemplate.increment(tableName, increments);
        }
    }

    private byte[] getDistributedKey(byte[] rowKey) {
        return rowKeyDistributorByHashPrefix.getDistributedKey(rowKey);
    }
}