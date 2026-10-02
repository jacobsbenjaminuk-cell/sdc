/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */
package org.openecomp.sdc.be.model.tosca.constraints;

import java.time.Duration;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openecomp.sdc.be.datatypes.enums.ConstraintType;
import org.openecomp.sdc.be.model.PropertyConstraint;
import org.openecomp.sdc.be.model.tosca.ToscaType;
import org.openecomp.sdc.be.model.tosca.constraints.exception.ConstraintFunctionalException;
import org.openecomp.sdc.be.model.tosca.constraints.exception.ConstraintViolationException;
import org.openecomp.sdc.be.model.tosca.constraints.exception.PropertyConstraintException;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
public class PatternConstraint extends AbstractStringPropertyConstraint {

    private static final int MAX_PATTERN_LENGTH = 1024;
    private static final int MAX_VALUE_LENGTH = 100_000;
    private static final Duration MATCH_TIME_LIMIT = Duration.ofMillis(100);

    @NotNull
    @Getter
    @EqualsAndHashCode.Include
    private String pattern;
    private Pattern compiledPattern;

    public PatternConstraint(String pattern) {
        setPattern(pattern);
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
        this.compiledPattern = compilePattern(pattern);
    }

    /**
     * Patterns saved before the limits existed must still load from the graph, so over-limit patterns degrade to a
     * constraint that fails validation instead of throwing here.
     */
    private static Pattern compilePattern(String pattern) {
        if (pattern == null || pattern.length() > MAX_PATTERN_LENGTH) {
            return null;
        }
        try {
            return Pattern.compile(pattern);
        } catch (StackOverflowError e) {
            return null;
        }
    }

    @Override
    protected void doValidate(String propertyValue) throws ConstraintViolationException {
        if (compiledPattern == null) {
            throw new ConstraintViolationException("The pattern is too long or too complex to evaluate");
        }
        if (propertyValue.length() > MAX_VALUE_LENGTH) {
            throw new ConstraintViolationException("The value is too long to validate against pattern " + pattern);
        }
        try {
            if (!compiledPattern.matcher(new BoundedCharSequence(propertyValue, MATCH_TIME_LIMIT.toNanos())).matches()) {
                throw new ConstraintViolationException("The value do not match pattern " + pattern);
            }
        } catch (PatternEvaluationAbortedException e) {
            throw new ConstraintViolationException("Evaluation of pattern " + pattern + " exceeded the allowed time", e);
        } catch (StackOverflowError e) {
            throw new ConstraintViolationException("Evaluation of pattern " + pattern + " is too complex", e);
        }
    }

    @Override
    public ConstraintType getConstraintType() {
        return ConstraintType.PATTERN;
    }

    @Override
    public void validateValueOnUpdate(PropertyConstraint newConstraint) throws PropertyConstraintException {
        // no need for implementation
    }

    @Override
    public String getErrorMessage(ToscaType toscaType, ConstraintFunctionalException e, String propertyName) {
        return getErrorMessage(toscaType, e, propertyName, "%s property value must match the regular expression %s", pattern);
    }

    /**
     * CharSequence that aborts pattern matching once a time budget is spent. java.util.regex reads input exclusively
     * through CharSequence, so checking the deadline here bounds catastrophic backtracking.
     */
    private static final class BoundedCharSequence implements CharSequence {

        private final CharSequence input;
        private final long deadlineNanos;

        BoundedCharSequence(CharSequence input, long remainingBudgetNanos) {
            this(input, remainingBudgetNanos, false);
        }

        private BoundedCharSequence(CharSequence input, long time, boolean absoluteDeadline) {
            this.input = input;
            this.deadlineNanos = absoluteDeadline ? time : System.nanoTime() + time;
        }

        @Override
        public int length() {
            checkDeadline();
            return input.length();
        }

        @Override
        public char charAt(int index) {
            checkDeadline();
            return input.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            checkDeadline();
            return new BoundedCharSequence(input.subSequence(start, end), deadlineNanos, true);
        }

        @Override
        public String toString() {
            checkDeadline();
            return input.toString();
        }

        private void checkDeadline() {
            if (System.nanoTime() - deadlineNanos >= 0) {
                throw new PatternEvaluationAbortedException();
            }
        }
    }

    private static final class PatternEvaluationAbortedException extends RuntimeException {

        private PatternEvaluationAbortedException() {
            super("Pattern evaluation exceeded the allowed time");
        }
    }
}
