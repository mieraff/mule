/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.metadata.ExpressionLanguageMetadataService;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.weave.v2.el.metadata.WeaveExpressionLanguageMetadataServiceImpl;

import java.util.List;

import org.slf4j.Logger;

public class StaticAstManipulator {

  private static final Logger LOGGER = getLogger(StaticAstManipulator.class);

  // @Inject
  // TODO Inject this
  private final ExpressionLanguageMetadataService dwMetadata = new WeaveExpressionLanguageMetadataServiceImpl();

  public ArtifactAst optimizeStaticAst(ArtifactAst ast) {
    // 1. sub-flow inline (rodro)
    // 2. get the inputs/output of every processor (lisch)

    // Not just a list, we need a map ComponentAst -> {inputs, outputs}
    final List<ComponentAst> compactableElements = ast.recursiveStream()
        .filter(comp -> comp.getIdentifier().getName().equals("set-variable")
            || comp.getIdentifier().getName().equals("set-payload"))

        .map(c -> {
          // left value is expression
          c.getParameters().stream().forEach(p -> p.getValue().mapLeft(null));

          return c;
        })

        .collect(toList());

    // 3a. Determine segments (rodro)

    List<List<ComponentAst>> compactableElementsSegments = singletonList(compactableElements);
    // 3b. create a dependency graph for every flow segment based on the inputs/outputs (rodro)
    // 4. ...
    return ast;

    // x. generate and log the modified xml for reference. (rodro)
  }

}
