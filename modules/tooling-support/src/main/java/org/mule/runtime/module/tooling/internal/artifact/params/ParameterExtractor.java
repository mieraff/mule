/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.tooling.internal.artifact.params;

import static java.util.stream.Collectors.joining;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.extractExpression;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.isExpression;

import org.mule.metadata.api.annotation.TypeIdAnnotation;
import org.mule.metadata.api.model.ArrayType;
import org.mule.metadata.api.model.DateTimeType;
import org.mule.metadata.api.model.DateType;
import org.mule.metadata.api.model.LocalDateTimeType;
import org.mule.metadata.api.model.LocalTimeType;
import org.mule.metadata.api.model.MetadataType;
import org.mule.metadata.api.model.ObjectFieldType;
import org.mule.metadata.api.model.ObjectType;
import org.mule.metadata.api.visitor.MetadataTypeVisitor;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.metadata.DataType;
import org.mule.runtime.api.metadata.TypedValue;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.app.declaration.api.ParameterValue;
import org.mule.runtime.app.declaration.api.ParameterValueVisitor;
import org.mule.runtime.app.declaration.api.fluent.ParameterListValue;
import org.mule.runtime.app.declaration.api.fluent.ParameterObjectValue;
import org.mule.runtime.app.declaration.api.fluent.ParameterSimpleValue;

import java.time.LocalDateTime;
import java.util.Date;


public class ParameterExtractor implements ParameterValueVisitor {

  private static final DataType DW_DATA_TYPE = DataType.builder().mediaType("application/dw").build();

  public static TypedValue<?> extractValue(ParameterValue parameterValue, MetadataType expectedType) {
    String dataWeaveValue = extractAsDataWeave(parameterValue, expectedType);
    if (isExpression(dataWeaveValue)) {
      String expression = dataWeaveValue;
      dataWeaveValue = extractExpression(dataWeaveValue)
          .orElseThrow(
                       () -> new MuleRuntimeException(createStaticMessage("Could not extract expression from: "
                           + expression)));
    }
    return new TypedValue<>(dataWeaveValue, DW_DATA_TYPE);
  }

  private static String extractAsDataWeave(ParameterValue parameterValue, MetadataType metadataType) {
    final ParameterExtractor extractor = new ParameterExtractor(metadataType);
    parameterValue.accept(extractor);
    return extractor.get();
  }

  private final MetadataType expectedType;
  private String value;

  private ParameterExtractor(MetadataType expectedType) {
    this.expectedType = expectedType;
  }

  @Override
  public void visitSimpleValue(ParameterSimpleValue text) {
    String value = text.getValue();
    if (!isExpression(value)) {
      value = enrichedWithType("'" + value + "'");
    }
    this.value = value;
  }

  @Override
  public void visitListValue(ParameterListValue list) {
    this.value = "[" +
        list.getValues().stream().map(v -> extractAsDataWeave(v, getArrayInnerType())).collect(joining(",")) +
        "]";
  }

  @Override
  public void visitObjectValue(ParameterObjectValue objectValue) {
    this.value = "{" +
        objectValue
            .getParameters()
            .entrySet()
            .stream()
            .map((e) -> "'" + e.getKey() + "'" + ":" + extractAsDataWeave(e.getValue(), getObjectFieldType(e.getKey())))
            .collect(joining(","))
        + "}";
  }

  private String get() {
    return value;
  }

  private MetadataType getArrayInnerType() {
    Reference<MetadataType> typeReference = new Reference<>();
    if (this.expectedType != null) {
      this.expectedType.accept(new MetadataTypeVisitor() {

        @Override
        public void visitArrayType(ArrayType arrayType) {
          typeReference.set(arrayType.getType());
        }
      });
    }
    return typeReference.get();
  }

  private MetadataType getObjectFieldType(String fieldName) {
    Reference<MetadataType> typeReference = new Reference<>();
    if (this.expectedType != null) {
      this.expectedType.accept(new MetadataTypeVisitor() {

        @Override
        public void visitObject(ObjectType objectType) {
          typeReference.set(objectType.getFieldByName(fieldName).map(ObjectFieldType::getValue).orElse(null));
        }
      });
    }
    return typeReference.get();
  }

  private String enrichedWithType(String value) {
    Reference<String> explicitType = new Reference<>("");
    if (this.expectedType != null) {
      this.expectedType.accept(new MetadataTypeVisitor() {

        @Override
        public void visitDateTime(DateTimeType dateTimeType) {
          explicitType.set(
                           // This is because the java TypeLoader does not properly load LocalDateTime.
                           dateTimeType.getAnnotation(TypeIdAnnotation.class)
                               .map(a -> LocalDateTime.class.getName().equals(a.getValue()) ? " as LocalDateTime" : null)
                               .orElse(" as DateTime"));
        }

        @Override
        public void visitDate(DateType dateType) {
          explicitType.set(
                           dateType.getAnnotation(TypeIdAnnotation.class)
                               // Old Java Date is actually a DateTime
                               .map(a -> Date.class.getName().equals(a.getValue()) ? " as DateTime" : null)
                               .orElse(" as Date"));
        }

        @Override
        public void visitLocalDateTime(LocalDateTimeType localDateTimeType) {
          explicitType.set(" as LocalDateTime");
        }

        @Override
        public void visitLocalTime(LocalTimeType localTimeType) {
          explicitType.set(" as LocalTime");
        }
      });
    }
    return value + explicitType.get();
  }

}
