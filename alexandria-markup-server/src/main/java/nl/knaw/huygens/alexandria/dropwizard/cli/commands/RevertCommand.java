package nl.knaw.huygens.alexandria.dropwizard.cli.commands;

/*
 * #%L
 * alexandria-markup-server
 * =======
 * Copyright (C) 2015 - 2020 Huygens ING (KNAW)
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
import nl.knaw.huygens.alexandria.dropwizard.cli.FileInfo;
import nl.knaw.huygens.alexandria.storage.TAGStore;
import nl.knaw.huygens.alexandria.view.TAGView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

public class RevertCommand extends AlexandriaCommand {

  public RevertCommand() {
    super("revert", "Restore the document file(s).");
  }

  @Override
  public void configure(Subparser subparser) {
    subparser.addArgument(ARG_FILE)//
        .metavar("<file>")
        .dest(FILE)//
        .type(String.class)//
        .nargs("+")
        .required(true)//
        .help("the file to be reverted");
  }

  @Override
  public void run(Bootstrap<?> bootstrap, Namespace namespace) {
    checkAlexandriaIsInitialized();
    List<String> files = relativeFilePaths(namespace);

    CLIContext context = readContext();

    String viewName = context.getActiveView();
    boolean showAll = MAIN_VIEW.equals(viewName);

    try (TAGStore store = getTAGStore()) {
      store.runInTransaction(() -> {
        TAGView tagView = showAll
            ? TAGViews.getShowAllMarkupView(store)
            : getExistingView(viewName, store, context);
        files.forEach(fileName -> {
          Optional<String> documentName = context.getDocumentName(fileName);
          if (documentName.isPresent()) {
            System.out.printf("Reverting %s...%n", fileName);
            Long docId = getIdForExistingDocument(documentName.get());
            exportTAGML(context, store, tagView, fileName, docId);
            FileInfo fileInfo = context.getWatchedFiles().get(fileName);
            try {
              Path file = workFilePath(fileName);
              FileTime lastModifiedTime = Files.getLastModifiedTime(file);
              fileInfo.setLastCommit(lastModifiedTime.toInstant());
            } catch (IOException e) {
              e.printStackTrace();
            }

          } else {
            System.out.printf("%s is not linked to a document, not reverting.%n", fileName);
          }
        });
        storeContext(context);
      });
    }
    System.out.println("done!");
  }

}
