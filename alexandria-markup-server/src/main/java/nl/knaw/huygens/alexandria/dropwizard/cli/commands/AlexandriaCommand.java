package nl.knaw.huygens.alexandria.dropwizard.cli.commands;

/*
 * #%L
 * alexandria-markup-server
 * =======
 * Copyright (C) 2015 - 2018 Huygens ING (KNAW)
 * =======
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
 * #L%
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Charsets;
import io.dropwizard.cli.Cli;
import io.dropwizard.cli.Command;
import net.sourceforge.argparse4j.inf.Namespace;
import nl.knaw.huc.di.tag.tagml.exporter.TAGMLExporter;
import nl.knaw.huygens.alexandria.dropwizard.cli.AlexandriaCommandException;
import nl.knaw.huygens.alexandria.dropwizard.cli.CLIContext;
import nl.knaw.huygens.alexandria.dropwizard.cli.DocumentInfo;
import nl.knaw.huygens.alexandria.dropwizard.cli.FileType;
import nl.knaw.huygens.alexandria.markup.api.AlexandriaProperties;
import nl.knaw.huygens.alexandria.storage.TAGDocument;
import nl.knaw.huygens.alexandria.storage.TAGStore;
import nl.knaw.huygens.alexandria.view.TAGView;
import nl.knaw.huygens.alexandria.view.TAGViewDefinition;
import nl.knaw.huygens.alexandria.view.TAGViewFactory;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public abstract class AlexandriaCommand extends Command {
  private static final Logger LOG = LoggerFactory.getLogger(AlexandriaCommand.class);
  static final String ALEXANDRIA_DIR = ".alexandria";
  final String FILE = "file";

  private final String alexandriaDir;
  private final File contextFile;
  final String workDir;
  static ObjectMapper mapper = new ObjectMapper()
      .registerModule(new Jdk8Module())//
      .registerModule(new JavaTimeModule());

  public AlexandriaCommand(String name, String description) {
    super(name, description);
    workDir = System.getProperty(AlexandriaProperties.WORKDIR, ".");
    alexandriaDir = workDir + "/" + ALEXANDRIA_DIR;
    initProjectDir();

    contextFile = new File(alexandriaDir, "context.json");
  }

  private void initProjectDir() {
    new File(alexandriaDir).mkdir();
  }

  Map<String, TAGView> readViewMap(TAGStore store, final CLIContext context) {
    TAGViewFactory viewFactory = new TAGViewFactory(store);
    return context.getTagViewDefinitions()
        .entrySet()
        .stream()
        .collect(toMap(
            Map.Entry::getKey,
            e -> viewFactory.fromDefinition(e.getValue())
        ));
  }

  void storeViewMap(Map<String, TAGView> viewMap, CLIContext context) {
    Map<String, TAGViewDefinition> viewDefinitionMap = viewMap.entrySet()//
        .stream()//
        .collect(toMap(//
            Map.Entry::getKey,//
            e -> e.getValue().getDefinition()//
        ));
    context.setTagViewDefinitions(viewDefinitionMap);
  }

  CLIContext readContext() {
    return uncheckedRead(contextFile, CLIContext.class);
  }

  void storeContext(CLIContext context) {
    uncheckedStore(contextFile, context);
  }

  void checkDirectoryIsInitialized() {
    if (!contextFile.exists()) {
      System.out.println("This directory has not been initialized, run ");
      System.out.println("  alexandria init");
      System.out.println("first.");
      throw new AlexandriaCommandException("not initialized");
    }
  }

  private void uncheckedStore(File file, Object object) {
    try {
      mapper.writeValue(file, object);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private <T> T uncheckedRead(File file, Class<T> clazz) {
    try {
      return mapper.readValue(file, clazz);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private <T> T uncheckedRead(File file, TypeReference<T> typeReference) {
    try {
      return mapper.readValue(file, typeReference);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Long getIdForExistingDocument(String docName) {
    Map<String, DocumentInfo> documentIndex = readContext().getDocumentInfo();
    if (!documentIndex.containsKey(docName)) {
      System.err.printf("ERROR: No document '%s' was registered.\n  alexandria status\nwill show you which documents and views have been registered.%n", docName);
      throw new AlexandriaCommandException("unregistered document");
    }
    return documentIndex.get(docName).getDbId();
  }

  TAGView getExistingView(String viewName, final TAGStore store, final CLIContext context) {
    Map<String, TAGView> viewMap = readViewMap(store, context);
    if (!viewMap.containsKey(viewName)) {
      System.err.printf("ERROR: No view '%s' was registered.\n  alexandria status\nwill show you which documents and views have been registered.%n", viewName);
      throw new AlexandriaCommandException("unregistered view");
    }
    return viewMap.get(viewName);
  }

  Path workFilePath(final String relativePath) {
    return Paths.get(workDir).resolve(relativePath);
  }

  TAGStore getTAGStore() {
    return new TAGStore(alexandriaDir, false);
  }

  FileType fileType(String fileName) {
    if (fileName.endsWith(".tagml") || fileName.endsWith(".tag")) {
      return FileType.tagmlSource;
    }
    if (fileName.endsWith(".json")) {
      return FileType.viewDefinition;
    }
    return FileType.other;
  }

  @Override
  public void onError(Cli cli, Namespace namespace, Throwable e) {
    cli.getStdErr().println(e.getMessage());
  }

  void exportTAGML(final CLIContext context, final TAGStore store, final TAGView tagView, final String fileName, final Long docId) {
    TAGDocument document = store.getDocument(docId);
    TAGMLExporter tagmlExporter = new TAGMLExporter(store, tagView);
    String tagml = tagmlExporter.asTAGML(document)
        .replaceAll("\n\\s*\n", "\n")
        .trim();
    try {
      final File out = workFilePath(fileName).toFile();
      FileUtils.writeStringToFile(out, tagml, Charsets.UTF_8);
      context.getWatchedFiles().get(fileName).setLastCommit(Instant.now());
    } catch (IOException e) {
      e.printStackTrace();
      throw new UncheckedIOException(e);
    }
  }

}
