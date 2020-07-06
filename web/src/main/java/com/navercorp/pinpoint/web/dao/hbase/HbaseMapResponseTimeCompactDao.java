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

package com.navercorp.pinpoint.web.dao.hbase;

import com.navercorp.pinpoint.common.hbase.HbaseColumnFamily;
import com.navercorp.pinpoint.common.hbase.HbaseOperations2;
import com.navercorp.pinpoint.common.hbase.RowMapper;
import com.navercorp.pinpoint.common.hbase.TableDescriptor;
import com.navercorp.pinpoint.common.profiler.util.ApplicationMapStatisticsUtils;
import com.navercorp.pinpoint.web.dao.MapResponseDao;
import com.navercorp.pinpoint.web.vo.Application;
import com.navercorp.pinpoint.web.vo.Range;
import com.navercorp.pinpoint.web.vo.RangeFactory;
import com.navercorp.pinpoint.web.vo.ResponseTime;
import com.sematext.hbase.wd.RowKeyDistributorByHashPrefix;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Scan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author netspider
 * @author emeroad
 */
@Repository
public class HbaseMapResponseTimeCompactDao implements MapResponseDao {
    private static final int NUM_PARTITIONS = 8;
    private static final int SCAN_CACHE_SIZE = 40;

    private final RowMapper<ResponseTime> responseTimeMapper;
    private final HbaseOperations2 hbaseOperations2;
    private final RangeFactory rangeFactory;
    private final RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix;
    private final TableDescriptor<HbaseColumnFamily.SelfStatMapCompact> descriptor;

    public HbaseMapResponseTimeCompactDao(HbaseOperations2 hbaseOperations2,
                                          TableDescriptor<HbaseColumnFamily.SelfStatMapCompact> descriptor,
                                          @Qualifier("responseTimeCompactMapper") RowMapper<ResponseTime> responseTimeMapper,
                                          RangeFactory rangeFactory,
                                          @Qualifier("statisticsSelfCompactRowKeyDistributor") RowKeyDistributorByHashPrefix rowKeyDistributorByHashPrefix) {
        this.hbaseOperations2 = Objects.requireNonNull(hbaseOperations2, "hbaseOperations2");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.responseTimeMapper = Objects.requireNonNull(responseTimeMapper, "responseTimeMapper");
        this.rangeFactory = Objects.requireNonNull(rangeFactory, "rangeFactory");
        this.rowKeyDistributorByHashPrefix = Objects.requireNonNull(rowKeyDistributorByHashPrefix, "rowKeyDistributorByHashPrefix");
    }

    @Override
    public List<ResponseTime> selectResponseTime(Application application, Range range) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(range, "range");

        Scan scan = createScan(application, range, descriptor.getColumnFamilyName());
        TableName tableName = descriptor.getTableName();
        List<ResponseTime> responseTimeList = hbaseOperations2.findParallel(tableName, scan, rowKeyDistributorByHashPrefix, responseTimeMapper, NUM_PARTITIONS);
        if (!responseTimeList.isEmpty()) {
            return responseTimeList;
        }

        return new ArrayList<>();
    }

    private Scan createScan(Application application, Range range, byte[] family) {
        range = rangeFactory.createStatisticsRange(range);
        // start key is replaced by end key because timestamp has been reversed
        byte[] startKey = ApplicationMapStatisticsUtils.makeRowKey(application.getName(), application.getServiceTypeCode(), range.getTo());
        byte[] endKey = ApplicationMapStatisticsUtils.makeRowKey(application.getName(), application.getServiceTypeCode(), range.getFrom());

        final Scan scan = new Scan();
        scan.setCaching(SCAN_CACHE_SIZE);
        scan.setStartRow(startKey);
        scan.setStopRow(endKey);
        scan.addFamily(family);
        scan.setId("ApplicationSelfScan");

        return scan;
    }
}