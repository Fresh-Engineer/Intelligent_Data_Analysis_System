package com.intelligent_data_analysis_system.infrastructure.datasource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DomainRoutingDataSource extends AbstractRoutingDataSource{
    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceDomain domain = DomainContext.get();
        // 没设置时默认走 FINANCE（也可以改成抛异常）
        return domain == null ? DataSourceDomain.FINANCE.name() : domain.name();
    }
}
