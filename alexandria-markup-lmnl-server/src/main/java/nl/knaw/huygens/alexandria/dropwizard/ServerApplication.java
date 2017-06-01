package nl.knaw.huygens.alexandria.dropwizard;

import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.health.HealthCheck.Result;

/*
 * #%L
 * alexandria-markup-lmnl-server
 * =======
 * Copyright (C) 2015 - 2017 Huygens ING (KNAW)
 * =======
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import nl.knaw.huygens.alexandria.dropwizard.api.DocumentService;
import nl.knaw.huygens.alexandria.dropwizard.health.ServerHealthCheck;
import nl.knaw.huygens.alexandria.dropwizard.resources.AboutResource;
import nl.knaw.huygens.alexandria.dropwizard.resources.DocumentsResource;
import nl.knaw.huygens.alexandria.dropwizard.resources.HomePageResource;
import nl.knaw.huygens.alexandria.lmnl.exporter.LMNLExporter;
import nl.knaw.huygens.alexandria.lmnl.importer.LMNLImporter;
import nl.knaw.huygens.alexandria.texmecs.importer.TexMECSImporter;

public class ServerApplication extends Application<ServerConfiguration> {
  Logger LOG = LoggerFactory.getLogger(getClass());

  public static void main(String[] args) throws Exception {
    new ServerApplication().run(args);
  }

  @Override
  public String getName() {
    return "Alexandria Markup Server";
  }

  @Override
  public void initialize(Bootstrap<ServerConfiguration> bootstrap) {
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(//
        new SubstitutingSourceProvider(//
            bootstrap.getConfigurationSourceProvider(), //
            new EnvironmentVariableSubstitutor()));
  }

  @Override
  public void run(ServerConfiguration configuration, Environment environment) {
    DocumentService documentService = new DocumentService(configuration);
    LMNLImporter lmnlImporter = new LMNLImporter();
    LMNLExporter lmnlExporter = new LMNLExporter();
    TexMECSImporter texMECSImporter = new TexMECSImporter();

    environment.jersey().register(new HomePageResource());
    environment.jersey().register(new AboutResource(getName()));
    environment.jersey().register(new DocumentsResource(documentService, lmnlImporter, texMECSImporter, lmnlExporter, configuration));

    environment.healthChecks().register("server", new ServerHealthCheck());

    SortedMap<String, Result> results = environment.healthChecks().runHealthChecks();
    AtomicBoolean healthy = new AtomicBoolean(true);
    LOG.info("Healthchecks:");
    results.forEach((name, result) -> {
      LOG.info("{}: {}, message='{}'", name, result.isHealthy() ? "healthy" : "unhealthy", result.getMessage());
      healthy.set(healthy.get() && result.isHealthy());
    });
    if (!healthy.get()) {
      throw new RuntimeException("Failing health check(s)");
    }

  }
}
