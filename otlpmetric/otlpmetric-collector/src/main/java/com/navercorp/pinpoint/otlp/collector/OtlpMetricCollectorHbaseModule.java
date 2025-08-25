package com.navercorp.pinpoint.otlp.collector;

import com.navercorp.pinpoint.common.hbase.config.DistributorConfiguration;
import com.navercorp.pinpoint.common.hbase.config.HbaseNamespaceConfiguration;
import com.navercorp.pinpoint.common.hbase.config.HbasePutWriterConfiguration;
import com.navercorp.pinpoint.common.hbase.config.HbaseTemplateConfiguration;
import com.navercorp.pinpoint.common.hbase.util.DurabilityApplier;
import com.navercorp.pinpoint.common.hbase.wd.RowKeyDistributor;
import com.navercorp.pinpoint.common.server.CommonsHbaseConfiguration;
import com.navercorp.pinpoint.common.server.bo.serializer.trace.v2.TraceRowKeyEncoderV2;
import com.navercorp.pinpoint.common.server.hbase.config.HbaseClientConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

@Configuration
@Import({
        CommonsHbaseConfiguration.class,
        HbaseNamespaceConfiguration.class,
        DistributorConfiguration.class,

        HbaseClientConfiguration.class,
        HbaseTemplateConfiguration.class,
        HbasePutWriterConfiguration.class
})
@ComponentScan({
        "com.navercorp.pinpoint.otlp.collector.dao.hbase"
})
@PropertySource(name = "CollectorHbaseModule", value = {
        "classpath:hbase-root.properties",
        "classpath:profiles/${pinpoint.profiles.active:local}/hbase.properties"
})
public class OtlpMetricCollectorHbaseModule {
    private final Logger logger = LogManager.getLogger(OtlpMetricCollectorHbaseModule.class);

    @Bean
    public DurabilityApplier spanPutWriterDurabilityApplier(@Value("${collector.span.durability:USE_DEFAULT}") String spanDurability) {
        logger.info("Span(Trace Put) durability:{}", spanDurability);
        return new DurabilityApplier(spanDurability);
    }

    @Bean
    public TraceRowKeyEncoderV2 otlpTraceRowKeyEncoderV2(@Qualifier("traceV2Distributor") RowKeyDistributor rowKeyDistributor) {
        return new TraceRowKeyEncoderV2(rowKeyDistributor);
    }
}
