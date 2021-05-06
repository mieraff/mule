/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal;

import static java.lang.System.lineSeparator;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.mule.runtime.ast.api.util.MuleArtifactAstCopyUtils.copyRecursively;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.metadata.ExpressionLanguageMetadataService;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.util.BaseComponentAstDecorator;
import org.mule.runtime.config.api.dsl.ArtifactDeclarationXmlSerializer;
import org.mule.runtime.config.internal.dsl.model.XmlArtifactDeclarationLoader;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;

public class StaticAstManipulator {

  private static final Logger LOGGER = getLogger(StaticAstManipulator.class);

  // @Inject
  // TODO Inject this
  private final ExpressionLanguageMetadataService dwMetadata;

  public StaticAstManipulator(ExpressionLanguageMetadataService dwMetadata) {
    this.dwMetadata = dwMetadata;
  }

  public ArtifactAst optimizeStaticAst(ArtifactAst ast) {
    // 1. sub-flow inline (rodro)

    ArtifactAst subFlowsInlinedAst = copyRecursively(ast, comp -> {

      final List<ComponentAst> staticSubFlowRefs = comp.directChildrenStream()
          .filter(child -> isStaticSubFlowReference(ast, child))
          .filter(child -> child.getParameter("target").isDefaultValue())
          .collect(toList());

      if (staticSubFlowRefs.isEmpty()) {
        return comp;
      }

      return new BaseComponentAstDecorator(comp) {

        @Override
        public Stream<ComponentAst> directChildrenStream() {

          return super.directChildrenStream()
              .flatMap(child -> {
                if (isStaticSubFlowReference(ast, child)) {
                  String refName = (String) child.getParameter("name").getValue().getRight();
                  final ComponentAst subFlow = ast.topLevelComponentsStream()
                      .filter(tl -> tl.getIdentifier().getName().equals("sub-flow") && tl.getComponentId().get().equals(refName))
                      .findAny().get();

                  // TODO handle the case where the flowRef has target/targetValue
                  return subFlow.directChildrenStream();
                } else {
                  return Stream.of(child);
                }
              });
        }

      };
    });



    // 2. get the inputs/output of every processor (lisch)

    // Not just a list, we need a map ComponentAst -> {inputs, outputs}
    final List<ComponentAst> compactableElements = subFlowsInlinedAst.recursiveStream()
        .filter(comp -> comp.getIdentifier().getName().equals("set-variable")
            || comp.getIdentifier().getName().equals("set-payload"))

        .map(c -> {
          // left value is expression
          // c.getParameters().stream().forEach(p -> p.getValue().mapLeft(null));

          return c;
        })

        .collect(toList());

    // 3a. Determine segments (rodro)

    List<List<ComponentAst>> compactableElementsSegments = singletonList(compactableElements);
    // 3b. create a dependency graph for every flow segment based on the inputs/outputs (rodro)
    // 4. ...

    // x. generate and log the modified xml for reference. (rodro)

    DslResolvingContext dslContext = DslResolvingContext.getDefault(ast.dependencies());
    LOGGER.error("Manipulated app:" + lineSeparator()
        + ArtifactDeclarationXmlSerializer.getDefault(dslContext)
            .serialize(XmlArtifactDeclarationLoader.getDefault(dslContext).load(subFlowsInlinedAst)));

    return subFlowsInlinedAst;
  }

  protected boolean isStaticSubFlowReference(ArtifactAst ast, ComponentAst child) {
    if (child.getIdentifier().getName().equals("flow-ref")) {

      if (child.getParameter("name").getValue().isRight()) {
        String refName = (String) child.getParameter("name").getValue().getRight();

        final Optional<ComponentAst> subFlow = ast.topLevelComponentsStream()
            .filter(tl -> tl.getIdentifier().getName().equals("sub-flow") && tl.getComponentId().get().equals(refName))
            .findAny();

        if (subFlow.isPresent()) {
          return true;
        }
      }
    }
    return false;
  }

}
