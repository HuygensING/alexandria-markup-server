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

import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import nl.knaw.huc.di.tag.TAGViews;
import nl.knaw.huygens.alexandria.dropwizard.cli.CLIContext;
import nl.knaw.huygens.alexandria.dropwizard.cli.DocumentInfo;
import nl.knaw.huygens.alexandria.dropwizard.cli.FileInfo;
import nl.knaw.huygens.alexandria.dropwizard.cli.FileType;
import nl.knaw.huygens.alexandria.storage.TAGStore;
import nl.knaw.huygens.alexandria.view.TAGView;

import java.util.HashMap;
import java.util.Map;

public class CheckOutCommand extends AlexandriaCommand {
  private static final String VIEW = "view";
  private static final String DOCUMENT = "document";
  public static final String MAIN_VIEW = "-";

  public CheckOutCommand() {
    super("checkout", "Activate or deactivate a view in this directory");
  }

  @Override
  public void configure(Subparser subparser) {
    subparser.addArgument("view")//
        .metavar("VIEW")
        .dest(VIEW)//
        .type(String.class)//
        .required(true)//
        .help("The name of the view to use");
  }

  @Override
  public void run(Bootstrap<?> bootstrap, Namespace namespace) {
    checkDirectoryIsInitialized();

    String viewName = namespace.getString(VIEW);
    boolean showAll = MAIN_VIEW.equals(viewName);

    if (showAll) {
      System.out.println("Checking out main view...");
    } else {
      System.out.printf("Checking out view %s...%n", viewName);
    }
    try (TAGStore store = getTAGStore()) {
      CLIContext context = readContext();
      Map<String, FileInfo> watchedTranscriptions = new HashMap<>();
      context.getWatchedFiles().entrySet()
          .stream()
          .filter(e -> e.getValue().getFileType().equals(FileType.tagmlSource))
          .forEach(e -> {
            String fileName = e.getKey();
            FileInfo fileInfo = e.getValue();
            watchedTranscriptions.put(fileName, fileInfo);
          });

      Map<String, DocumentInfo> documentIndex = context.getDocumentInfo();
      store.runInTransaction(() -> {
        TAGView tagView = showAll
            ? TAGViews.getShowAllMarkupView(store)
            : getExistingView(viewName, store, context);
        watchedTranscriptions.forEach((fileName, fileInfo) -> {
          System.out.printf("  updating %s...%n", fileName);
          String documentName = fileInfo.getObjectName();
          final Long docId = documentIndex.get(documentName).getDbId();
          exportTAGML(context, store, tagView, fileName, docId);
        });
      });
      context.setActiveView(viewName);
      storeContext(context);
    }
    System.out.println("done!");
  }

}

