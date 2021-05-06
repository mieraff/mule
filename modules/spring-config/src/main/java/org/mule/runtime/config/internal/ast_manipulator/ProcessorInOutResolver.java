/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.ast_manipulator;

import static org.mule.runtime.config.internal.ast_manipulator.expression_language.ExpressionLanguageAstServiceFactory.getDefault;

import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.config.internal.ast_manipulator.expression_language.ExpressionLanguageAstService;

public class ProcessorInOutResolver {

  private static final String SET_VARIABLE = "set-variable";
  private static final String SET_PAYLOAD = "set-payload";

  private final ExpressionLanguageAstService expressionLanguageAstService = getDefault();

  public InOut resolve(ComponentAst componentAst) {
    if (SET_VARIABLE.equals(componentAst.getIdentifier().getName())) {
      return new SetVariableInOutResolver(this.expressionLanguageAstService).resolve(componentAst);
    } else if (SET_PAYLOAD.equals(componentAst.getIdentifier().getName())) {
      return new SetPayloadInOutResolver(this.expressionLanguageAstService).resolve(componentAst);
    } else {
      return null;
    }
  }
}
