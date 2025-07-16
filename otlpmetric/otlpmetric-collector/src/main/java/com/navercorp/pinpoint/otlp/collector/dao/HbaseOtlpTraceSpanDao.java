/*
 * Copyright 2019 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.otlp.collector.dao;


import com.navercorp.pinpoint.common.hbase.HbaseColumnFamily;
import com.navercorp.pinpoint.common.hbase.HbaseTables;
import com.navercorp.pinpoint.common.hbase.TableNameProvider;
import com.navercorp.pinpoint.common.hbase.async.HbasePutWriter;
import com.navercorp.pinpoint.common.hbase.util.DurabilityApplier;
import com.navercorp.pinpoint.common.profiler.util.TransactionId;
import com.navercorp.pinpoint.common.server.bo.serializer.RowKeyEncoder;
import com.navercorp.pinpoint.common.server.bo.serializer.trace.v2.SpanChunkSerializerV2;
import com.navercorp.pinpoint.common.server.bo.serializer.trace.v2.SpanSerializerV2;
import com.navercorp.pinpoint.otlp.collector.model.OtlpTraceSpan;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Put;


import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Repository
public class HbaseOtlpTraceSpanDao implements OtlpTraceSpanDao {
    private static final HbaseColumnFamily descriptor = HbaseTables.TRACE_V2_SPAN;

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final TableNameProvider tableNameProvider;
    private final SpanSerializerV2 spanSerializer;
    private final SpanChunkSerializerV2 spanChunkSerializer;
    private final RowKeyEncoder<byte[]> rowKeyEncoder;
    private final HbasePutWriter putWriter;
    private final DurabilityApplier durabilityApplier;

    public HbaseOtlpTraceSpanDao(@Qualifier("spanPutWriter") HbasePutWriter putWriter,
                                 TableNameProvider tableNameProvider,
                                 @Qualifier("otlpTraceRowKeyEncoderV2") RowKeyEncoder<byte[]> rowKeyEncoder,
                                 SpanSerializerV2 spanSerializer,
                                 SpanChunkSerializerV2 spanChunkSerializer,
                                 DurabilityApplier durabilityApplier) {
        this.putWriter = Objects.requireNonNull(putWriter, "putWriter");
        this.tableNameProvider = Objects.requireNonNull(tableNameProvider, "tableNameProvider");
        this.rowKeyEncoder = Objects.requireNonNull(rowKeyEncoder, "rowKeyEncoder");
        this.spanSerializer = Objects.requireNonNull(spanSerializer, "spanSerializer");
        this.spanChunkSerializer = Objects.requireNonNull(spanChunkSerializer, "spanChunkSerializer");
        this.durabilityApplier = Objects.requireNonNull(durabilityApplier, "durabilityApplier");
    }

    // need collectorAcceptTime
    // transactionId - otlpTraceSpan.traceId ?
    // add OtlpTraceRowKeyEncoder implements RowKeyEncoder
    // add OtlpTraceSpanSerializer implements HbaseSerializer
    public CompletableFuture<Void> asyncInsert(final OtlpTraceSpan spanBo) {
        Objects.requireNonNull(spanBo, "spanBo");

        long acceptedTime = spanBo.getCollectorAcceptTime();

        final byte[] rowKey = this.rowKeyEncoder.encodeRowKey(spanBo.getTraceId());
        final Put put = new Put(rowKey, acceptedTime, true);

        this.durabilityApplier.apply(put);

////        this.spanSerializer.serialize(spanBo, put, null);
////
////        TableName traceTableName = tableNameProvider.getTableName(descriptor.getTable());
//        return putWriter.put(traceTableName, put);
        return null;
    }
}
