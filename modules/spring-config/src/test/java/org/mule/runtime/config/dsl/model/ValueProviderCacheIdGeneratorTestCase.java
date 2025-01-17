/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.dsl.model;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.mule.runtime.app.declaration.api.component.location.Location.builderFromStringRepresentation;

import org.mule.runtime.api.component.location.Location;
import org.mule.runtime.api.meta.model.parameter.ParameterizedModel;
import org.mule.runtime.app.declaration.api.ArtifactDeclaration;
import org.mule.runtime.app.declaration.api.ConfigurationElementDeclaration;
import org.mule.runtime.app.declaration.api.ElementDeclaration;
import org.mule.runtime.app.declaration.api.ParameterElementDeclaration;
import org.mule.runtime.app.declaration.api.ParameterizedElementDeclaration;
import org.mule.runtime.app.declaration.api.fluent.ParameterSimpleValue;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.config.api.dsl.model.DslElementModel;
import org.mule.runtime.config.api.dsl.model.DslElementModelFactory;
import org.mule.runtime.config.api.dsl.model.metadata.ComponentAstBasedValueProviderCacheIdGenerator;
import org.mule.runtime.config.api.dsl.model.metadata.ComponentBasedValueProviderCacheIdGenerator;
import org.mule.runtime.config.api.dsl.model.metadata.DeclarationBasedValueProviderCacheIdGenerator;
import org.mule.runtime.config.api.dsl.model.metadata.DslElementBasedValueProviderCacheIdGenerator;
import org.mule.runtime.core.internal.locator.ComponentLocator;
import org.mule.runtime.core.internal.value.cache.ValueProviderCacheId;
import org.mule.runtime.core.internal.value.cache.ValueProviderCacheIdGenerator;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class ValueProviderCacheIdGeneratorTestCase extends AbstractMockedValueProviderExtensionTestCase {

  private DslElementModelFactory dslElementModelFactory;

  @Override
  public void before() {
    super.before();
    dslElementModelFactory = DslElementModelFactory.getDefault(dslContext);
  }

  private Optional<ValueProviderCacheId> computeIdFor(ArtifactDeclaration appDeclaration,
                                                      String location,
                                                      String parameterName)
      throws Exception {
    ArtifactAst app = loadApplicationModel(appDeclaration);
    Locator locator = new Locator(app);
    ComponentLocator<DslElementModel<?>> dslLocator =
        l -> getDeclaration(appDeclaration, l.toString()).map(d -> dslElementModelFactory.create(d).orElse(null));

    ComponentLocator<ElementDeclaration> declarationLocator =
        l -> appDeclaration.findElement(builderFromStringRepresentation(l.toString()).build());

    ValueProviderCacheIdGenerator<ComponentAst> componentAstBasedValueProviderCacheIdGenerator =
        new ComponentAstBasedValueProviderCacheIdGenerator(locator);
    ValueProviderCacheIdGenerator<ComponentAst> componentBasedValueProviderCacheIdGenerator =
        new ComponentBasedValueProviderCacheIdGenerator(dslContext, locator);
    ValueProviderCacheIdGenerator<DslElementModel<?>> dslElementModelValueProviderCacheIdGenerator =
        new DslElementBasedValueProviderCacheIdGenerator(dslLocator);
    ValueProviderCacheIdGenerator<ElementDeclaration> elementDeclarationValueProviderCacheIdGenerator =
        new DeclarationBasedValueProviderCacheIdGenerator(dslContext, declarationLocator);

    ComponentAst component = getComponentAst(app, location);
    DslElementModel<?> dslElementModel = dslLocator.get(Location.builderFromStringRepresentation(location).build())
        .orElseThrow(() -> new AssertionError("Could not create dslElementModel"));

    Optional<ParameterizedElementDeclaration> elementDeclaration =
        appDeclaration.findElement(builderFromStringRepresentation(location).build());
    Optional<ParameterizedModel> elementModel = component.getModel(ParameterizedModel.class);

    if (!elementDeclaration.isPresent() || !elementModel.isPresent()) {
      fail(format("missing declaration or model for: %s", location));
    }

    Optional<ValueProviderCacheId> dslElementId =
        dslElementModelValueProviderCacheIdGenerator.getIdForResolvedValues(dslElementModel, parameterName);
    Optional<ValueProviderCacheId> componentBasedId =
        componentBasedValueProviderCacheIdGenerator.getIdForResolvedValues(component, parameterName);
    Optional<ValueProviderCacheId> declarationBasedId =
        elementDeclarationValueProviderCacheIdGenerator.getIdForResolvedValues(elementDeclaration.get(), parameterName);
    Optional<ValueProviderCacheId> astId =
        componentAstBasedValueProviderCacheIdGenerator.getIdForResolvedValues(component, parameterName);

    checkIdsAreEqual(astId, dslElementId);
    checkIdsAreEqual(dslElementId, componentBasedId);
    checkIdsAreEqual(componentBasedId, declarationBasedId);

    // Any should be fine
    return dslElementId;
  }

  private Optional<ParameterizedElementDeclaration> getParameterElementDeclaration(ArtifactDeclaration artifactDeclaration,
                                                                                   String location) {
    AtomicBoolean isConnection = new AtomicBoolean(false);
    if (location.endsWith("/connection")) {
      isConnection.set(true);
      location = location.split("/connection")[0];
    }
    return artifactDeclaration.<ParameterizedElementDeclaration>findElement(builderFromStringRepresentation(location).build())
        .map(d -> isConnection.get() ? ((ConfigurationElementDeclaration) d).getConnection().orElse(null) : d);
  }

  private void modifyParameter(ArtifactDeclaration artifactDeclaration,
                               String ownerLocation,
                               String parameterName,
                               Consumer<ParameterElementDeclaration> parameterConsumer) {
    getParameterElementDeclaration(artifactDeclaration, ownerLocation)
        .map(
             owner -> owner.getParameterGroups()
                 .stream()
                 .flatMap(pg -> pg.getParameters().stream())
                 .filter(p -> p.getName().equals(parameterName))
                 .findAny()
                 .map(fp -> {
                   parameterConsumer.accept(fp);
                   return EMPTY; // Needed to avoid exception
                 })
                 .orElseThrow(() -> new RuntimeException("Could not find parameter to modify")))
        .orElseThrow(() -> new RuntimeException("Location not found"));
  }

  private void removeParameter(ArtifactDeclaration artifactDeclaration,
                               String ownerLocation,
                               String parameterName) {
    if (!getParameterElementDeclaration(artifactDeclaration, ownerLocation)
        .map(owner -> owner.getParameterGroups().stream()
            .filter(pg -> pg.getParameters().removeIf(p -> p.getName().equals(parameterName))).findAny())
        .orElseThrow(() -> new RuntimeException("Location not found")).isPresent()) {
      throw new RuntimeException("Could not remove parameter from component");
    }
  }

  @Test
  public void idForParameterWithNoProviderInConfig() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    assertThat(computeIdFor(app, MY_CONFIG, ACTING_PARAMETER_NAME).isPresent(), is(false));
  }

  @Test
  public void idForParameterWithNoProviderInSource() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    assertThat(computeIdFor(app, SOURCE_LOCATION, ACTING_PARAMETER_NAME).isPresent(), is(false));
  }

  @Test
  public void idForParameterWithNoProviderInOperation() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    assertThat(computeIdFor(app, OPERATION_LOCATION, ACTING_PARAMETER_NAME).isPresent(), is(false));
  }

  @Test
  public void idForConfigNoChanges() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> configId = computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME);
    assertThat(configId.isPresent(), is(true));
    checkIdsAreEqual(configId, computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME));
  }


  @Test
  public void idForConfigChangingNotActingParameters() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> configId = computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME);
    assertThat(configId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, PARAMETER_REQUIRED_FOR_METADATA_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(configId, computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfigChangingActingParameter() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> configId = computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME);
    assertThat(configId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(configId, computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfigChangingActingParameterInGroup() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> configId = computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME);
    assertThat(configId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, PARAMETER_IN_GROUP_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(configId, computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessOperationNoChanges() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    checkIdsAreEqual(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessOperationChangingActingParameter() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, OPERATION_LOCATION, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }


  @Test
  public void idForConfiglessAndConnectionlessOperationDefaultValueHashIdShouldBeSameWithExplicitValueOnActingParameter()
      throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    removeParameter(app, OPERATION_LOCATION, ACTING_PARAMETER_NAME);
    checkIdsAreEqual(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessOperationChangingActingParameterInGroup() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, OPERATION_LOCATION, PARAMETER_IN_GROUP_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessOperationChangesInConfig() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, PARAMETER_REQUIRED_FOR_METADATA_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    modifyParameter(app, MY_CONFIG, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessOperationChangesInConnection() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, MY_CONNECTION, PARAMETER_REQUIRED_FOR_METADATA_NAME,
                    p -> p.setValue(ParameterSimpleValue.of("newValue")));
    modifyParameter(app, MY_CONNECTION, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }


  @Test
  public void idForConfiglessAndConnectionlessSourceNoChanges() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    checkIdsAreEqual(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessSourceChangingActingParameter() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, SOURCE_LOCATION, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessSourceChangingActingParameterInGroup() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, SOURCE_LOCATION, PARAMETER_IN_GROUP_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessSourceChangesInConfig() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, PARAMETER_REQUIRED_FOR_METADATA_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    modifyParameter(app, MY_CONFIG, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfiglessAndConnectionlessSourceChangesInConnection() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, MY_CONNECTION, PARAMETER_REQUIRED_FOR_METADATA_NAME,
                    p -> p.setValue(ParameterSimpleValue.of("newValue")));
    modifyParameter(app, MY_CONNECTION, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfigAwareOperationChangesInConfigNotRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConfiguration()).thenReturn(true);
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfigAwareOperationChangesInConfigRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConfiguration()).thenReturn(true);
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, PARAMETER_REQUIRED_FOR_METADATA_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConnectionAwareOperationChangesInConnectionNotRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConnection()).thenReturn(true);
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, MY_CONNECTION, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConnectionAwareOperationChangesInConnectionRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConnection()).thenReturn(true);
    Optional<ValueProviderCacheId> opId = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(opId.isPresent(), is(true));
    modifyParameter(app, MY_CONNECTION, PARAMETER_REQUIRED_FOR_METADATA_NAME,
                    p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(opId, computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfigAwareSourceChangesInConfigNotRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConfiguration()).thenReturn(true);
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConfigAwareSourceChangesInConfigRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConfiguration()).thenReturn(true);
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, MY_CONFIG, PARAMETER_REQUIRED_FOR_METADATA_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConnectionAwareSourceChangesInConnectionNotRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConnection()).thenReturn(true);
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, MY_CONNECTION, ACTING_PARAMETER_NAME, p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreEqual(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void idForConnectionAwareSourceChangesInConnectionRequiredForMetadata() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConnection()).thenReturn(true);
    Optional<ValueProviderCacheId> sourceId = computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME);
    assertThat(sourceId.isPresent(), is(true));
    modifyParameter(app, MY_CONNECTION, PARAMETER_REQUIRED_FOR_METADATA_NAME,
                    p -> p.setValue(ParameterSimpleValue.of("newValue")));
    checkIdsAreDifferent(sourceId, computeIdFor(app, SOURCE_LOCATION, PROVIDED_PARAMETER_NAME));
  }

  @Test
  public void equalConfigsWithDifferentNameGetSameHash() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    ConfigurationElementDeclaration config = (ConfigurationElementDeclaration) app.getGlobalElements().get(0);
    app.addGlobalElement(declareConfig(config.getConnection().get(), "newName",
                                       PARAMETER_REQUIRED_FOR_METADATA_DEFAULT_VALUE,
                                       ACTING_PARAMETER_DEFAULT_VALUE,
                                       PROVIDED_PARAMETER_DEFAULT_VALUE,
                                       PARAMETER_IN_GROUP_DEFAULT_VALUE));
    Optional<ValueProviderCacheId> config1Id = computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME);
    Optional<ValueProviderCacheId> config2Id = computeIdFor(app, "newName", PROVIDED_PARAMETER_NAME);
    checkIdsAreEqual(config1Id, config2Id);
  }

  @Test
  public void differentConfigsWithSameParameterGetSameHash() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    ConfigurationElementDeclaration config = (ConfigurationElementDeclaration) app.getGlobalElements().get(0);
    app.addGlobalElement(declareOtherConfig(config.getConnection().get(), "newName",
                                            PARAMETER_REQUIRED_FOR_METADATA_DEFAULT_VALUE,
                                            ACTING_PARAMETER_DEFAULT_VALUE,
                                            PROVIDED_PARAMETER_DEFAULT_VALUE,
                                            PARAMETER_IN_GROUP_DEFAULT_VALUE));
    Optional<ValueProviderCacheId> config1Id = computeIdFor(app, MY_CONFIG, PROVIDED_PARAMETER_NAME);
    Optional<ValueProviderCacheId> config2Id = computeIdFor(app, "newName", PROVIDED_PARAMETER_NAME);
    checkIdsAreEqual(config1Id, config2Id);
  }

  @Test
  public void differentValueProviderNameGetsSameHash() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConnection()).thenReturn(true);
    when(valueProviderModel.requiresConfiguration()).thenReturn(true);
    Optional<ValueProviderCacheId> opId1 = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    when(valueProviderModel.getProviderName()).thenReturn("newValueProviderName");
    Optional<ValueProviderCacheId> opId2 = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    checkIdsAreEqual(opId1, opId2);
  }

  @Test
  public void differentValueProviderIdGetsDifferentHash() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    when(valueProviderModel.requiresConnection()).thenReturn(true);
    when(valueProviderModel.requiresConfiguration()).thenReturn(true);
    Optional<ValueProviderCacheId> opId1 = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    when(valueProviderModel.getProviderId()).thenReturn("newValueProviderId");
    Optional<ValueProviderCacheId> opId2 = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    checkIdsAreDifferent(opId1, opId2);
  }

  @Test
  public void differentOperationsWithSameParametersGetsSameHash() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    Optional<ValueProviderCacheId> opId1 = computeIdFor(app, OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    Optional<ValueProviderCacheId> opId2 = computeIdFor(app, OTHER_OPERATION_LOCATION, PROVIDED_PARAMETER_NAME);
    checkIdsAreEqual(opId1, opId2);
  }

  @Test
  public void differentHashForComplexActingParameterValue() throws Exception {
    ArtifactDeclaration app = getBaseApp();
    final int defaultInt = 0;
    final String defaultString = "zero";
    final List<String> defaultList = asList("one", "two", "three");
    final Map<String, String> defaultMap = ImmutableMap.of("0", "zero", "1", "one");
    final InnerPojo defaultInnerPojo = new InnerPojo(defaultInt, defaultString, defaultList, defaultMap);
    final List<InnerPojo> defaultComplexList = asList(defaultInnerPojo);
    final Map<String, InnerPojo> defaultComplexMap = ImmutableMap.of("0", defaultInnerPojo);

    List<Optional<ValueProviderCacheId>> allIds = new LinkedList<>();

    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(1,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      "one",
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      asList("one",
                                                                                                                             "two",
                                                                                                                             "four"),
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      ImmutableMap
                                                                                                                          .of("2",
                                                                                                                              "two",
                                                                                                                              "3",
                                                                                                                              "three"),
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    InnerPojo innerPojoChangedInt = new InnerPojo(1, defaultString, defaultList, defaultMap);
    InnerPojo innerPojoChangedString = new InnerPojo(defaultInt, "one", defaultList, defaultMap);
    InnerPojo innerPojoChangedList = new InnerPojo(defaultInt, defaultString, asList("one", "two", "four"), defaultMap);
    InnerPojo innerPojoChangedMap =
        new InnerPojo(defaultInt, defaultString, defaultList, ImmutableMap.of("0", "two", "1", "three"));


    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      innerPojoChangedInt,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      innerPojoChangedString,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      innerPojoChangedList,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      innerPojoChangedMap,
                                                                                                                      defaultComplexList,
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      asList(innerPojoChangedInt),
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      asList(innerPojoChangedString),
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      asList(innerPojoChangedList),
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      asList(innerPojoChangedMap),
                                                                                                                      defaultComplexMap)));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      ImmutableMap
                                                                                                                          .of("0",
                                                                                                                              innerPojoChangedInt))));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      ImmutableMap
                                                                                                                          .of("0",
                                                                                                                              innerPojoChangedString))));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      ImmutableMap
                                                                                                                          .of("0",
                                                                                                                              innerPojoChangedList))));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));

    modifyParameter(app, OPERATION_LOCATION, COMPLEX_ACTING_PARAMETER_NAME, p -> p.setValue(newComplexActingParameter(defaultInt,
                                                                                                                      defaultString,
                                                                                                                      defaultList,
                                                                                                                      defaultMap,
                                                                                                                      defaultInnerPojo,
                                                                                                                      defaultComplexList,
                                                                                                                      ImmutableMap
                                                                                                                          .of("0",
                                                                                                                              innerPojoChangedMap))));
    allIds.add(computeIdFor(app, OPERATION_LOCATION, PROVIDED_FROM_COMPLEX_PARAMETER_NAME));


    for (Optional<ValueProviderCacheId> idA : allIds) {
      for (Optional<ValueProviderCacheId> idB : allIds) {
        if (idA != idB) {
          checkIdsAreDifferent(idA, idB);
        }
      }
    }
  }

}
