package com.oiep;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * OIEP - Open Interview Experience Platform.
 *
 * <p>Classic Spring MVC application (JSP views, JDBC/SQLite persistence). The WAR is
 * executable ({@code java -jar target/oiep.war}) and can also be deployed to an external
 * servlet container because the class extends {@link SpringBootServletInitializer}.</p>
 */
@SpringBootApplication
public class OiepApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(OiepApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(OiepApplication.class);
    }
}
