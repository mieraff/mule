/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.ast_manipulator;

import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.ast.api.ComponentParameterAst;
import org.mule.runtime.config.internal.ast_manipulator.expression_language.ExpressionLanguageAstService;

import java.util.List;

public class SetPayloadInOutResolver implements InOutResolver {

  private static final String VARS = "vars";
  private static final String VALUE_PARAM = "value";
  private static final String PAYLOAD = "payload";

  private final ExpressionLanguageAstService expressionLanguageAstService;

  public SetPayloadInOutResolver(ExpressionLanguageAstService expressionLanguageAstService) {
    this.expressionLanguageAstService = expressionLanguageAstService;
  }

  @Override
  public InOut resolve(ComponentAst componentAst) {
    InOut.Builder builder = new InOut.Builder();
    ComponentParameterAst parameterAst =
        componentAst.getParameters().stream().filter(p -> VALUE_PARAM.equals(p.getModel().getName())).findAny().get();

    // Expression value
    if (parameterAst.getValue().isLeft()) {
      String expression = parameterAst.getValue().getLeft();
      List<String> inputs = expressionLanguageAstService.getInputs(expression);
      inputs.forEach(input -> builder.withIn(VARS + "." + input));
    }
    // variable name output
    builder.withOut(PAYLOAD);
    return builder.build();
  }
}
