/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.loader.java;

import static java.lang.String.format;
import static org.mule.runtime.api.meta.model.connection.ConnectionManagementType.CACHED;
import static org.mule.runtime.api.meta.model.connection.ConnectionManagementType.NONE;
import static org.mule.runtime.api.meta.model.connection.ConnectionManagementType.POOLING;
import static org.mule.runtime.module.extension.internal.ExtensionProperties.DEFAULT_CONNECTION_PROVIDER_NAME;
import org.mule.runtime.api.connection.CachedConnectionProvider;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.connection.PoolingConnectionProvider;
import org.mule.runtime.api.meta.model.connection.ConnectionManagementType;
import org.mule.runtime.api.meta.model.declaration.fluent.ConnectionProviderDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.HasConnectionProviderDeclarer;
import org.mule.runtime.connectivity.api.platform.schema.extension.ExcludeFromConnectivitySchemaModelProperty;
import org.mule.runtime.extension.api.annotation.connectivity.oauth.AuthorizationCode;
import org.mule.runtime.extension.api.annotation.connectivity.oauth.ClientCredentials;
import org.mule.runtime.extension.api.connectivity.NoConnectivityTest;
import org.mule.runtime.extension.api.connectivity.oauth.AuthorizationCodeGrantType;
import org.mule.runtime.extension.api.connectivity.oauth.ClientCredentialsGrantType;
import org.mule.runtime.extension.api.connectivity.oauth.OAuthGrantType;
import org.mule.runtime.extension.api.connectivity.oauth.OAuthModelProperty;
import org.mule.runtime.extension.api.exception.IllegalConnectionProviderModelDefinitionException;
import org.mule.runtime.module.extension.api.loader.java.type.ConnectionProviderElement;
import org.mule.runtime.module.extension.api.loader.java.type.Type;
import org.mule.runtime.module.extension.api.loader.java.type.WithConnectionProviders;
import org.mule.runtime.module.extension.internal.loader.java.property.ConnectionProviderFactoryModelProperty;
import org.mule.runtime.module.extension.internal.loader.java.property.ConnectionTypeModelProperty;
import org.mule.runtime.module.extension.internal.loader.java.property.ImplementingTypeModelProperty;
import org.mule.runtime.module.extension.internal.loader.java.type.property.ExtensionTypeDescriptorModelProperty;
import org.mule.runtime.module.extension.internal.loader.utils.ParameterDeclarationContext;
import org.mule.sdk.api.annotation.semantics.connectivity.ExcludeFromConnectivitySchema;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Helper class for declaring connection providers through a {@link DefaultJavaModelLoaderDelegate}
 *
 * @since 4.0
 */
final class ConnectionProviderModelLoaderDelegate extends AbstractModelLoaderDelegate {

  private static final String CONNECTION_PROVIDER = "Connection Provider";

  private final Map<ConnectionProviderElement, ConnectionProviderDeclarer> connectionProviderDeclarers = new HashMap<>();

  ConnectionProviderModelLoaderDelegate(DefaultJavaModelLoaderDelegate loader) {
    super(loader);
  }

  void declareConnectionProviders(HasConnectionProviderDeclarer declarer,
                                  WithConnectionProviders withConnectionProviders) {
    withConnectionProviders.getConnectionProviders().forEach(provider -> declareConnectionProvider(declarer, provider));
  }

  private void declareConnectionProvider(HasConnectionProviderDeclarer declarer, ConnectionProviderElement providerType) {
    ConnectionProviderDeclarer providerDeclarer = connectionProviderDeclarers.get(providerType);
    if (providerDeclarer != null) {
      declarer.withConnectionProvider(providerDeclarer);
      return;
    }

    String name = providerType.getAlias();
    String description = providerType.getDescription();

    if (providerType.getName().equals(providerType.getAlias())) {
      name = DEFAULT_CONNECTION_PROVIDER_NAME;
    }

    List<Type> providerGenerics = providerType.getSuperTypeGenerics(ConnectionProvider.class);

    if (providerGenerics.size() != 1) {
      // TODO: MULE-9220: Add a syntax validator for this
      throw new IllegalConnectionProviderModelDefinitionException(
                                                                  format("Connection provider class '%s' was expected to have 1 generic type "
                                                                      + "(for the connection type) but %d were found",
                                                                         providerType.getName(), providerGenerics.size()));
    }

    providerDeclarer = declarer.withConnectionProvider(name).describedAs(description);
    ConnectionProviderDeclarer finalProviderDeclarer = providerDeclarer;
    providerType.getDeclaringClass().ifPresent(clazz -> finalProviderDeclarer
        .withModelProperty(new ConnectionProviderFactoryModelProperty(new DefaultConnectionProviderFactory<>(clazz,
                                                                                                             getExtensionClassLoader())))
        .withModelProperty(new ImplementingTypeModelProperty(clazz)));

    providerDeclarer
        .withModelProperty(new ConnectionTypeModelProperty(providerGenerics.get(0)))
        .withModelProperty(new ExtensionTypeDescriptorModelProperty(providerType));

    if (providerType.isAnnotatedWith(ExcludeFromConnectivitySchema.class)) {
      providerDeclarer.withModelProperty(new ExcludeFromConnectivitySchemaModelProperty());
    }

    loader.parseExternalLibs(providerType, providerDeclarer);

    ConnectionManagementType managementType = NONE;
    if (providerType.isAssignableTo(PoolingConnectionProvider.class)) {
      managementType = POOLING;
    } else if (providerType.isAssignableTo(CachedConnectionProvider.class)) {
      managementType = CACHED;
    }

    parseOAuthGrantType(providerType, providerDeclarer);

    providerDeclarer.withConnectionManagementType(managementType);
    providerDeclarer.supportsConnectivityTesting(!providerType.isAssignableTo(NoConnectivityTest.class));
    connectionProviderDeclarers.put(providerType, providerDeclarer);
    ParameterDeclarationContext context = new ParameterDeclarationContext(CONNECTION_PROVIDER, providerDeclarer.getDeclaration());
    loader.getFieldParametersLoader().declare(providerDeclarer, providerType.getParameters(), context);
  }

  private void parseOAuthGrantType(ConnectionProviderElement providerType, ConnectionProviderDeclarer providerDeclarer) {
    List<OAuthGrantType> grantTypes = new LinkedList<>();
    providerType.getAnnotation(AuthorizationCode.class).ifPresent(a -> {
      grantTypes.add(new AuthorizationCodeGrantType(a.accessTokenUrl(),
                                                    a.authorizationUrl(),
                                                    a.accessTokenExpr(),
                                                    a.expirationExpr(),
                                                    a.refreshTokenExpr(),
                                                    a.defaultScopes()));

    });

    providerType.getAnnotation(ClientCredentials.class).ifPresent(a -> {
      grantTypes.add(new ClientCredentialsGrantType(a.tokenUrl(),
                                                    a.accessTokenExpr(),
                                                    a.expirationExpr(),
                                                    a.defaultScopes(),
                                                    a.credentialsPlacement()));

    });

    if (!grantTypes.isEmpty()) {
      providerDeclarer.withModelProperty(new OAuthModelProperty(grantTypes));
    }
  }
}
