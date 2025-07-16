package com.navercorp.pinpoint.common.server.bo.serializer.trace.otlp;

import com.navercorp.pinpoint.common.PinpointConstants;
import com.navercorp.pinpoint.common.hbase.wd.RowKeyDistributor;
import com.navercorp.pinpoint.common.server.bo.serializer.RowKeyEncoder;

import java.util.Objects;

public class OtlpTraceRowKeyEncoderV2 implements RowKeyEncoder<byte[]> {

    public static final int AGENT_ID_MAX_LEN = PinpointConstants.AGENT_ID_MAX_LEN;
    public static final int DISTRIBUTE_HASH_SIZE = 1;

    private final RowKeyDistributor rowKeyDistributor;

    public OtlpTraceRowKeyEncoderV2(RowKeyDistributor rowKeyDistributor) {
        this.rowKeyDistributor = Objects.requireNonNull(rowKeyDistributor, "rowKeyDistributor");
    }

    public byte[] encodeRowKey(byte[] transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        return wrapDistributedRowKey(transactionId);
    }

    private byte[] wrapDistributedRowKey(byte[] rowKey) {
        return rowKeyDistributor.getDistributedKey(rowKey);
    }
}
