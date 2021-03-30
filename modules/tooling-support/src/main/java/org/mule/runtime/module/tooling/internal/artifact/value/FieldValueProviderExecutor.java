/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.tooling.internal.artifact.value;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.mule.runtime.api.value.ResolvingFailure.Builder.newFailure;
import static org.mule.runtime.api.value.ValueResult.resultFrom;
import static org.mule.runtime.extension.api.values.ValueResolvingException.INVALID_VALUE_RESOLVER_NAME;

import org.mule.runtime.api.functional.Either;
import org.mule.runtime.api.meta.model.parameter.FieldValueProviderModel;
import org.mule.runtime.api.meta.model.parameter.ParameterModel;
import org.mule.runtime.api.meta.model.parameter.ParameterizedModel;
import org.mule.runtime.api.meta.model.parameter.ValueProviderModel;
import org.mule.runtime.api.value.Value;
import org.mule.runtime.api.value.ValueResult;
import org.mule.runtime.app.declaration.api.ParameterizedElementDeclaration;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.connector.ConnectionManager;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.extension.api.values.ValueResolvingException;
import org.mule.runtime.module.extension.internal.ExtensionResolvingContext;
import org.mule.runtime.module.extension.internal.runtime.resolver.ParameterValueResolver;
import org.mule.runtime.module.extension.internal.util.ReflectionCache;
import org.mule.runtime.module.extension.internal.value.ValueProviderMediator;
import org.mule.runtime.module.tooling.internal.utils.ArtifactHelper;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FieldValueProviderExecutor extends ValueProviderExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(FieldValueProviderExecutor.class);

  public FieldValueProviderExecutor(MuleContext muleContext, ConnectionManager connectionManager,
                                    ExpressionManager expressionManager, ReflectionCache reflectionCache,
                                    ArtifactHelper artifactHelper) {
    super(muleContext, connectionManager, expressionManager, reflectionCache, artifactHelper);
  }

  public ValueResult resolveValues(ParameterizedModel parameterizedModel,
                                   ParameterizedElementDeclaration parameterizedElementDeclaration, String providerName,
                                   String targetPath) {
    try {
      return doResolverValues(parameterizedModel, parameterizedElementDeclaration, providerName,
                              getFieldValueProviderModel(parameterizedModel, providerName, targetPath));
    } catch (ValueResolvingException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(format("Resolve field value provider has FAILED with code: %s for component: %s", e.getFailureCode(),
                           parameterizedModel.getName()),
                    e);
      }
      return resultFrom(newFailure(e).withFailureCode(e.getFailureCode()).build());
    }
  }

  @Override
  protected Set<Value> getValues(ValueProviderModel valueProviderModel, ParameterValueResolver parameterValueResolver,
                                 ValueProviderMediator valueProviderMediator, ExtensionResolvingContext context)
      throws ValueResolvingException {
    return valueProviderMediator.getValues(valueProviderModel.getProviderName(),
                                           parameterValueResolver,
                                           ((FieldValueProviderModel) valueProviderModel).getFieldPath(),
                                           connectionSupplier(context),
                                           configSupplier(context),
                                           context.getConnectionProvider().orElse(null));
  }

  private FieldValueProviderModel getFieldValueProviderModel(ParameterizedModel parameterizedModel, String providerName,
                                                             String targetPath)
      throws ValueResolvingException {
    Iterator<ParameterModel> parameterModelIterator = parameterizedModel.getAllParameterModels().iterator();
    Optional<FieldValueProviderModel> model = ofNullable(null);
    while (parameterModelIterator.hasNext() && !model.isPresent()) {
      Optional<Either<ValueProviderModel, List<FieldValueProviderModel>>> valueProviderModels =
          parameterModelIterator.next().getValueProviderModels();
      if (valueProviderModels.isPresent()
          && valueProviderModels.map(valueProviderModelListEither -> valueProviderModelListEither.isRight()).orElse(false)) {
        model = valueProviderModels.get().getRight().stream()
            .filter(fieldValueProviderModel -> fieldValueProviderModel.getProviderName().equals(providerName))
            .filter(fieldValueProviderModel -> fieldValueProviderModel.getFieldPath().equals(targetPath))
            .findFirst();
      }
    }
    return model
        .orElseThrow(() -> new ValueResolvingException(format("Unable to find field value provider model for parameter or parameter group with name '%s' and targetPath '%s'.",
                                                              providerName, targetPath),
                                                       INVALID_VALUE_RESOLVER_NAME));
  }
}
