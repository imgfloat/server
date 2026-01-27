package dev.kruhlmann.imgfloat.config;

import com.zaxxer.hikari.HikariDataSource;
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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "dev.kruhlmann.imgfloat.repository.audit",
    entityManagerFactoryRef = "auditEntityManagerFactory",
    transactionManagerRef = "auditTransactionManager"
)
public class AuditLogDataSourceConfig {

    @Bean
    @ConfigurationProperties("imgfloat.audit.datasource")
    public DataSourceProperties auditDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("imgfloat.audit.datasource.hikari")
    public HikariDataSource auditDataSource(@Qualifier("auditDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean auditEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("auditDataSource") DataSource dataSource,
        JpaProperties jpaProperties,
        HibernateProperties hibernateProperties
    ) {
        return builder
            .dataSource(dataSource)
            .packages("dev.kruhlmann.imgfloat.model.db.audit")
            .properties(hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), new HibernateSettings()))
            .persistenceUnit("audit")
            .build();
    }

    @Bean
    public PlatformTransactionManager auditTransactionManager(
        @Qualifier("auditEntityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @ConfigurationProperties("imgfloat.audit.flyway")
    public FlywayProperties auditFlywayProperties() {
        return new FlywayProperties();
    }

    @Bean(initMethod = "migrate")
    public Flyway auditFlyway(
        @Qualifier("auditDataSource") DataSource dataSource,
        @Qualifier("auditFlywayProperties") FlywayProperties properties
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
