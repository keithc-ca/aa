package com.cliffc.aa;

import com.cliffc.aa.type.*;
import org.junit.Before;
import org.junit.Test;

import static com.cliffc.aa.HM.*;
import static org.junit.Assert.assertEquals;

public class TestHM {

  @Before public void reset() { HM.reset(); }

  @Test(expected = RuntimeException.class)
  public void test00() {
    Syntax syn = new Ident("fred");
    HM.hm(syn);
  }

  @Test
  public void test01() {
    Syntax syn = new Con(TypeInt.con(3));
    T2 t = HM.hm(syn);
    assertEquals("3",t.p());
  }

  @Test
  public void test02() {
    Syntax syn = new Apply(new Ident("pair"),new Con(TypeInt.con(3)));
    T2 t = HM.hm(syn);
    assertEquals("{ V34 -> (pair 3 V34) }",t.p());
  }

  @Test
  public void test03() {
    // { z -> (pair2 (z 3) (z "abc")) }
    Syntax x =
      new Lambda("z",
                 new Apply(new Ident("pair2"),
                           new Apply(new Ident("z"), new Con(TypeInt.con(3))),
                           new Apply(new Ident("z"), new Con(TypeStr.ABC))));
    T2 t = HM.hm(x);
    assertEquals("{ { all -> V32 } -> (pair2 V32 V32) }",t.p());
  }

  @Test
  public void test04() {
    // let fact = {n -> (  if/else (==0 n)  1  ( * n  (fact (dec n))))} in fact;
    // let fact = {n -> (((if/else (==0 n)) 1) ((* n) (fact (dec n))))} in fact;
    Syntax fact =
      new Let("fact",
              new Lambda("n",
                         new Apply(new Ident("if/else3"),
                                   new Apply(new Ident("==0"),new Ident("n")), // Predicate
                                   new Con(TypeInt.con(1)),                    // True arm
                                   new Apply(new Ident("*2"),                  // False arm
                                             new Ident("n"),
                                             new Apply(new Ident("fact"),
                                                       new Apply(new Ident("dec"),new Ident("n")))))),
              new Ident("fact"));
    T2 t = HM.hm(fact);
    assertEquals("#{ int64 -> int64 }",t.p());
  }

  @Test
  public void test05() {
    // ({ x -> (pair2 (x 3) (x "abc")) } {x->x})
    Syntax x =
      new Apply(new Lambda("x",
                           new Apply(new Ident("pair2"),
                                     new Apply(new Ident("x"), new Con(TypeInt.con(3))),
                                     new Apply(new Ident("x"), new Con(TypeStr.ABC)))),
                new Lambda("y", new Ident("y")));

    T2 t1 = HM.hm(x);
    assertEquals("(pair2 all all)",t1.p());
  }


  @Test//(expected = RuntimeException.class)  No longer throws, but returns a recursive type
  public void test06() {
    // recursive unification
    // fn f => f f (fail)
    Syntax x =
      new Lambda("f", new Apply(new Ident("f"), new Ident("f")));
    T2 t1 = HM.hm(x);
    assertEquals("{ $34:{ $34 -> V31 } -> V31 }",t1.p());
  }

  @Test
  public void test07() {
    // let g = fn f => 5 in g g
    Syntax x =
      new Let("g",
              new Lambda("f", new Con(TypeInt.con(5))),
              new Apply(new Ident("g"), new Ident("g")));
    T2 t1 = HM.hm(x);
    assertEquals("5",t1.p());
  }

  @Test
  public void test08() {
    // example that demonstrates generic and non-generic variables:
    // fn g => let f = fn x => g in pair2 (f 3, f true)
    Syntax syn =
      new Lambda("g",
                 new Let("f",
                         new Lambda("x", new Ident("g")),
                         new Apply(new Ident("pair2"),
                                   new Apply(new Ident("f"), new Con(TypeInt.con(3))),
                                   new Apply(new Ident("f"), new Con(TypeInt.con(1))))));

    T2 t1 = HM.hm(syn);
    assertEquals("{ V2 -> (pair2 V36 V39) }",t1.p());
  }

  @Test
  public void test09() {
    // Function composition
    // fn f (fn g (fn arg (f g arg)))
    Syntax syn =
      new Lambda("f", new Lambda("g", new Lambda("arg", new Apply(new Ident("g"), new Apply(new Ident("f"), new Ident("arg"))))));

    T2 t1 = HM.hm(syn);
    assertEquals("{ { V0 -> V36 } -> { { V36 -> V35 } -> { V0 -> V35 } } }",t1.p());
  }


  @Test
  public void test10() {
    // Looking at when tvars are duplicated ("fresh" copies made).
    // This is the "map" problem with a scalar instead of a collection.
    // Takes a '{a->b}' and a 'a' for a couple of different prims.
    // let map = { fun -> {x -> (fun x) }} in (pair2 ((map str) 5) ((map factor) 2.3))
    Syntax syn =
      new Let("map",
              new Lambda("fun",
                         new Lambda("x",
                                    new Apply(new Ident("fun"),new Ident("x")))),
              new Apply(new Ident("pair2"),
                        new Apply(new Apply(new Ident("map"), new Ident("str")),
                                  new Con(TypeInt.con(5))),
                        new Apply(new Apply(new Ident("map"), new Ident("factor")),
                                  new Con(TypeFlt.con(2.3))))
              );
    T2 t1 = HM.hm(syn);
    assertEquals("(pair2 str (divmod flt64 flt64))",t1.p());
  }

  @Test
  public void test11() {
    // map takes a function and an element (collection?) and applies it (applies to collection?)
    //   let map = { fun -> {x -> (fun x) }} in
    //   { p -> 5 }
    Syntax syn =
      new Let("map",
              new Lambda("fun",
                         new Lambda("x",
                                    new Apply(new Ident("fun"),new Ident("x")))),
              new Lambda("p",
                         new Con(TypeInt.con(5))));
    T2 t1 = HM.hm(syn);
    assertEquals("{ V2 -> 5 }",t1.p());
  }

  @Test
  public void test12() {
    Syntax syn =
      new Let("map",
              new Lambda("fun",
                         new Lambda("x",
                                    new Con(TypeInt.con(2)))),
              new Apply(new Apply(new Ident("map"),
                                  new Con(TypeInt.con(3))),
                        new Con(TypeInt.con(5))));
    T2 t1 = HM.hm(syn);
    assertEquals("2",t1.p());
  }

  @Test
  public void test13() {
    // map takes a function and an element (collection?) and applies it (applies to collection?)
    //   let map = { fun -> {x -> (fun x) }} in
    //      (map {a -> 3} 5)
    // Should return  { p -> [5,5] }
    Syntax syn =
      new Let("map",
              new Lambda("fun",
                         new Lambda("x",
                                    new Apply(new Ident("fun"),new Ident("x")))),
              new Apply(new Apply(new Ident("map"),
                                  new Lambda("a",new Con(TypeInt.con(3)))),
                        new Con(TypeInt.con(5))));
    T2 t1 = HM.hm(syn);
    assertEquals("3",t1.p());
  }

  @Test
  public void test14() {
    // map takes a function and an element (collection?) and applies it (applies to collection?)
    //   let map = { fun -> {x -> (fun x) }} in
    //      (map {a -> [a,a]} 5)
    Syntax syn =
      new Let("map",
              new Lambda("fun",
                         new Lambda("x",
                                    new Apply(new Ident("fun"),new Ident("x")))),
              new Apply(new Apply(new Ident("map"),
                                  new Lambda("a",
                                             new Apply(new Ident("pair2"),
                                                       new Ident("a"),
                                                       new Ident("a")))),
                        new Con(TypeInt.con(5))));
    T2 t1 = HM.hm(syn);
    assertEquals("(pair2 5 5)",t1.p());
  }

  @Test
  public void test15() {
    //   let fcn = { p -> {a -> pair[a,a]} } in
    // map takes a function and an element (collection?) and applies it (applies to collection?)
    //   let map = { fun -> {x -> (fun x) }} in
    // Should return  { p -> [5,5] }
    Syntax syn =
      new Let("fcn",
              new Lambda("p",
                         new Lambda("a",
                                    new Apply(new Ident("pair2"),
                                              new Ident("a"),
                                              new Ident("a")))),
              new Let("map",
                      new Lambda("fun",
                                 new Lambda("x",
                                            new Apply(new Ident("fun"),new Ident("x")))),
                      new Lambda("q",
                                 new Apply(new Apply(new Ident("map"),
                                                     new Apply(new Ident("fcn"),new Ident("q"))),
                                           new Con(TypeInt.con(5))))));
    T2 t1 = HM.hm(syn);
    assertEquals("{ V4 -> (pair2 5 5) }",t1.p());
  }


  @Test(expected = RuntimeException.class)
  public void test16() {
    // Checking behavior when using "if/else" to merge two functions with
    // sufficiently different signatures, then attempting to pass them to a map
    // & calling internally.
    // fcn takes a predicate 'p' and returns one of two fcns.
    //   let fcn = { p -> (if/else3 p {a -> pair[a,a]} {b -> pair[b,pair[3,b]]}) } in
    // map takes a function and an element (collection?) and applies it (applies to collection?)
    //   let map = { fun -> {x -> (fun x) }} in
    // Should return either { p -> p ? [5,5] : [5,[3,5]] }
    //   { q -> ((map (fcn q)) 5) }
    Syntax syn =
      new Let("fcn",
              new Lambda("p",
                         new Apply(new Ident("if/else3"),
                                   new Ident("p"), // p ?
                                   new Lambda("a",
                                              new Apply(new Ident("pair2"),
                                                        new Ident("a"),
                                                        new Ident("a"))),
                                   new Lambda("b",
                                              new Apply(new Ident("pair2"),
                                                        new Ident("b"),
                                                        new Apply(new Ident("pair2"),
                                                                  new Con(TypeInt.con(3)),
                                                                  new Ident("b")))))),
              new Let("map",
                      new Lambda("fun",
                                 new Lambda("x",
                                            new Apply(new Ident("fun"),new Ident("x")))),
                      new Lambda("q",
                                 new Apply(new Apply(new Ident("map"),
                                                     new Apply(new Ident("fcn"),new Ident("q"))),
                                           new Con(TypeInt.con(5))))));
    // Ultimately, unifies "a" with "pair[3,a]" which throws recursive unification.
    T2 t1 = HM.hm(syn);
    assertEquals("TBD",t1.p());
  }

}
