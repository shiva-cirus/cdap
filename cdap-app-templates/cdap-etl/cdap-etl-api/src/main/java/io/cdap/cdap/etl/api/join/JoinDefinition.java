/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.etl.api.join;

import io.cdap.cdap.api.annotation.Beta;
import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.etl.api.join.error.JoinError;
import io.cdap.cdap.etl.api.join.error.SelectedFieldError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Specifies how a join should be executed.
 */
@Beta
public class JoinDefinition {
  private final List<JoinField> selectedFields;
  private final List<JoinStage> stages;
  private final JoinCondition condition;
  private final Schema outputSchema;

  private JoinDefinition(List<JoinField> selectedFields, List<JoinStage> stages,
                         JoinCondition condition, Schema outputSchema) {
    this.stages = Collections.unmodifiableList(stages);
    this.selectedFields = Collections.unmodifiableList(new ArrayList<>(selectedFields));
    this.condition = condition;
    this.outputSchema = outputSchema;
  }

  public List<JoinField> getSelectedFields() {
    return selectedFields;
  }

  public List<JoinStage> getStages() {
    return stages;
  }

  public JoinCondition getCondition() {
    return condition;
  }

  public Schema getOutputSchema() {
    return outputSchema;
  }

  /**
   * @return a Builder to create a JoinSpecification.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builds a JoinSpecification.
   */
  public static class Builder {
    private final List<JoinStage> stages;
    private final List<JoinField> selectedFields;
    private JoinCondition condition;
    private String schemaName;

    private Builder() {
      stages = new ArrayList<>();
      selectedFields = new ArrayList<>();
      schemaName = null;
      condition = null;
    }

    public Builder select(List<JoinField> selectedFields) {
      this.selectedFields.clear();
      this.selectedFields.addAll(selectedFields);
      return this;
    }

    public Builder select(JoinField... fields) {
      return select(Arrays.asList(fields));
    }

    public Builder from(Collection<JoinStage> stages) {
      this.stages.clear();
      this.stages.addAll(stages);
      return this;
    }

    public Builder from(JoinStage... stages) {
      return from(Arrays.asList(stages));
    }

    public Builder on(JoinCondition condition) {
      this.condition = condition;
      return this;
    }

    public Builder setOutputSchemaName(@Nullable String name) {
      schemaName = name;
      return this;
    }

    /**
     * @return a valid JoinDefinition
     * @throws InvalidJoinException if the join is invalid
     */
    public JoinDefinition build() {
      if (stages.size() < 2) {
        throw new InvalidJoinException("At least two stages must be specified.");
      }

      List<JoinError> errors = new ArrayList<>();
      if (selectedFields.isEmpty()) {
        errors.add(new JoinError("At least one field must be selected."));
      }

      // validate the join condition
      if (condition == null) {
        errors.add(new JoinError("A join condition must be specified."));
      } else {
        errors.addAll(condition.validate(stages));
      }

      Schema outputSchema = getOutputSchema(errors);
      if (!errors.isEmpty()) {
        throw new InvalidJoinException(errors);
      }

      return new JoinDefinition(selectedFields, stages, condition, outputSchema);
    }

    @Nullable
    private Schema getOutputSchema(List<JoinError> errors) {
      Set<String> outputFieldNames = new HashSet<>();
      List<Schema.Field> outputFields = new ArrayList<>(selectedFields.size());
      Map<String, JoinStage> stageMap = stages.stream()
        .collect(Collectors.toMap(JoinStage::getStageName, s -> s));

      for (JoinField field : selectedFields) {
        JoinStage joinStage = stageMap.get(field.getStageName());
        if (joinStage == null) {
          errors.add(new SelectedFieldError(field, String.format(
            "Selected field '%s'.'%s' is invalid because stage '%s' is not part of the join.",
            field.getStageName(), field.getFieldName(), field.getStageName())));
          continue;
        }
        Schema stageSchema = joinStage.getSchema();
        // schema is null if the schema is unknown
        // for example, when the pipeline is being deployed, the schema might not yet be known due to
        // macros not being evaluated yet.
        if (stageSchema == null) {
          return null;
        }
        Schema.Field schemaField = stageSchema.getField(field.getFieldName());
        if (schemaField == null) {
          errors.add(new SelectedFieldError(field, String.format(
            "Selected field '%s'.'%s' is invalid because stage '%s' does not contain field '%s'.",
            field.getStageName(), field.getFieldName(), field.getStageName(), field.getFieldName())));
          continue;
        }

        String outputFieldName = field.getAlias() == null ? field.getFieldName() : field.getAlias();
        if (!outputFieldNames.add(outputFieldName)) {
          errors.add(new SelectedFieldError(field, String.format(
            "Field '%s' from stage '%s' is a duplicate. Set an alias to make it unique.",
            outputFieldName, field.getStageName())));
          continue;
        }

        Schema outputFieldSchema = schemaField.getSchema();
        if (!joinStage.isRequired() && !outputFieldSchema.isNullable()) {
          outputFieldSchema = Schema.nullableOf(outputFieldSchema);
        }
        outputFields.add(Schema.Field.of(outputFieldName, outputFieldSchema));
      }

      if (!errors.isEmpty()) {
        return null;
      }

      return Schema.recordOf(schemaName == null ? "joined" : schemaName, outputFields);
    }
  }

}
