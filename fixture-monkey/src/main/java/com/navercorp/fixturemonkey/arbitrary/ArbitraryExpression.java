/*
 * Fixture Monkey
 *
 * Copyright (c) 2021-present NAVER Corp.
 *
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
 */

package com.navercorp.fixturemonkey.arbitrary;

import static com.navercorp.fixturemonkey.Constants.ALL_INDEX_STRING;
import static com.navercorp.fixturemonkey.Constants.HEAD_NAME;
import static com.navercorp.fixturemonkey.Constants.NO_OR_ALL_INDEX_INTEGER_VALUE;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apiguardian.api.API;
import org.apiguardian.api.API.Status;

import com.navercorp.fixturemonkey.api.generator.ArbitraryProperty;
import com.navercorp.fixturemonkey.api.generator.ObjectProperty;
import com.navercorp.fixturemonkey.expression.MonkeyExpression;
import com.navercorp.fixturemonkey.resolver.CompositeNodeResolver;
import com.navercorp.fixturemonkey.resolver.ContainerElementPredicate;
import com.navercorp.fixturemonkey.resolver.DefaultNodeResolver;
import com.navercorp.fixturemonkey.resolver.IdentityNodeResolver;
import com.navercorp.fixturemonkey.resolver.NodeResolver;
import com.navercorp.fixturemonkey.resolver.PropertyNameNodePredicate;

public final class ArbitraryExpression implements MonkeyExpression, Comparable<ArbitraryExpression> {
	private final List<Exp> expList;

	private ArbitraryExpression(List<Exp> expList) {
		this.expList = expList;
	}

	private ArbitraryExpression(String expression) {
		expList = Arrays.stream(expression.split("\\."))
			.map(Exp::new)
			.collect(toList());
	}

	public static ArbitraryExpression from(String expression) {
		return new ArbitraryExpression(expression);
	}

	public ArbitraryExpression addFirst(String expression) {
		String newStringExpression = expression + "." + this;
		return new ArbitraryExpression(newStringExpression);
	}

	public ArbitraryExpression addLast(String expression) {
		String newStringExpression = this + "." + expression;
		return new ArbitraryExpression(newStringExpression);
	}

	@API(since = "0.4.0", status = Status.EXPERIMENTAL)
	public ArbitraryExpression pollLast() {
		if (expList.isEmpty()) {
			return this;
		}

		List<Exp> newExpList = new ArrayList<>(this.expList);
		int lastIndex = newExpList.size() - 1;
		Exp lastExp = newExpList.get(lastIndex);
		newExpList.remove(lastIndex);

		if (!lastExp.indices.isEmpty()) {
			List<ExpIndex> newExpIndexList = new ArrayList<>(lastExp.indices);
			newExpIndexList.remove(newExpIndexList.size() - 1);
			lastExp = new Exp(lastExp.name, newExpIndexList);
			newExpList.add(lastExp);
		}
		return new ArbitraryExpression(newExpList);
	}

	@Override
	public int compareTo(ArbitraryExpression arbitraryExpression) {
		List<Exp> oExpList = arbitraryExpression.expList;

		if (expList.size() != oExpList.size()) {
			return Integer.compare(expList.size(), oExpList.size());
		}

		for (int i = 0; i < expList.size(); i++) {
			Exp exp = expList.get(i);
			Exp oExp = oExpList.get(i);
			int expCompare = exp.compareTo(oExp);
			if (expCompare != 0) {
				return expCompare;
			}
		}

		return 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		ArbitraryExpression other = (ArbitraryExpression)obj;
		return expList.equals(other.expList);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.expList);
	}

	public String toString() {
		return expList.stream()
			.map(Exp::toString)
			.collect(Collectors.joining("."));
	}

	public NodeResolver toNodeResolver() {
		NodeResolver nodeResolver = null;

		for (Exp exp : expList) {
			if (nodeResolver == null) {
				nodeResolver = exp.toNodeResolver();
			} else {
				nodeResolver = new CompositeNodeResolver(nodeResolver, exp.toNodeResolver());
			}
		}
		return nodeResolver;
	}

	/**
	 *  use {@link com.navercorp.fixturemonkey.resolver.NodeResolver} instead
	 *  */
	@Deprecated
	public List<Cursor> toCursors() {
		return this.expList.stream()
			.flatMap(it -> it.toCursors().stream())
			.filter(Cursor::isNotHeadName)
			.collect(toList());
	}

	private static final class ExpIndex implements Comparable<ExpIndex> {
		public static final ExpIndex ALL_INDEX_EXP_INDEX = new ExpIndex(NO_OR_ALL_INDEX_INTEGER_VALUE);

		private final int index;

		public ExpIndex(int index) {
			this.index = index;
		}

		public int getIndex() {
			return index;
		}

		public boolean equalsIgnoreAllIndex(ExpIndex expIndex) {
			return this.index == expIndex.index;
		}

		@Override
		public int compareTo(ExpIndex expIndex) {
			return Integer.compare(this.index, expIndex.index);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			ExpIndex expIndex = (ExpIndex)obj;
			return index == expIndex.index || index == NO_OR_ALL_INDEX_INTEGER_VALUE
				|| expIndex.index == NO_OR_ALL_INDEX_INTEGER_VALUE;
		}

		@Override
		public int hashCode() {
			return 0; // for allIndex, hash always return 0.
		}

		public String toString() {
			return index == NO_OR_ALL_INDEX_INTEGER_VALUE ? ALL_INDEX_STRING : String.valueOf(index);
		}
	}

	private static final class Exp implements Comparable<Exp> {
		private final String name;
		private final List<ExpIndex> indices;

		private Exp(String name, List<ExpIndex> indices) {
			this.name = name;
			this.indices = indices;
		}

		public Exp(String expression) {
			indices = new ArrayList<>();
			int li = expression.indexOf('[');
			int ri = expression.indexOf(']');

			if ((li != -1 && ri == -1) || (li == -1 && ri != -1)) {
				throw new IllegalArgumentException("expression is invalid. expression : " + expression);
			}

			if (li == -1) {
				this.name = expression;
			} else {
				this.name = expression.substring(0, li);
				while (li != -1 && ri != -1) {
					if (ri - li > 1) {
						String indexString = expression.substring(li + 1, ri);
						final int indexValue = indexString.equals(ALL_INDEX_STRING)
							? NO_OR_ALL_INDEX_INTEGER_VALUE
							: Integer.parseInt(indexString);
						this.indices.add(new ExpIndex(indexValue));
					}
					expression = expression.substring(ri + 1);
					li = expression.indexOf('[');
					ri = expression.indexOf(']');
				}
			}
		}

		public List<Cursor> toCursors() {
			List<Cursor> steps = new ArrayList<>();
			String expName = this.getName();
			steps.add(new ExpNameCursor(expName));
			steps.addAll(this.getIndices().stream()
				.map(it -> new ExpIndexCursor(expName, it.getIndex()))
				.collect(toList()));
			return steps;
		}

		public NodeResolver toNodeResolver() {
			NodeResolver nodeResolver;

			if (HEAD_NAME.equals(name)) {
				nodeResolver = IdentityNodeResolver.INSTANCE;
			} else {
				nodeResolver = new DefaultNodeResolver(new PropertyNameNodePredicate(name));
			}

			for (ExpIndex index : indices) {
				nodeResolver = new CompositeNodeResolver(
					nodeResolver,
					new DefaultNodeResolver(new ContainerElementPredicate(index.getIndex()))
				);
			}
			return nodeResolver;
		}

		public String getName() {
			return name;
		}

		public List<ExpIndex> getIndices() {
			return indices;
		}

		public String toString() {
			String indexBrackets = indices.stream()
				.map(i -> "[" + i.toString() + "]")
				.collect(Collectors.joining());
			return name + indexBrackets;
		}

		@Override
		public int compareTo(Exp exp) {
			List<ExpIndex> indices = this.getIndices();
			List<ExpIndex> oIndices = exp.getIndices();

			if (exp.name.equals(this.name)) {
				int indexLength = Math.min(oIndices.size(), indices.size());
				for (int i = 0; i < indexLength; i++) {
					ExpIndex index = indices.get(i);
					ExpIndex oIndex = oIndices.get(i);
					int indexCompare = oIndex.compareTo(index);
					if (indexCompare != 0) {
						return indexCompare;
					}
				}
			}
			return Integer.compare(indices.size(), oIndices.size());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			Exp exp = (Exp)obj;
			return name.equals(exp.name) && indices.equals(exp.indices);
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, indices);
		}
	}

	/**
	 *  use {@link com.navercorp.fixturemonkey.resolver.NodeResolver} instead
	 *  */
	@Deprecated
	public abstract static class Cursor {
		private final String name;
		private final int index;

		public Cursor(String name, int index) {
			this.name = name;
			this.index = index;
		}

		public boolean match(ArbitraryProperty arbitraryProperty) {
			ObjectProperty objectProperty = arbitraryProperty.getObjectProperty();
			String resolvePropertyName = objectProperty.getResolvedPropertyName();
			boolean samePropertyName;
			if (resolvePropertyName == null) {
				samePropertyName = true; // ignore property name equivalence.
			} else {
				samePropertyName = nameEquals(resolvePropertyName);
			}

			boolean sameIndex = true;
			if (objectProperty.getElementIndex() != null) {
				sameIndex = indexEquals(objectProperty.getElementIndex()); // notNull
			}
			return samePropertyName && sameIndex;
		}

		public boolean isNotHeadName() {
			return !(this instanceof ExpNameCursor) || !HEAD_NAME.equals(this.getName());
		}

		private boolean indexEquals(int index) {
			return this.index == index
				|| index == NO_OR_ALL_INDEX_INTEGER_VALUE
				|| this.index == NO_OR_ALL_INDEX_INTEGER_VALUE;
		}

		private boolean nameEquals(String name) {
			return this.name.equals(name)
				|| ALL_INDEX_STRING.equals(name)
				|| ALL_INDEX_STRING.equals(this.name);
		}

		public String getName() {
			return name;
		}

		public int getIndex() {
			return index;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Cursor)) {
				return false;
			}
			Cursor cursor = (Cursor)obj;

			boolean indexEqual = indexEquals(cursor.getIndex());
			boolean nameEqual = nameEquals(cursor.getName());
			return nameEqual && indexEqual;
		}

		@Override
		public int hashCode() {
			return Objects.hash(name);
		}
	}

	static final class ExpIndexCursor extends Cursor {
		ExpIndexCursor(String name, int index) {
			super(name, index);
		}
	}

	public static final class ExpNameCursor extends Cursor {
		ExpNameCursor(String name) {
			super(name, NO_OR_ALL_INDEX_INTEGER_VALUE);
		}
	}

}
