/*
 * Copyright 2012-2015 the original author or authors.
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

package sample.tomcat.jndi;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.naming.java.javaURLContextFactory;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSourceFactory;
import org.apache.tomcat.dbcp.pool2.impl.DefaultEvictionPolicy;
import org.apache.tomcat.util.descriptor.web.ContextResource;
import org.postgresql.Driver;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.DecoratingProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@ImportRuntimeHints(SampleTomcatJndiApplication.JndiHints.class)
@SpringBootApplication
public class SampleTomcatJndiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleTomcatJndiApplication.class, args);
    }

    @Bean
    TomcatServletWebServerFactory configurableTomcatWebServerFactory(
            DataSourceProperties dataSourceProperties) {
        return new TomcatServletWebServerFactory() {

            @Override
            protected TomcatWebServer getTomcatWebServer(Tomcat tomcat) {
                tomcat.enableNaming();
                return super.getTomcatWebServer(tomcat);
            }

            @Override
            protected void postProcessContext(Context context) {
                var resource = new ContextResource();
                resource.setName("jdbc/myDataSource");
                resource.setType(DataSource.class.getName());
                resource.setProperty("driverClassName", Driver.class.getName());
                resource.setProperty("url", dataSourceProperties.determineUrl());
                resource.setProperty("username", dataSourceProperties.determineUsername());
                resource.setProperty("password", dataSourceProperties.determinePassword());
                context.getNamingResources().addResource(resource);
            }
        };
    }

    static class JndiHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

            var mcs = MemberCategory.values();
            Set.of(javaURLContextFactory.class, DefaultEvictionPolicy.class, BasicDataSourceFactory.class)
                    .forEach(c -> hints.reflection().registerType(c, mcs));

            Set.of("org.apache.tomcat.dbcp.dbcp2.LocalStrings")
                    .forEach(rb -> hints.resources().registerResourceBundle(rb));

            hints.proxies().registerJdkProxy(
                    DataSource.class,
                    SpringProxy.class,
                    Advised.class,
                    DecoratingProxy.class
            );
        }
    }

    @Bean(destroyMethod = "")
    DataSource jndiDataSource() throws IllegalArgumentException, NamingException {
        var bean = new JndiObjectFactoryBean();
        bean.setJndiName("java:comp/env/jdbc/myDataSource");
        bean.setProxyInterface(DataSource.class);
        bean.setLookupOnStartup(false);
        bean.afterPropertiesSet();
        return (DataSource) bean.getObject();
    }
}

@RestController
class DataSourceController {

    private final DataSource dataSource ;

    DataSourceController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/compare")
    Map<String, Object> direct() throws NamingException {
        return Map.of( //
                "direct", "" +  new InitialContext().lookup("java:comp/env/jdbc/myDataSource"), //
                "dataSource", "" + this.dataSource //
        );
    }
}

@RestController
class CustomersHttpController {

    private final JdbcTemplate template;

    CustomersHttpController(JdbcTemplate template) {
        this.template = template;
    }

    @GetMapping("/customers")
    Collection<Customer> all() {
        return template.query("select * from customers",
                (rs, rowNum) -> new Customer(rs.getInt("id"), rs.getString("name")));
    }
}


record Customer(Integer id, String name) {
}
