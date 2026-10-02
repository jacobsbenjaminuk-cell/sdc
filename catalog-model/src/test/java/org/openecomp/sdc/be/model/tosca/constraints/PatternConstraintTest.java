/*-
 * ============LICENSE_START=======================================================
 * SDC
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openecomp.sdc.be.model.tosca.constraints.exception.ConstraintViolationException;

public class PatternConstraintTest {

	private PatternConstraint createTestSubject() {
		return new PatternConstraint();
	}

	@Test
	public void testSetPattern() throws Exception {
		PatternConstraint testSubject;
		String pattern = "";

		// default test
		testSubject = createTestSubject();
		testSubject.setPattern(pattern);
	}

	@Test
	public void patternLongerThanLimitFailsValidationButStillLoads() {
		String pattern = "a".repeat(1025);
		// Construction must not throw: pre-existing stored patterns above the limit still load from the graph.
		PatternConstraint testSubject = new PatternConstraint(pattern);
		assertThrows(ConstraintViolationException.class, () -> testSubject.validate("x"));
	}

	@Test
	public void acceptsValidPatternAndValidatesValue() {
		PatternConstraint testSubject = new PatternConstraint("[a-z]+");
		assertDoesNotThrow(() -> testSubject.validate("abc"));
		assertThrows(ConstraintViolationException.class, () -> testSubject.validate("123"));
	}

	@Test
	public void rejectsValueLongerThanLimit() {
		PatternConstraint testSubject = new PatternConstraint(".*");
		String value = "a".repeat(100_001);
		assertThrows(ConstraintViolationException.class, () -> testSubject.validate(value));
	}

	@Test
	public void abortsCatastrophicBacktrackingWithinTimeLimit() {
		PatternConstraint testSubject = new PatternConstraint("(a+)+$");
		String value = "a".repeat(50) + "!";
		long start = System.nanoTime();
		assertThrows(ConstraintViolationException.class, () -> testSubject.validate(value));
		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
		// Without the bound this match would not finish; allow generous headroom for slow CI machines.
		assertTrue(elapsedMillis < 10_000, "Pattern evaluation was not aborted in time, took " + elapsedMillis + "ms");
	}

	@Test
	public void stackOverflowDuringMatchBecomesViolation() {
		// Quantified groups recurse one stack frame per input char in java.util.regex.
		PatternConstraint testSubject = new PatternConstraint("(a|b)*");
		String value = "a".repeat(100_000);
		assertThrows(ConstraintViolationException.class, () -> testSubject.validate(value));
	}
}
