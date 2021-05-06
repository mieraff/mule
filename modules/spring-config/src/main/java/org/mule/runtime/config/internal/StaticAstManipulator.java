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
import static java.util.stream.Collectors.toMap;
import static org.mule.runtime.api.component.TypedComponentIdentifier.ComponentType.OPERATION;
import static org.mule.runtime.ast.api.util.MuleArtifactAstCopyUtils.copyRecursively;
import static org.mule.runtime.internal.dsl.DslConstants.EE_NAMESPACE;
import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.component.ComponentIdentifier;
import org.mule.metadata.message.api.el.ExpressionLanguageMetadataTypeResolver;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.metadata.ExpressionLanguageMetadataService;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.ComponentParameterAst;
import org.mule.runtime.ast.api.builder.ComponentAstBuilder;
import org.mule.runtime.ast.api.util.BaseComponentAstDecorator;
import org.mule.runtime.ast.internal.builder.LightComponentAstBuilder;
import org.mule.runtime.config.api.dsl.ArtifactDeclarationXmlSerializer;
import org.mule.runtime.config.internal.ast_manipulator.InOut;
import org.mule.runtime.config.internal.ast_manipulator.SetVariableInOutResolver;
import org.mule.runtime.config.internal.dsl.model.XmlArtifactDeclarationLoader;
import org.mule.runtime.dsl.api.component.config.DefaultComponentLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;

public class StaticAstManipulator {

  private static final Logger LOGGER = getLogger(StaticAstManipulator.class);

  private static final String SET_VARIABLE = "set-variable";
  private static final String SET_PAYLOAD = "set-payload";

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

    // 2. Determine segments (rodro)
    List<List<ComponentAst>> segments = subFlowsInlinedAst.recursiveStream()
        .flatMap(comp -> determineSegments(comp).stream())
        .collect(toList());
    LOGGER.error("Found segments: " + segments);


    // 3. get the inputs/output of every processor (lisch)
    Map<ComponentAst, InOut> componentAstInOutMap = determinateInputOutput(subFlowsInlinedAst);
    /*
     * // Not just a list, we need a map ComponentAst -> {inputs, outputs} final List<ComponentAst> compactableElements =
     * subFlowsInlinedAst.recursiveStream() .filter(comp -> comp.getIdentifier().getName().equals("set-variable") ||
     * comp.getIdentifier().getName().equals("set-payload"))
     * 
     * .map(c -> { // left value is expression // c.getParameters().stream().forEach(p -> p.getValue().mapLeft(null));
     * 
     * return c; })
     * 
     * .collect(toList());
     */

    // 4. create a dependency graph for every segment based on the inputs/outputs (rodro)


    ast.dependencies().stream().filter(extModel -> extModel.getName().equals("ee")).findAny()
        .ifPresent(eeExt -> {
          final OperationModel transformOperationModel = eeExt.getOperationModel("transform").get();

          final ComponentAstBuilder eeTransfromBuilder = new LightComponentAstBuilder()
              // TODO generate a more meaningful location
              .withLocation(DefaultComponentLocation.from("(astManipulation)"))
              .withIdentifier(ComponentIdentifier.builder().name("transform").namespace("ee").namespaceUri(EE_NAMESPACE).build())
              .withComponentType(OPERATION)
              .withExtensionModel(eeExt)
              .withParameterizedModel(transformOperationModel);


          eeTransfromBuilder.with
        });

    // 5. ...

    // x. generate and log the modified xml for reference. (rodro)

    DslResolvingContext dslContext = DslResolvingContext.getDefault(ast.dependencies());
    LOGGER.error("Manipulated app:" + lineSeparator()
        + ArtifactDeclarationXmlSerializer.getDefault(dslContext)
            .serialize(XmlArtifactDeclarationLoader.getDefault(dslContext).load(subFlowsInlinedAst)));

    return subFlowsInlinedAst;
  }

  protected List<List<ComponentAst>> determineSegments(ComponentAst comp) {
    List<List<ComponentAst>> foundSegments = new ArrayList<>();
    List<ComponentAst> currentSegment = new ArrayList<>();

    for (ComponentAst child : comp.directChildrenStream().collect(toList())) {
      if (child.getComponentType().equals(OPERATION)) {
        currentSegment.add(child);
      }

      // do a control break on anything that is not an operation to determine the boundaries of each segment.
      if (!child.getComponentType().equals(OPERATION)) {
        // a segments ends here
        if (!currentSegment.isEmpty()) {
          foundSegments.add(currentSegment);
          currentSegment = new ArrayList<>();
        }

        // do a recursion to determine segments in nested scopes/routers.
        foundSegments.addAll(determineSegments(child));
      }
    }

    // store the data for the last segment
    if (!currentSegment.isEmpty()) {
      foundSegments.add(currentSegment);
      currentSegment = new ArrayList<>();
    }

    return foundSegments;
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

  private Map<ComponentAst, InOut> determinateInputOutput(ArtifactAst ast) {
    return new HashMap<>();
    /*
     * final List<ComponentAst> setVariables = ast.recursiveStream() .filter(comp ->
     * SET_VARIABLE.equals(comp.getIdentifier().getName())) .collect(toList());
     * 
     * Map<ComponentAst, InOut> outputs = setVariables.stream().collect(toMap(componentAst -> componentAst,
     * this::determinateInputOutSetVariableComponent)); return outputs;
     */
  }
  /*
   * private InOut determinateInputOutSetVariableComponent(ComponentAst componentAst) { InOut inOut = new
   * SetVariableInOutResolver(this.dwMetadata).resolve(componentAst); return inOut; }
   */
}
