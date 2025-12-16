package com.intelligent_data_analysis_system.infrastructure.datasource;

public final class DomainContext {
    private static final ThreadLocal<DataSourceDomain> CTX = new ThreadLocal<>();

    private DomainContext() {}

    public static void set(DataSourceDomain domain) { CTX.set(domain); }

    public static DataSourceDomain get() { return CTX.get(); }

    public static void clear() { CTX.remove(); }
}
