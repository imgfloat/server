package dev.kruhlmann.imgfloat.config;

import com.zaxxer.hikari.HikariDataSource;
import dev.kruhlmann.imgfloat.repository.audit.AuditLogRepository;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
    basePackages = "dev.kruhlmann.imgfloat.repository",
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuditLogRepository.class)
)
public class PrimaryDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(@Qualifier("dataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("dataSource") DataSource dataSource,
        JpaProperties jpaProperties,
        HibernateProperties hibernateProperties
    ) {
        return builder
            .dataSource(dataSource)
            .packages("dev.kruhlmann.imgfloat.model")
            .properties(hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), new HibernateSettings()))
            .persistenceUnit("primary")
            .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
        @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.flyway")
    public FlywayProperties flywayProperties() {
        return new FlywayProperties();
    }

    @Bean(initMethod = "migrate")
    @Primary
    public Flyway flyway(
        @Qualifier("dataSource") DataSource dataSource,
        @Qualifier("flywayProperties") FlywayProperties properties
    ) {
        FluentConfiguration configuration = Flyway.configure().dataSource(dataSource);
        if (properties.getLocations() != null && !properties.getLocations().isEmpty()) {
            configuration.locations(properties.getLocations().toArray(new String[0]));
        }
        if (properties.isBaselineOnMigrate()) {
            configuration.baselineOnMigrate(true);
        }
        if (properties.getBaselineVersion() != null) {
            configuration.baselineVersion(properties.getBaselineVersion());
        }
        return configuration.load();
    }
}
