/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.tooling.internal.artifact.value;

import static com.google.common.base.Throwables.propagateIfPossible;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.mule.runtime.api.metadata.resolving.FailureCode.COMPONENT_NOT_FOUND;
import static org.mule.runtime.api.value.ResolvingFailure.Builder.newFailure;
import static org.mule.runtime.api.value.ValueResult.resultFrom;
import static org.mule.runtime.core.api.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.extension.api.values.ValueResolvingException.INVALID_VALUE_RESOLVER_NAME;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.getClassLoader;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.parameter.ParameterizedModel;
import org.mule.runtime.api.meta.model.parameter.ValueProviderModel;
import org.mule.runtime.api.value.ResolvingFailure;
import org.mule.runtime.api.value.Value;
import org.mule.runtime.api.value.ValueResult;
import org.mule.runtime.app.declaration.api.ComponentElementDeclaration;
import org.mule.runtime.app.declaration.api.ParameterizedElementDeclaration;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.connector.ConnectionManager;
import org.mule.runtime.core.api.el.ExpressionManager;
import org.mule.runtime.core.api.util.func.CheckedSupplier;
import org.mule.runtime.extension.api.runtime.config.ConfigurationInstance;
import org.mule.runtime.extension.api.values.ValueResolvingException;
import org.mule.runtime.module.extension.internal.ExtensionResolvingContext;
import org.mule.runtime.module.extension.internal.runtime.resolver.ParameterValueResolver;
import org.mule.runtime.module.extension.internal.util.ReflectionCache;
import org.mule.runtime.module.extension.internal.value.ValueProviderMediator;
import org.mule.runtime.module.tooling.internal.artifact.AbstractParameterResolverExecutor;
import org.mule.runtime.module.tooling.internal.artifact.ExecutorExceptionWrapper;
import org.mule.runtime.module.tooling.internal.artifact.params.ExpressionNotSupportedException;
import org.mule.runtime.module.tooling.internal.utils.ArtifactHelper;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValueProviderExecutor extends AbstractParameterResolverExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ValueProviderExecutor.class);

  private final ConnectionManager connectionManager;

  public ValueProviderExecutor(MuleContext muleContext, ConnectionManager connectionManager,
                               ExpressionManager expressionManager, ReflectionCache reflectionCache,
                               ArtifactHelper artifactHelper) {
    super(muleContext, expressionManager, reflectionCache, artifactHelper);
    this.connectionManager = connectionManager;
  }

  public ValueResult resolveValues(ParameterizedModel parameterizedModel,
                                   ParameterizedElementDeclaration parameterizedElementDeclaration, String providerName) {
    try {
      return doResolverValues(parameterizedModel, parameterizedElementDeclaration, providerName,
                              getValueProviderModel(parameterizedModel, providerName));
    } catch (ValueResolvingException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(format("Resolve value provider has FAILED with code: %s for component: %s", e.getFailureCode(),
                           parameterizedModel.getName()),
                    e);
      }
      return resultFrom(newFailure(e).withFailureCode(e.getFailureCode()).build());
    }
  }

  protected ValueResult doResolverValues(ParameterizedModel parameterizedModel,
                                         ParameterizedElementDeclaration parameterizedElementDeclaration, String providerName,
                                         ValueProviderModel valueProviderModel) {
    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Resolve value provider: {} STARTED for component: {}", providerName, parameterizedModel.getName());
      }
      Optional<ConfigurationInstance> optionalConfigurationInstance =
          getConfigurationInstance(parameterizedElementDeclaration, valueProviderModel);

      ParameterValueResolver parameterValueResolver = parameterValueResolver(parameterizedElementDeclaration, parameterizedModel);
      ValueProviderMediator valueProviderMediator = createValueProviderMediator(parameterizedModel);

      ExtensionResolvingContext context = new ExtensionResolvingContext(() -> optionalConfigurationInstance,
                                                                        connectionManager);
      ClassLoader extensionClassLoader = getClassLoader(artifactHelper.getExtensionModel(parameterizedElementDeclaration));
      try {
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Invoking connector's value provider: {} for component: {}", providerName,
                       parameterizedModel.getName());
        }
        return resultFrom(withContextClassLoader(extensionClassLoader,
                                                 () -> getValues(valueProviderModel, parameterValueResolver,
                                                                 valueProviderMediator,
                                                                 context),
                                                 ValueResolvingException.class,
                                                 e -> {
                                                   throw new ExecutorExceptionWrapper(e);
                                                 }));
      } finally {
        context.dispose();
      }
    } catch (ValueResolvingException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(format("Resolve value provider has FAILED with code: %s for component: %s", e.getFailureCode(),
                           parameterizedModel.getName()),
                    e);
      }
      return resultFrom(newFailure(e).withFailureCode(e.getFailureCode()).build());
    } catch (ExpressionNotSupportedException e) {
      return resultFrom(newFailure(new ValueResolvingException(e.getMessage(), INVALID_PARAMETER_VALUE))
          .withFailureCode(INVALID_PARAMETER_VALUE).build());
    } catch (ExecutorExceptionWrapper e) {
      Throwable cause = e.getCause();
      if (cause instanceof ValueResolvingException) {
        ValueResolvingException valueResolvingException = (ValueResolvingException) cause;
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(format("Resolve value provider has FAILED with code: %s for component: %s",
                             valueResolvingException.getFailureCode(), parameterizedModel.getName()),
                      cause);
        }
        ResolvingFailure.Builder failureBuilder = newFailure(cause);
        failureBuilder.withFailureCode(valueResolvingException.getFailureCode());
        return resultFrom(failureBuilder.build());
      }
      propagateIfPossible(cause, MuleRuntimeException.class);
      throw new MuleRuntimeException(cause);
    } catch (Exception e) {
      propagateIfPossible(e, MuleRuntimeException.class);
      throw new MuleRuntimeException(e);
    } finally {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Resolve value provider: {} FINISHED for component: {}", providerName, parameterizedModel.getName());
      }

    }
  }

  protected Set<Value> getValues(ValueProviderModel valueProviderModel, ParameterValueResolver parameterValueResolver,
                                 ValueProviderMediator valueProviderMediator, ExtensionResolvingContext context)
      throws ValueResolvingException {
    return valueProviderMediator.getValues(valueProviderModel.getProviderName(),
                                           parameterValueResolver,
                                           connectionSupplier(context),
                                           configSupplier(context),
                                           context.getConnectionProvider().orElse(null));
  }

  protected Supplier<Object> connectionSupplier(ExtensionResolvingContext context) {
    return (CheckedSupplier<Object>) () -> context.getConnection().orElse(null);
  }

  protected Supplier<Object> configSupplier(ExtensionResolvingContext context) {
    return (CheckedSupplier<Object>) () -> context.getConfig().orElse(null);
  }

  private Optional<ConfigurationInstance> getConfigurationInstance(ParameterizedElementDeclaration parameterizedElementDeclaration,
                                                                   ValueProviderModel valueProviderModel)
      throws ValueResolvingException {
    Optional<String> optionalConfigRef = getConfigRef(parameterizedElementDeclaration);
    Optional<ConfigurationInstance> optionalConfigurationInstance =
        optionalConfigRef.flatMap(artifactHelper::getConfigurationInstance);

    if (optionalConfigRef.isPresent()) {
      if (valueProviderModel.requiresConfiguration() && !optionalConfigurationInstance.isPresent()) {
        // Improves the error message when configuration is required and not present, as we do the resolve parameter with lazyInit
        // in order
        // to avoid getting an error when a required parameter from model is not defined for resolving the value provider.
        throw new ValueResolvingException(format("The provider requires a configuration but the one referenced by element declaration with name: '%s' is not present",
                                                 optionalConfigRef.get()),
                                          COMPONENT_NOT_FOUND.getName());
      }
    }

    return optionalConfigurationInstance;
  }

  private ValueProviderModel getValueProviderModel(ParameterizedModel parameterizedModel, String providerName)
      throws ValueResolvingException {
    return parameterizedModel.getAllParameterModels().stream()
        .filter(parameterModel -> parameterModel.getValueProviderModel()
            .map(vpm -> vpm.getProviderName().equals(providerName)).orElse(false))
        .findFirst().flatMap(parameterModel -> parameterModel.getValueProviderModel())
        .orElseThrow(() -> new ValueResolvingException(format("Unable to find value provider model for parameter or parameter group with name '%s'.",
                                                              providerName),
                                                       INVALID_VALUE_RESOLVER_NAME));
  }

  private ValueProviderMediator createValueProviderMediator(ParameterizedModel parameterizedModel) {
    return new ValueProviderMediator(parameterizedModel,
                                     () -> muleContext,
                                     () -> reflectionCache);
  }

  private Optional<String> getConfigRef(ParameterizedElementDeclaration component) {
    if (component instanceof ComponentElementDeclaration) {
      return ofNullable(((ComponentElementDeclaration) component).getConfigRef());
    }
    return empty();
  }

}
