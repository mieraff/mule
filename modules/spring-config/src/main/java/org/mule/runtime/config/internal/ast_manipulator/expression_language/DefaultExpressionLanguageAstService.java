/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal.ast_manipulator.expression_language;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultExpressionLanguageAstService implements ExpressionLanguageAstService {

  private static final Map<String, List<String>> SCRIPTS;

  static {
    Map<String, List<String>> scripts = new HashMap<>();
    cacheScript(scripts, "vars.non_dependency_var + 1", singletonList("non_dependency_var"));
    cacheScript(scripts, "vars.non_expression_var ++ ' ' ++ vars.expression_var", asList("non_dependency_var", "expression_var"));
    SCRIPTS = unmodifiableMap(scripts);
  }

  private static void cacheScript(Map<String, List<String>> scripts, String script, List<String> inputs) {
    scripts.putIfAbsent(script, inputs);
  }

  @Override
  public List<String> getInputs(String script) {
    return SCRIPTS.getOrDefault(script, emptyList());
  }
}
