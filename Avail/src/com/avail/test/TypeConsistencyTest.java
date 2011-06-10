/**
 * test/TypeConsistencyTest.java
 * Copyright (c) 2011, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.avail.test;

import static com.avail.descriptor.TypeDescriptor.Types;
import java.util.*;
import org.junit.*;
import com.avail.descriptor.*;


/**
 * Test various consistency properties for {@linkplain TypeDescriptor types} in
 * Avail.  The type system is really pretty complex, so these tests are quite
 * important.
 *
 * <p>
 * Here are some things to test.  T is the set of types, T(x) means the type of
 * x, Co(x) is some relation that's supposed to be covariant, Con(x) is some
 * relation that's supposed to be contravariant, &cup; is type union, and &cap;
 * is type intersection.
 *
 * <table border=1 cellspacing=0>
 * <tr>
 *     <td>Subtype reflexivity</td>
 *     <td>&forall;<sub>x&isin;T</sub>&thinsp;x&sube;x</td>
 * </tr><tr>
 *     <td>Subtype transitivity</td>
 *     <td>&forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&sube;y&thinsp;&and;&thinsp;y&sube;z
 *             &rarr; x&sube;z)</td>
 * </tr><tr>
 *     <td>Subtype asymmetry</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sub;y &rarr; &not;y&sub;x)
 *         <br>
 *         <em>or alternatively,</em>
 *         <br>
 *         &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y&thinsp;&and;&thinsp;y&sube;x
 *         = (x=y))</td>
 * </tr><tr>
 *     <td>Union closure</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y&thinsp;&isin;&thinsp;T)</td>
 * </tr><tr>
 *     <td>Union reflexivity</td>
 *     <td>&forall;<sub>x&isin;T</sub>&thinsp;(x&cup;x&thinsp;=&thinsp;x)</td>
 * </tr><tr>
 *     <td>Union commutativity</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y = y&cup;x)</td>
 * </tr><tr>
 *     <td>Union associativity</td>
 *     <td>&forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cup;y)&cup;z = x&cup;(y&cup;z)</td>
 * </tr><tr>
 *     <td>Intersection closure</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y&thinsp;&isin;&thinsp;T)</td>
 * </tr><tr>
 *     <td>Intersection reflexivity</td>
 *     <td>&forall;<sub>x&isin;T</sub>&thinsp;(x&cap;x&thinsp;=&thinsp;x)</td>
 * </tr><tr>
 *     <td>Intersection commutativity</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y = y&cap;x)</td>
 * </tr><tr>
 *     <td>Intersection associativity</td>
 *     <td>&forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cap;y)&cap;z = x&cap;(y&cap;z)</td>
 * </tr><tr>
 *     <td>Various covariance relationships (Co)</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Co(x)&sube;Co(y))</td>
 * </tr><tr>
 *     <td>Various contravariance relationships (Con)</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Con(y)&sube;Con(x))</td>
 * </tr><tr>
 *     <td>Metacovariance</td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; T(x)&sube;T(y))</td>
 * </tr><tr>
 *     <td><em>Metavariance (preservation)*</em></td>
 *     <td>&forall;<sub>x,y&isin;T</sub>&thinsp;(x&ne;y &rarr; T(x)&ne;T(y))</td>
 * </tr>
 * </table>
 * * It's unclear if metavariance is a useful property, but it prevents the
 * destruction of information in type-manipulating expressions.
 * </p>
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class TypeConsistencyTest
{
	/**
	 * {@code Node} records its instances upon creation.  They must be created
	 * in top-down order (i.e., supertypes before subtypes), as the {@link
	 * Node#Node(String, Node...) constructor} takes a variable number of
	 * supertype nodes.  The node supertype declarations are checked against the
	 * actual properties of the underlying types as one of the fundamental
	 * {@linkplain TypeConsistencyTest consistency checks}.
	 *
	 * <p>
	 * All {@link TypeDescriptor.Types} are included, as well as a few
	 * simple representative samples, such as the one-element string type and
	 * the type of whole numbers.
	 * </p>
	 */
	public abstract static class Node
	{
		/**
		 * The list of all currently defined {@linkplain Node type nodes}.
		 */
		private static final List<Node> values = new ArrayList<Node>();

		/**
		 * Return the list of all currently defined {@linkplain Node type
		 * nodes}.
		 *
		 * @return Every {@link Node} that has been defined thus far.
		 */
		static List<Node> values ()
		{
			return values;
		}


		/**
		 * A mapping from {@link TypeDescriptor.Types} to their corresponding
		 * {@link Node}s.
		 */
		private final static EnumMap<Types, Node> primitiveTypes =
			new EnumMap<Types, Node>(Types.class);

		static
		{
			// Include all primitive types except TERMINATES_TYPE and
			// TERMINATES, which will be added specially at the end.
			primitiveTypes.put(Types.TERMINATES_TYPE, null);
			primitiveTypes.put(Types.TERMINATES, null);
			for (final Types type : Types.values())
			{
				if (!primitiveTypes.containsKey(type))
				{
					final Types typeParent = type.parent;
					final Node [] parents =
						new Node[typeParent == null ? 0 : 1];
					if (typeParent != null)
					{
						parents[0] = primitiveTypes.get(typeParent);
					}
					final Node node = new Node(type.name(), parents)
					{
						@Override AvailObject get ()
						{
							return type.o();
						}
					};
					primitiveTypes.put(type, node);
				}
			}
		}



		/** The type {@code tuple} */
		final static Node TUPLE = new Node(
			"TUPLE",
			primitiveTypes.get(Types.ALL))
		{
			@Override AvailObject get ()
			{
				return TupleTypeDescriptor.mostGeneralTupleType();
			}
		};

		/**
		 * The type {@code string}, which is the same as {@code tuple of
		 * character}
		 */
		final static Node STRING = new Node("STRING", TUPLE)
		{
			@Override AvailObject get ()
			{
				return TupleTypeDescriptor.stringTupleType();
			}
		};

		/** The type {@code tuple [1..1] of character} */
		final static Node UNIT_STRING = new Node("UNIT_STRING", STRING)
		{
			@Override AvailObject get ()
			{
				return ByteStringDescriptor.from("x").type();
			}
		};

		/** The type {@code type of <>} */
		final static Node EMPTY_TUPLE = new Node("EMPTY_TUPLE", TUPLE, STRING)
		{
			@Override AvailObject get ()
			{
				return TupleDescriptor.empty().type();
			}
		};

		/** The type {@code set} */
		final static Node SET = new Node(
			"SET",
			primitiveTypes.get(Types.ALL))
		{
			@Override AvailObject get ()
			{
				return SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					Types.ALL.o());
			}
		};

		/** The most general closure type. */
		final static Node MOST_GENERAL_CLOSURE = new Node(
			"MOST_GENERAL_CLOSURE",
			primitiveTypes.get(Types.ALL))
		{
			@Override AvailObject get ()
			{
				return ClosureTypeDescriptor.forReturnType(Types.VOID_TYPE.o());
			}
		};

		/**
		 * The type for closures that accept no arguments and return an integer.
		 */
		final static Node NOTHING_TO_INT_CLOSURE = new Node(
			"NOTHING_TO_INT_CLOSURE",
			MOST_GENERAL_CLOSURE)
		{
			@Override AvailObject get ()
			{
				return ClosureTypeDescriptor.create(
					TupleDescriptor.empty(),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The type for closures that accept an integer and return an integer.
		 */
		final static Node INT_TO_INT_CLOSURE = new Node(
			"INT_TO_INT_CLOSURE",
			MOST_GENERAL_CLOSURE)
		{
			@Override AvailObject get ()
			{
				return ClosureTypeDescriptor.create(
					TupleDescriptor.from(IntegerRangeTypeDescriptor.integers()),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/**
		 * The type for closures that accept two integers and return an integer.
		 */
		final static Node INTS_TO_INT_CLOSURE = new Node(
			"INTS_TO_INT_CLOSURE",
			MOST_GENERAL_CLOSURE)
		{
			@Override AvailObject get ()
			{
				return ClosureTypeDescriptor.create(
					TupleDescriptor.from(
						IntegerRangeTypeDescriptor.integers(),
						IntegerRangeTypeDescriptor.integers()),
					IntegerRangeTypeDescriptor.integers());
			}
		};

		/** The most specific closure type, other than terminates. */
		final static Node MOST_SPECIFIC_CLOSURE = new Node(
			"MOST_SPECIFIC_CLOSURE",
			NOTHING_TO_INT_CLOSURE,
			INT_TO_INT_CLOSURE,
			INTS_TO_INT_CLOSURE)
		{
			@Override AvailObject get ()
			{
				return ClosureTypeDescriptor.createWithArgumentTupleType(
					TupleTypeDescriptor.mostGeneralTupleType(),
					Types.TERMINATES.o(),
					SetDescriptor.empty());
			}
		};

		/** The primitive type representing the extended integers [-∞..∞]. */
		final static Node EXTENDED_INTEGER = new Node(
			"EXTENDED_INTEGER",
			primitiveTypes.get(Types.ALL))
		{
			@Override AvailObject get ()
			{
				return IntegerRangeTypeDescriptor.extendedIntegers();
			}
		};

		/** The primitive type representing whole numbers [0..∞). */
		final static Node WHOLE_NUMBER = new Node(
			"WHOLE_NUMBER",
			EXTENDED_INTEGER)
		{
			@Override AvailObject get ()
			{
				return IntegerRangeTypeDescriptor.wholeNumbers();
			}
		};

		/** A generated cyclic type. */
		final static Node SOME_CYCLIC_TYPE = new Node(
			"SOME_CYCLIC_TYPE",
			primitiveTypes.get(Types.CYCLIC_TYPE))
		{
			@Override AvailObject get ()
			{
				return CyclicTypeDescriptor.create(
					ByteStringDescriptor.from("something"));
			}
		};

		/** A generated cyclic type distinct from {@link #SOME_CYCLIC_TYPE}. */
		final static Node ANOTHER_CYCLIC_TYPE = new Node(
			"ANOTHER_CYCLIC_TYPE",
			primitiveTypes.get(Types.CYCLIC_TYPE))
		{
			@Override AvailObject get ()
			{
				return CyclicTypeDescriptor.create(
					ByteStringDescriptor.from("another"));
			}
		};


		/** The type of {@code terminates}.  This is the most specific meta. */
		final static Node TERMINATES_TYPE = new Node(
			"TERMINATES_TYPE",
			primitiveTypes.get(Types.CLOSURE_TYPE),
			primitiveTypes.get(Types.CONTAINER_TYPE),
			primitiveTypes.get(Types.CONTINUATION_TYPE),
			primitiveTypes.get(Types.INTEGER_TYPE),
			primitiveTypes.get(Types.MAP_TYPE),
			primitiveTypes.get(Types.SET_TYPE),
			primitiveTypes.get(Types.TUPLE_TYPE),
			primitiveTypes.get(Types.META),
			SOME_CYCLIC_TYPE,
			ANOTHER_CYCLIC_TYPE)
		{
			@Override AvailObject get ()
			{
				return Types.TERMINATES_TYPE.o();
			}
		};

		/**
		 * The list of all {@link Node}s except TERMINATES.
		 */
		private final static List<Node> nonTerminatesTypes =
			new ArrayList<Node>();

		static
		{
			for (final Node existingType : values())
			{
				nonTerminatesTypes.add(existingType);
			}
		}

		/** The type {@code terminates} */
		final static Node TERMINATES = new Node(
			"TERMINATES",
			nonTerminatesTypes.toArray(new Node[0]))
		{
			@Override AvailObject get ()
			{
				return Types.TERMINATES.o();
			}
		};




		/** The name of this type node, used for error diagnostics. */
		final String name;

		/** The Avail {@link TypeDescriptor type} I represent in the graph. */
		AvailObject t;


		/** The supernodes in the graph. */
		final Node [] supernodes;


		/** The subnodes in the graph, as an {@link EnumSet}. */
		Set<Node> subnodes;


		/** Every node descended from this on, as an {@link EnumSet}. */
		Set<Node> allDescendants;

		/**
		 * Construct a new {@link Node}, capturing a varargs list of known
		 * supertypes.
		 *
		 * @param name
		 *            The printable name of this {@link Node}.
		 * @param supernodes
		 *            The array of {@linkplain Node nodes} that this node is
		 *            asserted to descend from.  Transitive ancestors may be
		 *            elided.
		 */
		Node (final String name, final Node... supernodes)
		{
			this.name = name;
			this.supernodes = supernodes;
			values.add(this);
		}


		/* The nodes' slots have to be initialized here because they pass
		 * the Node.class to the EnumSet factory, which attempts to
		 * determine the number of enumeration values, which isn't known yet
		 * when the constructors are still running.
		 *
		 * Also build the inverse and (downwards) transitive closure at each
		 * node of the graph, since they're independent of how the actual types
		 * are related.  Discrepancies between the graph information and the
		 * actual types is resolved in TypeConstincyTest#testGraphModel().
		 */
		static
		{
			for (final Node node : values())
			{
				node.subnodes = new HashSet<Node>();
				node.allDescendants = new HashSet<Node>();
			}
			for (final Node node : values())
			{
				for (final Node supernode : node.supernodes)
				{
					supernode.subnodes.add(node);
				}
			}
			for (final Node node : values())
			{
				node.allDescendants.add(node);
				node.allDescendants.addAll(node.subnodes);
			}
			boolean changed;
			do
			{
				changed = false;
				for (final Node node : values())
				{
					for (final Node subnode : node.subnodes)
					{
						changed |= node.allDescendants.addAll(
							subnode.allDescendants);
					}
				}
			}
			while (changed);
		}


		/**
		 * Enumeration instances are required to implement this to construct the
		 * actual Avail {@linkplain TypeDescriptor type} that this {@link Node}
		 * represents.
		 *
		 * @return The {@link AvailObject} that is the {@linkplain
		 *         TypeDescriptor type} that this {@link Node} represents.
		 */
		abstract AvailObject get ();


		@Override
		public String toString()
		{
			return name;
		};

		/**
		 * Record the actual type information into the graph.
		 */
		public static void createTypes()
		{
			for (final Node node : values())
			{
				node.t = node.get();
			}
		}

		/**
		 * Remove all type information from the graph, leaving the shape intact.
		 */
		public static void eraseTypes()
		{
			for (final Node node : values())
			{
				node.t = null;
			}
		}
	}


	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime, then set up the graph of types.
	 */
	@BeforeClass
	public static void initializeAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
		Node.createTypes();
		System.out.format("Checking %d types%n", Node.values().size());
	}



	/**
	 * Test fixture: clear all special objects, wiping each {@link Node}'s type.
	 */
	@AfterClass
	public static void clearAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		Node.eraseTypes();
	}


	/**
	 * Test that the {@linkplain Node#supernodes declared} subtype relations
	 * actually hold the way the graph says they should.
	 */
	@Test
	public void testGraphModel ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				assert x.t.isSubtypeOf(y.t) == y.allDescendants.contains(x)
				: "graph model (not as declared): " + x + ", " + y;
				assert (x == y) == x.t.equals(y.t)
				: "graph model (not unique)" + x + ", " + y;
			}
		}
	}

	/**
	 * Test that the subtype relationship is reflexive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x&isin;T</sub>&thinsp;x&sube;x
	 * </nobr></span>
	 */
	@Test
	public void testSubtypeReflexivity ()
	{
		for (final Node x : Node.values())
		{
			assert x.t.isSubtypeOf(x.t)
			: "subtype reflexivity: " + x;
		}
	}

	/**
	 * Test that the subtype relationship is transitive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&sube;y&thinsp;&and;&thinsp;y&sube;z
	 *     &rarr; x&sube;z)
	 * </nobr></span>
	 */
	@Test
	public void testSubtypeTransitivity ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				for (final Node z : Node.values())
				{
					assert (!(x.t.isSubtypeOf(y.t) && y.t.isSubtypeOf(z.t)))
						|| x.t.isSubtypeOf(z.t)
					: "subtype transitivity: " + x + ", " + y + ", " + z;
				}
			}
		}
	}

	/**
	 * Test that the subtype relationship is asymmetric.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sub;y &rarr; &not;y&sub;x)
	 * </nobr></span>
	 */
	@Test
	public void testSubtypeAsymmetry ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				assert (x.t.isSubtypeOf(y.t) && y.t.isSubtypeOf(x.t))
					== (x == y)
				: "subtype asymmetry: " + x + ", " + y;
			}
		}
	}

	/**
	 * Test that types are closed with respect to the type union operator.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y&thinsp;&isin;&thinsp;T)
	 * </nobr></span>
	 */
	@Test
	public void testUnionClosure ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				assert x.t.typeUnion(y.t).isInstanceOfSubtypeOf(Types.TYPE.o())
				: "union closure " + x + ", " + y;
			}
		}
	}

	/**
	 * Test that the type union operator is reflexive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x&isin;T</sub>&thinsp;(x&cup;x&thinsp;=&thinsp;x)
	 * </nobr></span>
	 */
	@Test
	public void testUnionReflexivity ()
	{
		for (final Node x : Node.values())
		{
			assert x.t.typeUnion(x.t).equals(x.t)
			: "union reflexivity: " + x;
		}
	}

	/**
	 * Test that the type union operator is commutative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cup;y = y&cup;x)
	 * </nobr></span>
	 */
	@Test
	public void testUnionCommutativity ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				assert x.t.typeUnion(y.t).equals(y.t.typeUnion(x.t))
				: "union commutativity: " + x + ", " + y;
			}
		}
	}

	/**
	 * Test that the type union operator is associative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cup;y)&cup;z = x&cup;(y&cup;z)
	 * </nobr></span>
	 */
	@Test
	public void testUnionAssociativity ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				for (final Node z : Node.values())
				{
					assert x.t.typeUnion(y.t).typeUnion(z.t).equals(
						x.t.typeUnion(y.t.typeUnion(z.t)))
					: "union associativity: " + x + ", " + y + ", " + z;
				}
			}
		}
	}

	/**
	 * Test that types are closed with respect to the type intersection
	 * operator.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y&thinsp;&isin;&thinsp;T)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionClosure ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				assert x.t.typeIntersection(y.t).isInstanceOfSubtypeOf(
					Types.TYPE.o())
				: "intersection closure " + x + ", " + y;
			}
		}
	}

	/**
	 * Test that the type intersection operator is reflexive.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x&isin;T</sub>&thinsp;(x&cap;x&thinsp;=&thinsp;x)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionReflexivity ()
	{
		for (final Node x : Node.values())
		{
			assert x.t.typeIntersection(x.t).equals(x.t)
			: "intersection reflexivity: " + x;
		}
	}

	/**
	 * Test that the type intersection operator is commutative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&cap;y = y&cap;x)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionCommutativity ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				assert x.t.typeIntersection(y.t).equals(
					y.t.typeIntersection(x.t))
				: "intersection commutativity: " + x + ", " + y;
			}
		}
	}

	/**
	 * Test that the type intersection operator is associative.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y,z&isin;T</sub>&thinsp;(x&cap;y)&cap;z = x&cap;(y&cap;z)
	 * </nobr></span>
	 */
	@Test
	public void testIntersectionAssociativity ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				for (final Node z : Node.values())
				{
					assert x.t.typeIntersection(y.t).typeIntersection(z.t)
						.equals(x.t.typeIntersection(y.t.typeIntersection(z.t)))
					: "intersection associativity: " + x + ", " + y + ", " + z;
				}
			}
		}
	}

	/**
	 * Test that the subtype relation covaries with closure return type.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Co(x)&sube;Co(y))
	 * </nobr></span>
	 */
	@Test
	public void testClosureResultCovariance ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				final AvailObject CoX = ClosureTypeDescriptor.create(
					TupleDescriptor.empty(),
					x.t);
				final AvailObject CoY = ClosureTypeDescriptor.create(
					TupleDescriptor.empty(),
					y.t);
				assert !x.t.isSubtypeOf(y.t) || CoX.isSubtypeOf(CoY)
				: "covariance (closure result): " + x + ", " + y;
;
			}
		}
	}

	/**
	 * Test that the subtype relation covaries with (homogeneous) tuple element
	 * type.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Co(x)&sube;Co(y))
	 * </nobr></span>
	 */
	@Test
	public void testTupleEntryCovariance ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				final AvailObject CoX =
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						x.t);
				final AvailObject CoY =
					TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
						IntegerRangeTypeDescriptor.wholeNumbers(),
						TupleDescriptor.empty(),
						y.t);
				assert !x.t.isSubtypeOf(y.t) || CoX.isSubtypeOf(CoY)
				: "covariance (tuple entries): " + x + ", " + y;
			}
		}
	}

	/**
	 * Test that the subtype relation <em>contravaries</em> with closure
	 * argument type.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; Con(y)&sube;Con(x))
	 * </nobr></span>
	 */
	@Test
	public void testClosureArgumentContravariance ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				final AvailObject ConX = ClosureTypeDescriptor.create(
					TupleDescriptor.from(
						x.t),
					Types.VOID_TYPE.o());
				final AvailObject ConY = ClosureTypeDescriptor.create(
					TupleDescriptor.from(
						y.t),
					Types.VOID_TYPE.o());
				assert !x.t.isSubtypeOf(y.t) || ConY.isSubtypeOf(ConX)
				: "contravariance (closure argument): " + x + ", " + y;
			}
		}
	}

	/**
	 * Check that the subtype relation covaries under the "type-of" mapping.
	 * This is simply covariance of metatypes, which is abbreviated as
	 * metacovariance.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr; T(x)&sube;T(y))
	 * </nobr></span>
	 */
	@Test
	public void testMetacovariance ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				final AvailObject Tx = x.t.type();
				final AvailObject Ty = y.t.type();
				assert !x.t.isSubtypeOf(y.t) || Tx.isSubtypeOf(Ty)
				: "metacovariance: " + x + ", " + y;
			}
		}
	}

	/**
	 * Check that a type and a proper subtype are distinct after transformation
	 * through the "type-of" mapping.
	 * <span style="border-width:thin; border-style:solid"><nobr>
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&ne;y &equiv; T(x)&ne;T(y))
	 * </nobr></span>
	 */
	//TODO Enable this when meta smears are generalized to all metas.
	//@Test
	public void testMetavariance ()
	{
		for (final Node x : Node.values())
		{
			for (final Node y : Node.values())
			{
				final AvailObject Tx = x.t.type();
				final AvailObject Ty = y.t.type();
				assert x.t.equals(y.t) == Tx.equals(Ty)
				: "metavariance: " + x + ", " + y;
			}
		}
	}
}
