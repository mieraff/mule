/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.ast_manipulator;

import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.api.model.MetadataFormat;
import org.mule.metadata.message.api.MuleEventMetadataType;
import org.mule.metadata.message.api.MuleEventMetadataTypeBuilder;
import org.mule.runtime.api.metadata.ExpressionLanguageMetadataService;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.ComponentParameterAst;

public class SetVariableInOutResolver implements InOutResolver {

  private static final String VALUE_PARAM = "value";

  private final ExpressionLanguageMetadataService expressionLanguageMetadataService;

  public SetVariableInOutResolver(ExpressionLanguageMetadataService expressionLanguageMetadataService) {
    this.expressionLanguageMetadataService = expressionLanguageMetadataService;
  }

  public InOut resolve(ComponentAst componentAst) {
    InOut.Builder builder = new InOut.Builder();
    ComponentParameterAst valueSetVariable =
        componentAst.getParameters().stream().filter(p -> VALUE_PARAM.equals(p.getModel().getName())).findAny().get();

    if (valueSetVariable.getValue().isLeft()) {
      // Expression value
      String expression = valueSetVariable.getValue().getLeft();
      MuleEventMetadataTypeBuilder metadataBuilder = new MuleEventMetadataTypeBuilder();
      expressionLanguageMetadataService.getInputType(expression, BaseTypeBuilder.create(MetadataFormat.JAVA).anyType().build(),
                                                     metadataBuilder, createMessageCallback());
      MuleEventMetadataType metadataType = metadataBuilder.build();
      // metadataType.getVariables().getFields().stream().forEach(builder::withIn);
      // builder.withIn(variablesNames)
    } else {
      // Non Expression value
    }
    // variable name output
    return builder.build();
  }

  private ExpressionLanguageMetadataService.MessageCallback createMessageCallback() {
    return new ExpressionLanguageSimpleMessageCallback();
  }
}
