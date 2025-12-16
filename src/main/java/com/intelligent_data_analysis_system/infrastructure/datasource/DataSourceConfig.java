package com.intelligent_data_analysis_system.infrastructure.datasource;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.intelligent_data_analysis_system.infrastructure.config.properties.MultiDataSourceProperties;
import com.intelligent_data_analysis_system.infrastructure.config.properties.RoutingProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({RoutingProperties.class, MultiDataSourceProperties.class})
public class DataSourceConfig {

    private final RoutingProperties routingProperties;
    private final MultiDataSourceProperties multi;

    @Bean
    public DataSource routingDataSource() {
        String dbms = routingProperties.getDbms().toLowerCase(); // mysql / pg

        DataSource finance = buildDataSource(dbms.equals("pg") ? multi.getFinancePg() : multi.getFinanceMysql());
        DataSource healthcare = buildDataSource(dbms.equals("pg") ? multi.getHealthcarePg() : multi.getHealthcareMysql());

        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceDomain.FINANCE.name(), finance);
        targets.put(DataSourceDomain.HEALTHCARE.name(), healthcare);

        DomainRoutingDataSource rds = new DomainRoutingDataSource();
        rds.setTargetDataSources(targets);
        rds.setDefaultTargetDataSource(finance);
        rds.afterPropertiesSet();

        return rds;
    }

    private DataSource buildDataSource(MultiDataSourceProperties.DbProps p) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(p.getUrl());
        ds.setUsername(p.getUsername());
        ds.setPassword(p.getPassword());
        ds.setDriverClassName(p.getDriverClassName());

        // 可选：比赛/开发阶段建议加
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setPoolName("Hikari-" + p.getUrl());

        return ds;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource routingDataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(routingDataSource);

        // ✅ 手动创建 MybatisConfiguration（类型正确）
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(org.apache.ibatis.logging.stdout.StdOutImpl.class); // 可选
        factory.setConfiguration(configuration);

        // ✅ 关键：让 MP 还能识别 @TableName/@TableField 等（建议保留）
        factory.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig());

        return factory.getObject();
    }


    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
